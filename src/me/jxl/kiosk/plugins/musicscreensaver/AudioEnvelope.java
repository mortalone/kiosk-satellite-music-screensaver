package me.jxl.kiosk.plugins.musicscreensaver;

/**
 * Band-domain beat envelope tracker.
 *
 * Expands log-compressed FFT bass magnitudes linearly to separate drum punches
 * from continuous low-frequency hum, maintaining an adaptive baseline that tracks
 * room acoustics without locking out during loud passages.
 */
final class AudioEnvelope {
    private float peak = 0.5f;
    private float floor = 0.1f;
    private float previous = 0f;
    private float strength = 0f;
    private long last = -1;
    private long hit = -10000;
    private int beatCount = 0;

    void feed(float rawBass, long now) {
        float bass = Float.isFinite(rawBass) ? Math.max(0f, rawBass) : 0f;
        // Non-linear power expansion to separate drum kicks from baseline hum
        float linBass = (float) Math.pow(Math.min(1.8f, bass), 2.2f);

        if (last < 0 || now - last > 500) {
            peak = Math.max(0.3f, linBass);
            floor = Math.max(0.02f, linBass * 0.3f);
            previous = linBass;
            strength = 0;
            last = now;
            return;
        }

        float dt = Math.max(0.001f, Math.min(0.25f, (now - last) / 1000f));

        // Peak tracker: instant attack, slow decay (~700ms)
        if (linBass > peak) {
            peak = linBass;
        } else {
            peak -= (peak - linBass) * (1f - (float) Math.exp(-dt / 0.70f));
        }
        peak = Math.max(0.20f, peak);

        // Floor tracker: instant drop to inter-beat troughs, slow rise (~900ms)
        if (linBass < floor) {
            floor = linBass;
        } else {
            floor += (linBass - floor) * (1f - (float) Math.exp(-dt / 0.90f));
        }
        // Ensure dynamic headroom: floor can never exceed 55% of peak (eliminates lockout)
        floor = Math.min(floor, peak * 0.55f);

        float dynamicRange = Math.max(0.08f, peak - floor);
        float threshold = floor + dynamicRange * 0.42f;
        float diff = linBass - threshold;
        float onset = linBass - previous;
        // Require a sharp transient attack flux (prevents false triggering on gentle bassline swells)
        float minAttackFlux = Math.max(0.035f, dynamicRange * 0.12f);

        // Beat hit:
        // 1. Minimum 230ms between beats (~260 BPM max) - strictly prevents double-triggering on single kick tail!
        // 2. Above adaptive threshold (upper 58% of dynamic range)
        // 3. Positive onset attack flux (rising faster than continuous bass swell)
        if (now - hit >= 230 && diff > 0 && onset >= minAttackFlux) {
            strength = Math.min(1.6f, Math.max(0.50f, 0.50f + (diff / dynamicRange) * 1.1f));
            hit = now;
            beatCount++;
        }

        previous = linBass;
        last = now;
    }

    float pulse(long now) {
        if (last < 0 || now - last > 600) return 0f;
        long elapsed = Math.max(0, now - hit);
        if (elapsed > 450) return 0f;
        // Smooth 210ms half-life: bridges naturally between beats without jarring stops
        return strength * (float) Math.exp(-elapsed / 210f);
    }

    long getHitTime() {
        return hit;
    }

    float getStrength() {
        return strength;
    }

    float getBaseline() {
        return floor;
    }

    int getBeatCount() {
        return beatCount;
    }
}
