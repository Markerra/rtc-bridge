package me.markerra.rtcbridge.audio;

/**
 * The one raw-audio format supported by the first bridge protocol version.
 * Raw PCM means that every sample is sent directly, without a container such as WAV.
 */
public record AudioFormat(int sampleRate, int channels, int bitsPerSample, int frameDurationMs) {
    public static final AudioFormat VOICE_CHAT = new AudioFormat(48_000, 1, 16, 20);

    /**
     * calculates expected size of one raw PCM frame in bytes
     * for 48kHz, 1 ch, 16-bit, 20ms -> (48000 * 20 / 1000) * 1 * 2 = 1920 bytes.
     */
    public int expectedFrameBytes() {
        int samplesPerChannel = sampleRate * frameDurationMs / 1_000;
        return samplesPerChannel * channels * (bitsPerSample / Byte.SIZE);
    }

    public boolean isSupported() {
        return equals(VOICE_CHAT);
    }
}
