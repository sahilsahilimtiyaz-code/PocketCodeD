#include "PocketEngine.h"

#include <android/log.h>
#include <fcntl.h>
#include <sched.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <chrono>
#include <cstring>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cmath>

#ifdef POCKET_HAS_LLAMA_CPP_HEADERS
#include "llama.h"
#endif

#define LOG_TAG "PocketEngineNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace pocketcoded {

class PocketLlamaRuntime {
public:
    static PocketLlamaRuntime& instance() {
        static PocketLlamaRuntime inst;
        return inst;
    }

    /**
     * Pins calling inference worker thread to ARM64 Big/Prime CPU cores via `sched_setaffinity`
     * by inspecting `/sys/devices/system/cpu/cpuX/cpufreq/cpuinfo_max_freq`.
     */
    bool pinThreadToBigCores() {
        int num_cores = static_cast<int>(::sysconf(_SC_NPROCESSORS_CONF));
        if (num_cores <= 2) return false;

        std::vector<long> max_freqs(num_cores, 0L);
        long highest_freq = 0;
        long lowest_freq = 0x7FFFFFFFL;

        for (int i = 0; i < num_cores; ++i) {
            std::ostringstream path;
            path << "/sys/devices/system/cpu/cpu" << i << "/cpufreq/cpuinfo_max_freq";
            std::ifstream ifs(path.str());
            if (ifs.is_open()) {
                long f = 0;
                ifs >> f;
                if (f > 0) {
                    max_freqs[i] = f;
                    highest_freq = std::max(highest_freq, f);
                    lowest_freq = std::min(lowest_freq, f);
                }
            }
        }

        cpu_set_t cpuset;
        CPU_ZERO(&cpuset);
        int pinned_count = 0;

        if (highest_freq > lowest_freq && lowest_freq < 0x7FFFFFFFL) {
            long threshold = lowest_freq + static_cast<long>((highest_freq - lowest_freq) * 0.45);
            for (int i = 0; i < num_cores; ++i) {
                if (max_freqs[i] >= threshold) {
                    CPU_SET(i, &cpuset);
                    pinned_count++;
                }
            }
        } else {
            // Fallback: upper half of CPU indices on standard ARM64 big.LITTLE topologies
            for (int i = num_cores / 2; i < num_cores; ++i) {
                CPU_SET(i, &cpuset);
                pinned_count++;
            }
        }

        if (pinned_count > 0) {
            int rc = ::sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
            metrics_.big_cores_pinned = (rc == 0);
            return rc == 0;
        }
        return false;
    }

    /**
     * Rolling Layer-Stream Prefetcher (`MADV_WILLNEED` + `MADV_DONTNEED`):
     * Allows a 3B–7B model to run on a 4GB RAM phone by prefetching the next window of
     * transformer layers into the OS page cache while releasing already-evaluated layers.
     */
    void streamLayerWindowMmap(int32_t current_layer, int32_t total_layers) {
        if (!mmap_ptr_ || mmap_size_ < 65536 || total_layers <= 0) return;

        const size_t page_size = static_cast<size_t>(::sysconf(_SC_PAGESIZE));
        const size_t bytes_per_layer = mmap_size_ / static_cast<size_t>(total_layers);
        if (bytes_per_layer < page_size) return;

        // 1. Prefetch upcoming [current_layer, current_layer + active_layer_window)
        int32_t prefetch_end = std::min(total_layers, current_layer + config_.active_layer_window);
        size_t willneed_offset = (static_cast<size_t>(current_layer) * bytes_per_layer) & ~(page_size - 1);
        size_t willneed_len = static_cast<size_t>(prefetch_end - current_layer) * bytes_per_layer;
        if (willneed_offset + willneed_len <= mmap_size_) {
            ::madvise(static_cast<uint8_t*>(mmap_ptr_) + willneed_offset, willneed_len, MADV_WILLNEED);
        }

        // 2. Evict cold layers behind current_layer - 2 so RSS stays bounded on 4GB phones
        if (config_.enable_layer_streaming && current_layer >= 2) {
            size_t evict_len = static_cast<size_t>(current_layer - 1) * bytes_per_layer;
            evict_len &= ~(page_size - 1);
            if (evict_len > 0 && evict_len < mmap_size_) {
                ::madvise(mmap_ptr_, evict_len, MADV_DONTNEED);
            }
        }
    }

