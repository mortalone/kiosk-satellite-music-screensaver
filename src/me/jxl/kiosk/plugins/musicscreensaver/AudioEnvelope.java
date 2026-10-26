package me.jxl.kiosk.plugins.musicscreensaver;

/**
 * Band-domain beat envelope tracker.
 *
 * Expands log-compressed FFT bass magnitudes linearly to separate drum punches
 * from continuous low-frequency hum, maintaining an adaptive baseline that tracks
 * room acoustics without locking out during loud passages.
 */
final class AudioEnvelope {
    private float baseline = 0.2f;
    private float previous = 0f;
    private float strength = 0f;
    private long last = -1;
    private long hit = -10000;
    private int beatCount = 0;

    void feed(float rawBass, long now) {
        float bass = Float.isFinite(rawBass) ? Math.max(0f, rawBass) : 0f;
        // Non-linear power expansion to separate drum kicks from baseline hum
        float linBass = (float) Math.pow(Math.min(1.8f, bass), 2.5f);

        if (last < 0 || now - last > 500) {
            baseline = linBass;
            previous = linBass;
            strength = 0;
            last = now;
            return;
        }

        float dt = Math.max(0.001f, Math.min(0.25f, (now - last) / 1000f));
        float diff = linBass - baseline;
        float dynamicThreshold = Math.max(0.035f, baseline * 0.16f);

        // Beat hit: minimum 110ms between beats (~545 BPM max), above adaptive threshold, rising
        if (now - hit >= 110 && diff > dynamicThreshold && linBass > previous) {
            strength = Math.min(1.5f, Math.max(0.35f, diff / Math.max(0.04f, baseline * 0.45f)));
            hit = now;
            beatCount++;
        }

        // Adaptive baseline tracks background loudness
        float timeConstant = linBass > baseline ? 1.2f : 0.45f;
        baseline += (linBass - baseline) * (1f - (float) Math.exp(-dt / timeConstant));
        previous = linBass;
        last = now;
    }

    float pulse(long now) {
        if (last < 0 || now - last > 500) return 0f;
        long elapsed = Math.max(0, now - hit);
        if (elapsed > 400) return 0f;
        return strength * (float) Math.exp(-elapsed / 160f);
    }

    long getHitTime() {
        return hit;
    }

    float getStrength() {
        return strength;
    }

    float getBaseline() {
        return baseline;
    }

    int getBeatCount() {
        return beatCount;
    }
}
