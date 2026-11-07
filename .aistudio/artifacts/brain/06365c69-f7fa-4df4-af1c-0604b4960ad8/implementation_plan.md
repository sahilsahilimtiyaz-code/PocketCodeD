# PocketCodeD (Pocket Code Daemon)

PocketCodeD transforms constrained 4GB and 6GB RAM Android smartphones into high-speed, OpenAI- and DeepSeek-compatible local coding LLM inference daemons (`127.0.0.1` for Termux and `0.0.0.0` for LAN IDEs). It combines a native C++ JNI inference engine (with zero-copy `mmap`, Flash Attention, quantized KV caches, Prefix KV pinning, and Prompt Lookup Decoding) with a "Harness Diet" middleware interceptor, dynamic JSON-Schema-to-GBNF grammar enforcement, and a Hybrid Gemini High-Thinking (`gemini-4-argon` with `ThinkingLevel.HIGH`) router.

### User Review & Critical Decisions

> [!IMPORTANT]
> The architectural choices below incorporate your confirmed preferences from Phase 1 and establish the blueprint for execution.

- **Confirmed Decision 1 — Hybrid Gemini High-Thinking Router**: The local API gateway routes standard coding completions, Fill-In-The-Middle (FIM), and fast tool loops to the native C++ GGUF engine while automatically escalating complex multi-file architectural queries (or explicit requests to the `gemini-4-argon` / `deepseek-reasoner` model aliases) to Firebase AI Logic (`gemini-4-argon` configured with `ThinkingLevel.HIGH` and no `maxOutputTokens` cap).
- **Confirmed Decision 2 — Bundled Micro-GGUF Synthesizer & HuggingFace Model Hub**: On first launch, PocketCodeD synthesizes a valid, memory-mapped micro-GGUF coding weight file on-device so the native C++ NDK engine (`mmap`, quantized KV cache, PLD n-gram speculative drafting, and GBNF grammar sampler) can be tested immediately with zero wait time, alongside a resumable HuggingFace GGUF downloader for `Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf`, `Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf`, and `DeepSeek-Coder-1.3B-Instruct-Q4_K_M.gguf`.
- **Confirmed Decision 3 — Interactive On-Device API & GBNF Grammar Test Console**: The dashboard includes a dedicated interactive playground where developers can test `/v1/chat/completions`, `/v1/completions` (FIM), JSON tool-schema-to-GBNF compilation, Prompt Lookup Decoding n-gram drafting, and High-Thinking routing directly on the phone.

---

### 1. Overview & Core Concept

- **What It Does**: Runs a persistent Android Foreground Service hosting an OpenAI- and DeepSeek-compatible HTTP/SSE server backed by a native ARM64 C++ inference engine and a smart pre-tokenization middleware layer tailored for coding harnesses like `opencode`, Aider, and Continue.
- **Target Audience / Persona**: Mobile & edge AI developers, Termux power users, and software engineers who want an always-available local coding copilot and agent daemon running directly on their smartphone hardware.
- **Key Value**: Solves the two biggest bottlenecks of running coding agents on 4GB–6GB phones—RAM exhaustion and fragile tool-call formatting—by compressing bloated 5,000+ token agent prompts, quantizing the KV cache by up to 72%, accelerating code edits 2x–3x with zero-memory Prompt Lookup Decoding (PLD), and enforcing 100% valid JSON tool calls via dynamic GBNF grammars.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Zero-State Hardware Profiling & Instant Readiness**: On launch, the RAM Watchdog inspects `ActivityManager.MemoryInfo`, automatically applies the **4GB RAM Profile** (`ctx=4096`, `type_k=q8_0`, `type_v=q4_0`, `n_ubatch=256`) or **6GB+ RAM Profile** (`ctx=8192`, `type_k=q8_0`, `type_v=q8_0`, `n_ubatch=512`), and synthesizes a testable local micro-GGUF model so the daemon can start within seconds.
  2. **Server Control Deck & Harness Pairing**: With one tap, the user starts the persistent Foreground Service (`PARTIAL_WAKE_LOCK` + `FOREGROUND_SERVICE_SPECIAL_USE`), toggles between Localhost (`127.0.0.1:8080`) and LAN (`0.0.0.0:8080`), generates or revokes `sk-mobile-...` API keys, and copies a ready-to-use `opencode.json` configuration snippet.
  3. **Model Hub & Hardware Tuner**: Users download curated GGUF models from HuggingFace with live byte-level progress and pause/resume support, or fine-tune Context Window, CPU big-core threads, GPU Vulkan layers, KV Cache quantization (`f16`, `q8_0`, `q4_0`), Prefix KV Pinning, and Prompt Lookup Decoding (`n-gram` draft length).
  4. **Interactive API & GBNF Sandbox + Live Telemetry Console**: Users inspect real-time token-compression ratios from the "Harness Diet" middleware, view generated GBNF grammars from OpenAI tool schemas, trigger live streaming chat/FIM/High-Thinking requests, and monitor KV cache fill percentage, Prefill tok/s, Decode tok/s, and PLD draft acceptance rates.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Low-Overhead Cyberpunk Systems Terminal** — high-contrast obsidian surfaces, phosphor emerald and cyber-cyan telemetry accents, crisp monospace metrics, and zero visual bloat.
  - *Color Palette & Mood*:
    - `--background`: `#090D16` (Deep Obsidian Void)
    - `--surface`: `#111827` (Terminal Slate Card with subtle 1dp `#1F2937` border)
    - `--primary`: `#10B981` (Phosphor Matrix Emerald — active daemon, tok/s indicators, KV hit gauge)
    - `--secondary`: `#06B6D4` (Cyber Cyan — PLD speculative tokens, GBNF grammar badges, network endpoints)
    - `--tertiary`: `#A855F7` (Argon Violet — Gemini High-Thinking mode & hybrid cloud escalation)
    - `--warning` / `--danger`: `#F59E0B` (Amber RAM pressure) / `#EF4444` (Critical OOM trim alert)
  - *Typography & Hierarchy*: Bundled Google Fonts (`JetBrains Mono` for code, GBNF rules, logs, API keys, and tok/s counters; `Space Grotesk` for technical section headers and HUD metrics).
  - *Component Styling & Layout*: Adaptive Material 3 navigation (bottom `NavigationBar` on compact handhelds, side `NavigationRail` on expanded tablets/foldables) with four focused workspaces: **Control Deck**, **Model & Hardware Tuner**, **API & GBNF Sandbox**, and **Live Telemetry Console**.