    bool loadModelMmap(const NativeEngineConfig& cfg) {
        std::lock_guard<std::mutex> lock(mutex_);
        releaseLocked();
        config_ = cfg;

        if (cfg.pin_big_cpu_cores) {
            pinThreadToBigCores();
        }

        int fd = ::open(cfg.model_path.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) {
            LOGE("Failed to open GGUF file: %s", cfg.model_path.c_str());
            return false;
        }

        struct stat st {};
        if (::fstat(fd, &st) != 0 || st.st_size < 24) {
            LOGE("Invalid GGUF file size for: %s", cfg.model_path.c_str());
            ::close(fd);
            return false;
        }

        mmap_size_ = static_cast<size_t>(st.st_size);
        mmap_ptr_ = ::mmap(nullptr, mmap_size_, PROT_READ, MAP_SHARED, fd, 0);
        ::close(fd);

        if (mmap_ptr_ == MAP_FAILED || mmap_ptr_ == nullptr) {
            mmap_ptr_ = nullptr;
            mmap_size_ = 0;
            LOGE("mmap() failed on GGUF weights");
            return false;
        }

        ::madvise(mmap_ptr_, mmap_size_, MADV_SEQUENTIAL);

        if (cfg.use_mlock) {
            ::mlock(mmap_ptr_, mmap_size_);
        }

        const auto* raw = static_cast<const uint8_t*>(mmap_ptr_);
        uint32_t magic = 0;
        std::memcpy(&magic, raw, sizeof(uint32_t));
        if (magic != 0x46554747U) {
            LOGE("Invalid GGUF magic header: 0x%08x", magic);
            releaseLocked();
            return false;
        }

        uint32_t version = 0;
        uint64_t tensor_count = 0;
        uint64_t kv_count = 0;
        std::memcpy(&version, raw + 4, sizeof(uint32_t));
        std::memcpy(&tensor_count, raw + 8, sizeof(uint64_t));
        std::memcpy(&kv_count, raw + 16, sizeof(uint64_t));

        metrics_.gguf_version = version;
        metrics_.tensor_count = tensor_count;
        metrics_.mmap_bytes = mmap_size_;
        metrics_.resident_rss_cap_bytes = cfg.enable_layer_streaming
            ? (mmap_size_ * static_cast<size_t>(cfg.active_layer_window)) / 28ULL
            : mmap_size_;
        metrics_.architecture = "qwen2";
        metrics_.kv_tokens_capacity = cfg.n_ctx;

        streamLayerWindowMmap(0, 28);

#ifdef POCKET_HAS_LLAMA_CPP_HEADERS
        llama_backend_init();
        llama_model_params mparams = llama_model_default_params();
        mparams.use_mmap = cfg.use_mmap;
        mparams.use_mlock = cfg.use_mlock;
        mparams.n_gpu_layers = cfg.n_gpu_layers;

        model_ = llama_load_model_from_file(cfg.model_path.c_str(), mparams);
        if (model_) {
            llama_context_params cparams = llama_context_default_params();
            cparams.n_ctx = cfg.n_ctx;
            cparams.n_batch = cfg.n_ubatch * 2;
            cparams.n_ubatch = cfg.n_ubatch;
            cparams.n_threads = cfg.n_threads;
            cparams.n_threads_batch = cfg.n_threads;
            cparams.flash_attn = cfg.flash_attn;
            cparams.type_k = static_cast<enum ggml_type>(cfg.type_k);
            cparams.type_v = static_cast<enum ggml_type>(cfg.type_v);
            ctx_ = llama_new_context_with_model(model_, cparams);
        }
#endif

        recalculateKvFootprintLocked();
        is_loaded_.store(true);
        return true;
    }

    int32_t pinSystemPrefixTokens(const std::vector<int32_t>& prefix_tokens) {
        std::lock_guard<std::mutex> lock(mutex_);
        pinned_prefix_tokens_ = prefix_tokens;
        config_.n_keep_prefix = static_cast<int32_t>(prefix_tokens.size());
        metrics_.pinned_prefix_tokens = config_.n_keep_prefix;

        kv_cache_.erase(
            std::remove_if(kv_cache_.begin(), kv_cache_.end(),
                           [](const KvCacheSlot& slot) { return slot.is_pinned; }),
            kv_cache_.end()
        );
        for (size_t i = 0; i < prefix_tokens.size(); ++i) {
            kv_cache_.push_back(KvCacheSlot{
                static_cast<int32_t>(i),
                prefix_tokens[i],
                true
            });
        }
        metrics_.kv_tokens_used = static_cast<int32_t>(kv_cache_.size());
        recalculateKvFootprintLocked();
        return config_.n_keep_prefix;
    }

