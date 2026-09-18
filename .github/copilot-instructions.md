# GitHub Copilot Instructions — Walkie-Talkie

## Product goal

Build a reliable push-to-talk walkie-talkie application.

The first release is an Android application backed by a Java server. Users join a channel, hold a push-to-talk button to transmit, and hear the current speaker with low delay. Treat this as a half-duplex system: only one user may transmit in a channel at a time.

## Required technology

- Server: Java 21 with Spring Boot 3.
- Mobile: Kotlin for Android. Prefer Jetpack Compose, coroutines, and structured concurrency.
- Realtime protocol: secure WebSocket (`wss://`).
- Use JSON text frames for control events and binary WebSocket frames for encoded audio.
- Encode audio as Opus, mono, 16 kHz, in 20 ms frames unless a measured requirement justifies another format.
- Use HTTP only for non-realtime concerns such as authentication, health checks, and optional channel discovery.
- Do not replace WebSocket with polling, SSE, WebRTC, or a third-party realtime service unless the task explicitly changes this constraint.

## Engineering principles

Apply these rules to every task:

### 1. Think before coding

- Restate the requested outcome and identify the affected server, Android, and protocol components.
- Inspect existing code and tests before editing.
- State assumptions when requirements are ambiguous.
- Identify protocol compatibility, concurrency, security, audio latency, lifecycle, and permission implications.
- When multiple reasonable approaches exist, briefly explain the tradeoff and choose the simplest one that meets the current requirement.
- Ask for clarification when a choice would materially alter behavior or compatibility.

### 2. Keep it simple

- Implement only the requested behavior and the smallest supporting code.
- Prefer standard Java, Spring, Kotlin, Android, and OkHttp APIs over new dependencies.
- Do not add speculative features, generic frameworks, premature abstractions, or distributed infrastructure.
- Start with a single deployable server and in-memory channel state. Add persistence, Redis, or horizontal fan-out only when a concrete requirement demands it.
- Prefer clear domain names and short, explicit functions over clever code.

### 3. Make surgical changes

- Modify only files required for the task.
- Preserve established naming, formatting, package structure, and public contracts.
- Do not refactor unrelated code, rename unrelated symbols, or rewrite working components.
- Keep server, mobile, and protocol changes in separate logical commits when practical.
- Never silently change a WebSocket message schema. Update protocol documentation and compatibility tests with every schema change.

### 4. Define and verify success

Before implementation, turn the request into observable acceptance criteria. Before declaring completion:

- Compile all affected modules.
- Run relevant unit, integration, and Android tests.
- Exercise the WebSocket flow end to end when the protocol changes.
- Test at least the happy path, invalid input, disconnect/reconnect, and contention for the talk lock.
- Report exactly what was verified and any checks that could not be run.
- Do not claim success from code inspection alone.

## MVP behavior

1. A user authenticates and opens one WebSocket connection.
2. The client sends `join_channel`.
3. The user presses push-to-talk and sends `request_talk`.
4. The server atomically grants the channel talk lock to at most one connection and broadcasts `talk_granted` and `speaker_changed`.
5. Only the lock holder may send binary audio frames.
6. The server validates and relays accepted audio to other channel members without persisting it.
7. Releasing the button sends `release_talk`; the server clears the lock and broadcasts `speaker_changed`.
8. Disconnect, timeout, or send failure also releases the user's talk lock.
9. A client denied the lock receives `talk_denied` and must not transmit audio.
10. The client reconnects with bounded exponential backoff and rejoins explicitly.

## WebSocket contract

Version every text message. Use lower snake case for event names and camelCase for JSON fields.

Example client event:

```json
{
  "version": 1,
  "type": "join_channel",
  "requestId": "b7a8f70f-0b80-43ef-aa90-1db1645304ca",
  "channelId": "ops"
}
```

Example server event:

```json
{
  "version": 1,
  "type": "talk_granted",
  "requestId": "b7a8f70f-0b80-43ef-aa90-1db1645304ca",
  "channelId": "ops",
  "speakerId": "user-123"
}
```

Supported control event types for the MVP:

