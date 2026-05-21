# Architectural Decision Records (ADR)

This document tracks the major architectural decisions executed during the development of the Scypheon ecosystem. It provides technical rationale on *why* specific engineering paths were chosen to meet our strict zero-trust and disconnected-environment requirements.

---

## ADR-001: Dual-Branch Inference Routing (LiteRT + llama.cpp)
**Date:** April 4, 2026
**Decision:** Implement a dual-engine architecture governed by `NeuralGateway` instead of standardizing on a single monolithic backend.
**Context:** LiteRT (TensorFlow Lite) provides exceptional power efficiency by targeting mobile NPUs (Neural Processing Units), but it lacks the dynamic runtime flexibility and deep reasoning capabilities required for complex DAG workflows that `llama.cpp` (via GGUF) excels at.
**Consequences:** Increased APK footprint and maintenance surface. However, it mathematically guarantees minimal battery drain for OODA loop classification while reserving the high-wattage GGUF engine exclusively for deep ORRIGA graph reasoning.

## ADR-002: Process Isolation and The Lazarus Protocol
**Date:** April 15, 2026
**Decision:** Encapsulate the `llama.cpp` native engine inside an isolated Android service (`android:isolatedProcess="true"`) monitored via `IBinder.DeathRecipient`.
**Context:** Native C++ segmentation faults (SIGSEGV) or Linux LMKD (Low Memory Killer Daemon) Out-Of-Memory terminations in the LLM engine instantly crash the host application. In disaster zones, application crashes are unacceptable.
**Consequences:** The UI thread is completely insulated from inference engine instability. If the sandbox faults, the `SandboxLlamaEngine` traps the Binder death callback, executes immediate garbage collection on dangling `ParcelFileDescriptor` references, and initiates a seamless, background resurrection of the native engine (The Lazarus Protocol).

## ADR-003: Zero-Copy Shared Memory IPC via memfd_create
**Date:** April 22, 2026
**Decision:** Bypass standard Android Binder IPC limits utilizing `memfd_create` and `ParcelFileDescriptor.adoptFd()`.
**Context:** Android Binder limits payload transactions to 1MB. Passing high-frequency token streams or tensor arrays between the isolated C++ process and the Kotlin UI process caused severe JNI memory fragmentation and GC (Garbage Collection) pauses.
**Consequences:** The system creates anonymous Unix file descriptors that are mapped into Kotlin's `SharedMemory` utilizing direct `ByteBuffer` manipulation. Both the sandbox and the UI process read/write to the exact same virtual memory addresses in $O(1)$ time, guaranteeing zero-latency throughput regardless of token generation speed.

## ADR-004: Cryptographic Model Integrity Hashing (SHA-256)
**Date:** May 1, 2026
**Decision:** Enforce deterministic cryptographic hash validation of LLM weights (`.gguf`) prior to mapping them into the Native Inference Core.
**Context:** Edge deployments in hostile environments present severe physical supply-chain risks. Adversaries could silently replace local models with trojaned weights designed to execute Base64-encoded prompt injections or hallucinate clinical medical data.
**Consequences:** Introduces a marginal initialization latency. However, it mathematically guarantees that the `Zero-Copy SHM Pipeline` will only ingest authentic, developer-signed neural networks, establishing a complete defense against localized model poisoning attacks.

## ADR-005: Asymmetric Fermat Spiral Layout for NeuralGraphView
**Date:** May 15, 2026 (Hardening Phase)
**Decision:** Replace $O(N^2)$ force-directed physics simulations with deterministic mathematical positioning (Fermat's Spiral and the Golden Angle) combined with an $O(1)$ `MutableGridSpatialIndex`.
**Context:** Simulating gravitational and repulsive forces for hundreds of Knowledge Graph nodes devours mobile CPU cycles, inducing thermal throttling and UI lag.
**Consequences:** Node positions are calculated deterministically: $\theta = i \times 137.5^\circ$ and $r = c \sqrt{i+1}$. Touch events are resolved instantly using an inverse matrix transformation mapped against the spatial grid bucket. This guarantees a pristine 120 FPS rendering budget while presenting an organic, non-overlapping visual structure.
