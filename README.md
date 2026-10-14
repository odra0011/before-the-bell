# Before the Bell — Preview

*Before the Bell* is an interactive narrative RPG and storytelling platform built natively for Android using Jetpack Compose. Driven by large language models, the app delivers reactive, turn-based dialogue, continuity-aware memory tracking, relationship dynamics, and customizable prompt pipelines.

This **Preview** release (`com.beforethebell.preview`) runs as a completely standalone installation with dedicated local storage, allowing it to coexist alongside any other installed versions.

---

## Features

- **Dynamic Turn Engine**: Real-time narrative progression with structured player choices (action and speech) alongside free-form text input.
- **Continuity & Memory System**: Persistent character journals, emergent world facts, and relational anchors tracked turn-by-turn.
- **Modular Prompt Pipeline**: Fully inspectable and customizable prompt modules (System Prompts, Scenario Framing, Dialogue Directives, Freaky Mode, etc.) with custom ordering and toggle controls.
- **Multi-Provider Support**: Seamlessly connect to Google Vertex AI, Google Gemini API (AI Studio), OpenRouter, or any custom OpenAI-compatible server.
- **Character Compatibility**: Import SillyTavern / V2 character cards (JSON or embedded metadata) or craft characters within the app.
- **Hermetic Save & Branching**: Branch any campaign at any previous turn to explore alternate outcomes.

---

## Installing the APK

1. Go to the [Releases](https://github.com/odra0011/before-the-bell/releases) tab of this repository.
2. Download the latest `BeforeTheBell-Preview.apk`.
3. Open the APK on your Android device (requires Android 7.0 / API 24 or newer).
4. If prompted, allow your browser or file manager to "Install unknown apps".
5. Complete installation. The app will appear in your launcher as **Before the Bell — Preview**.

---

## Setup: Provider & API Credentials

To generate story turns and choices, *Before the Bell* connects directly to your chosen AI provider. **No subscription is charged by this app; API usage is billed directly by your selected provider.**

1. Tap the **Settings** icon (gear) in the top bar.
2. Select your provider:
   - **Google Vertex AI**: High-throughput Google Cloud endpoint. Enter your Vertex API key and Model ID (e.g., `gemini-2.5-flash`).
   - **Google Gemini API / AI Studio**: Standard Google AI Studio API. Enter your Gemini API key (`AIza...`) and Model ID (e.g., `gemini-2.5-flash`).
   - **OpenRouter**: Access hundreds of models with unified billing. Enter your OpenRouter API key (`sk-or-v1-...`) and Model ID (e.g., `anthropic/claude-3.5-sonnet`, `meta-llama/llama-3.3-70b-instruct`).
   - **Custom OpenAI-Compatible**: Connect to local servers (LM Studio, Ollama, vLLM, llama.cpp) or third-party proxies. Enter your Base URL (e.g., `http://192.168.1.100:1234/v1`), Model ID, and optional API key.
3. Tap **Test Connection** to confirm connectivity.
4. Your API keys are saved securely in private app storage on your device and are never exported, logged, or sent to third-party tracking services.

---

## Character Cards & Prompt Presets

### Characters
- Switch to the **Characters** tab to view, edit, or create characters.
- Tap **Import** to load SillyTavern / Character Card V2 JSON files.

### Prompt Modules & Presets
- Switch to the **Prompts** tab to inspect active prompt modules.
- The app comes bundled with the default **Before the Bell** preset, containing tuned narrative modules (including Freaky Mode).
- **Toggle Modules**: Enable or disable specific prompt layers with a single switch.
- **Custom Ordering**: Reorder prompt execution sequence to fit your narrative preferences.
- **Import / Export**: Save and share custom configurations as `.json` presets.
- **Preset Management**: Delete unwanted presets with the trash icon; your ongoing campaign retains its prompt configuration even if its source preset is deleted.

---

## Building from Source

### Prerequisites
- JDK 17 (or newer)
- Android SDK (API 36 platform and build tools)
- Android NDK (optional, for native optimizations if configured)

### Build Steps

1. Clone this repository:
   ```bash
   git clone https://github.com/odra0011/before-the-bell.git
   cd before-the-bell
   ```

2. Build the Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. Build the Release APK:
   ```bash
   ./gradlew assembleRelease
   ```
   *(If no external release keystore is configured in `signing.properties`, Gradle automatically uses the debug signing config for convenient local testing).*
   The release APK will be located at:
   `app/build/outputs/apk/release/app-release.apk`

---

## License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.
