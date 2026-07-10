# Repository Guidelines

## Project Structure & Module Organization

```
├── app/
│   ├── src/main/java/com/app/checkot/
│   │   ├── model/            # Data classes (Models.kt)
│   │   ├── navigation/       # Compose Navigation graph
│   │   ├── service/          # Firebase messaging, notifications
│   │   ├── ui/screens/       # Compose screen composables
│   │   ├── ui/theme/         # Material3 theme (Color, Type, Theme)
│   │   ├── viewmodel/        # MVVM ViewModels
│   │   └── utils/            # Helpers (DateUtils)
│   ├── src/main/res/         # Android resources (drawables, xml)
│   └── build.gradle.kts      # App-level dependencies
├── gradle/libs.versions.toml # Version catalog
├── build.gradle.kts          # Root build config
├── settings.gradle.kts       # Project settings
└── firestore.rules           # Firestore security rules
```

The app follows **MVVM** with Jetpack Compose, Firebase Auth/Firestore/FCM, and Navigation Compose. Screens are under `ui/screens/`; each screen consumes ViewModels from `viewmodel/`.

## Build, Test, and Development Commands

| Command | Purpose |
|---|---|
| `./gradlew assembleDebug` | Build debug APK |
| `./gradlew assembleRelease` | Build release (R8 disabled, debug keystore) |
| `./gradlew test` | Run unit tests (`app/src/test/`) |
| `./gradlew connectedAndroidTest` | Run instrumentation tests on emulator/device |
| `./gradlew lint` | Run Android lint checks |

Requires **JDK 17** and Android SDK 36. Open in Android Studio for the best IDE experience.

## Coding Style & Naming Conventions

- **Language**: Kotlin, target JVM 17.
- **Indentation**: 4 spaces (Kotlin convention). Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Files**: PascalCase for classes/composables (`HomeScreen.kt`), camelCase for utilities (`DateUtils.kt`).
- **Package**: `com.app.checkot` with sub-packages by feature layer (`model`, `viewmodel`, `ui/screens`).
- **Imports**: Wildcard imports are acceptable. Organize with Android Studio defaults.
- **Compose**: Use `MaterialTheme` wrappers, `collectAsState()` for ViewModel flows, and pass `navController` through the navigation graph.

## Testing Guidelines

- **Unit tests**: JUnit 4 (`testImplementation("junit:junit:4.13.2")`). Place in `app/src/test/`.
- **Instrumentation tests**: Espresso + Compose UI testing (`androidTestImplementation`). Place in `app/src/androidTest/`.
- No coverage thresholds are enforced. Run `./gradlew test` before submitting PRs.

## Commit & Pull Request Guidelines

- **Commit format**: `Type: Short description` — see `git log --oneline` for examples (`Fix:`, `Feat:`, `Refactor:`, `Cleanup:`).
- **PRs**: Link related issues, describe what changed and why. Include screenshots for UI changes.
- **Branching**: Work on feature/fix branches off `main`.

## Security & Configuration

- `google-services.json` is **gitignored** — each developer must provide their own Firebase project config.
- `firestore.rules` defines Firestore access rules. Test rule changes in the Firebase console before deploying.
- Debug builds use the default debug keystore at `~/.android/debug.keystore`.