- **Interactive Feedback & Motion**: Pulsing emerald status beacon when the Ktor daemon is listening, live animated progress bars for RAM and KV cache occupancy, one-tap clipboard confirmation snackbars, and smooth token-streaming terminal output.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native C++ JNI Engine with Zero-Copy `mmap` & Prompt Lookup Decoding**
  - *Chosen Approach*: Compile a dedicated C++17 JNI shared library (`libpocketcoded_jni.so`) via CMake with ARM64 optimizations (`dotprod`, `i8mm`, FP16 vector arithmetic, KleidiAI flags) that memory-maps GGUF files (`mmap`) directly from disk, manages quantized KV cache buffers (`GGML_TYPE_Q8_0` / `GGML_TYPE_Q4_0`), pins system prompt prefixes (`n_keep`), and executes n-gram Prompt Lookup Decoding (PLD) over the input context.
  - *Why*: Avoids copying multi-gigabyte model weights into the constrained Android Java heap and delivers 2x–3x faster code generation on repetitive code edits with zero extra RAM overhead.
  - *Alternatives Considered*: Pure Java/Kotlin inference or unquantized FP16 KV caches, which immediately trigger Android's `lmkd` (Low Memory Killer Daemon) on 4GB devices.
- **Decision 2: Pre-Tokenization "Harness Diet" Middleware & Deterministic GBNF Compiler**
  - *Chosen Approach*: Intercept every `/v1/chat/completions` payload before tokenization to (a) strip verbose agent boilerplate into a compact `Qwen2.5-Coder` system prompt, (b) apply a smart sliding window at 85% context capacity that summarizes/truncates old `role: "tool"` outputs while preserving file paths and the last 3 turns, and (c) compile OpenAI `tools` JSON schemas into strict `llama.cpp` GBNF production rules.
  - *Why*: 1.5B–3B models hallucinate invalid JSON when overwhelmed by 5,000-token system prompts; grammar-constrained sampling guarantees 100% syntactically valid tool calls for `opencode` and Aider.
- **Decision 3: Hybrid Routing with Firebase AI Logic (`gemini-4-argon` High-Thinking)**
  - *Chosen Approach*: Integrate Firebase AI Logic (`Firebase.ai`) configured with `gemini-4-argon` and `thinkingLevel = ThinkingLevel.HIGH` (without setting `maxOutputTokens`). The gateway routes requests to `gemini-4-argon` when the client requests the `gemini-4-argon` / `deepseek-reasoner` model alias or when the Hybrid Router detects deep architectural reasoning queries that exceed local edge capacity.
  - *Why*: Gives developers the best of both worlds—zero-latency offline/local GGUF inference for rapid coding loops and frontier High-Thinking reasoning through the exact same OpenAI-compatible endpoint.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                   EXTERNAL / LOCAL CODING HARNESSES                        │
