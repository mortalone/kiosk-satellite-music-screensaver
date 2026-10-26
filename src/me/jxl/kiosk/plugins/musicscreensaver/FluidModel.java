// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import java.util.Random;

/**
 * Dynamic, living sound-reactive ferrofluid simulation.
 *
 * Simulates a morphing multi-nucleus fluid body with dynamic Rosensweig spikes
 * and pinch-off droplets that dance, stretch into asymmetric tendrils, and
 * coalesce back into the mother pool.
 *
 * Uses overdamped magnetic retraction physics (zeta >= 1.05) to ensure that
 * beat transients produce immediate, visceral outward spikes that snap back
 * smoothly without mechanical spring rebound or double-pulsing.
 */
final class FluidModel {
    static final int COUNT = 9;
    final float[] x = new float[COUNT], y = new float[COUNT];
    final float[] vx = new float[COUNT], vy = new float[COUNT], radius = new float[COUNT];
    private final float[] baseSector = new float[COUNT];
    private final float[] lobeSpeed = new float[COUNT];
    private final Random random;
    private float pulse, lastPulse;
    private float currentEnergy = 0f;
    private float magnetX = 0f, magnetY = 0f;
    private float groovePhase = 0f;
    private float vortexAngle = 0f;
    private int beatCounter = 0;

    void setPulse(float value) {
        pulse = Math.max(0f, Math.min(1.6f, value));
    }

    FluidModel(long seed) {
        random = new Random(seed);

        // Masses 0..3: Core fluid body (overlapping amoebic nuclei)
        radius[0] = 0.125f;
        radius[1] = 0.105f;
        radius[2] = 0.100f;
        radius[3] = 0.095f;

        for (int i = 0; i < 4; i++) {
            baseSector[i] = (float) (i * (2.0 * Math.PI / 4.0));
            lobeSpeed[i] = (i % 2 == 0 ? 0.7f : -0.6f) + (i * 0.15f);
            x[i] = (float) Math.cos(baseSector[i]) * 0.035f;
            y[i] = (float) Math.sin(baseSector[i]) * 0.035f;
        }

        // Masses 4..8: Dynamic tendrils & reactive pinch-off droplets
        for (int i = 4; i < COUNT; i++) {
            radius[i] = 0.052f + 0.006f * (i % 3);
            baseSector[i] = (float) ((i - 4) * (2.0 * Math.PI / 5.0) + 0.35);
            lobeSpeed[i] = ((i % 2 == 1) ? 1.4f : -1.2f) + (i - 4) * 0.2f;
            // Rest distance nestled smoothly inside core radius at rest (~0.08)
            float initR = 0.075f + 0.012f * (i % 3);
            x[i] = (float) Math.cos(baseSector[i]) * initR;
            y[i] = (float) Math.sin(baseSector[i]) * initR;
        }
    }

