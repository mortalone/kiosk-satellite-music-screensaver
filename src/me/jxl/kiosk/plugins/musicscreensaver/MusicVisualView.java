// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class MusicVisualView extends View {
    interface Listener {
        void onPlayPause();
        void onNext();
        void onPrevious();
        void onVolumeAdjust(int delta);
        void onDismiss();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private Listener listener;
    private String effect = "hybrid_vinyl";
    private String vinylStyle = "With Ambient Glow";
    private String ferrofluidColor = "Obsidian Black";
    private String backgroundTheme = "Album Tinted";
    private boolean showControls = true;
    private String volumeControls = "Buttons";
    private boolean tapDismiss = true;
    private boolean economy = false;
    private int gain = 3;

    private boolean playing = false;
    private String trackTitle = "";
    private String trackArtist = "";
    private Bitmap coverArt;
    private Bitmap blurredBg;
    private double positionSeconds = 0;
    private long anchorTime = 0;

    private final float[] levels = new float[32];
    private final float[] smoothedLevels = new float[32];
    private long lastAudioFrameAt = 0;
    private float bassEnergy = 0;
    private float smoothedBass = 0;
    private float smoothedMid = 0;
    private float smoothedTreble = 0;
    private long lastPhysicsTime = 0;

    // Hit rects for touch
    private final RectF prevRect = new RectF();
    private final RectF playRect = new RectF();
    private final RectF nextRect = new RectF();
    private final RectF volMinusRect = new RectF();
    private final RectF volPlusRect = new RectF();

    MusicVisualView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void configure(String effect, String vinylStyle, String ferrofluidColor, String backgroundTheme,
                   boolean showControls, String volumeControls, boolean tapDismiss, boolean economy, int gain) {
        this.effect = effect;
        this.vinylStyle = vinylStyle;
        this.ferrofluidColor = ferrofluidColor;
        this.backgroundTheme = backgroundTheme;
        this.showControls = showControls;
        this.volumeControls = volumeControls;
        this.tapDismiss = tapDismiss;
        this.economy = economy;
        this.gain = gain;
        invalidate();
    }

    void updateTrack(String title, String artist, Bitmap cover, boolean isPlaying, double position) {
        this.trackTitle = title == null ? "" : title;
        this.trackArtist = artist == null ? "" : artist;
        this.playing = isPlaying;
        this.positionSeconds = position;
        this.anchorTime = SystemClock.elapsedRealtime();

        if (cover != this.coverArt) {
            this.coverArt = cover;
            generateBlurredBackground(cover);
        }
        invalidate();
    }

    private void generateBlurredBackground(Bitmap src) {
        if (src == null || src.isRecycled()) {
            blurredBg = null;
            return;
        }
        try {
            // Low-res sampled bitmap for ultra-fast GPU ambient blur
            blurredBg = Bitmap.createScaledBitmap(src, 24, 24, true);
        } catch (Throwable ignored) {
            blurredBg = null;
        }
    }

    private int extractDominantColor() {
        if (blurredBg != null && !blurredBg.isRecycled()) {
            try {
                int pixel = blurredBg.getPixel(blurredBg.getWidth() / 2, blurredBg.getHeight() / 2);
                if (pixel != 0) return pixel;
            } catch (Throwable ignored) {}
        }
        return 0xFF00E5FF;
    }

    void acceptAudio(float[] bands) {
        if (bands == null || bands.length == 0) return;
        int count = Math.min(levels.length, bands.length);
        float gainMult = 0.7f + (gain * 0.28f);
        boolean hasSignal = false;
        for (int i = 0; i < count; i++) {
            float v = bands[i];
            float clamped = Float.isNaN(v) || Float.isInfinite(v) ? 0 : Math.max(0, Math.min(1.8f, v));
            if (clamped > 0.015f) hasSignal = true;
            levels[i] = clamped * gainMult;
        }
        if (hasSignal) {
            lastAudioFrameAt = SystemClock.elapsedRealtime();
        }
        postInvalidateOnAnimation();
    }

    private void updateAudioDynamics(float dt, double elapsed) {
        boolean hasLiveAudio = (SystemClock.elapsedRealtime() - lastAudioFrameAt) < 1200;

        if (!hasLiveAudio && playing) {
            // Autonomous beat & spectrum synthesizer synchronized to song playback time
            // Standard musical pulse at ~124 BPM (0.4838s per beat, 1.935s per 4-beat bar)
            double beatPhase = (elapsed % 0.4838) / 0.4838;
            double barPhase = (elapsed % 1.935) / 1.935;

            // Kick drum: sharp attack on beat 1 & 3, exponential decay
            float kick = (float) Math.max(0.0, Math.exp(-beatPhase * 7.5) * 1.1);

            // Snare / clap on beat 2 & 4 (at 0.25 and 0.75 of bar)
            double snareDist1 = Math.abs(barPhase - 0.25);
            double snareDist2 = Math.abs(barPhase - 0.75);
            float snare = (float) (Math.max(0.0, Math.exp(-snareDist1 * 22.0)) + Math.max(0.0, Math.exp(-snareDist2 * 22.0)));

            // Bassline groove modulation
            float groove = (float) (0.35 + 0.45 * Math.sin(elapsed * 6.28 * 2.0));

            // Hi-hats ticking on 16th notes
            double sixteenth = (elapsed % 0.121) / 0.121;
            float hihat = (float) Math.max(0.0, Math.exp(-sixteenth * 10.0) * 0.75);

            float gainMult = 0.7f + (gain * 0.22f);

            for (int i = 0; i < 32; i++) {
                float target;
                if (i < 5) {
                    target = (kick * 0.95f + groove * 0.45f) * gainMult;
                } else if (i < 14) {
                    target = (snare * 0.85f + 0.3f * (float) Math.sin(elapsed * 7.0 + i)) * gainMult;
                } else {
                    target = (hihat * 0.75f + 0.2f * (float) Math.cos(elapsed * 11.0 + i * 2)) * gainMult;
                }
                levels[i] = Math.max(0f, Math.min(1.8f, target));
            }
        } else if (!hasLiveAudio && !playing) {
            for (int i = 0; i < 32; i++) {
                levels[i] = 0f;
            }
        }

        // Viscous ballistic filtering: fast magnetic spike attack, organic surface tension decay
        float bassSum = 0;
        float midSum = 0;
        float trebleSum = 0;
        for (int i = 0; i < 32; i++) {
            float target = levels[i];
            float current = smoothedLevels[i];
            float speed = (target > current) ? Math.min(1.0f, dt * 26.0f) : Math.min(1.0f, dt * 8.0f);
            smoothedLevels[i] = current + (target - current) * speed;

            if (i < 6) bassSum += smoothedLevels[i];
            else if (i < 18) midSum += smoothedLevels[i];
            else trebleSum += smoothedLevels[i];
        }

        smoothedBass = bassSum / 6.0f;
        smoothedMid = midSum / 12.0f;
        smoothedTreble = trebleSum / 14.0f;
        bassEnergy = smoothedBass;
    }

    double elapsedSeconds() {
        if (!playing) return positionSeconds;
        return positionSeconds + Math.max(0, SystemClock.elapsedRealtime() - anchorTime) / 1000.0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX(), y = event.getY();
            if (showControls) {
                if (playRect.contains(x, y)) {
                    if (listener != null) listener.onPlayPause();
                    return true;
                } else if (prevRect.contains(x, y)) {
                    if (listener != null) listener.onPrevious();
                    return true;
                } else if (nextRect.contains(x, y)) {
                    if (listener != null) listener.onNext();
                    return true;
                }
            }
            if ("Buttons".equals(volumeControls)) {
                if (volMinusRect.contains(x, y)) {
                    if (listener != null) listener.onVolumeAdjust(-5);
                    return true;
                } else if (volPlusRect.contains(x, y)) {
                    if (listener != null) listener.onVolumeAdjust(5);
                    return true;
                }
            }
            // Canvas/background tap dismiss
            if (tapDismiss && listener != null) {
                listener.onDismiss();
                return true;
            }
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        double elapsed = elapsedSeconds();
        double time = SystemClock.elapsedRealtime() / 1000.0;

        long now = SystemClock.elapsedRealtime();
        float dt = lastPhysicsTime > 0 ? Math.min(0.08f, (now - lastPhysicsTime) / 1000.0f) : 0.033f;
        lastPhysicsTime = now;

        updateAudioDynamics(dt, elapsed);

        // 1. Draw Background
        drawBackground(canvas, w, h);

        // 2. Determine Layout (Split-stage vs Centered)
        boolean isSplit = "hybrid_vinyl".equals(effect) || "hybrid_ferrofluid".equals(effect);

        if ("vinyl".equals(effect) || "hybrid_vinyl".equals(effect)) {
            drawVinyl(canvas, w, h, isSplit, playing, coverArt, elapsed, smoothedBass);
        } else if ("ferrofluid".equals(effect) || "hybrid_ferrofluid".equals(effect)) {
            drawFerrofluid(canvas, w, h, isSplit, smoothedBass, time);
        } else if ("cotodama".equals(effect)) {
            drawCotodamaAmbient(canvas, w, h);
        }

        // 3. Draw Track Metadata (Title, Artist)
        drawTrackInfo(canvas, w, h, isSplit);

        // 4. Draw Controls
        drawControls(canvas, w, h);

        if (playing || "ferrofluid".equals(effect) || "hybrid_ferrofluid".equals(effect) || "cotodama".equals(effect)) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBackground(Canvas c, float w, float h) {
        if ("Pure Black".equals(backgroundTheme)) {
            c.drawColor(0xFF000000);
        } else if ("Deep Charcoal".equals(backgroundTheme)) {
            c.drawColor(0xFF101317);
        } else {
            // Album Tinted with subtle dark overlay
            if (blurredBg != null && !blurredBg.isRecycled()) {
                paint.setAlpha(180);
                rect.set(0, 0, w, h);
                c.drawBitmap(blurredBg, null, rect, paint);
                paint.setAlpha(255);
                c.drawColor(0xCC0D1014);
            } else {
                c.drawColor(0xFF13171C);
            }
        }
    }

    private void drawVinyl(Canvas c, float w, float h, boolean split, boolean isPlaying,
                           Bitmap cover, double elapsed, float bass) {
        float cx = split ? w * 0.23f : w * 0.50f;
        float cy = h * 0.48f;
        float discR = Math.min(w, h) * (split ? 0.35f : 0.40f);
        if (discR <= 10) return;

        boolean withGlow = !"Disc Only (Minimal)".equals(vinylStyle);

        // Ambient Backlight Glow
        if (withGlow) {
            paint.setStyle(Paint.Style.FILL);
            float glowR = discR * (1.20f + 0.15f * bass);
            paint.setShader(new RadialGradient(cx, cy, glowR,
                    new int[]{0x4065E5CF, 0x18182230, 0x00000000},
                    new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, glowR, paint);
            paint.setShader(null);
        }

        // Disc Body
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0D0E11);
        c.drawCircle(cx, cy, discR, paint);

        // Concentric Micro-Grooves (Rilles)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        float labelR = discR * 0.38f;
        int rings = economy ? 6 : 10;
        for (int i = 0; i < rings; i++) {
            float r = labelR + (discR - labelR) * (i + 1) / (rings + 1f);
            paint.setColor((i % 2 == 0) ? 0x1CFFFFFF : 0x0EFFFFFF);
            paint.setStrokeWidth(1.2f);
            c.drawCircle(cx, cy, r, paint);
        }

        // Anisotropic Sheen Reflection (Bow-tie reflection rotating at 33⅓ RPM)
        float angle = isPlaying ? (float) ((elapsed * 200.0) % 360.0) : 0f;
        rect.set(cx - discR, cy - discR, cx + discR, cy + discR);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x14FFFFFF);
        c.drawArc(rect, angle - 24, 48, true, paint);
        c.drawArc(rect, angle + 156, 48, true, paint);

        // Center Label (Circular Cropped Album Art)
        c.save();
        path.reset();
        path.addCircle(cx, cy, labelR, Path.Direction.CW);
        c.clipPath(path);
        if (cover != null && !cover.isRecycled()) {
            c.save();
            c.rotate(angle, cx, cy);
            rect.set(cx - labelR, cy - labelR, cx + labelR, cy + labelR);
            int minSide = Math.min(cover.getWidth(), cover.getHeight());
            Rect src = new Rect((cover.getWidth() - minSide) / 2, (cover.getHeight() - minSide) / 2,
                    (cover.getWidth() + minSide) / 2, (cover.getHeight() + minSide) / 2);
            c.drawBitmap(cover, src, rect, paint);
            c.restore();
        } else {
            paint.setColor(0xFF232D38);
            c.drawCircle(cx, cy, labelR, paint);
        }
        c.restore();

        // Label Outer Rim
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x44000000);
        paint.setStrokeWidth(2.5f);
        c.drawCircle(cx, cy, labelR, paint);

        // Spindle Hole with Brass Rim
        float holeR = discR * 0.045f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF040507);
        c.drawCircle(cx, cy, holeR, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFB0BAC6);
        paint.setStrokeWidth(1.6f);
        c.drawCircle(cx, cy, holeR, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFerrofluid(Canvas c, float w, float h, boolean split, float bass, double time) {
        float cx = split ? w * 0.23f : w * 0.50f;
        float cy = h * 0.48f;
        // Bob center of mass with bass transients
        cy -= smoothedBass * 16.0f;

        float baseR = Math.min(w, h) * (split ? 0.15f : 0.20f) * (1.0f + 0.10f * smoothedBass);
        if (baseR <= 10) return;

        // Resolve colors
        int auraColor = 0x351E293B; // Deep obsidian aura
        int fluidColor = 0xFF07080B; // Deep glossy black
        int highlightColor = 0xAAFFFFFF;

        if ("Cyber Cyan".equals(ferrofluidColor)) {
            auraColor = 0x6000E5FF;
            fluidColor = 0xFF05151D;
            highlightColor = 0xEE70FFFF;
        } else if ("Neon Magenta".equals(ferrofluidColor)) {
            auraColor = 0x60FF0077;
            fluidColor = 0xFF190513;
            highlightColor = 0xEEFF70BA;
        } else if ("Album Matched".equals(ferrofluidColor)) {
            int dominant = extractDominantColor();
            auraColor = (dominant & 0x00FFFFFF) | 0x55000000;
            fluidColor = 0xFF07080B;
            highlightColor = 0xEEFFFFFF;
        }

        // 1. Magnetic Field Aura (Pulsing with overall energy)
        float auraR = baseR * (1.75f + 0.45f * smoothedBass);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, auraR,
                new int[]{auraColor, 0x12102030, 0x00000000},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, auraR, paint);
        paint.setShader(null);

        // 2. Rosensweig Spikes (Needle-sharp conical peaks)
        int spikeCount = economy ? 20 : 28;
        float[] apexX = new float[spikeCount];
        float[] apexY = new float[spikeCount];
        float[] apexR = new float[spikeCount];
        float[] valleyX = new float[spikeCount];
        float[] valleyY = new float[spikeCount];

        float angleStep = (float) (Math.PI * 2.0 / spikeCount);
        float gainScale = 0.75f + (gain * 0.20f);

        for (int i = 0; i < spikeCount; i++) {
            float theta = (float) (i * angleStep - Math.PI / 2.0);

            // Polar magnetic alignment (top & bottom poles have strongest spikes)
            float poleFactor = (float) Math.abs(Math.sin(theta)); // 1.0 at top/bottom poles
            int band = Math.min(31, Math.abs((i * 32) / spikeCount));
            float bandVal = smoothedLevels[band];

            // Nonlinear spike excitation
            float excitation = (float) Math.pow(Math.max(0f, bandVal), 1.30) * gainScale;
            float poleBoost = 1.0f + 0.70f * poleFactor * smoothedBass;

            // Micro capillary ripple for liquid trembling
            float ripple = (float) Math.sin(time * 6.0 + i * 1.3) * (baseR * (0.02f + 0.04f * smoothedTreble));

            float spikeHeight = excitation * baseR * 1.25f * poleBoost;
            float rPeak = baseR + spikeHeight + ripple;
            apexR[i] = rPeak;
            apexX[i] = cx + (float) Math.cos(theta) * rPeak;
            apexY[i] = cy + (float) Math.sin(theta) * rPeak;

            // Valley between spike i and spike i+1
            float thetaMid = theta + angleStep * 0.5f;
            float valleyR = baseR * (0.94f - 0.05f * smoothedBass) + ripple * 0.4f;
            valleyX[i] = cx + (float) Math.cos(thetaMid) * valleyR;
            valleyY[i] = cy + (float) Math.sin(thetaMid) * valleyR;
        }

        // 3. Draw Body with Sharp Cusps (Concave flank beziers to needle tips)
        path.reset();
        int last = spikeCount - 1;
        path.moveTo(valleyX[last], valleyY[last]);

        for (int i = 0; i < spikeCount; i++) {
            // Flank from previous valley to apex i:
            // Control point pulled inward toward core makes the flank concave and the tip razor sharp!
            float flankMidAngle1 = (float) (i * angleStep - Math.PI / 2.0 - angleStep * 0.22f);
            float ctrlR1 = baseR * 1.02f + (apexR[i] - baseR) * 0.28f;
            float c1x = cx + (float) Math.cos(flankMidAngle1) * ctrlR1;
            float c1y = cy + (float) Math.sin(flankMidAngle1) * ctrlR1;
            path.quadTo(c1x, c1y, apexX[i], apexY[i]);

            // Flank from apex i down to valley i:
            float flankMidAngle2 = (float) (i * angleStep - Math.PI / 2.0 + angleStep * 0.22f);
            float ctrlR2 = baseR * 1.02f + (apexR[i] - baseR) * 0.28f;
            float c2x = cx + (float) Math.cos(flankMidAngle2) * ctrlR2;
            float c2y = cy + (float) Math.sin(flankMidAngle2) * ctrlR2;
            path.quadTo(c2x, c2y, valleyX[i], valleyY[i]);
        }
        path.close();

        // Fill fluid body
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fluidColor);
        c.drawPath(path, paint);

        // 4. Specular Spine Ridge Highlights (Wet liquid 3D chrome look)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < spikeCount; i++) {
            float spikeLen = apexR[i] - baseR;
            if (spikeLen > baseR * 0.08f) {
                float theta = (float) (i * angleStep - Math.PI / 2.0);
                // Angle relative to light source (-3pi/4 = top-left)
                float lightDot = (float) Math.max(0.15, Math.cos(theta - (-2.356)));
                int alpha = Math.min(230, Math.round(lightDot * Math.min(1.0f, spikeLen / (baseR * 0.45f)) * 255f));
                if (alpha > 20) {
                    paint.setColor((highlightColor & 0x00FFFFFF) | (alpha << 24));
                    paint.setStrokeWidth(Math.max(1.2f, Math.min(3.5f, spikeLen * 0.08f)));
                    float startR = baseR + spikeLen * 0.22f;
                    float endR = baseR + spikeLen * 0.94f;
                    c.drawLine(
                            cx + (float) Math.cos(theta) * startR,
                            cy + (float) Math.sin(theta) * startR,
                            cx + (float) Math.cos(theta) * endR,
                            cy + (float) Math.sin(theta) * endR,
                            paint
                    );
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);

        // 5. Specular Wet Dome Highlight (Overhead light glint)
        float specX = cx - baseR * 0.30f;
        float specY = cy - baseR * 0.30f;
        float specR = baseR * 0.48f;
        paint.setShader(new RadialGradient(specX, specY, specR,
                new int[]{0x88FFFFFF, 0x28FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(specX, specY, specR, paint);
        paint.setShader(null);

        // Sharp pin-point glint on the wet liquid dome
        paint.setColor(0xDDFFFFFF);
        c.drawCircle(specX - specR * 0.22f, specY - specR * 0.22f, baseR * 0.065f, paint);

        // 6. Rayleigh-Plateau Satellite Droplets (Ejected beads on heavy kicks)
        if (smoothedBass > 0.30f) {
            paint.setColor(fluidColor);
            int satellites = economy ? 4 : 6;
            for (int s = 0; s < satellites; s++) {
                float satAngle = (float) ((s % 2 == 0 ? -Math.PI / 2 : Math.PI / 2) + ((s / 2) - 0.5f) * 0.48f);
                float surge = (smoothedBass - 0.30f) / 0.70f;
                float dist = baseR * (1.28f + surge * 0.60f * gainScale);
                float dropR = 3.5f + surge * 7.0f;

                float dx = cx + (float) Math.cos(satAngle) * dist;
                float dy = cy + (float) Math.sin(satAngle) * dist;

                // Satellite body
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fluidColor);
                c.drawCircle(dx, dy, dropR, paint);

                // Satellite glint
                paint.setColor(0xCCFFFFFF);
                c.drawCircle(dx - dropR * 0.32f, dy - dropR * 0.32f, dropR * 0.38f, paint);
            }
        }
    }

    private void drawCotodamaAmbient(Canvas c, float w, float h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x4065E5CF);
        paint.setStrokeWidth(2.5f);
        path.reset();
        for (int i = 0; i < smoothedLevels.length; i++) {
            float x = w * i / Math.max(1, smoothedLevels.length - 1);
            float y = h * 0.88f - smoothedLevels[i] * h * 0.08f;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        c.drawPath(path, paint);
    }

    private void drawTrackInfo(Canvas c, float w, float h, boolean split) {
        if (split) {
            // Track title in bottom left beneath vinyl/ferrofluid
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(MusicUi.INK);
            paint.setTextSize(MusicUi.dp(getContext(), 18));
            paint.setTextAlign(Paint.Align.CENTER);
            float textX = w * 0.23f;
            float textY = h * 0.88f;
            if (!trackTitle.isEmpty()) {
                c.drawText(trackTitle, textX, textY, paint);
            }
            if (!trackArtist.isEmpty()) {
                paint.setColor(MusicUi.MUTED);
                paint.setTextSize(MusicUi.dp(getContext(), 14));
                c.drawText(trackArtist, textX, textY + MusicUi.dp(getContext(), 20), paint);
            }
        }
    }

    private void drawControls(Canvas c, float w, float h) {
        float density = getResources().getDisplayMetrics().density;
        float bottomMargin = 28 * density;

        // Play/Pause, Prev, Next Controls in Bottom Center
        if (showControls) {
            float btnR = 24 * density;
            float cy = h - bottomMargin;
            float cx = w * 0.50f;

            playRect.set(cx - btnR, cy - btnR, cx + btnR, cy + btnR);
            prevRect.set(cx - 70 * density - btnR, cy - btnR, cx - 70 * density + btnR, cy + btnR);
            nextRect.set(cx + 70 * density - btnR, cy - btnR, cx + 70 * density + btnR, cy + btnR);

            // Draw circular backgrounds
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1F2833);
            c.drawCircle(cx, cy, btnR, paint);
            c.drawCircle(cx - 70 * density, cy, btnR, paint);
            c.drawCircle(cx + 70 * density, cy, btnR, paint);

            // Draw glyphs
            paint.setColor(MusicUi.INK);
            if (playing) {
                // Pause bars
                c.drawRoundRect(cx - 6 * density, cy - 8 * density, cx - 2 * density, cy + 8 * density, 2, 2, paint);
                c.drawRoundRect(cx + 2 * density, cy - 8 * density, cx + 6 * density, cy + 8 * density, 2, 2, paint);
            } else {
                // Play triangle
                path.reset();
                path.moveTo(cx - 4 * density, cy - 8 * density);
                path.lineTo(cx + 7 * density, cy);
                path.lineTo(cx - 4 * density, cy + 8 * density);
                path.close();
                c.drawPath(path, paint);
            }

            // Prev icon
            path.reset();
            path.moveTo(cx - 65 * density, cy - 6 * density);
            path.lineTo(cx - 75 * density, cy);
            path.lineTo(cx - 65 * density, cy + 6 * density);
            path.close();
            c.drawPath(path, paint);
            c.drawRect(cx - 77 * density, cy - 6 * density, cx - 75 * density, cy + 6 * density, paint);

            // Next icon
            path.reset();
            path.moveTo(cx + 65 * density, cy - 6 * density);
            path.lineTo(cx + 75 * density, cy);
            path.lineTo(cx + 65 * density, cy + 6 * density);
            path.close();
            c.drawPath(path, paint);
            c.drawRect(cx + 75 * density, cy - 6 * density, cx + 77 * density, cy + 6 * density, paint);
        }

        // Volume Buttons in Bottom Right
        if ("Buttons".equals(volumeControls)) {
            float btnR = 20 * density;
            float cy = h - bottomMargin;
            float rx = w - 40 * density;
            volPlusRect.set(rx - btnR, cy - btnR, rx + btnR, cy + btnR);
            volMinusRect.set(rx - 50 * density - btnR, cy - btnR, rx - 50 * density + btnR, cy + btnR);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1F2833);
            c.drawCircle(rx, cy, btnR, paint);
            c.drawCircle(rx - 50 * density, cy, btnR, paint);

            paint.setColor(MusicUi.INK);
            paint.setStrokeWidth(2 * density);
            paint.setStyle(Paint.Style.STROKE);
            // Plus
            c.drawLine(rx - 6 * density, cy, rx + 6 * density, cy, paint);
            c.drawLine(rx, cy - 6 * density, rx, cy + 6 * density, paint);
            // Minus
            c.drawLine(rx - 56 * density, cy, rx - 44 * density, cy, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
