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
    private float bassEnergy = 0;

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

    void acceptAudio(float[] bands) {
        if (bands == null) return;
        int count = Math.min(levels.length, bands.length);
        float bass = 0;
        float gainMult = 0.5f + (gain * 0.25f);
        for (int i = 0; i < count; i++) {
            float v = bands[i];
            float clamped = Float.isNaN(v) || Float.isInfinite(v) ? 0 : Math.max(0, Math.min(1, v));
            levels[i] = clamped * gainMult;
            if (i < 6) bass += levels[i];
        }
        bassEnergy = bass / 6.0f;
        invalidate();
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

        // 1. Draw Background
        drawBackground(canvas, w, h);

        // 2. Determine Layout (Split-stage vs Centered)
        boolean isSplit = "hybrid_vinyl".equals(effect) || "hybrid_ferrofluid".equals(effect);

        if ("vinyl".equals(effect) || "hybrid_vinyl".equals(effect)) {
            drawVinyl(canvas, w, h, isSplit, playing, coverArt, elapsed, bassEnergy);
        } else if ("ferrofluid".equals(effect) || "hybrid_ferrofluid".equals(effect)) {
            drawFerrofluid(canvas, w, h, isSplit, bassEnergy, time);
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
        float baseR = Math.min(w, h) * (split ? 0.16f : 0.21f) * (1.0f + 0.15f * bass);
        if (baseR <= 10) return;

        // Aura color
        int auraColor = 0x4065E5CF;
        int fluidColor = 0xFF08090C;
        if ("Cyber Cyan".equals(ferrofluidColor)) {
            auraColor = 0x5500E5FF;
            fluidColor = 0xFF061820;
        } else if ("Neon Magenta".equals(ferrofluidColor)) {
            auraColor = 0x55FF0077;
            fluidColor = 0xFF1C0614;
        }

        // Magnetic Aura Glow
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, baseR * 1.8f,
                new int[]{auraColor, 0x15102030, 0x00000000},
                new float[]{0f, 0.60f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, baseR * 1.8f, paint);
        paint.setShader(null);

        // Rosensweig Spike Points
        int count = economy ? 24 : 36;
        float[] ptsX = new float[count];
        float[] ptsY = new float[count];
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count - Math.PI / 2;
            int band = Math.abs((i * 2) % levels.length);
            float spike = (float) Math.pow(levels[band], 1.35) * baseR * 0.85f;
            float ripple = (float) Math.sin(time * 3.5 + i * 0.8) * baseR * 0.04f;
            float r = baseR + spike + ripple;
            ptsX[i] = cx + (float) Math.cos(angle) * r;
            ptsY[i] = cy + (float) Math.sin(angle) * r;
        }

        // Closed Catmull-Rom Bezier Spline
        path.reset();
        path.moveTo((ptsX[0] + ptsX[count - 1]) / 2, (ptsY[0] + ptsY[count - 1]) / 2);
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            path.quadTo(ptsX[i], ptsY[i], (ptsX[i] + ptsX[next]) / 2, (ptsY[i] + ptsY[next]) / 2);
        }
        path.close();

        // Fluid Body
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fluidColor);
        c.drawPath(path, paint);

        // Specular Wet Highlight
        float specX = cx - baseR * 0.32f, specY = cy - baseR * 0.32f;
        float specR = baseR * 0.46f * (1.0f + 0.08f * bass);
        paint.setShader(new RadialGradient(specX, specY, specR,
                new int[]{0x66FFFFFF, 0x22FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(specX, specY, specR, paint);
        paint.setShader(null);

        // Satellite Droplets on Bass Hit
        if (bass > 0.40f) {
            paint.setColor(fluidColor);
            int satellites = 4;
            for (int s = 0; s < satellites; s++) {
                double a = Math.PI * 2 * s / satellites + time * 0.5;
                float dist = baseR * (1.25f + 0.35f * bass);
                float dropR = 3.5f + 4.5f * bass;
                c.drawCircle(cx + (float) Math.cos(a) * dist, cy + (float) Math.sin(a) * dist, dropR, paint);
            }
        }
    }

    private void drawCotodamaAmbient(Canvas c, float w, float h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x4065E5CF);
        paint.setStrokeWidth(2.5f);
        path.reset();
        for (int i = 0; i < levels.length; i++) {
            float x = w * i / Math.max(1, levels.length - 1);
            float y = h * 0.88f - levels[i] * h * 0.08f;
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
