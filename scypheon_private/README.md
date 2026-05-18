# Scypheon Private: Application & UI Client Workspace Component
*Path: `:scypheon_private`*

This directory contains the main **Jetpack Compose UI Client** and Hilt Dependency Injection module setup for Scypheon Private. The application is built using advanced reactive state flows, Obsidian glassmorphism styling, and dynamic on-demand model boot-up sequences.

---

## 🎨 Core Architectural Features

### 1. Lazy Standby Engine Loading & Non-Dropping Channels
The application maintains instant cold-starts by avoiding blocking loading screens. 
* At startup, models are placed in a `(STANDBY)` state. 
* When the user submits their first chat prompt or opens Live Voice Mode, a JIT (Just-In-Time) coroutine is launched in [MainViewModel.kt](file:///D:/AuraLink/scypheon_private/app/src/main/java/com/scypheon/app/ui/MainViewModel.kt) to initialize the backend neural engine.
* User prompts submitted while the model is booting are safely captured inside a non-dropping Coroutine `Channel` and processed sequentially as soon as the state transitions to `Success`.

### 2. Premium UI Screens & Composables
* 🟢 **Welcome Status Badge:** Located in [MainChatScreen.kt](file:///D:/AuraLink/scypheon_private/app/src/main/java/com/scypheon/app/ui/screens/MainChatScreen.kt#L690). Features a premium glassmorphic pill badge with a thin border and a breathing neon-green status dot that pulses via Jetpack Compose's `InfiniteTransition`. Unicode bullets are escape-coded (`\u2022`) to prevent mojibake corruption.
* 🎙️ **Obsidian Live Orb Interface:** Hosted in [LiveModeScreen.kt](file:///D:/AuraLink/scypheon_private/app/src/main/java/com/scypheon/app/ui/screens/LiveModeScreen.kt). Implements an immersive obsidian dark layout (`Color(0xFF090A0F)`), neon-watercolor ambient canvas waves, and a central morphing Orb controller.

---

## 🎙️ Push-to-Talk (PTT) Voice Interaction Flow
To ensure absolute reliability in high-noise or crisis zones, the voice pipeline uses a manual **Push-to-Talk (PTT)** interaction loop:
1. **Orb Click (Start Session):** Switches from `Idle` to `Listening`.
2. **Orb Click (Record):** Begins local PCM speech capture, changing the watercolor canvas flow.
3. **Orb Click (Manual Submit):** Stops recording and submits the audio stream for local translation.
4. **Interruption:** Clicking the Orb while the AI is speaking or processing immediately aborts the JIT generation and resets to `Listening` standby.

---

## 📘 Sub-Directory Map

* 📂 **`app/src/main/java/com/scypheon/app/`**
  * `ui/MainActivity.kt` — Manages foreground task elevation and layout orchestration.
  * `ui/MainViewModel.kt` — Orchestrates active engine state machines, prompt queues, and orb manual clicks.
  * `ui/screens/MainChatScreen.kt` — Implements the primary chat bubble thread container and welcome greeting.
  * `ui/screens/LiveModeScreen.kt` — Renders the high-end Obsidian audio canvas.
  * `di/SdkModule.kt` — Binds the SDK modules (Neural Gateway, Dual Memory, Circuit Breakers) to Hilt scopes.
