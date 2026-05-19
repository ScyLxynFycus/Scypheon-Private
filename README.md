# SCYPHEON: Offline-Native Humanitarian AI Platform
[![License: Proprietary](https://img.shields.io/badge/License-Proprietary-red.svg)](#)
[![Android SDK: 35](https://img.shields.io/badge/Android%20SDK-35-blue.svg)](#)
[![Kotlin: 1.9+](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](#)
[![Engine: LiteRT %26 llama.cpp](https://img.shields.io/badge/Engine-LiteRT%20%26%20llama.cpp-green.svg)](#)
[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/ScyLxynFycus/Scypheon-Private/releases/download/release/app-debug.apk)

Welcome to the authoritative repository of **Scypheon**, a secure, offline-native, resilient humanitarian AI platform designed for high-stress deployments, disaster-relief routing, and remote offline medical triage grounding.

---

## 🏛️ Modular Repository Structure
The codebase is structured as a clean, highly decoupled modular architecture divided into specific sub-project directories. Each directory features its own specialized README document detailing component APIs and internal design patterns:

```
aura-link/
├── scypheon_private/              # Main Compose Application Workspace
│   ├── app/                       # Android UI layouts, ViewModels, and screens
│   └── README.md                  # App features, Compose components & PTT guide
│
├── scypheon_sdk/                  # Core Systems, Models & Engines Module
│   ├── src/main/java/             # Gateways, JNI executors, and circuit breakers
│   └── README.md                  # SDK API reference, gateways & resilience
│
├── llama/                         # Native llama.cpp GGUF compilation JNI
│
└── docs/                          # Global Architectural blueprints & clinical telemetry
    ├── SCYPHEON_ARCHITECTURE.md   # Unified Architectural Blueprint & Flowcharts
    └── DATA_SOURCES.md            # Pharmacopeia data provenance & medical review plan
```

---

## 💡 Quick Component Index

* **For Application Devs (UI / ViewModels / compose):**
  * Check out the [App Workspace Component Guide](file:///D:/AuraLink/scypheon_private/README.md) to explore the **Obsidian Live Orb speech interface**, Jetpack Compose screen components, and global state machines.
* **For Systems & ML Devs (Gateways / Sandboxing / Recovery):**
  * Dive into the [SDK Module Core Architecture](file:///D:/AuraLink/scypheon_sdk/README.md) to learn about **Lazarus binder death recovery**, local JNI library mapping, dual-branch gateway routing, and HSL async telemetry pipelines.
* **For High-Level Blueprints:**
  * Read the [Unified SCYPHEON_ARCHITECTURE.md manual](file:///D:/AuraLink/docs/SCYPHEON_ARCHITECTURE.md) to inspect comprehensive Mermaid sequence flowcharts and architectural system design principles.

---

## 🚀 Build & Deployment Guidelines
Ensure your local environment has the Android target SDK 35 and appropriate Mali GPU compilers installed.

```bash
# Clean project build cache
./gradlew clean

# Run complete Kotlin compilation check across all modules
./gradlew compileDebugKotlin
```

---
*Signed,*  
*The Scypheon Core Engineering Team*
