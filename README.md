# Bumpr

**Bumpr** is an Android music player focused on local audio playback, synchronized lyrics, audio customization, and a polished, themeable listening experience. It is built with Kotlin and Jetpack Compose, with AndroidX Media3 handling playback and media-session integration.

> This README describes the implementation present in this repository. Features that depend on device permissions or audio hardware may behave differently across Android devices.

## Features

### Music library
- Scans audio available on the device through Android `MediaStore`.
- Lets users choose which music folders are included in the library.
- Organizes the library by **Songs**, **Albums**, **Artists**, **Folders**, and **Favorites**.
- Supports searching the available library.
- Supports importing audio from a user-selected folder using Android's Storage Access Framework.

### Playback
- Plays local audio through AndroidX Media3 ExoPlayer.
- Provides a dedicated Now Playing screen.
- Supports play/pause, next/previous, seeking, shuffle, and repeat controls.
- Runs playback in a `MediaLibraryService`, allowing audio to continue when the app is in the background.
- Integrates with Android media controls and exposes a browsable media library for compatible clients, including Android Auto.

### Lyrics
- Reads synchronized `.lrc` lyric files located alongside supported audio files.
- Supports plain-text lyrics as well as timestamped LRC lyrics.
- Parses LRC metadata such as title, artist, and timing offset.
- Highlights the active lyric line during playback.
- Includes a lyrics screen and a lyrics synchronization editor.
- Saves edited lyrics locally using Room.
- Provides a setting to control lyrics display in the car experience.

### Audio controls
- Includes an equalizer interface with preset configurations.
- Includes bass boost and treble controls where supported by the device audio effects implementation.
- Persists equalizer settings between sessions.

### Personalization
- Offers multiple app color themes, including Neon Waves, Soundwave Obsidian, Sunset Retro, Cyberpunk Lime, Midnight Amethyst, Turquoise Classic, and Pure AMOLED.
- Supports light, dark, and system theme modes.
- Offers background styles such as musical notes, audio waveform, aurora gradient, and minimal.
- Includes a keep-screen-awake option and a configurable lyrics font size.

### Local persistence
- Uses **Room** for saved lyrics and favorite tracks.
- Uses **Preferences DataStore** for app settings and selected music folders.

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 ExoPlayer |
| Media session / library | AndroidX Media3 Session |
| Architecture | Activity + ViewModel, Kotlin Coroutines and StateFlow |
| Local database | Room |
| Settings | Preferences DataStore |
| Image loading | Coil |
| Navigation | Navigation Compose |
| Dependency management | Gradle Kotlin DSL, version catalog |
| Tests | JUnit, AndroidX test, Robolectric |

## Project structure

```text
Bumpr/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/
│       │   │   ├── data/          # Room database, preferences, media scanning
│       │   │   ├── lyrics/        # LRC parsing and lyric utilities
│       │   │   ├── model/         # Track, lyric, theme and EQ models
│       │   │   ├── playback/      # Media3 service and controller connection
│       │   │   └── ui/
│       │   │       ├── components/
│       │   │       ├── screens/   # Library, Now Playing, Lyrics, Settings, EQ
│       │   │       └── theme/
│       │   └── res/               # App resources and launcher assets
│       └── test/                  # Local unit and Robolectric tests
├── gradle/
│   └── libs.versions.toml         # Dependency and plugin versions
├── build.gradle.kts
└── settings.gradle.kts
```

## Requirements

The project configuration currently specifies:

- **Android Studio** with support for the configured Android Gradle Plugin.
- **JDK 11-compatible source/target compilation** (use the JDK recommended by the installed Android Gradle Plugin/Gradle version).
- **Android SDK Platform 36.1** for compilation.
- **Minimum Android version: Android 7.0 (API 24)**.
- A physical Android device or emulator for running the app.

The app targets **Android 16 / API 36**. Gradle and plugin versions are defined in `gradle/libs.versions.toml` and the Gradle wrapper configuration.

## Getting started

1. Clone the repository:

   ```bash
   git clone https://github.com/<your-username>/Bumpr.git
   cd Bumpr
   ```

2. Open the project in Android Studio.

3. Allow Android Studio to sync the Gradle project and download dependencies.

4. Connect an Android device or start an emulator.

5. Run the `app` configuration.

You can also build from the project root using the Gradle wrapper:

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

The debug APK is normally generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

Bumpr requests Android permissions needed for its local media and background playback features. Depending on the Android version and the actions used, these include:

- `READ_MEDIA_AUDIO` on newer Android versions.
- `READ_EXTERNAL_STORAGE` on Android 12L and earlier.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for background playback.
- `POST_NOTIFICATIONS` for playback notifications on Android versions that require it.
- `WAKE_LOCK` to support playback-related wake behavior.

Grant the requested audio permission and select the folders you want included in the library. Folder access granted through the system folder picker is managed separately by Android.

## Lyrics

For automatic sidecar lyric discovery, place an `.lrc` file beside the corresponding audio file and use the same base filename.

Example:

```text
Music/
├── Example Song.mp3
└── Example Song.lrc
```

Bumpr's parser recognizes common LRC timestamp lines and metadata tags such as:

```text
[ti:Example Song]
[ar:Example Artist]
[offset:0]
[00:12.30]First lyric line
[00:16.00]Second lyric line
```

The parser also supports multiple timestamps on a single lyric line and plain-text lyric content. Timing quality depends on the supplied LRC file.

## Tests

Run local unit tests with:

```bash
# Windows
gradlew.bat test

# macOS / Linux
./gradlew test
```

Run Android instrumented tests on a connected device or emulator with:

```bash
# Windows
gradlew.bat connectedAndroidTest

# macOS / Linux
./gradlew connectedAndroidTest
```

The repository includes tests for basic app resources, LRC parsing and active-line lookup, and folder filtering logic.

## Configuration and secrets

The repository includes an `.env.example` template for the Secrets Gradle Plugin. It contains a commented placeholder for `GEMINI_API_KEY`.

The inspected application source does not currently show Gemini API calls or Firebase authentication/Firestore usage. Do not add real API keys or credentials to source control. If you introduce a service that requires a key, configure it locally using the project's secrets mechanism and document the required setup.

The Google Services Gradle plugin is configured with a warning strategy when its configuration is missing. A `google-services.json` file is not included in this repository archive.

## Notes and implementation boundaries

- The library scanner filters to music tracks and excludes very short audio entries (under five seconds).
- The MediaStore scanner uses folder names to apply the selected-folder filter. If two different storage locations contain folders with the same name, they may be treated as the same selection.
- Folder-picker imports currently enumerate audio documents directly inside the selected folder; nested-folder traversal is not implemented in that scanner.
- Hardware equalizer and bass-boost availability varies by device and Android audio implementation.
- Android Auto availability and behavior depend on the installed Android Auto / vehicle environment.
- The current Gradle configuration sets `versionCode` to `1` and `versionName` to `1.0`; update these when preparing a release.

## Contributing

Contributions, bug reports, and feature suggestions are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make and test your changes.
4. Open a pull request describing the change and how it was tested.

## License

No license file was present in the inspected repository. Until a license is added, the project should be treated as **all rights reserved**; do not assume it is open source or reuse its code under an open-source license.

---

**Bumpr** — your local music, your sound, your style.
