package me.markerra.rtcbridge.audio;

/** Generates PCM frames of one continuous sine wave for transport testing. */
public final class PcmSineGenerator {
    private static final double TWO_PI = Math.PI * 2;

    private final AudioFormat format;
    private final double phaseStep;
    private final short amplitude;
    private double phase;

    public PcmSineGenerator(AudioFormat format, double frequencyHz, double volume) {
        if (!format.isSupported()) {
            throw new IllegalArgumentException("Unsupported audio format: " + format);
        }
        if (frequencyHz <= 0 || frequencyHz >= format.sampleRate() / 2.0) {
            throw new IllegalArgumentException("Frequency must be between 0 and the Nyquist frequency.");
        }
        if (volume <= 0 || volume > 1) {
            throw new IllegalArgumentException("Volume must be in (0, 1].");
        }
        this.format = format;
        this.phaseStep = TWO_PI * frequencyHz / format.sampleRate();
        this.amplitude = (short) Math.round(Short.MAX_VALUE * volume);
    }

    /**
     * Creates exactly one frame. A frame boundary does not reset {@code phase},
     * so adjacent frames form one continuous wave instead of producing clicks.
     */
    public byte[] nextFrame() {
        byte[] frame = new byte[format.expectedFrameBytes()];
        for (int offset = 0; offset < frame.length; offset += Short.BYTES) {
            short sample = (short) Math.round(Math.sin(phase) * amplitude);
            frame[offset] = (byte) sample;
            frame[offset + 1] = (byte) (sample >>> Byte.SIZE);
            phase = (phase + phaseStep) % TWO_PI;
        }
        return frame;
    }
}
