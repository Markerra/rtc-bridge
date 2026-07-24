package me.markerra.rtcbridge.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PcmSineGeneratorTest {
    @Test
    void producesFixedSizeNonSilentFrames() {
        var generator = new PcmSineGenerator(AudioFormat.VOICE_CHAT, 440, 0.25);

        byte[] frame = generator.nextFrame();

        assertEquals(1_920, frame.length);
        assertEquals(0, frame[0]);
        assertNotEquals(0, frame[20]);
    }
}
