# LiteLedger

LiteLedger is a minimalistic, privacy-focused personal finance Android application designed to track transactions between you and other people. Built with a "thin as air" philosophy, it focuses on speed, aesthetics, and essential functionality without the bloat.

## Features

- **Person Tracking**: Easily add people and track balances (money given vs. received).
- **Intuitive Dashboard**: 
  - Swipe-to-action gestures (Rename/Delete) with fluid spring animations.
  - Collapsible top bar for maximum content visibility.
  - Smart search with instant filtering.
- **Transaction Details**: Log specific "Gave" or "Got" transactions with notes and dates.
- **Privacy Mode**: Toggle to hide sensitive balance amounts from the dashboard.
- **Security**: Optional Biometric App Lock (Fingerprint/Face Unlock).
- **Data Management**:
  - Backup and Restore data via JSON.
  - Export transactions to CSV for external analysis.
- **Customization**:
  - Light/Dark/System themes.
  - Haptic feedback controls.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3 Expressive Design)
- **Architecture**: MVVM
- **Local Database**: Room
- **Async**: Coroutines & Flow
- **Navigation**: Typesafe Navigation Compose
- **Other Libs**: 
  - AndroidX Biometric
  - Serialization (JSON)
  - Haptics

## Setup & Build

1. **Prerequisites**:
   - Android Studio (Koala or newer recommended)
   - JDK 11 or higher
   - Android SDK API 35 (compileSdk)

2. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/LiteLedger.git
   ```

3. **Open in Android Studio**:
   - Open Android Studio and select "Open an existing Android Studio project".
   - Navigate to the cloned directory.

4. **Build and Run**:
   - Sync Gradle files.
   - Select a device/emulator (minSdk 26).
   - Click Run (Shift+F10).

## screenshots

*(Add screenshots here)*

## Contributing

Contributions are welcome! Please follow the code style (Kotlin official style guide) and ensure all existing functionality is preserved.

## License

[MIT License](LICENSE)
