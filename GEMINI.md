# Project Overview: UNO (Android Multiplayer Game)

This is an Android application for playing the UNO card game over a Local Area Network (LAN). It supports 2-4 players and features automated LAN discovery using UDP.

## Core Technologies
- **Android SDK (Java):** Minimum SDK 24, Target SDK 34.
- **Networking:** 
  - **UDP (Port 6001):** Used for LAN discovery (`LanHostScanner`, `HostService` discovery responder).
  - **TCP (Port 6000):** Used for game state synchronization between the Host and Clients.
- **Architecture:** 
  - **Lobby-based:** Players join a lobby before starting the match.
  - **Snapshot System:** The server (`HostService`) broadcasts game state snapshots as JSON strings to all connected clients (`ClientService`).
  - **Material Design 3:** Uses Material components and Dynamic Colors.

## Key Components & Architecture
- **`pro.uno.LobbyActivity`:** The entry point where players can choose to host or join a game. Handles LAN scanning and lobby state.
- **`pro.uno.GameActivity`:** The main game UI. It renders the player's hand, the discard pile, and handles player interactions (playing cards, drawing, calling UNO).
- **`pro.uno.HostService`:** The central game engine. It manages the deck, player hands, turns, and applies card effects. It runs on the host device.
- **`pro.uno.ClientService`:** A singleton that manages the TCP connection to the host and broadcasts server messages to the UI.
- **`pro.uno.cards` Package:** Contains the logic for different card types:
  - `Card`: Base abstract class for all cards.
  - `NormalCard`: Standard numbered cards.
  - `CardPlusTwo`, `CardPlusFour`: Draw cards.
  - `CardReverse`, `CardBlock`: Direction change and skip cards.
  - `CardColorChange`: Wild cards.
- **`pro.uno.CardRegistry`:** Factory class to create card instances and validate play rules.

## Building and Running
The project uses Gradle for builds and dependency management.

### Build Commands
- **Assemble Debug APK:** `./gradlew assembleDebug`
- **Clean Project:** `./gradlew clean`

### Running
- Install the debug APK onto an Android device or emulator using Android Studio or `adb install`.
- Ensure all players are on the same Wi-Fi network for LAN discovery to work.

### Testing
- **Local Unit Tests:** `./gradlew test` (Target: `LogicUnitTest.java`)
- **Instrumentation Tests:** `./gradlew connectedAndroidTest` (Target: `ExampleInstrumentedTest.java`)

## Development Conventions
- **State Management:** The game state is serialized into JSON in `HostService` and deserialized in `LobbySnapshot` or `GameSnapshot`.
- **UI Updates:** Activities listen for "snapshot" broadcasts from `ClientService` and update the UI accordingly.
- **Card Formatting:** Card IDs follow a specific string format: `family_color_value` (e.g., `card_red_5`, `super_black_plus`). Use `CardFormatter` for normalization.
- **Theming:** Adhere to Material Design 3 guidelines. Use `MaterialAlertDialogBuilder` for dialogs and `MaterialButton` for UI actions.
- **Logging:** Use standard Android `Log` or `Toast` for debugging and user feedback.

## Network Protocol (Simplified)
- **Discovery:** Client sends `UNO_DISCOVER_REQUEST_V1` via UDP broadcast to port 6001. Host responds with its name, IP, and current player count.
- **Game State:** Server sends `snapshot|<JSON>` to clients.
- **Client Commands:** Clients send commands like `join|<name>`, `ready|1`, `play|<card_id>`, `draw`, `uno` over the TCP socket.
