# WebSocket protocol

The server endpoint is `wss://host/ws?userId=<authenticated-user-id>`. Production deployments must authenticate the handshake and authorize channel membership; the query parameter is only a local-development identity placeholder.

Text messages are JSON with `version: 1`, lower snake case `type`, and camelCase fields. Client events are `join_channel`, `leave_channel`, `request_talk`, `release_talk`, and `ping`. Server events are `channel_joined`, `channel_left`, `talk_granted`, `talk_denied`, `speaker_changed`, `pong`, and `error`.

Binary audio frames contain a version byte, a big-endian unsigned 32-bit sequence, a big-endian signed 64-bit Unix millisecond timestamp, and one Opus packet. Frames are mono 16 kHz, 20 ms, and must be no larger than 4096 bytes. The current speaker is the only sender accepted by the server.
