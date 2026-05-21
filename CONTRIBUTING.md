# Contributing to the Scypheon Ecosystem

Thank you for your interest in contributing to Scypheon. Because this platform is deployed in life-critical and disaster-response environments, we enforce enterprise-grade engineering standards, strict lifecycle bounds, and uncompromising review processes.

## Architectural Prerequisites
Before attempting to compile the source tree, ensure your development environment satisfies the following enterprise dependencies:
- **Android Studio Ladybug** (or later stable releases).
- **Android NDK (Version 26.1.10909125+)** - Crucial for accurate POSIX signal trapping.
- **CMake (Version 3.22+)** - Required for JNI and OpenBLAS compilation.
- **Minimum Target**: API 28 (Android 9.0).

*Note on Testing:* Emulators are **explicitly unsupported**. Emulated memory architectures and CPU virtualization fail to accurately reproduce Linux LMKD behavior, thermal throttling dynamics, and `memfd_create` bindings. You must test on physical silicon.

## Building from Source
1. Clone the repository securely: `git clone https://github.com/ScyLxynFycus/Scypheon-Private.git`
2. Sync the project against the `build.gradle.kts` definitions.
3. Securely provision the required model weights (e.g., Gemma-4) and load them into `app/src/main/assets/models/`.
4. Run a clean build to clear the C++ object caches: `./gradlew clean`
5. Compile the executable: `./gradlew :app:assembleDebug`

## Strict Thread Policies and Guidelines
To maintain our 120 FPS frame budget and zero-latency UI commitments, Scypheon utilizes aggressive `StrictMode` penalties (`VmPolicy` and `ThreadPolicy`).
- **Zero I/O on Main:** Any disk read, database query, or network attempt executed on the main UI thread will intentionally crash the application during development.
- **CompletableDeferred Locks:** Ensure any heavy initialization (e.g., PBKDF2 key derivation for SQLCipher) is delegated to `Dispatchers.IO` and synchronized across the UI using process-scoped barriers like `DatabaseReadySignal`.
- **Zero-Copy Adherence:** Never serialize large arrays (e.g., token sequences) across the Android Binder. Always utilize the `SharedMemory` pipeline via `ParcelFileDescriptor`.

## Submitting Pull Requests
1. Fork the repository and check out a feature branch originating from `main`.
2. Ensure your implementation adheres to all programmatic boundaries and passes linting: `./gradlew lintDebug`
3. Include rigorous unit testing for any modifications touching the `Helios Sentinel` security gates or the `ClinicalValidator`.
4. Submit a detailed Pull Request containing the Architectural Decision logic (Why) and the operational impact analysis.

## Security Disclosures
If you discover a zero-day vulnerability or memory leak capable of bypassing the Lazarus isolation, **do not open a public issue**. Please submit a secure disclosure directly to the repository maintainers.
