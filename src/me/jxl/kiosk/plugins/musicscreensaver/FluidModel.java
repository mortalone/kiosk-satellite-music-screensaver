// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import java.util.Random;

/**
 * Centered magnetic ferrofluid simulation.
 *
 * Models a central magnetic droplet with 8 peripheral magnetic lobes that
 * produce authentic Rosensweig instability spikes radially outward on beat hits
 * and contract into a single cohesive droplet under surface tension during quiet passages.
 */
final class FluidModel {
    static final int COUNT = 9;
    final float[] x = new float[COUNT], y = new float[COUNT];
    final float[] vx = new float[COUNT], vy = new float[COUNT], radius = new float[COUNT];
    private final float[] baseAngle = new float[COUNT];
    private final Random random;
    private float pulse, lastPulse;

    void setPulse(float value) {
        pulse = Math.max(0f, Math.min(1.5f, value));
    }

    FluidModel(long seed) {
        random = new Random(seed);
        // Mass 0: Central anchor core
        radius[0] = 0.155f;
        x[0] = 0f;
        y[0] = 0f;
        baseAngle[0] = 0f;

        // Masses 1..8: Radial magnetic lobes distributed evenly around the core
        for (int i = 1; i < COUNT; i++) {
            float angle = (float) ((i - 1) * (2.0 * Math.PI / 8.0));
            baseAngle[i] = angle;
            radius[i] = 0.070f + 0.006f * (i % 3);
            float r0 = 0.11f;
            x[i] = (float) Math.cos(angle) * r0;
            y[i] = (float) Math.sin(angle) * r0;
        }
    }

    void step(float dt, float bass, float mid, float treble) {
        dt = Math.max(0.001f, Math.min(0.05f, dt));
        bass = finite(bass);
        mid = finite(mid);
        treble = finite(treble);

        float kick = Math.max(0f, pulse - lastPulse);
        lastPulse = pulse;

        // Core mass dynamics: tightly anchored at center (0, 0)
        float coreFx = -x[0] * 35.0f;
        float coreFy = -y[0] * 35.0f;
        float coreDamp = (float) Math.exp(-dt * 6.0);
        vx[0] = (vx[0] + coreFx * dt) * coreDamp;
        vy[0] = (vy[0] + coreFy * dt) * coreDamp;
        x[0] += vx[0] * dt;
        y[0] += vy[0] * dt;

        // Peripheral lobes dynamics
        float baseRestingR = 0.11f;
        float energy = Math.min(1.2f, bass * 0.7f + mid * 0.3f);

        for (int i = 1; i < COUNT; i++) {
            float angle = baseAngle[i];
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            // Explosive radial pulse on beat hit (Rosensweig spike)
            if (kick > 0.02f) {
                float spikeKick = kick * (2.8f + 0.5f * (i % 2));
                vx[i] += cosA * spikeKick;
                vy[i] += sinA * spikeKick;
            }

            // Radial target position driven by pulse and bass energy
            float targetR = baseRestingR + pulse * 0.28f + energy * 0.09f;
            float targetX = cosA * targetR;
            float targetY = sinA * targetR;

            // Restoring magnetic spring force toward radial target
            float fx = (targetX - x[i]) * 24.0f;
            float fy = (targetY - y[i]) * 24.0f;

            // Acoustic treble micro-agitation
            if (treble > 0.05f) {
                fx += (random.nextFloat() - 0.5f) * treble * 1.5f;
                fy += (random.nextFloat() - 0.5f) * treble * 1.5f;
            }

            // Surface tension repulsion from adjacent lobes to avoid collapse
            for (int j = 1; j < COUNT; j++) {
                if (i != j) {
                    float dx = x[i] - x[j];
                    float dy = y[i] - y[j];
                    float dSq = dx * dx + dy * dy + 0.0001f;
                    float minSpacing = (radius[i] + radius[j]) * 0.85f;
                    if (dSq < minSpacing * minSpacing) {
                        float d = (float) Math.sqrt(dSq);
                        float push = (minSpacing - d) * 12.0f;
                        fx += (dx / d) * push;
                        fy += (dy / d) * push;
                    }
                }
            }

            // Viscous fluid damping
            float damp = (float) Math.exp(-dt * 5.2);
            vx[i] = (vx[i] + fx * dt) * damp;
            vy[i] = (vy[i] + fy * dt) * damp;

            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;

            // Container boundary constraint
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
            float r = radius[i] * (1f + 0.35f * pulse);
            f += (r * r) / (dx * dx + dy * dy + 0.0007f);
        }
        return f;
    }
}
