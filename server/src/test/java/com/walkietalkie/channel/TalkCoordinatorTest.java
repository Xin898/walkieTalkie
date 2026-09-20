package com.walkietalkie.channel;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TalkCoordinatorTest {
    @Test void allowsOnlyOneSpeakerPerChannel() {
        TalkCoordinator coordinator = new TalkCoordinator(Duration.ofSeconds(30));
        coordinator.join("one", "ops");
        coordinator.join("two", "ops");
        assertEquals(TalkCoordinator.Result.GRANTED, coordinator.requestTalk("one"));
        assertEquals(TalkCoordinator.Result.DENIED, coordinator.requestTalk("two"));
        assertTrue(coordinator.canSendAudio("one", 100));
        assertFalse(coordinator.canSendAudio("two", 100));
    }

    @Test void releaseIsIdempotentAndAllowsNextSpeaker() {
        TalkCoordinator coordinator = new TalkCoordinator(Duration.ofSeconds(30));
        coordinator.join("one", "ops");
        coordinator.join("two", "ops");
        coordinator.requestTalk("one");
        assertEquals(TalkCoordinator.Result.RELEASED, coordinator.releaseTalk("one"));
        assertEquals(TalkCoordinator.Result.RELEASED, coordinator.releaseTalk("one"));
        assertEquals(TalkCoordinator.Result.GRANTED, coordinator.requestTalk("two"));
    }

    @Test void expiresTalkAndRejectsNonMembers() throws InterruptedException {
        TalkCoordinator coordinator = new TalkCoordinator(Duration.ofMillis(1));
        coordinator.join("one", "ops");
        coordinator.requestTalk("one");
        Thread.sleep(5);
        assertFalse(coordinator.canSendAudio("one", 100));
        assertEquals(TalkCoordinator.Result.NOT_MEMBER, coordinator.requestTalk("missing"));
    }
}
