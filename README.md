# PocketCodeD (Pocket Code Daemon)

**PocketCodeD** is an Android edge AI coding daemon designed to run quantized coding LLMs (1.5B to 3B parameters, such as `Qwen2.5-Coder-1.5B-Instruct-Q4_K_M`, `Qwen2.5-Coder-3B-Instruct-Q4_K_M`, and `DeepSeek-Coder-1.3B-Instruct-Q4_K_M`) on constrained 4GB and 6GB RAM smartphones, exposing an OpenAI-compatible and DeepSeek-compatible HTTP/SSE gateway (`127.0.0.1:8080` for local Termux and `0.0.0.0:8080` for LAN IDEs) with `Bearer sk-mobile-...` authentication.

## Core Architectural Pillars

1. **Native C++ Inference Engine (`app/src/main/cpp/PocketEngine.cpp` & `PocketEngineBridge.kt`)**:
   - **ARM64 Hardware Flags**: Configured in `CMakeLists.txt` with `-DGGML_USE_KLEIDIAI=ON`, `-march=armv8.2-a+dotprod+i8mm+fp16`, and optional `-DGGML_VULKAN=ON`.
   - **Zero-Copy Memory Mapping (`mmap`)**: Loads GGUF v3 weights via OS page cache (`use_mmap = true`, `use_mlock = false`) without duplicating model weights in the Android Java heap.
   - **Flash Attention & Quantized KV Cache**: Forces `flash_attn = true` and supports `GGML_TYPE_Q8_0` / `GGML_TYPE_Q4_0` KV quantization (47%–72% KV cache RAM reduction).
   - **Prompt Lookup Decoding (PLD)**: Zero-memory n-gram speculative decoding that drafts 4–8 tokens per step from existing code context in the prompt.
   - **Prefix KV Cache Pinning (`n_keep`)**: Pins the system prompt and tool definitions in KV slot 0 across multi-turn coding sessions.
2. **"Harness Diet" Agent Middleware (`HarnessDietMiddleware.kt` & `GbnfGrammarCompiler.kt`)**:
   - **System Prompt Compressor**: Distills 5,000+ token `opencode`/Aider boilerplate into a compact `Qwen2.5-Coder` system prompt while preserving file paths and environment constraints.
   - **Smart Context Sliding Window**: Automatically summarizes/truncates older `role: "tool"` outputs when total tokens exceed 85% of the context window while preserving the system prompt, file paths, and the latest 3 turns.
   - **Grammar-Enforced Tool Calling (GBNF)**: Dynamically compiles OpenAI `tools` JSON schemas into `llama.cpp` GBNF production rules (`root ::= ...`) for 100% syntactically valid JSON tool calls.
3. **OpenAI & DeepSeek Compatible HTTP/SSE Gateway (`PocketHttpServer.kt` & `PocketDaemonService.kt`)**:
   - Endpoints: `GET /v1/models`, `POST /v1/chat/completions` (SSE `text/event-stream` and JSON), `POST /v1/completions` (FIM `<|fim_prefix|>...<|fim_suffix|>...<|fim_middle|>`), `GET /health`, and `GET /metrics`.
   - Encrypted `sk-mobile-...` API Key Vault (Android Keystore AES-256-GCM) and One-Tap `opencode.json` + Termux configuration generator.
4. **RAM Watchdog & Adaptive Hardware Profiler (`RamWatchdogProfiler.kt`)**:
   - Automatically detects 4GB vs 6GB+ RAM profiles and big CPU cores, and flushes non-pinned KV cache tokens on `ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`.
5. **Cyberpunk Terminal Developer UI (Jetpack Compose)**:
   - Four dedicated workspaces: **Control Deck**, **Model & Tuner**, **API & GBNF Sandbox**, and **Live Telemetry Console**.
