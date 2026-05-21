# SCYPHEON: OFFLINE-NATIVE EDGE INTELLIGENCE ECOSYSTEM

[![License: Proprietary](https://img.shields.io/badge/License-Proprietary-red.svg)](#)
[![Android SDK: 35](https://img.shields.io/badge/Android%20SDK-35-blue.svg)](#)
[![Kotlin: 1.9+](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](#)
[![Engine: LiteRT %26 llama.cpp](https://img.shields.io/badge/Engine-LiteRT%20%26%20llama.cpp-green.svg)](#)
[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/ScyLxynFycus/Scypheon-Private/releases/download/release/app-debug.apk)

Welcome to the central repository of the **Scypheon Edge Intelligence Ecosystem**. This repository houses the complete, decoupled architecture for a secure, offline-native humanitarian artificial intelligence platform. It is engineered specifically for mission-critical deployments, disaster-relief coordination, and deterministic offline medical triage.

## 1. Modular Repository Topography

The platform eschews monolithic design in favor of a strictly decoupled, modular architecture. The repository is partitioned into isolated subsystems to enforce separation of concerns between the presentation layer, the systems-level logic, and the native C++ inference boundaries.

`	ext
Scypheon-Private (Root)
├── scypheon_private/              # Presentation Layer & Workspace Component
│   ├── app/                       # Jetpack Compose UI, ViewModels, and UI States
│   └── README.md                  # Application-specific architectural guide
│
├── scypheon_sdk/                  # Systems Intelligence & Resilience Core
│   ├── src/main/java/             # Native Gateways, Lazarus Protocol, Circuit Breakers
│   └── README.md                  # SDK API reference and defensive guardrail documentation
│
├── llama/                         # Native C++ Boundaries
│   └── src/main/cpp/              # JNI execution, memory mapping, and llama.cpp bindings
│
└── docs/                          # Enterprise Documentation & Audit Artifacts
    ├── SCYPHEON_ENTERPRISE_ARCHITECTURE.md
    ├── SCYPHEON_HUMANITARIAN_IMPACT.md
    ├── SCYPHEON_VS_PRODUCTION_GRADE.md
    └── JUDGES_QUICK_START_GUIDE.md
`

## 2. Navigating the Architecture

To comprehensively understand the structural integrity and capabilities of this ecosystem, auditors and engineers should navigate the repository via the following entry points:

### 2.1 The Application Presentation Layer
*   **Path:** ./scypheon_private/README.md
*   **Focus:** Review the presentation layer documentation to understand the Jetpack Compose architecture, the Zero MutableState churn philosophy, and the integration of the Sentient Mirror visual nodes.

### 2.2 The SDK Safety & Resilience Core
*   **Path:** ./scypheon_sdk/README.md
*   **Focus:** Inspect the core SDK module documentation. This details the implementation of the Lazarus Binder Death Recovery, the Zero-Copy Shared Memory (SHM) mapping via memfd_create, and the Shannon Entropy Layer 0 interceptors.

### 2.3 Global Architectural Blueprints
*   **Path:** ./docs/SCYPHEON_ENTERPRISE_ARCHITECTURE.md
*   **Focus:** The definitive 17-point whitepaper mapping the holistic system. It provides Mermaid topological diagrams of data flow across the Security Gateways, Cognitive Persistence layers, and the Native Inference Core.

## 3. Compilation and Execution Protocol

Engineers must ensure the local build environment is configured with Android Target SDK 35, NDK version 26.1.10909125+, and CMake 3.22.1+. 

Execute the following directives from the repository root to purge stale artifacts and initiate a clean cross-module compilation:

`ash
# Purge historical build caches and native objects
./gradlew clean

# Execute strict Kotlin compilation and linking across all discrete modules
./gradlew compileDebugKotlin

# Generate the executable artifact
./gradlew :app:assembleRelease
`

*Signed, The Scypheon Engineering Directorate.*