│        opencode • Aider • Continue • Termux (127.0.0.1 / LAN 0.0.0.0)      │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ HTTP / SSE (Bearer sk-mobile-***)
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│             ANDROID FOREGROUND SERVICE (PocketDaemonService)               │
│     PARTIAL_WAKE_LOCK • FOREGROUND_SERVICE_SPECIAL_USE • RAM Watchdog      │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │               EMBEDDED HTTP & SSE GATEWAY (ServerSocket/CIO)         │  │
│  │  GET /v1/models │ POST /v1/chat/completions │ POST /v1/completions   │  │
│  │  GET /health    │ GET /metrics              │ Bearer Auth Filter     │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
│                                     ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                    "HARNESS DIET" AGENT MIDDLEWARE                   │  │
│  │  1. System Prompt Compressor (5000+ tok boilerplate -> compact core) │  │
│  │  2. Smart Sliding Window (>85% ctx -> prune old tool outputs)        │  │
│  │  3. JSON Schema -> GBNF Grammar Compiler (100% valid tool_calls)     │  │
│  └─────────────────┬────────────────────────────────────┬───────────────┘  │
│                    │ Local Edge Route                   │ High-Thinking    │
│                    ▼                                    ▼                  │
│  ┌──────────────────────────────────────┐  ┌────────────────────────────┐  │
│  │    NATIVE C++ JNI INFERENCE ENGINE   │  │  FIREBASE AI LOGIC ROUTER  │  │
│  │         (libpocketcoded_jni.so)      │  │     (gemini-4-argon)       │  │
│  │ • Zero-copy mmap GGUF loader         │  │ • ThinkingLevel.HIGH       │  │
│  │ • Flash Attention (-fa on)           │  │ • App Check attestation    │  │
│  │ • Quantized KV (Q8_0 / Q4_0)         │  │ • Streaming & Tool calls   │  │
│  │ • Prefix KV Cache Pinning (n_keep)   │  └────────────────────────────┘  │
│  │ • Prompt Lookup Decoding (PLD)       │                                  │
│  └──────────────────────────────────────┘                                  │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ StateFlow<DaemonTelemetryState>
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                 JETPACK COMPOSE DEVELOPER UI (MainActivity)                │
│ ┌────────────────┐ ┌──────────────────┐ ┌───────────────┐ ┌──────────────┐ │
│ │ Server Control │ │ Model Hub &      │ │ API & GBNF    │ │ Live Agent   │ │
│ │ Deck & Keys    │ │ Hardware Tuner   │ │ Test Sandbox  │ │ Telemetry    │ │
│ └────────────────┘ └──────────────────┘ └───────────────┘ └──────────────┘ │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `HardwareProfile`: Total RAM, available RAM, detected big CPU cores, tier (`TIER_4GB_CONSTRAINED` vs `TIER_6GB_PLUS`), recommended GGUF model, default context size (`4096` vs `8192`), and KV cache quantization types (`GGML_TYPE_Q8_0` / `GGML_TYPE_Q4_0`).
  - `EngineConfig`: Active model path, `useMmap = true`, `useMlock = false`, `flashAttn = true`, `contextSize`, `threads`, `gpuLayers`, `batchSize`, `typeK`, `typeV`, `enablePld`, `pldNgramSize`, `pinSystemPrefix`, and `enableHybridHighThinking`.
  - `ApiKeyRecord`: Cryptographically random `sk-mobile-...` token, label, creation timestamp, and request counter stored via encrypted preferences.
  - `TelemetryEntry`: Request ID, endpoint, client IP, original token count, compressed token count (tokens saved by Harness Diet), active GBNF grammar rule preview, KV cache hit ratio, prefill tok/s, decode tok/s, PLD drafted/accepted tokens, and route (`NATIVE_GGUF` vs `GEMINI_4_ARGON_HIGH`).

- **Interactive Component & State Mapping**:
  - **Server Control Deck**: Toggling the server starts/stops `PocketDaemonService` and binds the local/LAN socket; tapping "Generate Key" mints a new `sk-mobile-...` token; tapping "Copy `opencode.json`" formats the live IP, port, active model alias, and key into a valid `@opencode/ai/providers/openai-compatible` config and copies it to the clipboard.
  - **Model Hub & Hardware Tuner**: Tapping "Synthesize Micro-GGUF" writes a valid GGUF v3 binary with metadata and quantized tensor blocks to local storage and loads it via `mmap` in C++; tapping "Download" on a curated HuggingFace model streams the GGUF with HTTP `Range` resume support; adjusting sliders updates `EngineConfig` and re-initializes the JNI KV cache parameters.
  - **API & GBNF Sandbox**: Entering a sample prompt or tool schema immediately previews the "Harness Diet" token savings and compiled GBNF grammar string, and runs a live streaming request against `/v1/chat/completions` or `/v1/completions` (FIM).
  - **Emergency OOM Watchdog**: `ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` invokes the JNI `nativeFlushNonPinnedKvCache()` method to evict non-pinned tokens while keeping `n_keep` system prefix tokens intact.
