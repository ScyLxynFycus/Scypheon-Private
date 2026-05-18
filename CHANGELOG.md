# Scypheon Engine Changelog

## [v1.0.6-SAR] - 2026-04-22

### ☀️ Solaris Phase 4: Recovery & Control
- **Transactional Outbox**: Implemented a zero-loss message persistence layer in `DualMemoryManager`. User messages are now saved synchronously to the encrypted Room DB before inference begins, ensuring recovery from engine crashes.
- **Inference Kill-Switch**: Added a real-time 'Stop' mechanism in `MainViewModel`. Users can now instantly terminate active inference streams (Claude/ChatGPT style) to save battery and compute cycles.
- **Solaris Message Recovery**: Integrated a dedicated 'Retry' protocol in the UI for failed neural turns. The system now tracks message lifecycle through `QUEUED`, `PROCESSING`, and `SUCCESS/FAILED` states.
- **Async Vector Embedding**: Optimized the message saving pipeline by offloading semantic vector generation to a background supervisor scope, preventing UI jank during rapid message exchanges.

### 🏗️ UI/UX Enhancements
- **Dynamic Control Pivot**: The chat send button now dynamically morphs into a red 'Stop' button during active AI generation.
- **Visual Failure Recovery**: Added inline 'Retry' actions and failure banners to message bubbles that failed the Phoenix sync protocol.


## [v1.0.5-SAR] - 2026-04-22

### 🏗️ Phoenix Protocol & Competitive Focus
- **Gemma 4 Competition Pivot**: Stripped legacy support for Llama, Qwen, and Phi model families. Optimized the entire neural pipeline for **Gemma 3n** and **Gemma 4** architectures.
- **Turn-Based Neural Protocol**: Replaced flat-string prompting with a structured `NeuralTurn` list schema. Implemented the official Gemma Instruct template (<start_of_turn>user/model) to ensure perfect KV-cache role isolation.
- **Context Hygiene (Phoenix)**: Upgraded `ScypheonDbHelper` to v3 with `isContextEligible` flags. Background swarm reports and failed inferences are now automatically filtered from the LLM context window to prevent hallucination loops.
- **RAG Turn Injection**: Integrated semantic (Vector) and logical (Graph) context as discrete `SYSTEM` turns placed immediately before the user query for maximum attention weight.
- **FTS4 Atomic Sync**: Refactored SQLite triggers to use the official `'delete'` command for FTS4 index maintenance, ensuring persistent search integrity without performance overhead.

### 🐛 Bug Fixes
- **Syntax Fix**: Resolved a critical unclosed brace in `NeuralGateway.kt` that was blocking SDK builds after the turn-protocol refactor.
- **SDK Stability**: Re-implemented the `routeRequest` convenience wrapper to prevent breaking existing humanitarian features (ScamGuard, SignLanguageBridge) during the protocol migration.


## [v1.0.4-SAR-rc1] - 2026-04-22

### 🛡️ Architecture Stabilization (SAR)
- **Native Thread Safety**: Implemented `std::recursive_mutex` in `llama-android.cpp` to protect high-risk JNI call sites from race conditions during async hotswapping.
- **V.I.I.P (Vitreon Intelligent Inference Protection)**: Added `ACTION_PROMOTE` intent to `ModelSandboxService` with exponential backoff (1s→2s→4s) to ensure Android 12+ foreground service compliance.
- **MDRS 4.2 Dynamic Context**: Added memory-aware context scaling. Devices with <2GB available RAM now dynamicly scale context down to a stable 2048-token floor or lower, ensuring multi-turn stability.

### 🐛 Critical Bug Fixes
- **Pipeline Blocker**: Resolved an over-aggressive token filter in `llama-android.cpp` that was blocking the pipe character ('|'), restoring full support for Markdown tables and structural formatting.
- **Double-Close Race**: Fixed a JNI lifecycle bug where the sandbox service and native layer both attempted to close SharedMemory file descriptors, preventing potential race-condition crashes.

## [v1.0.3] - 2026-04-15

### 🏗️ Isolated Sandbox Architecture
- **Process Isolation**: Migrated `llama.cpp` native engine to an isolated `:sandbox` process to prevent main app crashes during OOM events.
- **Aegis KeyStore**: Implemented hardware-backed RSA keypair for encrypted intent payloads between background workers and the Accessibility Service.
- **Memory Gatekeeper**: Initial release of the quantization logic for safe model loading parameters.

## [v1.0.2] - 2026-04-10

### 🧠 Dual-Memory RAG Integration
- **Hybrid Search**: Combined Vector embeddings (Semantic) with Knowledge Graph facts (Logical) for enhanced grounding.
- **Graph Explorer**: Added a visual debugger for the logical knowledge graph.
- **Solaris Telemetry**: Initial integration of the black-box logging system for on-device failure analysis.

## [v1.0.1] - 2026-04-02

### 🛡️ Core Foundation & Aegis Shield
- **Initial SDK Release**: Core humanitarian features (ScamGuard, SignLanguageBridge) established.
- **Aegis Privacy Shield**: Real-time PII redaction and safety guardrails (System/Crisis/Malice scanning).
- **LiteRT Integration**: High-speed edge inference support via Gemini-Gemma task formats.
