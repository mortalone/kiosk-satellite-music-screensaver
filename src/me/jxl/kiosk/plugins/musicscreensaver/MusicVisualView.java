// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
        void onVolumeSet(float fraction);
        void onDismiss();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final RectF shadowRect = new RectF();

    private Listener listener;
    private String effect = "hybrid_vinyl";
    private String vinylStyle = "With Ambient Glow";
    private String ferrofluidColor = "Obsidian Black";
    private String backgroundTheme = "Album Tinted";
    private boolean showControls = true;
    private String volumeControls = "Slider";
    private boolean tapDismiss = true;
    private boolean economy = false;
    private int gain = 3;

    private boolean playing = false;
    private String trackTitle = "";
    private String trackArtist = "";
    private Bitmap coverArt;
    private Bitmap blurredBg;
    private int dominantColor = 0xFF1A212D;
    private double positionSeconds = 0;
    private long anchorTime = 0;

    private final float[] levels = new float[32];
    private final float[] smoothedLevels = new float[32];
    private long lastAudioFrameAt = 0;
    private final AudioEnvelope audioEnvelope=new AudioEnvelope();
    private float smoothedBass = 0;
    private float smoothedMid = 0;
    private float smoothedTreble = 0;
    private long lastPhysicsTime = 0;

    private final BacklitVinylRenderer backlit = new BacklitVinylRenderer();
    void setFluidQuality(String value){fluid.setQuality(value);invalidate();}
    void setVinylLight(String value){backlit.mode=value;invalidate();}
    private final FluidRenderer fluid = new FluidRenderer();
    // Interactive hit rects
    private final RectF prevRect = new RectF();
    private final RectF playRect = new RectF();
    private final RectF nextRect = new RectF();
    private final RectF volMinusRect = new RectF();
    private final RectF volPlusRect = new RectF();
    private final RectF sliderTrackRect = new RectF();
    private boolean draggingSlider = false;
    private float currentVolume = 0.5f;

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

    void setVolumeLevel(float volume) {
        this.currentVolume = Math.max(0f, Math.min(1f, volume));
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
            extractPalette(cover);
        }
        invalidate();
    }

    private void extractPalette(Bitmap src) {
        if (src == null || src.isRecycled()) {
            dominantColor = 0xFF181F28;
            blurredBg = null;
            return;
        }
        try {
            blurredBg = Bitmap.createScaledBitmap(src, 32, 32, true);
            float maxSat = -1f;
            int bestColor = 0xFF181F28;
            int w = blurredBg.getWidth(), h = blurredBg.getHeight();
            float[] hsv = new float[3];
            for (int y = 0; y < h; y += 2) {
                for (int x = 0; x < w; x += 2) {
                    int p = blurredBg.getPixel(x, y);
                    Color.colorToHSV(p, hsv);
                    if (hsv[2] > 0.12f && hsv[2] < 0.95f && hsv[1] > maxSat) {
                        maxSat = hsv[1];
                        hsv[2] = Math.max(0.20f, Math.min(0.38f, hsv[2]));
                        hsv[1] = Math.max(0.40f, Math.min(0.85f, hsv[1]));
                        bestColor = Color.HSVToColor(hsv);
                    }
                }
            }
            dominantColor = bestColor;
        } catch (Throwable ignored) {
            dominantColor = 0xFF181F28;
        }
    }

    String audioStatus(){
        long age=SystemClock.elapsedRealtime()-lastAudioFrameAt;
        return age>1200?"Ingen live lyddata":String.format(java.util.Locale.ROOT,"Live · bas %.2f · puls %.2f",smoothedBass,audioEnvelope.pulse(SystemClock.elapsedRealtime()));
    }

    void acceptAudio(float[] bands) {
        if (bands == null || bands.length == 0) return;
        int count = Math.min(levels.length, bands.length);
        float gainMult = .45f + gain * .45f;
        float rawBass=0;
        for(int i=0;i<Math.min(6,count);i++)if(Float.isFinite(bands[i]))rawBass+=Math.max(0,bands[i]);
        audioEnvelope.feed(rawBass/Math.max(1,Math.min(6,count)),SystemClock.elapsedRealtime());
        lastAudioFrameAt = SystemClock.elapsedRealtime();
        for(int i=count;i<levels.length;i++)levels[i]=0;
        for (int i = 0; i < count; i++) {
            float v = bands[i];
            float clamped = Float.isNaN(v) || Float.isInfinite(v) ? 0 : Math.max(0, Math.min(1.8f, v));
            levels[i] = clamped * gainMult;
        }
        postInvalidateOnAnimation();
    }

    private void updateAudioDynamics(float dt, double elapsed) {
        boolean hasLiveAudio = (SystemClock.elapsedRealtime() - lastAudioFrameAt) < 1200;

        if (!hasLiveAudio || !playing) {
            for (int i=0;i<levels.length;i++) levels[i]=0;
        }

        // Viscous ballistic smoothing
        float bassSum = 0;
        float midSum = 0;
        float trebleSum = 0;
        for (int i = 0; i < 32; i++) {
            float target = levels[i];
            float current = smoothedLevels[i];
            float speed = (target > current) ? Math.min(1.0f, dt * 24.0f) : Math.min(1.0f, dt * 8.0f);
            smoothedLevels[i] = current + (target - current) * speed;

            if (i < 6) bassSum += smoothedLevels[i];
            else if (i < 18) midSum += smoothedLevels[i];
            else trebleSum += smoothedLevels[i];
        }

        smoothedBass = bassSum / 6.0f;
        smoothedMid = midSum / 12.0f;
        smoothedTreble = trebleSum / 14.0f;

        fluid.setPulse(playing?Math.min(1.5f,audioEnvelope.pulse(SystemClock.elapsedRealtime())*(.35f+gain*.17f)):0);
        fluid.step(dt, smoothedBass, smoothedMid, smoothedTreble);

    }

    double elapsedSeconds() {
        if (!playing) return positionSeconds;
        return positionSeconds + Math.max(0, SystemClock.elapsedRealtime() - anchorTime) / 1000.0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        int action = event.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            if ("Slider".equals(volumeControls) && sliderTrackRect.contains(x, y)) {
                draggingSlider = true;
                updateSliderFromTouch(x);
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (draggingSlider) {
                updateSliderFromTouch(x);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (draggingSlider) {
                draggingSlider = false;
                updateSliderFromTouch(x);
                return true;
            }
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
            } else if ("Slider".equals(volumeControls)) {
                if (volMinusRect.contains(x, y)) {
                    if (listener != null) listener.onVolumeAdjust(-5);
                    return true;
                } else if (volPlusRect.contains(x, y)) {
                    if (listener != null) listener.onVolumeAdjust(5);
                    return true;
                }
            }
            // Tap outside controls dismisses screensaver
            if (tapDismiss && listener != null) {
                listener.onDismiss();
                return true;
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            draggingSlider = false;
        }
        return true;
    }

    private void updateSliderFromTouch(float x) {
        if (sliderTrackRect.width() <= 0) return;
        float fraction = Math.max(0f, Math.min(1f, (x - sliderTrackRect.left) / sliderTrackRect.width()));
        currentVolume = fraction;
        invalidate();
        if (listener != null) {
            listener.onVolumeSet(fraction);
        }
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

        // Layout mode: Portrait vs Landscape split
        boolean isPortrait = h >= w;
        boolean isSplit = !isPortrait && ("hybrid_vinyl".equals(effect) || "hybrid_ferrofluid".equals(effect));

        // 1. Draw Background
        drawBackground(canvas, w, h);

        // 2. Draw Visual Centerpiece
        if ("vinyl".equals(effect) || "hybrid_vinyl".equals(effect)) {
            drawVinyl(canvas, w, h, isPortrait, isSplit, playing, coverArt, elapsed, smoothedBass);
        } else if ("ferrofluid".equals(effect) || "hybrid_ferrofluid".equals(effect)) {
            drawFerrofluid(canvas, w, h, isPortrait, isSplit, smoothedBass, time);
        } else if ("cotodama".equals(effect)) {
            drawCotodamaAmbient(canvas, w, h);
        }

        // 3. Draw Track Metadata (Always drawn cleanly centered in portrait or split)
        if (!"cotodama".equals(effect)) drawTrackInfo(canvas, w, h, isPortrait, isSplit);

        // 4. Draw Controls & Volume Slider
        drawControls(canvas, w, h, isPortrait, isSplit);

        if (playing || "ferrofluid".equals(effect) || "hybrid_ferrofluid".equals(effect) || "cotodama".equals(effect)) {
            postInvalidateDelayed(economy ? 66 : 40);
        }
    }

    private void drawBackground(Canvas c, float w, float h) {
        if ("Pure Black".equals(backgroundTheme)) {
            c.drawColor(0xFF000000);
        } else if ("Deep Charcoal".equals(backgroundTheme)) {
            c.drawColor(0xFF101317);
        } else {
            // Album Tinted: smooth saturated backdrop matching album palette
            c.drawColor(dominantColor);
            // Subtle top-down lighting vignette
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(w * 0.5f, h * 0.35f, Math.max(w, h) * 0.85f,
                    new int[]{0x2AFFFFFF, 0x00000000, 0x55000000},
                    new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    private void drawVinyl(Canvas c, float w, float h, boolean isPortrait, boolean split,
                           boolean isPlaying, Bitmap cover, double elapsed, float bass) {
        float cx = split ? w * 0.23f : w * 0.50f;
        float cy = isPortrait ? h * 0.36f : h * 0.44f;
        float discR = isPortrait ? Math.min(w * 0.41f, h * 0.25f) : Math.min(w * (split ? 0.20f : 0.34f), h * 0.36f);
        if (discR <= 10) return;

        if(vinylStyle.startsWith("Backlit ")){backlit.setPattern(vinylStyle);backlit.draw(c,cx,cy,discR,cover,dominantColor,elapsed,bass);return;}

        boolean withGlow = !"Disc Only (Minimal)".equals(vinylStyle);

        // 1. Ambient Glow
        if (withGlow) {
            paint.setStyle(Paint.Style.FILL);
            float glowR = discR * (1.28f + 0.15f * bass);
            int glowColor = (dominantColor & 0x00FFFFFF) | 0x44000000;
            paint.setShader(new RadialGradient(cx, cy, glowR,
                    new int[]{glowColor, 0x1A000000, 0x00000000},
                    new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, glowR, paint);
            paint.setShader(null);
        }

        // 2. Realistic Multi-Layer Drop Shadow (floating 3D disc)
        float shadowY = cy + discR * 0.08f;
        float shadowR = discR * 1.12f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, shadowY, shadowR,
                new int[]{0x75000000, 0x30000000, 0x00000000},
                new float[]{0.60f, 0.88f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, shadowY, shadowR, paint);
        paint.setShader(null);

        // 3. Disc Body
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0F1014);
        c.drawCircle(cx, cy, discR, paint);

        // 4. Concentric Micro-Grooves (Rilles)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        float labelR = discR * 0.44f;
        int rings = economy ? 8 : 14;
        for (int i = 0; i < rings; i++) {
            float r = labelR * 1.05f + (discR * 0.95f - labelR * 1.05f) * (i + 1) / (rings + 1f);
            paint.setColor((i % 2 == 0) ? 0x1AFFFFFF : 0x0AFFFFFF);
            paint.setStrokeWidth(1.2f);
            c.drawCircle(cx, cy, r, paint);
        }

        // 5. Anisotropic Radial Sheen (Bow-tie reflection rotating at 33⅓ RPM)
        float angle = isPlaying ? (float) ((elapsed * 200.0) % 360.0) : 15f;
        rect.set(cx - discR, cy - discR, cx + discR, cy + discR);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x1AFFFFFF);
        c.drawArc(rect, angle - 22, 44, true, paint);
        c.drawArc(rect, angle + 158, 44, true, paint);

        // 6. Center Label (Album Art - 100% full crisp opacity!)
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
            paint.reset();
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            c.drawBitmap(cover, src, rect, paint);
            c.restore();
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF263238);
            c.drawCircle(cx, cy, labelR, paint);
            // Default vinyl record label placeholder
            paint.setColor(0xFF65E5CF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            c.drawCircle(cx, cy, labelR * 0.65f, paint);
        }
        c.restore();

        // Label Outer Rim
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x40000000);
        paint.setStrokeWidth(2.5f);
        c.drawCircle(cx, cy, labelR, paint);

        // Spindle Hole with Chrome/Brass Rim
        float holeR = discR * 0.045f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF060709);
        c.drawCircle(cx, cy, holeR, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFCAD4DF);
        paint.setStrokeWidth(1.8f);
        c.drawCircle(cx, cy, holeR, paint);

        // 7. Realistic Tonearm / Stylus (rested on disc when playing, lifted/rested off when paused)
        drawTonearm(c, cx, cy, discR, isPlaying);
    }

    private void drawTonearm(Canvas c, float cx, float cy, float discR, boolean isPlaying) {
        float pivotX = cx + discR * 0.80f;
        float pivotY = cy - discR * 0.80f;

        // Base gimbal at top-right
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        c.drawCircle(pivotX + 2, pivotY + 4, 16, paint); // Gimbal shadow
        paint.setColor(0xFF232A34);
        c.drawCircle(pivotX, pivotY, 15, paint);
        paint.setColor(0xFF384352);
        c.drawCircle(pivotX, pivotY, 11, paint);
        paint.setColor(0xFF9AA7B6);
        c.drawCircle(pivotX, pivotY, 4, paint);

        // Needle contact point
        float cartX = isPlaying ? (cx + discR * 0.65f) : (cx + discR * 0.90f);
        float cartY = isPlaying ? (cy - discR * 0.32f) : (cy - discR * 0.36f);

        // Tonearm shadow
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x40000000);
        paint.setStrokeWidth(4.5f);
        c.drawLine(pivotX + 3, pivotY + 5, cartX + 3, cartY + 5, paint);

        // Tonearm metallic arm tube
        paint.setColor(0xFF1E252E);
        c.drawLine(pivotX, pivotY, cartX, cartY, paint);
        paint.setColor(0xFF708092);
        paint.setStrokeWidth(2.0f);
        c.drawLine(pivotX, pivotY, cartX, cartY, paint);

        // Cartridge headshell (angled rectangle)
        c.save();
        c.translate(cartX, cartY);
        float angle = (float) Math.toDegrees(Math.atan2(cartY - pivotY, cartX - pivotX)) + 22f;
        c.rotate(angle);
        rect.set(-5, -12, 5, 4);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF12161C);
        c.drawRoundRect(rect, 2, 2, paint);
        // Cartridge chrome accent line
        paint.setColor(0xFFE2E8F0);
        c.drawRect(-3, -10, 3, -8, paint);
        c.restore();
    }

    private void drawFerrofluid(Canvas c, float w, float h, boolean isPortrait, boolean split, float bass, double time) {
        float cx=split?w*.23f:w*.5f,cy=isPortrait?h*.36f:h*.44f;
        float r=isPortrait?Math.min(w*.43f,h*.25f):Math.min(w*(split?.21f:.34f),h*.36f);
        int tint="Cyber Cyan".equals(ferrofluidColor)?0x659C9A:"Neon Magenta".equals(ferrofluidColor)?0x9C6584:"Album Matched".equals(ferrofluidColor)?dominantColor:0x777B80;
        if(r>10)fluid.draw(c,cx,cy,r,economy,tint);
        if(playing && SystemClock.elapsedRealtime()-lastAudioFrameAt>=1200){
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(MusicUi.MUTED);paint.setTextSize(MusicUi.dp(getContext(),12));
            c.drawText("Afventer live lyddata",cx,cy+r*1.24f,paint);
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

    private void drawTrackInfo(Canvas c, float w, float h, boolean isPortrait, boolean split) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);

        float textX = split ? w * 0.23f : w * 0.50f;
        float textY = isPortrait ? h * 0.66f : h * 0.82f;

        if (!trackTitle.isEmpty()) {
            paint.setColor(MusicUi.INK);
            paint.setTextSize(MusicUi.dp(getContext(), isPortrait ? 22 : 18));
            paint.setFakeBoldText(true);
            c.drawText(trackTitle, textX, textY, paint);
        }
        if (!trackArtist.isEmpty()) {
            paint.setColor(0xCCAAB7C4);
            paint.setTextSize(MusicUi.dp(getContext(), isPortrait ? 15 : 13));
            paint.setFakeBoldText(false);
            c.drawText(trackArtist, textX, textY + MusicUi.dp(getContext(), isPortrait ? 24 : 20), paint);
        }
    }

    private void drawControls(Canvas c, float w, float h, boolean isPortrait, boolean split) {
        float density = getResources().getDisplayMetrics().density;

        float ctrlY = isPortrait ? h * 0.77f : h * 0.92f;
        float ctrlX = split ? w * 0.23f : w * 0.50f;

        // 1. Playback Controls (Pills matching home-assistant-vinyl-player)
        if (showControls) {
            float playR = 26 * density;
            float skipR = 21 * density;
            float spacing = 62 * density;

            playRect.set(ctrlX - playR, ctrlY - playR, ctrlX + playR, ctrlY + playR);
            prevRect.set(ctrlX - spacing - skipR, ctrlY - skipR, ctrlX - spacing + skipR, ctrlY + skipR);
            nextRect.set(ctrlX + spacing - skipR, ctrlY - skipR, ctrlX + spacing + skipR, ctrlY + skipR);

            // Translucent rounded pills
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x30FFFFFF);
            c.drawCircle(ctrlX, ctrlY, playR, paint);
            paint.setColor(0x20FFFFFF);
            c.drawCircle(ctrlX - spacing, ctrlY, skipR, paint);
            c.drawCircle(ctrlX + spacing, ctrlY, skipR, paint);

            // Control glyphs
            paint.setColor(MusicUi.INK);
            if (playing) {
                // Pause bars
                c.drawRoundRect(ctrlX - 6 * density, ctrlY - 8 * density, ctrlX - 2 * density, ctrlY + 8 * density, 2, 2, paint);
                c.drawRoundRect(ctrlX + 2 * density, ctrlY - 8 * density, ctrlX + 6 * density, ctrlY + 8 * density, 2, 2, paint);
            } else {
                // Play triangle
                path.reset();
                path.moveTo(ctrlX - 5 * density, ctrlY - 8 * density);
                path.lineTo(ctrlX + 7 * density, ctrlY);
                path.lineTo(ctrlX - 5 * density, ctrlY + 8 * density);
                path.close();
                c.drawPath(path, paint);
            }

            // Prev icon
            path.reset();
            path.moveTo(ctrlX - spacing + 4 * density, ctrlY - 6 * density);
            path.lineTo(ctrlX - spacing - 5 * density, ctrlY);
            path.lineTo(ctrlX - spacing + 4 * density, ctrlY + 6 * density);
            path.close();
            c.drawPath(path, paint);
            c.drawRect(ctrlX - spacing - 7 * density, ctrlY - 6 * density, ctrlX - spacing - 5 * density, ctrlY + 6 * density, paint);

            // Next icon
            path.reset();
            path.moveTo(ctrlX + spacing - 4 * density, ctrlY - 6 * density);
            path.lineTo(ctrlX + spacing + 5 * density, ctrlY);
            path.lineTo(ctrlX + spacing - 4 * density, ctrlY + 6 * density);
            path.close();
            c.drawPath(path, paint);
            c.drawRect(ctrlX + spacing + 5 * density, ctrlY - 6 * density, ctrlX + spacing + 7 * density, ctrlY + 6 * density, paint);
        }

        // 2. Volume Controls (Slider or Buttons)
        if ("Slider".equals(volumeControls)) {
            float sliderY = isPortrait ? h * 0.88f : h * 0.94f;
            float sliderW = isPortrait ? (w * 0.65f) : (w * (split ? 0.32f : 0.45f));
            float sliderLeft = ctrlX - sliderW * 0.5f;
            float sliderRight = ctrlX + sliderW * 0.5f;
            float trackH = 4 * density;

            sliderTrackRect.set(sliderLeft, sliderY - 14 * density, sliderRight, sliderY + 14 * density);

            // Speaker Low icon on left
            float leftIconX = sliderLeft - 22 * density;
            volMinusRect.set(leftIconX - 14 * density, sliderY - 14 * density, leftIconX + 14 * density, sliderY + 14 * density);
            drawSpeakerIcon(c, leftIconX, sliderY, density, false);

            // Speaker High icon on right
            float rightIconX = sliderRight + 22 * density;
            volPlusRect.set(rightIconX - 14 * density, sliderY - 14 * density, rightIconX + 14 * density, sliderY + 14 * density);
            drawSpeakerIcon(c, rightIconX, sliderY, density, true);

            // Background track
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x35FFFFFF);
            c.drawRoundRect(sliderLeft, sliderY - trackH * 0.5f, sliderRight, sliderY + trackH * 0.5f, trackH, trackH, paint);

            // Active track
            float knobX = sliderLeft + sliderW * currentVolume;
            paint.setColor(0xEEFFFFFF);
            c.drawRoundRect(sliderLeft, sliderY - trackH * 0.5f, knobX, sliderY + trackH * 0.5f, trackH, trackH, paint);

            // Knob thumb
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(knobX, sliderY, 7 * density, paint);
        } else if ("Buttons".equals(volumeControls)) {
            float btnR = 18 * density;
            float cy = isPortrait ? h * 0.88f : h * 0.92f;
            float rx = isPortrait ? (w * 0.5f) : (w - 40 * density);

            volMinusRect.set(rx - 38 * density - btnR, cy - btnR, rx - 38 * density + btnR, cy + btnR);
            volPlusRect.set(rx + 38 * density - btnR, cy - btnR, rx + 38 * density + btnR, cy + btnR);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x25FFFFFF);
            c.drawCircle(rx - 38 * density, cy, btnR, paint);
            c.drawCircle(rx + 38 * density, cy, btnR, paint);

            paint.setColor(MusicUi.INK);
            paint.setStrokeWidth(2 * density);
            paint.setStyle(Paint.Style.STROKE);
            // Minus
            c.drawLine(rx - 44 * density, cy, rx - 32 * density, cy, paint);
            // Plus
            c.drawLine(rx + 32 * density, cy, rx + 44 * density, cy, paint);
            c.drawLine(rx + 38 * density, cy - 6 * density, rx + 38 * density, cy + 6 * density, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawSpeakerIcon(Canvas c, float cx, float cy, float density, boolean loud) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCCAAB7C4);
        path.reset();
        // Speaker cone
        path.moveTo(cx - 5 * density, cy - 3 * density);
        path.lineTo(cx - 2 * density, cy - 3 * density);
        path.lineTo(cx + 2 * density, cy - 7 * density);
        path.lineTo(cx + 2 * density, cy + 7 * density);
        path.lineTo(cx - 2 * density, cy + 3 * density);
        path.lineTo(cx - 5 * density, cy + 3 * density);
        path.close();
        c.drawPath(path, paint);

        if (loud) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            rect.set(cx + 2 * density, cy - 6 * density, cx + 7 * density, cy + 6 * density);
            c.drawArc(rect, -45, 90, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
