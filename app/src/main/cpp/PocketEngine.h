#ifndef POCKET_ENGINE_H
#define POCKET_ENGINE_H

#include <jni.h>
#include <stdint.h>
#include <stddef.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>
#include <atomic>

namespace pocketcoded {

/**
 * GGML Quantization Types matching llama.cpp ggml_type enum values.
 * Used to configure quantized K/V caches and sub-2-bit importance matrix weights on 4GB/6GB RAM devices.
 */
enum GgmlType : int32_t {
    GGML_TYPE_F32     = 0,
    GGML_TYPE_F16     = 1,
    GGML_TYPE_Q4_0    = 2,
    GGML_TYPE_Q4_1    = 3,
    GGML_TYPE_Q5_0    = 6,
    GGML_TYPE_Q5_1    = 7,
    GGML_TYPE_Q8_0    = 8,
    GGML_TYPE_Q4_K    = 12,
    GGML_TYPE_Q6_K    = 14,
    GGML_TYPE_IQ2_XXS = 16
};

struct NativeEngineConfig {
    std::string model_path;
    bool use_mmap = true;
    bool use_mlock = false;
    bool flash_attn = true;
    int32_t n_ctx = 4096;
    int32_t n_ubatch = 256;
    int32_t n_threads = 4;
    int32_t n_gpu_layers = 0;
    GgmlType type_k = GGML_TYPE_Q8_0;
    GgmlType type_v = GGML_TYPE_Q4_0;
    bool enable_pld = true;
    int32_t pld_ngram_len = 3;
    int32_t pld_max_draft = 6;
    int32_t n_keep_prefix = 0;
    // Ultra-Low-RAM & Speed Optimizations for 4GB/6GB Smartphones:
    bool enable_layer_streaming = true;      // Rolling madvise(MADV_WILLNEED/MADV_DONTNEED) window
    int32_t active_layer_window = 6;         // Max layers resident in physical RAM simultaneously
    bool enable_self_speculative_skip = true;// Early-exit 8-layer draft + 28-layer batch verify
    bool pin_big_cpu_cores = true;           // sched_setaffinity to Cortex-A7x/X prime cores
};

struct KvCacheSlot {
    int32_t pos = 0;
    int32_t token_id = 0;
    bool is_pinned = false;
};

struct PldDraftResult {
    std::vector<int32_t> drafted_tokens;
    int32_t match_source_index = -1;
    int32_t ngram_order = 0;
};

struct EngineMetrics {
    uint64_t mmap_bytes = 0;
    uint64_t resident_rss_cap_bytes = 0;
    uint64_t kv_cache_bytes = 0;
    uint64_t kv_cache_saved_bytes = 0;
    int32_t kv_tokens_used = 0;
    int32_t kv_tokens_capacity = 4096;
    int32_t pinned_prefix_tokens = 0;
    double last_prefill_tok_per_sec = 0.0;
    double last_decode_tok_per_sec = 0.0;
    int32_t pld_drafted_total = 0;
    int32_t pld_accepted_total = 0;
    int32_t layer_skip_drafted_total = 0;
    bool big_cores_pinned = false;
    std::string architecture;
    uint32_t gguf_version = 0;
    uint64_t tensor_count = 0;
};

} // namespace pocketcoded

#endif // POCKET_ENGINE_H
