package com.walkietalkie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "walkie-talkie")
public record WalkieProperties(int maxJsonBytes, int maxAudioBytes, int maxTalkSeconds) {}
