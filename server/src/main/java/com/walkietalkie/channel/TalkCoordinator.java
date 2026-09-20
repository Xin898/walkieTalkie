package com.walkietalkie.channel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TalkCoordinator {
    public enum Result { GRANTED, DENIED, RELEASED, NOT_MEMBER }
    private final Map<String, String> members = new HashMap<>();
    private final Map<String, Long> talkStartedAt = new HashMap<>();
    private final long maxTalkMillis;

    public TalkCoordinator(Duration maxTalkDuration) {
        this.maxTalkMillis = maxTalkDuration.toMillis();
    }

    public synchronized void join(String connectionId, String channelId) {
        members.put(connectionId, channelId);
    }

    public synchronized void leave(String connectionId) {
        members.remove(connectionId);
        talkStartedAt.remove(connectionId);
    }

    public synchronized Result requestTalk(String connectionId) {
        String channel = members.get(connectionId);
        if (channel == null) return Result.NOT_MEMBER;
        expireTalkers();
        boolean occupied = talkStartedAt.keySet().stream().anyMatch(id -> Objects.equals(members.get(id), channel));
        if (occupied) return Result.DENIED;
        talkStartedAt.put(connectionId, System.currentTimeMillis());
        return Result.GRANTED;
    }

    public synchronized Result releaseTalk(String connectionId) {
        if (!members.containsKey(connectionId)) return Result.NOT_MEMBER;
        talkStartedAt.remove(connectionId);
        return Result.RELEASED;
    }

    public synchronized boolean canSendAudio(String connectionId, int payloadBytes) {
        expireTalkers();
        return payloadBytes > 0 && members.containsKey(connectionId) && talkStartedAt.containsKey(connectionId);
    }

    public synchronized String channelOf(String connectionId) { return members.get(connectionId); }
    public synchronized String speakerOf(String channelId) {
        expireTalkers();
        return talkStartedAt.keySet().stream().filter(id -> channelId.equals(members.get(id))).findFirst().orElse(null);
    }

    private void expireTalkers() {
        long now = System.currentTimeMillis();
        talkStartedAt.entrySet().removeIf(entry -> now - entry.getValue() >= maxTalkMillis);
    }
}
