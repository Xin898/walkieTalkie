# Walkie-Talkie Project Startup Configuration Plan

This plan outlines the steps required to successfully start and run the Walkie-Talkie project, including both the backend server and the Android application.

## User Review Required

> [!IMPORTANT]
> **WebSocket URL Configuration**: The current URL is a placeholder. You must decide whether to run the server locally or on a remote host.
> **Device Access**: An Android device or emulator must be running and accessible via ADB.

## Proposed Changes

### 1. Backend Server Setup
The project includes a Spring Boot backend in the `server/` directory.

- **Action**: Start the server.
- **Command**: From the root directory, run `cd server && ./mvnw spring-boot:run` (or use the IDE's Spring Boot runner).
- **Default Port**: `8080`.
- **WebSocket Endpoint**: `ws://localhost:8080/ws`.

### 2. Android App Configuration
#### [MODIFY] [build.gradle.kts](file:///C:/Workspace/walkieTalkie/app/build.gradle.kts)
Update the `WALKIE_WS_URL` to point to the server.
- For **Android Emulator**: Use `ws://10.0.2.2:8080/ws`.
- For **Physical Device**: Use the local IP of your development machine (e.g., `ws://192.168.1.5:8080/ws`).

#### [FIX] [local.properties](file:///C:/Workspace/walkieTalkie/local.properties)
Ensure the Android SDK path is correctly configured. If you are using Android Studio, this should be handled automatically, but if you are running from the command line, you may need to create this file:
```properties
sdk.dir=/path/to/your/android/sdk
```

### 3. Missing Features (Future)
> [!NOTE]
> The current Android client code (`WalkieViewModel`) implements the signaling protocol (joining channels, requesting talk) but **does not yet implement audio recording or playback**. To have a functional walkie-talkie, you will need to add:
> - `AudioRecord` for capturing voice.
> - `AudioTrack` or `MediaPlayer` for playback.
> - Opus encoding/decoding as per `protocol.md`.

## Verification Plan

### Manual Verification
1. **Start Server**: Verify the server is running by checking `http://localhost:8080/actuator/health`.
2. **Deploy App**: Run the app on an emulator.
3. **Check Connection**: Verify the UI changes from "Connecting" to "Channel ops" and "Listening".
4. **Test Signaling**: Press the "HOLD TO TALK" button and verify the status changes to "Transmitting" (and check server logs for `request_talk` event).