- Client: `join_channel`, `leave_channel`, `request_talk`, `release_talk`, `ping`.
- Server: `channel_joined`, `channel_left`, `talk_granted`, `talk_denied`, `speaker_changed`, `pong`, `error`.

Every error event must contain a stable machine-readable `code`, a safe `message`, and the related `requestId` when available. Do not expose stack traces or internal identifiers.

Binary audio frame format:

| Bytes | Field | Encoding |
|---|---|---|
| 0 | Protocol version | Unsigned byte; MVP value is `1` |
| 1–4 | Sequence number | Unsigned 32-bit, big-endian |
| 5–12 | Capture timestamp | Signed 64-bit Unix milliseconds, big-endian |
| 13… | Audio payload | One Opus packet |

The server derives user and channel identity from the authenticated WebSocket session; never trust identity or channel metadata embedded in an audio payload. Reject binary frames from a client that is not the current speaker. Put explicit limits on JSON size, audio frame size, message rate, and talk duration.

## Server guidelines

- Organize code by responsibility: configuration, authentication, WebSocket transport, protocol messages, channel/talk coordination, and tests.
- Keep the WebSocket handler thin. Put talk-lock rules in a testable service.
- Make talk-lock acquisition and release atomic. Model membership and lock ownership by connection/session ID.
- Release locks idempotently on explicit release, disconnect, timeout, and error.
- Validate every incoming message at the boundary and reject unknown versions or event types cleanly.
- Do not block WebSocket event-loop or I/O threads.
- Apply bounded queues and backpressure. For live audio, drop stale frames instead of allowing unbounded memory growth.
- Use structured logs with connection, channel, and request correlation IDs, but never log tokens or raw audio.
- Expose liveness and readiness health checks.
- Keep secrets outside source control and load them from environment or secret storage.

## Android/Kotlin guidelines

- Separate UI, application/domain logic, WebSocket transport, and audio capture/playback.
- Model connection, channel membership, and push-to-talk as explicit state rather than scattered booleans.
- Use coroutines tied to lifecycle-aware scopes. Cancel recording, playback, and socket work when their owner stops.
- Hold-to-talk behavior must stop transmission on touch release, cancellation, lifecycle loss, socket loss, or server denial.
- Request microphone permission immediately before it is needed and explain denial without crashing.
- Never record in the background without an explicit product requirement and the required Android foreground-service treatment.
- Keep audio buffers bounded. Avoid allocations in the audio hot path where practical.
- Do not play the user's own relayed audio.
- Surface connecting, connected, reconnecting, denied, speaking, and listening states accessibly.
- Restore membership only after a successful reconnect; never assume the old server lock survived.

## Security and privacy

- Require TLS in all non-local environments.
- Authenticate during the WebSocket handshake and authorize every channel join.
- Treat all client messages and audio as untrusted input.
- Enforce payload, rate, connection, channel-membership, and maximum-transmit-duration limits on the server.
- Use generic external errors and detailed internal logs.
- Do not persist audio in the MVP. If recording is later requested, require explicit consent, retention rules, access controls, and a separate design review.
- Do not commit credentials, private keys, production endpoints, personal data, or captured audio fixtures.

## Testing expectations

Server tests should cover:

- authentication and channel authorization;
- message parsing, validation, and unsupported versions;
- atomic talk-lock contention;
- rejection of audio from non-speakers;
- release on button-up, disconnect, timeout, and repeated release;
- payload and rate limits;
- WebSocket integration flow for two or more clients.

Android tests should cover:

- push-to-talk state transitions;
- permission denial;
- reconnect and explicit rejoin;
- stopping capture on lifecycle or connection loss;
- protocol encoding/decoding;
- bounded audio buffering and stale-frame behavior.

Use generated synthetic tones or tiny licensed fixtures in tests. Never add real user recordings.

## Definition of done for an MVP slice

A change is complete only when its acceptance criteria are met, affected code builds, relevant tests pass, the documented protocol matches the implementation, failures are handled safely, and no unrelated files were changed.

When a requested feature conflicts with this document, follow the explicit user requirement, call out the conflict, and update this guideline or protocol documentation if the change is intended to be permanent.
