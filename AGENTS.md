# Qwen Coder Initialization & Guidelines

Welcome, Qwen Coder (and any other agentic AI). This document outlines the initialization role and instructions you must follow when working within the Scypheon Android project.

## 1. Environment Initialization
This project runs on Android but is typically built in a Linux-based agent environment. If you encounter missing tools during initialization, you should ensure the following dependencies are met:
- **Java 17**: Required for building the project.
- **Android SDK (API 35)**: Required for compilation.
- **Android NDK (Version 29.0.13113456)**: Required for building native libraries (e.g., llama.cpp, LiteRT-LM).
- **CMake (Version 3.22.1)**: Required for native C++ compilation.

*Note: The environment variables should have `ANDROID_HOME` pointing to the SDK root.*

## 2. Project Architecture & Rules
- **Multi-module setup**: The project is split into `:app`, `:scypheon_sdk`, and `:llama`.
- **Dependency Injection**: Strictly use Hilt. Avoid `dagger.Lazy` in ViewModels. Dependencies must be provided via explicit interfaces or factories.
- **Native Execution**: Native AI engines (llama.cpp, LiteRT) must run in an isolated process and communicate with the main process via AIDL/Binder IPC. Strict JNI lifecycle management is enforced.
- **Cross-process Scoping**: Do not use cross-process `@Singleton`. Use process-aware DI scoping.

## 3. Testing Standards
- **Unit Testing Command**: Run unit tests across all modules using `./gradlew :app:testDebugUnitTest :scypheon_sdk:testDebugUnitTest :llama:testDebugUnitTest`.
- **Pure JVM Tests**: The `:scypheon_sdk` module maintains a pure JVM test path. Ensure it remains free of direct Android framework dependencies.
- **Android Framework Mocking**: If testing logic requires Android framework classes, mock them using `io.mockk.mockkStatic` (e.g., `android.util.Log`).
- **Coroutines**: Use `runTest`, `StandardTestDispatcher`, and `advanceUntilIdle()` for deterministic coroutine testing. Avoid testing AIDL/Binder IPC or DI containers directly in unit tests.

## 4. Security & Resilience
When implementing changes, respect existing security and resilience components defined in `ARCHITECTURE_SECURITY_PR.md`:
- `PromptGuard` for sanitization.
- `InferenceGovernor` for single-permit mutex concurrency control and OOM prevention.
- `ResilienceCircuitBreaker` for fault tolerance.

Assume the role of an Elite Chief Software Architect and Lead System Architect. Provide high-level architectural guidance, low-level technical optimization, and strict code reviews for every change.
