# Scypheon

**Intelligence that stays with you — on your device, under your control, even when the world goes dark.**

[![Version](https://img.shields.io/badge/Version-1.5.0--SAR-orange.svg)](#)
[![License: Proprietary](https://img.shields.io/badge/License-Proprietary-red.svg)](#)
[![Android SDK: 35](https://img.shields.io/badge/Android%20SDK-35-blue.svg)](#)
[![Kotlin: 1.9+](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](#)
[![Engine: LiteRT & llama.cpp](https://img.shields.io/badge/Engine-LiteRT%20%26%20llama.cpp-green.svg)](#)

Scypheon is an offline-first, zero-trust AI platform for Android. It brings capable, frontier-class reasoning to an ordinary phone — and keeps it there. No cloud round-trip. No data leaving the device. No dependency on a signal that may never arrive.

> Scypheon isn't a chat UI wrapped around someone else's API. It's a complete, self-contained intelligence stack — engineered so the people who need it most can still use it when everything else has stopped working.

**Get the APK:** [Latest release](https://github.com/ScyLxynFycus/Scypheon-Private/releases/download/release/app-debug.apk)

---

## Contents

- [Why we built this](#why-we-built-this)
- [What using it feels like](#what-using-it-feels-like)
- [Under the hood](#under-the-hood)
- [How it's put together](#how-its-put-together)
- [Build & run](#build--run)
- [Documentation](#documentation)
- [License](#license)
- [Acknowledgements](#acknowledgements)

---

## Why we built this

When disaster hits, connectivity is usually the first thing to go. In conflict zones, refugee camps, and disaster epicentres, the deadliest failure isn't the event itself — it's the silence that follows. Communication collapses. Records disappear. Guidance becomes guesswork.

Most AI tools simply stop being useful at that exact moment:

- **Cloud assistants go dark.** No signal, no intelligence — precisely when it matters.
- **Static reference apps can't reason.** They can't weigh conflicting symptoms or adapt to a situation nobody scripted.
- **Naive offline models crash.** On real consumer hardware they throttle, fragment memory, and get killed by the OS under pressure.

Scypheon was built for that gap. It runs the **Gemma 4** model family locally on consumer Android devices, wrapped in layered defenses that keep it memory-safe, predictable, and completely private.

---

## What using it feels like

- **It just works — offline.** Airplane mode, no tower, no Wi-Fi. Scypheon doesn't care.
- **Nothing leaves your phone.** Your conversations, your context, your data. All of it stays client-side, encrypted at rest.
- **It recovers instead of crashing.** If the OS tries to kill the engine under memory pressure, Scypheon catches it and brings the runtime back — without dropping your screen.
- **It stays responsive.** Heavy work happens off the UI thread, so the interface holds its frame budget even while the model is generating.
- **It's honest about limits.** Safety rails and clear disclaimers are built into the experience, not bolted on.

---

## Under the hood

For those who want to look past the interface, here's what makes it hold up.

### Resilience

| Mechanism | What it does |
|---|---|
| **The Lazarus Protocol** | Watches the native runtime. If the system kills it under memory pressure, Scypheon traps the failure, reads the tombstone, and cold-restarts the sandbox — the UI never dies. |
| **Zero-copy SHM pipeline** | Moves tensors through shared memory instead of Android's IPC limit, keeping the interface smooth at full generation speed. |
| **Thermal & memory governance** | Actively tracks device constraints and thermal state so the model degrades gracefully instead of collapsing. |

### Performance

| Mechanism | What it does |
|---|---|
| **Spatial grid indexing (O(1))** | The knowledge-graph view resolves touches and node positions in constant time — panning and zooming stay fluid no matter how large the graph grows. |
| **Cryptographic pre-warming** | Heavy AES-256 database setup runs on background I/O threads, so cold start never blocks the screen. |

### Safety

| Guardrail | What it does |
|---|---|
| **Shannon Entropy Guard** | Inspects input before it reaches the model and drops obfuscated, adversarial payloads on sight. |
| **Clinical Safety Pre-Hook** | Intercepts agentic tool calls and blocks mathematically absurd parameters (e.g. an impossible dosage) before execution. |
| **Deterministic execution** | Same input, same conditions, same outcome — reproducible by design. |

### Intelligence

- **Agentic orchestration** — an OODA loop with extensible skills (math, medical, tutoring) and parallel multi-agent reasoning.
- **Mesh RAG & dual memory** — hybrid, time-aware retrieval keeps context relevant without ballooning the prompt.
- **BLE mesh sync** — offline communities can share vital knowledge vectors peer-to-peer, with no cloud in the path.
- **Explainability layer** — critic nodes surface *why* an answer was produced, not just the answer.

### Privacy

- **Encrypted at rest** via SQLCipher (AES-256).
- **BlackBoxVault** — tamper-evident, offline audit logging.
- **Zero-trust containment** — no telemetry egress, no hidden network path.

---

## How it's put together

```text
Scypheon-Private (Repository Root)
├── scypheon_private/              # Presentation layer — Compose UI, screens, app state
│   └── app/src/main/java/com/scypheon/app/
│       ├── ui/screens/            # Live Mode, Graph Explorer
│       ├── ui/views/              # Custom rendering (Neural Graph View)
│       ├── orchestrator/          # App-level orchestration
│       └── data/                  # Repositories and local providers
│
├── scypheon_sdk/                  # Systems intelligence & resilience core
│   └── src/main/java/com/scypheon/sdk/core/
│       ├── agent/                 # OODA loop, skills, tool hooks, agent swarm
│       ├── safety/                # Layered sanitizers & integrity verifiers
│       ├── resilience/            # Circuit breakers & fallback engines
│       ├── memory/                # Dual memory & context replay buffers
│       ├── telemetry/             # BlackBoxVault — offline encrypted auditing
│       ├── medical/               # Humanitarian grounding, triage & routing
│       ├── gateway/               # Model gateway & dynamic prompt compilers
│       ├── mesh/                  # P2P cryptographic identity mesh
│       ├── xai/                   # Explainability & critic nodes
│       └── environment/           # Device constraints & thermal monitoring
│
├── llama/                         # Native C++ boundaries
│   └── src/main/cpp/              # JNI execution, zero-copy shared memory
│
└── docs/                          # Architecture & reference documentation
    ├── ARCHITECTURE_OVERVIEW.md
    ├── ARCHITECTURE_TECHNICAL_REFERENCE.md
    ├── ARCHITECTURE_ENTERPRISE_WHITEPAPER.md
    ├── QUICK_ARCHITECTURE_DIAGRAM.svg
    ├── GLOSSARY.md
    ├── COMPONENT_MAP.md
    ├── JOURNEY.md
    ├── FAQ.md
    ├── ARCHITECTURAL_DECISION_RECORDS.md
    ├── SCYPHEON_HUMANITARIAN_IMPACT.md
    ├── SCYPHEON_VS_PRODUCTION_GRADE.md
    ├── PROJECT_DESCRIPTION.md
    └── DATA_SOURCES.md
```

---

## Build & run

### Prerequisites

- Android Studio Ladybug (or newer stable)
- Android NDK **26.1.10909125+**
- CMake **3.22.1+**
- JDK **17**

### Device requirements

Deploy to a **physical Android device** (API 26+). Emulators aren't supported — they can't faithfully reproduce real SoC thermals, Linux LMKD behaviour, or the shared-memory bindings the resilience layer depends on.

### Build

```bash
# 1. Clean stale build artifacts and native objects
./gradlew clean

# 2. Compile Kotlin across all modules
./gradlew compileDebugKotlin

# 3. Produce the release artifact
./gradlew :app:assembleRelease
```

The native C++ runtime and hardware configuration providers cross-compile automatically.

---

## Documentation

Start with **[ARCHITECTURE_OVERVIEW.md](./docs/ARCHITECTURE_OVERVIEW.md)** — the visual entry point into how data flows through Scypheon.

From there: a [technical reference](./docs/ARCHITECTURE_TECHNICAL_REFERENCE.md), an [enterprise whitepaper](./docs/ARCHITECTURE_ENTERPRISE_WHITEPAPER.md), a [component map](./docs/COMPONENT_MAP.md), the [build journey](./docs/JOURNEY.md), and the [glossary](./docs/GLOSSARY.md) for terms like *Lazarus Protocol* and *Sentient Mirror*.

---

## License

Proprietary. All rights reserved.

Scypheon is distributed under a proprietary license — it may be viewed and evaluated, but not redistributed, modified, or used in derivative works without written permission.

---

## Acknowledgements

Scypheon stands on the work of others:

- **Google Gemma** — the open model family that makes on-device reasoning viable.
- **LiteRT (TensorFlow Lite)** — the runtime path for accelerated mobile inference.
- **llama.cpp** — the native C++ engine behind the local execution boundary.
- **SQLCipher** — transparent, full-database encryption at rest.

---

*Built for the people who can't afford to lose connection.*