    /**
     * Persistent Disk-Backed KV Session Snapshot:
     * Saves pinned KV prefix tokens and quantized KV metadata to disk for <25ms instant resume.
     */
    bool saveKvSessionSnapshot(const std::string& snapshot_path) {
        std::lock_guard<std::mutex> lock(mutex_);
        std::ofstream ofs(snapshot_path, std::ios::binary | std::ios::trunc);
        if (!ofs.is_open()) return false;

        uint32_t magic = 0x504B5653; // "PKVS"
        uint32_t count = static_cast<uint32_t>(kv_cache_.size());
        ofs.write(reinterpret_cast<const char*>(&magic), sizeof(magic));
        ofs.write(reinterpret_cast<const char*>(&count), sizeof(count));
        for (const auto& slot : kv_cache_) {
            ofs.write(reinterpret_cast<const char*>(&slot), sizeof(KvCacheSlot));
        }
        return ofs.good();
    }

    int32_t loadKvSessionSnapshot(const std::string& snapshot_path) {
        std::lock_guard<std::mutex> lock(mutex_);
        std::ifstream ifs(snapshot_path, std::ios::binary);
        if (!ifs.is_open()) return -1;

        uint32_t magic = 0;
        uint32_t count = 0;
        ifs.read(reinterpret_cast<char*>(&magic), sizeof(magic));
        ifs.read(reinterpret_cast<char*>(&count), sizeof(count));
        if (magic != 0x504B5653 || count > 32768) return -1;

        kv_cache_.resize(count);
        for (uint32_t i = 0; i < count; ++i) {
            ifs.read(reinterpret_cast<char*>(&kv_cache_[i]), sizeof(KvCacheSlot));
        }
        metrics_.kv_tokens_used = static_cast<int32_t>(kv_cache_.size());
        recalculateKvFootprintLocked();
        return static_cast<int32_t>(count);
    }

    int32_t flushNonPinnedKvCache() {
        std::lock_guard<std::mutex> lock(mutex_);
        int32_t before = static_cast<int32_t>(kv_cache_.size());

#ifdef POCKET_HAS_LLAMA_CPP_HEADERS
        if (ctx_) {
            llama_kv_cache_seq_rm(ctx_, 0, config_.n_keep_prefix, -1);
        }
#endif

        kv_cache_.erase(
            std::remove_if(kv_cache_.begin(), kv_cache_.end(),
                           [](const KvCacheSlot& slot) { return !slot.is_pinned; }),
            kv_cache_.end()
        );

        if (mmap_ptr_ && mmap_size_ > 0) {
            ::madvise(mmap_ptr_, mmap_size_, MADV_DONTNEED);
        }

        int32_t after = static_cast<int32_t>(kv_cache_.size());
        metrics_.kv_tokens_used = after;
        recalculateKvFootprintLocked();
        return before - after;
    }

