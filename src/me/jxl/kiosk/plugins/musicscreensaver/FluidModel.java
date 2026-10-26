// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import java.util.Random;

/**
 * Critically damped, organic sound-reactive magnetic ferrofluid model.
 *
 * Implements an anchored central nucleus with 8 magnetic perimeter lobes.
 * Radial expansion is critically damped (first-order exponential approach)
 * so that each drum transient punches outward and snaps smoothly back
 * without mechanical oscillation, spring overshoot, or double-pulsing.
 */
final class FluidModel {
    static final int COUNT = 9;
    final float[] x = new float[COUNT], y = new float[COUNT];
    final float[] vx = new float[COUNT], vy = new float[COUNT], radius = new float[COUNT];
    private final float[] currentDist = new float[COUNT];
    private final float[] baseAngle = new float[COUNT];
    private final Random random;
    private float pulse;
    private float currentEnergy = 0f;
    private float fieldAngle = 0f;

    void setPulse(float value) {
        pulse = Math.max(0f, Math.min(1.6f, value));
    }

    FluidModel(long seed) {
        random = new Random(seed);
        // Mass 0: Central nucleus
        radius[0] = 0.160f;
        currentDist[0] = 0f;
        baseAngle[0] = 0f;
        x[0] = 0f;
        y[0] = 0f;

        // Masses 1..8: 8 cohesive magnetic perimeter lobes (Rosensweig spikes)
        for (int i = 1; i < COUNT; i++) {
            radius[i] = 0.072f + 0.008f * (i % 3);
            currentDist[i] = 0.075f; // Seamlessly merged with nucleus at rest
            baseAngle[i] = (float) (i * (2.0 * Math.PI / 8.0));
            x[i] = (float) Math.cos(baseAngle[i]) * currentDist[i];
            y[i] = (float) Math.sin(baseAngle[i]) * currentDist[i];
        }
    }

    void step(float dt, float bass, float mid, float treble) {
        dt = Math.max(0.001f, Math.min(0.05f, dt));
        float b = finite(bass);
        float m = finite(mid);
        float t = finite(treble);

        currentEnergy = Math.min(1.2f, b * 0.7f + m * 0.3f);

        // Magnetic vortex rotation speeds smoothly with music rhythm (~6-12 RPM)
        fieldAngle += dt * (0.28f + currentEnergy * 0.45f + pulse * 0.20f);

        // Core nucleus breathing: stays grounded at center (0, 0)
        radius[0] = 0.160f * (1.0f + pulse * 0.18f + currentEnergy * 0.08f);

        for (int i = 1; i < COUNT; i++) {
            // Lobe angle with subtle asymmetric organic variation
            float lobeAngle = baseAngle[i] + fieldAngle;
            float asym = 1.0f + 0.16f * (float) Math.sin(lobeAngle * 2.0 + fieldAngle * 0.7);

            // Target radial extension:
            // Rest: ~0.075 (seamlessly merged into smooth nucleus 0.160)
            // Energy: ~0.09 - 0.11 (fluid organically morphs and breathes)
            // Beat transient: ~0.15 - 0.19 (magnetic spikes punch outward)
            float targetR = 0.075f + currentEnergy * 0.038f + pulse * 0.095f * asym;

            // Acoustic treble micro-jitter
            if (t > 0.05f) {
                targetR += (random.nextFloat() - 0.5f) * t * 0.02f;
            }

            // Critically damped first-order tracking:
            // Fast attack on beats (~35ms), smooth viscous contraction (~90ms).
            // Zero inertia, zero spring overshoot, zero double-bounce!
            float speed = (targetR > currentDist[i]) ? 28.0f : 11.0f;
            currentDist[i] += (targetR - currentDist[i]) * (1.0f - (float) Math.exp(-dt * speed));

            x[i] = (float) Math.cos(lobeAngle) * currentDist[i];
            y[i] = (float) Math.sin(lobeAngle) * currentDist[i];
        }
    }

    private float finite(float v) {
        return Float.isNaN(v) || Float.isInfinite(v) ? 0f : Math.max(0f, Math.min(2.0f, v));
    }

    float field(float px, float py) {
        float f = 0;
        for (int i = 0; i < COUNT; i++) {
            float dx = px - x[i], dy = py - y[i];
            float r = radius[i] * (1f + (i == 0 ? 0.22f : 0.18f) * pulse);
            f += (r * r) / (dx * dx + dy * dy + 0.0007f);
        }
        return f;
    }
}
