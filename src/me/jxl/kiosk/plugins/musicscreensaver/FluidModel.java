// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import java.util.Random;

/**
 * Organic, sound-reactive magnetic ferrofluid simulation.
 *
 * Simulates a central magnetic nucleus with swirling, pulsating fluid lobes
 * and satellite droplets that dance, stretch into asymmetric tendrils, split,
 * and coalesce in response to audio frequency bands and beat transients.
 */
final class FluidModel {
    static final int COUNT = 9;
    final float[] x = new float[COUNT], y = new float[COUNT];
    final float[] vx = new float[COUNT], vy = new float[COUNT], radius = new float[COUNT];
    private final float[] phase = new float[COUNT];
    private final float[] orbitSpeed = new float[COUNT];
    private final float[] orbitDist = new float[COUNT];
    private final Random random;
    private float pulse, lastPulse;
    private float currentEnergy = 0f;
    private float fieldAngle = 0f;

    void setPulse(float value) {
        pulse = Math.max(0f, Math.min(2.0f, value));
    }

    FluidModel(long seed) {
        random = new Random(seed);
        // Mass 0: Central nucleus
        radius[0] = 0.155f;
        x[0] = 0f;
        y[0] = 0f;
        orbitDist[0] = 0f;
        orbitSpeed[0] = 0f;
        phase[0] = 0f;

        // Masses 1..3: Primary inner fluid body (organic, morphing lobes)
        for (int i = 1; i <= 3; i++) {
            radius[i] = 0.088f + 0.008f * (i % 2);
            orbitDist[i] = 0.080f + 0.022f * i;
            orbitSpeed[i] = (i % 2 == 0 ? 0.95f : -0.80f) + (float) (random.nextGaussian() * 0.15);
            phase[i] = (float) (i * (2.0 * Math.PI / 3.0));
            x[i] = (float) Math.cos(phase[i]) * orbitDist[i];
            y[i] = (float) Math.sin(phase[i]) * orbitDist[i];
        }

        // Masses 4..8: Outer satellite droplets & dynamic tendrils
        for (int i = 4; i < COUNT; i++) {
            radius[i] = 0.052f + 0.008f * (i % 3);
            orbitDist[i] = 0.135f + 0.028f * (i - 4);
            orbitSpeed[i] = ((i % 2 == 1) ? 1.35f : -1.15f) + 0.15f * (i - 4);
            phase[i] = (float) (random.nextFloat() * 2.0 * Math.PI);
            x[i] = (float) Math.cos(phase[i]) * orbitDist[i];
            y[i] = (float) Math.sin(phase[i]) * orbitDist[i];
        }
    }

    void step(float dt, float bass, float mid, float treble) {
        dt = Math.max(0.001f, Math.min(0.05f, dt));
        bass = finite(bass);
        mid = finite(mid);
        treble = finite(treble);

        float kick = Math.max(0f, pulse - lastPulse);
        lastPulse = pulse;

        currentEnergy = Math.min(1.2f, bass * 0.7f + mid * 0.3f);
        float stretchFactor = 1.0f + pulse * 1.05f + currentEnergy * 0.28f;

        // Music rhythm advances the magnetic vortex angle with audio energy
        float motionRate = 0.12f + currentEnergy * 0.88f + pulse * 0.35f;
        fieldAngle += dt * motionRate * 0.75f;

        // Core nucleus dynamics: slight breathing oscillation, anchored at center
        float coreFx = -x[0] * 32.0f;
        float coreFy = -y[0] * 32.0f;
        float coreDamp = (float) Math.exp(-dt * 8.0);
        vx[0] = (vx[0] + coreFx * dt) * coreDamp;
        vy[0] = (vy[0] + coreFy * dt) * coreDamp;
        x[0] += vx[0] * dt;
        y[0] += vy[0] * dt;

        for (int i = 1; i < COUNT; i++) {
            // Update organic orbit angle with musical rhythm
            phase[i] += dt * orbitSpeed[i] * motionRate;
            float a = phase[i] + fieldAngle * (i <= 3 ? 0.35f : 0.70f);

            // Dynamic asymmetric magnetic deformation
            float ellipseDistort = 1.0f + 0.22f * (float) Math.sin(a * 2.0 + fieldAngle * 1.5);
            float currentTargetR = orbitDist[i] * stretchFactor * ellipseDistort;

            float targetX = (float) Math.cos(a) * currentTargetR;
            float targetY = (float) Math.sin(a) * currentTargetR;

            // Explosive pulse kick on drum beats: drives immediate outward burst!
            if (kick > 0.02f) {
                float d = (float) Math.sqrt(x[i] * x[i] + y[i] * y[i] + 0.0001f);
                float dirX = d > 0.01f ? x[i] / d : (float) Math.cos(a);
                float dirY = d > 0.01f ? y[i] / d : (float) Math.sin(a);
                // Outward radial punch + swirling whip
                float kickStr = kick * (2.8f + 0.4f * (i % 3));
                vx[i] += dirX * kickStr - dirY * kickStr * 0.25f;
                vy[i] += dirY * kickStr + dirX * kickStr * 0.25f;
            }

            // Magnetic spring force toward orbiting target
            float springK = i <= 3 ? 18.0f : 12.0f;
            float fx = (targetX - x[i]) * springK;
            float fy = (targetY - y[i]) * springK;

            // Acoustic treble surface jitter (ferrofluid micro-ripples)
            if (treble > 0.04f) {
                fx += (random.nextFloat() - 0.5f) * treble * 1.2f;
                fy += (random.nextFloat() - 0.5f) * treble * 1.2f;
            }

            // Inter-droplet surface tension and repulsion to prevent unnatural stacking
            for (int j = 1; j < COUNT; j++) {
                if (i != j) {
                    float dx = x[i] - x[j];
                    float dy = y[i] - y[j];
                    float dSq = dx * dx + dy * dy + 0.0001f;
                    float minSpacing = (radius[i] + radius[j]) * 0.70f;
                    if (dSq < minSpacing * minSpacing) {
                        float d = (float) Math.sqrt(dSq);
                        float push = (minSpacing - d) * 12.0f;
                        fx += (dx / d) * push;
                        fy += (dy / d) * push;
                    }
                }
            }

            // Viscous fluid damping
            float damp = (float) Math.exp(-dt * (3.8f + (1f - Math.min(1f, currentEnergy)) * 1.8f));
            vx[i] = (vx[i] + fx * dt) * damp;
            vy[i] = (vy[i] + fy * dt) * damp;

            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;

            // Boundary containment
            float d = (float) Math.sqrt(x[i] * x[i] + y[i] * y[i]);
            float bound = 0.74f - radius[i];
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
            float r = radius[i] * (1f + (i == 0 ? 0.38f : 0.28f) * pulse + (i == 0 ? 0.12f : 0.20f) * currentEnergy);
            f += (r * r) / (dx * dx + dy * dy + 0.0007f);
        }
        return f;
    }
}
