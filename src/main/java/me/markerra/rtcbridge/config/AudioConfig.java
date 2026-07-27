package me.markerra.rtcbridge.config;

import me.markerra.rtcbridge.audio.AudioFormat;

public record AudioConfig(double volume, AudioFormat format) {
    public AudioConfig {
        if (format == null) format = AudioFormat.VOICE_CHAT;
    }
}