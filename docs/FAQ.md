# Enterprise FAQ & Technical Troubleshooting

## System & Deployment Inquiries

**Q: Does the Scypheon ecosystem require any outbound network connectivity?**
A: **Absolutely not.** Scypheon is engineered as a zero-trust, air-gapped platform. All cognitive processing, including the ORRIGA deep reasoning loop, executes exclusively on local silicon. There is no telemetry exfiltration, API dependency, or cloud fallback.

**Q: What is the deterministic memory limit for the inference engine?**
A: The `MemoryGatekeeper` enforces a strict hard ceiling of **32,768 tokens**. If a user request or swarm aggregation exceeds the dynamic memory threshold of the hosting device, the context window is safely truncated, or the Lazarus Protocol engages a recursive Context-Halving fallback algorithm (down to a minimum floor of 512 tokens) to guarantee successful allocation.

**Q: Which hardware architectures are formally supported?**
A: Scypheon requires Android API 28 (Android 9.0) or higher. For optimal deployment of the 4B parameter models (e.g., Gemma 2B/4B), 6GB of physical RAM is strictly recommended. The system natively targets ARM64-v8a architectures with dynamic backend compilation (Vulkan, OpenCL, CPU).

## Technical Troubleshooting

**Q: The application immediately crashes upon initializing a model payload. How do I resolve this?**
A: This typically indicates a catastrophic hardware Out-Of-Memory (OOM) fault that bypassed the standard POSIX signal handler, or a corrupted binary weight file. 
*Resolution*: Verify the cryptographic SHA-256 hash of the `.gguf` file via the `ModelIntegrityGuard`. If the hash is valid, clear background daemon processes to free physically contiguous RAM, or select a smaller quantized model.

**Q: Native inference is failing or hanging indefinitely on Exynos/Mali-equipped devices.**
A: Fragmentation within the Android Hardware Abstraction Layer (HAL) graphics drivers frequently causes Vulkan compute shaders to panic.
*Resolution*: Scypheon's **Triple-Fallback GPU Triage** should automatically intercept the SIGSEGV and degrade the execution path to OpenCL or CPU. If the driver deadlocks the kernel before the exception can be trapped, navigate to settings and manually enforce a CPU-only execution profile.

**Q: A background autonomous agent is refusing to execute a system command (e.g., initiating a local file transfer).**
A: This is the **HITL (Human-In-The-Loop) Puppet Subsystem** enforcing its defensive perimeter. Scypheon prohibits autonomous agents from executing high-risk system calls without explicit cryptographic authorization.
*Resolution*: Inspect your secure notification shade. You will see an active `[AWAITING_APPROVAL]` intercept broadcast. The execution pipeline is suspended via a `CompletableDeferred` lock until you manually press the "APPROVE" action to release the OS intent.
