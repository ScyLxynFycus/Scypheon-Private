# Scypheon Glossary

The Scypheon ecosystem utilizes specialized terminology to describe its unique subsystems and architectural components. This glossary provides definitions and analogies for these terms.

### Helios Sentinel
**Definition:** A multi-layered defensive security pipeline that sanitizes inputs before they reach the inference engine.
**Analogy:** The bouncer at the club door checking IDs and patting down visitors before they enter the VIP area.
**Code Location:** `SafetyPipelineImpl.kt`

### Lazarus Protocol
**Definition:** An automatic recovery mechanism that listens for native C++ sandbox crashes (e.g., from Linux Out-Of-Memory kills) and seamlessly resurrects the process without crashing the UI.
**Analogy:** A defibrillator that automatically shocks a stopped heart back into rhythm without the patient ever knowing.
**Code Location:** `SandboxLlamaEngine.kt`

### Sentient Mirror (Graph Memory Manager)
**Definition:** A semantic memory graph that extracts named entities and facts from conversations, persisting them in a local SQL database to allow the AI to remember historical context infinitely without blowing up the active token window.
**Analogy:** A detective's corkboard connecting suspects, places, and events with red string, constantly updated as new clues arrive.
**Code Location:** `GraphMemoryManager.kt`

### Zero-Copy SHM (Shared Memory)
**Definition:** A data pipeline bridging the native C++ inference sandbox and the Kotlin UI process by mapping a shared virtual memory address, bypassing standard 1MB Android Binder limits.
**Analogy:** Two workers sharing the exact same whiteboard to write and read, rather than mailing letters back and forth.
**Code Location:** `ShmLifecycleManager.kt`

### OODA Loop (Fast Path)
**Definition:** Observe, Orient, Decide, Act. The fast orchestration engine for quick tool invocations and simple queries.
**Analogy:** Reflexes. Pulling your hand away from a hot stove instantly.
**Code Location:** `docs/ARCHITECTURE_TECHNICAL_REFERENCE.md#ooda`

### ORRIGA Loop (Deep Path)
**Definition:** Observe, Reflect, Reason, Investigate, Ground, Answer. The deep reasoning DAG pipeline used for complex factual synthesis and medical verification.
**Analogy:** Deliberation. Sitting down to solve a complex calculus problem step-by-step.
**Code Location:** `docs/ARCHITECTURE_TECHNICAL_REFERENCE.md#orriga`

### HITL (Human-in-the-Loop) Puppet Subsystem
**Definition:** A security mechanism that intercepts autonomous agent actions deemed "high-risk" (e.g., deleting files) and suspends the background worker until cryptographic human approval is provided.
**Analogy:** A nuclear launch sequence requiring two human commanders to turn their keys before the missile fires.
**Code Location:** `VitreusFlowWorker.kt`