    void step(float dt, float bass, float mid, float treble) {
        dt = Math.max(0.001f, Math.min(0.05f, dt));
        float b = finite(bass);
        float m = finite(mid);
        float t = finite(treble);

        currentEnergy = Math.min(1.4f, b * 0.75f + m * 0.25f);

        // Music rhythm advances the magnetic groove and vortex rotation
        groovePhase += dt * (0.45f + currentEnergy * 1.2f);
        vortexAngle += dt * (0.35f + currentEnergy * 0.85f + pulse * 0.40f);

        // Magnetic center of attraction sways smoothly with musical groove
        float targetMagX = (float) Math.sin(groovePhase * 0.72) * (0.03f + b * 0.07f);
        float targetMagY = (float) Math.cos(groovePhase * 0.58) * (0.025f + b * 0.055f);
        magnetX += (targetMagX - magnetX) * Math.min(1.0f, dt * 3.2f);
        magnetY += (targetMagY - magnetY) * Math.min(1.0f, dt * 3.2f);

        // Beat kick transient detection
        float kick = Math.max(0f, pulse - lastPulse);
        lastPulse = pulse;

        if (kick > 0.025f) {
            beatCounter++;
            // Asymmetric burst vector: each beat emphasizes a dynamic directional spike
            float burstAngle = groovePhase * 1.5f + (float) (beatCounter * 1.2566); // ~72 deg rotation per beat
            float burstDirX = (float) Math.cos(burstAngle);
            float burstDirY = (float) Math.sin(burstAngle);

            for (int i = 0; i < COUNT; i++) {
                float d = (float) Math.sqrt(x[i] * x[i] + y[i] * y[i] + 0.0001f);
                float radX = d > 0.01f ? x[i] / d : (float) Math.cos(baseSector[i]);
                float radY = d > 0.01f ? y[i] / d : (float) Math.sin(baseSector[i]);

                // Alignment with the asymmetric burst vector
                float align = Math.max(0.2f, radX * burstDirX + radY * burstDirY + 0.6f);

                if (i < 4) {
                    // Core masses deform along burst axis
                    float coreImpulse = kick * (0.8f + align * 0.6f);
                    vx[i] += radX * coreImpulse;
                    vy[i] += radY * coreImpulse;
                } else {
                    // Spikes punch outward explosively with swirling tangential whip
                    float spikeImpulse = kick * (1.8f + align * 1.4f + (i % 3) * 0.3f);
                    float whip = ((i % 2 == 0) ? 0.35f : -0.35f) * kick;
                    vx[i] += radX * spikeImpulse - radY * whip;
                    vy[i] += radY * spikeImpulse + radX * whip;
                }
            }
        }

        // Physics integration for all fluid masses
        for (int i = 0; i < COUNT; i++) {
            float angle = baseSector[i] + vortexAngle * lobeSpeed[i] * 0.5f;

            // Target resting and breathing distance from magnet center
            float targetR;
            if (i < 4) {
                // Core amoebic nuclei swirl close to center, breathing with energy
                targetR = (0.035f + 0.012f * (i % 2)) * (1.0f + currentEnergy * 0.35f + pulse * 0.20f);
            } else {
                // Dynamic tendrils stretch with energy, creating organic undulating contour
                float organicWobble = 1.0f + 0.22f * (float) Math.sin(angle * 2.0 + groovePhase);
                targetR = (0.075f + 0.015f * (i % 3) + currentEnergy * 0.065f) * organicWobble;
            }

            float targetX = magnetX + (float) Math.cos(angle) * targetR;
            float targetY = magnetY + (float) Math.sin(angle) * targetR;

            // Overdamped magnetic spring restoring force
            float springK = i < 4 ? 26.0f : 18.0f;
            float fx = (targetX - x[i]) * springK;
            float fy = (targetY - y[i]) * springK;

            // Inter-mass surface tension & volume preservation
            for (int j = 0; j < COUNT; j++) {
                if (i != j) {
                    float dx = x[j] - x[i];
                    float dy = y[j] - y[i];
                    float dSq = dx * dx + dy * dy + 0.0001f;
                    float minSpacing = (radius[i] + radius[j]) * 0.72f;
                    if (dSq < minSpacing * minSpacing) {
                        float d = (float) Math.sqrt(dSq);
                        float push = (minSpacing - d) * 14.0f;
                        fx -= (dx / d) * push;
                        fy -= (dy / d) * push;
                    }
                }
            }

            // Acoustic treble micro-jitter (ripples across ferrofluid surface)
            if (t > 0.04f) {
                float jitter = t * (i < 4 ? 0.6f : 1.8f);
                fx += (random.nextFloat() - 0.5f) * jitter;
                fy += (random.nextFloat() - 0.5f) * jitter;
            }

            // Critically/overdamped viscous damping factor (zeta >= 1.05)
            // Critical damping c_crit = 2 * sqrt(springK).
            // For k = 18: c_crit = 8.48. Using c = 9.2 gives zeta = 1.08 -> zero rebound!
            // For k = 26: c_crit = 10.20. Using c = 10.8 gives zeta = 1.06 -> zero rebound!
            float dampingC = i < 4 ? 10.8f : 9.2f;
            float damp = (float) Math.exp(-dt * dampingC);
            vx[i] = (vx[i] + fx * dt) * damp;
            vy[i] = (vy[i] + fy * dt) * damp;

            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;

            // Boundary containment
            float d = (float) Math.sqrt(x[i] * x[i] + y[i] * y[i]);
            float bound = 0.76f - radius[i];
            if (d > bound) {
                x[i] = (x[i] / d) * bound;
                y[i] = (y[i] / d) * bound;
                float dot = (vx[i] * x[i] + vy[i] * y[i]) / (bound * bound);
                if (dot > 0) {
                    vx[i] -= 1.4f * dot * x[i];
                    vy[i] -= 1.4f * dot * y[i];
                }
            }
        }
    }

    private float finite(float v) {
        return Float.isNaN(v) || Float.isInfinite(v) ? 0f : Math.max(0f, Math.min(2.0f, v));
    }

    float field(float px, float py) {
        float f = 0;
        for (int i = 0; i < COUNT; i++) {
            float dx = px - x[i], dy = py - y[i];
            float r = radius[i] * (1f + (i < 4 ? 0.22f : 0.16f) * pulse);
            f += (r * r) / (dx * dx + dy * dy + 0.0007f);
        }
        return f;
    }
}

