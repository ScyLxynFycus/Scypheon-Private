# Scypheon Component Map

> **Scale Overview**
> This repository is not a typical hackathon prototype. Scypheon Private comprises a massive ecosystem:
> - **~35,000 Lines of Code (Kotlin)** for the Core Agentic Engine, Resiliency Mechanisms, and UI Orchestration.
> - **~5.8 Million Lines of Code (C/C++)** within the `llama` module for the native edge inference engine, GGML tensor operations, and hardware acceleration pipelines.

A quick reference mapping high-level architectural domains to their physical source code files.

## Domain 1: Core Cognitive Engine & JNI Bridge
Handles the brutal transition from high-level agentic thought to raw native tensor calculations.

| Component Name | Primary Function | Source File / Location |
|:---|:---|:---|
| **Lazarus Protocol** | Binder Death Recovery & Sandbox Isolation | `SandboxLlamaEngine.kt` |
| **Native Inference Core** | C++ JNI bridge and engine orchestrator | `InferenceEngineImpl.kt` |
| **Sandbox IPC Contract** | AIDL definition for out-of-process engine binding | `IScypheonSandbox.aidl` |
| **GPU Triage Subsystem**| Hardware degradation path (Vulkan -> CPU) | `LlamaCppManager.kt` |

## Domain 2: System Memory & High-Speed IPC
Bypasses Android's standard 1MB Binder limit using low-level Unix memory constructs.

| Component Name | Primary Function | Source File / Location |
|:---|:---|:---|
| **Zero-Copy SHM** | High-throughput native memory sharing | `NativeMemoryManager.kt` |
| **Sentient Mirror** | Semantic graph extraction & memory persistence | `VectorMemoryManager.kt` |
| **Dual Memory Manager** | Hybrid Time-Aware RAG & Fact Extraction | `DualMemoryManager.kt` |
| **Mesh Sync Engine** | Enterprise P2P Decentralized RAG over BLE | `MeshVectorSyncManager.kt` |
| **KV Cache & Memory Gatekeeper** | Q4/Q8 KV Cache pruning and budget limits | `MemoryGatekeeper.kt` |
| **Context Handler** | General K-V Cache context window pruning | `MemoryCache.kt` |

## Domain 3: Sentinel & Cryptographic Safety
The defensive perimeter ensuring output cannot be poisoned and the device cannot be hijacked.

| Component Name | Primary Function | Source File / Location |
|:---|:---|:---|
| **Helios Sentinel** | Entropy analysis & adversarial input blocking | `SafetyGuard.kt` |
| **System Enforcer** | Root-level architectural safety policies | `VitreonGuard.kt` |
| **Clinical Validator** | OpenFDA grounding and medical safety checks | `ClinicalValidator.kt` |

## Domain 4: Agency & Autonomous Orchestration
The "Ghost in the Machine" orchestrating multi-agent tasks continuously in the background.

| Component Name | Primary Function | Source File / Location |
|:---|:---|:---|
| **Agent Planner** | High-level autonomous reasoning state machine | `AgentPlanner.kt` |
| **HITL Puppet Subsystem** | High-risk agent execution interception | `VitreusFlowWorker.kt` |
| **MCP Orchestrator** | Dynamic external tool & plugin integration | `McpOrchestrator.kt` |
| **Deep Research Agent** | Recursive search, validation, and synthesis | `DeepResearchAgent.kt` |

## Domain 5: Semantic Routing & Offline Telemetry
Ensures visual coherence and auditable analytics in completely air-gapped environments.

| Component Name | Primary Function | Source File / Location |
|:---|:---|:---|
| **GraphPhysicsEngine** | Fermat's Spiral $O(1)$ spatial node layout | `GraphPhysicsEngine.kt` |
| **Neural Gateway** | Prompt compilation & specialized model routing | `NeuralRouter.kt` |
| **Solaris Telemetry** | Offline encrypted metric auditing | `LocalTelemetry.kt` |
