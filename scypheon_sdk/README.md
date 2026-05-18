# Scypheon SDK: Offline Core Engines & Resilience Framework
*Path: `:scypheon_sdk`*

This directory houses the authoritative local AI engines, JNI frameworks, memory managers, safety validators, and robust recovery architectures that power Scypheon's zero-dependency runtime.

---

## 🏛️ Systems & Engine Architecture

### 1. Dual-Branch Gateway Routing
In [NeuralGateway.kt](file:///D:/AuraLink/scypheon_sdk/src/main/java/com/scypheon/sdk/core/gateway/NeuralGateway.kt), the platform maps all system prompts and chat history, routing the request to the most optimal active local engine:
* **Branch A (Proprietary NPU/GPU acceleration):** [LiteRtEliteEngine.kt](file:///D:/AuraLink/scypheon_sdk/src/main/java/com/scypheon/sdk/core/engine/LiteRtEliteEngine.kt) uses the Google LiteRT-LM runtime for extremely fast local Gemma inference.
* **Branch B (Universal Sandboxed Fallback):** [SandboxLlamaEngine.kt](file:///D:/AuraLink/scypheon_sdk/src/main/java/com/scypheon/sdk/core/engine/SandboxLlamaEngine.kt) utilizes an isolated AIDL service framework executing GGUF formats to prevent GPU driver faults or heap exhaustion from bringing down the parent process.

### 2. Resilience, Circuit Breakers & Self-Healing
* **Resilience Circuit Breakers:** Both engines execute within the `ResilienceCircuitBreaker` framework. If an engine throws an exception (such as JNI load failed, out-of-memory, or native segmentation fault), the circuit breaker records the failure. Once the failure threshold is reached, the circuit opens, causing `isReady()` to return false and triggering an **instant, seamless fallback** to the other engine in `NeuralGateway`.
* **Lazarus Binder Death Recovery:** `SandboxLlamaEngine` registers a `DeathRecipient` callback on the sandboxed Binder connection. If the native driver crashes and the process terminates, the SDK automatically performs cleanup, re-binds to `ModelSandboxService`, and recovers normal execution.

### 3. Solaris Telemetry
Offline metrics and failures are safely queued and written asynchronously in batches of 50 to a local NDJSON ring buffer (`telemetry.ndjson`) in [SolarisTelemetry.kt](file:///D:/AuraLink/scypheon_sdk/src/main/java/com/scypheon/sdk/core/utils/SolarisTelemetry.kt). This ring buffer rotates atomically at 5MB, pruning the oldest 30% of lines to prevent storage bloat.

---

## 📘 Sub-Directory Map

* 📂 **`src/main/java/com/scypheon/sdk/core/`**
  * `gateway/NeuralGateway.kt` — Orchestrates active engine routing and chatML system prompt formatting.
  * `engine/SandboxLlamaEngine.kt` — Heavy, binder-isolated GGUF interpreter.
  * `engine/LiteRtEliteEngine.kt` — Circuit-breaker hardened local Gemma engine.
  * `resilience/DefaultResilienceCircuitBreaker.kt` — Multi-circuit thread-safe lock-free state engine.
  * `memory/DualMemoryManager.kt` — High-speed offline hybrid vector & Graph memory coordinator.
  * `utils/SolarisTelemetry.kt` — Telemetry flusher decoupling IO from inference threads.
