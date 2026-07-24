package me.markerra.rtcbridge.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioFormatTest {
    @Test
    void voiceChatFormatHasFiftyFramesPerSecond() {
        assertEquals(1_920, AudioFormat.VOICE_CHAT.expectedFrameBytes());
        assertEquals(96_000, AudioFormat.VOICE_CHAT.expectedFrameBytes() * 50);
    }
}