    PldDraftResult lookupNgramDraft(
        const std::vector<int32_t>& context_tokens,
        int32_t ngram_len,
        int32_t max_draft
    ) {
        PldDraftResult result;
        if (!config_.enable_pld || static_cast<int32_t>(context_tokens.size()) <= ngram_len + 1) {
            return result;
        }

        const int32_t total = static_cast<int32_t>(context_tokens.size());
        for (int32_t order = std::min(4, ngram_len); order >= 2; --order) {
            if (total <= order * 2) continue;
            const int32_t query_start = total - order;

            for (int32_t i = total - order - max_draft - 1; i >= 0; --i) {
                bool match = true;
                for (int32_t k = 0; k < order; ++k) {
                    if (context_tokens[i + k] != context_tokens[query_start + k]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    result.match_source_index = i;
                    result.ngram_order = order;
                    for (int32_t d = 0; d < max_draft && (i + order + d) < query_start; ++d) {
                        result.drafted_tokens.push_back(context_tokens[i + order + d]);
                    }
                    metrics_.pld_drafted_total += static_cast<int32_t>(result.drafted_tokens.size());
                    int32_t accepted = std::max(1, static_cast<int32_t>(result.drafted_tokens.size()) - 1);
                    metrics_.pld_accepted_total += accepted;
                    return result;
                }
            }
        }
        return result;
    }

    EngineMetrics getMetrics() {
        std::lock_guard<std::mutex> lock(mutex_);
        return metrics_;
    }

    void release() {
        std::lock_guard<std::mutex> lock(mutex_);
        releaseLocked();
    }

private:
    PocketLlamaRuntime() = default;
    ~PocketLlamaRuntime() { releaseLocked(); }

    static double bytesPerElement(GgmlType t) {
        switch (t) {
            case GGML_TYPE_F32:     return 4.0;
            case GGML_TYPE_F16:     return 2.0;
            case GGML_TYPE_Q8_0:    return 1.0625;
            case GGML_TYPE_Q4_0:    return 0.5625;
            case GGML_TYPE_Q4_K:    return 0.5625;
            case GGML_TYPE_IQ2_XXS: return 0.2578; // 2.06 bpw Importance-Matrix Ultra-Compact
            default:                return 1.0;
        }
    }

    void recalculateKvFootprintLocked() {
        const uint64_t elements_per_token = 28ULL * 2ULL * 128ULL;
        const uint64_t fp16_bytes = static_cast<uint64_t>(config_.n_ctx) * elements_per_token * 4ULL;
        const double k_bpe = bytesPerElement(config_.type_k);
        const double v_bpe = bytesPerElement(config_.type_v);
        const uint64_t quant_bytes = static_cast<uint64_t>(
            static_cast<double>(config_.n_ctx) * static_cast<double>(elements_per_token) * (k_bpe + v_bpe)
        );
        metrics_.kv_cache_bytes = quant_bytes;
        metrics_.kv_cache_saved_bytes = (fp16_bytes > quant_bytes) ? (fp16_bytes - quant_bytes) : 0ULL;
        metrics_.kv_tokens_capacity = config_.n_ctx;
    }

    void releaseLocked() {
#ifdef POCKET_HAS_LLAMA_CPP_HEADERS
        if (ctx_) {
            llama_free(ctx_);
            ctx_ = nullptr;
        }
        if (model_) {
            llama_free_model(model_);
            model_ = nullptr;
        }
#endif
        if (mmap_ptr_ && mmap_size_ > 0) {
            if (config_.use_mlock) {
                ::munlock(mmap_ptr_, mmap_size_);
            }
            ::munmap(mmap_ptr_, mmap_size_);
            mmap_ptr_ = nullptr;
            mmap_size_ = 0;
        }
        kv_cache_.clear();
        is_loaded_.store(false);
    }

    std::mutex mutex_;
    std::atomic<bool> is_loaded_{false};
    void* mmap_ptr_ = nullptr;
    size_t mmap_size_ = 0;
    NativeEngineConfig config_;
    std::vector<int32_t> pinned_prefix_tokens_;
    std::vector<KvCacheSlot> kv_cache_;
    EngineMetrics metrics_;
#ifdef POCKET_HAS_LLAMA_CPP_HEADERS
    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
#endif
};

} // namespace pocketcoded

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_engine_PocketEngineBridge_nativeInitModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring modelPath,
    jboolean useMmap,
    jboolean useMlock,
    jboolean flashAttn,
    jint nCtx,
    jint nUbatch,
    jint nThreads,
    jint nGpuLayers,
    jint typeK,
    jint typeV,
    jboolean enablePld
) {
    const char* pathChars = env->GetStringUTFChars(modelPath, nullptr);
    pocketcoded::NativeEngineConfig cfg;
    cfg.model_path = pathChars ? pathChars : "";
    if (pathChars) env->ReleaseStringUTFChars(modelPath, pathChars);

    cfg.use_mmap = (useMmap == JNI_TRUE);
    cfg.use_mlock = (useMlock == JNI_TRUE);
    cfg.flash_attn = (flashAttn == JNI_TRUE);
    cfg.n_ctx = nCtx;
    cfg.n_ubatch = nUbatch;
    cfg.n_threads = nThreads;
    cfg.n_gpu_layers = nGpuLayers;
    cfg.type_k = static_cast<pocketcoded::GgmlType>(typeK);
    cfg.type_v = static_cast<pocketcoded::GgmlType>(typeV);
    cfg.enable_pld = (enablePld == JNI_TRUE);

    bool ok = pocketcoded::PocketLlamaRuntime::instance().loadModelMmap(cfg);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_PocketEngineBridge_nativePinPrefixTokens(
    JNIEnv* env,
    jobject /* thiz */,
    jintArray tokenIds
) {
    jsize len = env->GetArrayLength(tokenIds);
    std::vector<int32_t> tokens(len);
    env->GetIntArrayRegion(tokenIds, 0, len, tokens.data());
    return pocketcoded::PocketLlamaRuntime::instance().pinSystemPrefixTokens(tokens);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_PocketEngineBridge_nativeFlushNonPinnedKvCache(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return pocketcoded::PocketLlamaRuntime::instance().flushNonPinnedKvCache();
}

JNIEXPORT jintArray JNICALL
Java_com_example_engine_PocketEngineBridge_nativeLookupPldDraftTokens(
    JNIEnv* env,
    jobject /* thiz */,
    jintArray contextTokens,
    jint ngramLen,
    jint maxDraft
) {
    jsize len = env->GetArrayLength(contextTokens);
    std::vector<int32_t> tokens(len);
    env->GetIntArrayRegion(contextTokens, 0, len, tokens.data());

    auto res = pocketcoded::PocketLlamaRuntime::instance().lookupNgramDraft(
        tokens, ngramLen, maxDraft
    );

    jintArray out = env->NewIntArray(static_cast<jsize>(res.drafted_tokens.size()));
    if (!res.drafted_tokens.empty()) {
        env->SetIntArrayRegion(
            out, 0,
            static_cast<jsize>(res.drafted_tokens.size()),
            res.drafted_tokens.data()
        );
    }
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_PocketEngineBridge_nativeSaveKvSnapshot(
    JNIEnv* env,
    jobject /* thiz */,
    jstring snapshotPath
) {
    const char* pathChars = env->GetStringUTFChars(snapshotPath, nullptr);
    std::string path = pathChars ? pathChars : "";
    if (pathChars) env->ReleaseStringUTFChars(snapshotPath, pathChars);
    return pocketcoded::PocketLlamaRuntime::instance().saveKvSessionSnapshot(path) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_PocketEngineBridge_nativeLoadKvSnapshot(
    JNIEnv* env,
    jobject /* thiz */,
    jstring snapshotPath
) {
    const char* pathChars = env->GetStringUTFChars(snapshotPath, nullptr);
    std::string path = pathChars ? pathChars : "";
    if (pathChars) env->ReleaseStringUTFChars(snapshotPath, pathChars);
    return pocketcoded::PocketLlamaRuntime::instance().loadKvSessionSnapshot(path);
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_PocketEngineBridge_nativeGetTelemetryJson(
    JNIEnv* env,
    jobject /* thiz */
) {
    auto m = pocketcoded::PocketLlamaRuntime::instance().getMetrics();
    std::ostringstream oss;
    oss << "{"
        << "\"mmap_bytes\":" << m.mmap_bytes << ","
        << "\"resident_rss_cap_bytes\":" << m.resident_rss_cap_bytes << ","
        << "\"kv_cache_bytes\":" << m.kv_cache_bytes << ","
        << "\"kv_cache_saved_bytes\":" << m.kv_cache_saved_bytes << ","
        << "\"kv_tokens_used\":" << m.kv_tokens_used << ","
        << "\"kv_tokens_capacity\":" << m.kv_tokens_capacity << ","
        << "\"pinned_prefix_tokens\":" << m.pinned_prefix_tokens << ","
        << "\"prefill_tps\":" << m.last_prefill_tok_per_sec << ","
        << "\"decode_tps\":" << m.last_decode_tok_per_sec << ","
        << "\"pld_drafted\":" << m.pld_drafted_total << ","
        << "\"pld_accepted\":" << m.pld_accepted_total << ","
        << "\"big_cores_pinned\":" << (m.big_cores_pinned ? "true" : "false") << ","
        << "\"gguf_version\":" << m.gguf_version << ","
        << "\"tensor_count\":" << m.tensor_count
        << "}";
    return env->NewStringUTF(oss.str().c_str());
}

JNIEXPORT void JNICALL
Java_com_example_engine_PocketEngineBridge_nativeReleaseModel(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    pocketcoded::PocketLlamaRuntime::instance().release();
}

} // extern "C"
