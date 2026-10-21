// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

public final class MusicScreensaverPlugin implements KioskPlugin {
    private PluginHost host;
    private Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService ioPool;

    // Settings
    private String speakerEntity = "";
    private String activationMode = "When screensaver starts";
    private boolean onlyWhilePlaying = true;
    private boolean showPaused = true;
    private boolean kioskScreensaverActive = false;
    private boolean manualActive = false;
    private String effect = "hybrid_vinyl";
    private String vinylStyle = "With Ambient Glow";
    private String ferrofluidColor = "Obsidian Black";
    private String backgroundTheme = "Album Tinted";
    private boolean showPlaybackControls = true;
    private String volumeControls = "Buttons";
    private boolean tapDismiss = true;
    private int gain = 3;
    private boolean settingEconomy = false;
    private boolean overruleScreensaver = true;
    private String visibilityEntity = "";
    private String visibilityCondition = "Always";
    private String visibilityValue = "";

    // State
    private boolean isOverlayActive = false;
    private boolean userDismissed = false;
    private String dismissedTrackId = "";
    private String playerState = "idle";
    private String trackTitle = "";
    private String trackArtist = "";
    private String trackAlbum = "";
    private String artworkUrl = "";
    private String haBaseUrl = "";
    private Bitmap currentCover;
    private double trackDuration = 0;
    private double trackPosition = 0;
    private long trackPositionAnchor = 0;
    private float currentVolume = 0.5f;

    private MusicLyrics lyrics = MusicLyrics.parse("", "");
    private String lyricsTrackKey = "";
    private boolean lyricsPending = false;

    private FrameLayout rootContainer;
    private MusicVisualView visualView;
    private MusicLyricsView lyricsView;
    private WeakReference<Activity> activeActivity = new WeakReference<>(null);

    private BroadcastReceiver audioReceiver;
    private long lastScreensaverPostpone = 0;
    private boolean mediaPending = false;
    private boolean visibilityPending = false;
    private String visibilityState = "";

    private final Runnable tickRunnable = new Runnable() {
        @Override public void run() {
            if (host == null || context == null) return;
            pollMedia();
            pollVisibility();
            updatePresentation();
            if (isOverlayActive) {
                if (lyricsView != null && isLyricsEffect()) {
                    lyricsView.updatePosition(estimatedPosition());
                }
                if (overruleScreensaver && SystemClock.elapsedRealtime() - lastScreensaverPostpone > 12000) {
                    lastScreensaverPostpone = SystemClock.elapsedRealtime();
                    postponeKioskScreensaver();
                }
            }
            main.postDelayed(this, 1000);
        }
    };

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        this.context = resolveContext(host);
        this.ioPool = Executors.newFixedThreadPool(3);

        Activity current = findResumedActivity();
        if (current != null) {
            activeActivity = new WeakReference<>(current);
        }

        configure(settings);
        registerLifecycle();
        registerAudioReceiver();

        // Retrieve HA base URL from Kiosk host
        host.executeCommand("getDashboardState", Collections.emptyMap(), (ok, data, error) -> {
            if (ok && data instanceof Map) {
                Object url = ((Map<?, ?>) data).get("haBaseUrl");
                if (url == null) url = ((Map<?, ?>) data).get("homeAssistantUrl");
                if (url != null) haBaseUrl = String.valueOf(url);
            }
        });

        // Subscribe to Kiosk Satellite screensaver events
        try {
            host.subscribe("screensaver.state");
            host.subscribe("screensaver.view");
            host.executeCommand("isScreensaverActive", Collections.emptyMap(), (ok, data, error) -> {
                if (ok && data instanceof Boolean) {
                    kioskScreensaverActive = (Boolean) data;
                    main.post(this::updatePresentation);
                }
            });
        } catch (Throwable ignored) {}

        main.post(tickRunnable);
        publishState();
    }

    @Override
    public synchronized void stop() {
        main.removeCallbacksAndMessages(null);
        if (audioReceiver != null && context != null) {
            try { context.unregisterReceiver(audioReceiver); } catch (Throwable ignored) {}
            audioReceiver = null;
        }
        hideOverlay();
        if (ioPool != null) {
            ioPool.shutdownNow();
            ioPool = null;
        }
        this.host = null;
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        if (settings == null) return;
        speakerEntity = str(settings, "speakerEntity", "");
        activationMode = str(settings, "activationMode", "When screensaver starts");
        onlyWhilePlaying = bool(settings, "onlyWhilePlaying", true);
        showPaused = bool(settings, "showPaused", true);
        effect = str(settings, "effect", "hybrid_vinyl");
        vinylStyle = str(settings, "vinylStyle", "With Ambient Glow");
        ferrofluidColor = str(settings, "ferrofluidColor", "Obsidian Black");
        backgroundTheme = str(settings, "backgroundTheme", "Album Tinted");
        showPlaybackControls = bool(settings, "showPlaybackControls", true);
        volumeControls = str(settings, "volumeControls", "Buttons");
        tapDismiss = bool(settings, "tapDismiss", true);
        gain = num(settings, "gain", 3);
        settingEconomy = str(settings, "refreshRate", "25 FPS Normal").contains("Eco");
        overruleScreensaver = bool(settings, "overruleScreensaver", true);
        visibilityEntity = str(settings, "visibilityEntity", "");
        visibilityCondition = str(settings, "visibilityCondition", "Always");
        visibilityValue = str(settings, "visibilityValue", "");

        if (visualView != null) {
            visualView.configure(effect, vinylStyle, ferrofluidColor, backgroundTheme,
                    showPlaybackControls, volumeControls, tapDismiss, settingEconomy, gain);
        }
        publishState();
    }

    @Override
    public void execute(String id, Map<String, Object> arguments) {
        if ("active".equals(id) || "show".equals(id)) {
            boolean turnOn = arguments == null || !Boolean.FALSE.equals(arguments.get("value"));
            if (turnOn) {
                userDismissed = false;
                manualActive = true;
                bringKioskToFront();
                showOverlay();
            } else {
                userDismissed = true;
                manualActive = false;
                hideOverlay();
            }
        } else if ("hide".equals(id)) {
            userDismissed = true;
            manualActive = false;
            hideOverlay();
        } else if ("effectVinyl".equals(id)) {
            setEffect("vinyl");
        } else if ("effectFerrofluid".equals(id)) {
            setEffect("ferrofluid");
        } else if ("effectCotodama".equals(id)) {
            setEffect("cotodama");
        } else if ("effectHybridVinyl".equals(id)) {
            setEffect("hybrid_vinyl");
        } else if ("effectHybridFerrofluid".equals(id)) {
            setEffect("hybrid_ferrofluid");
        } else if ("toggleControls".equals(id)) {
            showPlaybackControls = !showPlaybackControls;
            if (visualView != null) visualView.configure(effect, vinylStyle, ferrofluidColor, backgroundTheme, showPlaybackControls, volumeControls, tapDismiss, settingEconomy, gain);
        } else if ("toggleVolume".equals(id)) {
            volumeControls = "Off".equals(volumeControls) ? "Buttons" : "Off";
            if (visualView != null) visualView.configure(effect, vinylStyle, ferrofluidColor, backgroundTheme, showPlaybackControls, volumeControls, tapDismiss, settingEconomy, gain);
        }
    }

    @Override
    public void onEvent(String event, Map<String, Object> payload) {
        if (event != null && (event.equals("switch.active") || event.endsWith(".active") || event.endsWith(".aktiv"))) {
            boolean turnOn = false;
            if (payload != null) {
                if (payload.get("on") instanceof Boolean) turnOn = (Boolean) payload.get("on");
                else if (payload.get("value") instanceof Boolean) turnOn = (Boolean) payload.get("value");
                else if ("on".equalsIgnoreCase(String.valueOf(payload.get("state")))) turnOn = true;
            }
            final boolean finalTurnOn = turnOn;
            main.post(() -> {
                if (finalTurnOn) {
                    userDismissed = false;
                    manualActive = true;
                    bringKioskToFront();
                    showOverlay();
                } else {
                    userDismissed = true;
                    manualActive = false;
                    hideOverlay();
                }
            });
            return;
        }

        if (event != null && (event.equals("select.effect") || event.endsWith(".effect") || event.endsWith(".visualisering"))) {
            if (payload != null) {
                Object opt = payload.get("option");
                if (opt == null) opt = payload.get("value");
                if (opt != null) {
                    final String effectName = String.valueOf(opt);
                    main.post(() -> setEffect(effectName));
                }
            }
            return;
        }

        if ("ks.screensaver.state".equals(event) || "screensaver.state".equals(event)) {
            kioskScreensaverActive = Boolean.TRUE.equals(payload != null ? payload.get("active") : false);
            if (!kioskScreensaverActive) {
                userDismissed = false;
                if (!manualActive && isOverlayActive) {
                    hideOverlay();
                }
            } else {
                userDismissed = false;
            }
            main.post(this::updatePresentation);
        }
    }

    private void setEffect(String newEffect) {
        this.effect = newEffect;
        if (visualView != null) {
            visualView.configure(effect, vinylStyle, ferrofluidColor, backgroundTheme,
                    showPlaybackControls, volumeControls, tapDismiss, settingEconomy, gain);
        }
        if (isOverlayActive) {
            Activity act = activeKioskActivity();
            if (act != null) rebuildOverlayViews(act);
        }
        publishState();
    }

    private void publishState() {
        if (host == null) return;
        try {
            host.publishSelect("effect", "Visualisering",
                    new String[]{"hybrid_vinyl", "hybrid_ferrofluid", "vinyl", "ferrofluid", "cotodama"}, effect);
            host.publishSwitch("active", "Aktiv", isOverlayActive);
        } catch (Throwable ignored) {}
    }

    private void pollMedia() {
        if (host == null || speakerEntity.isEmpty() || mediaPending) return;
        final String id = speakerEntity;
        mediaPending = true;
        host.executeCommand("getHaEntityState", Collections.singletonMap("entityId", id), (ok, data, error) -> main.post(() -> {
            mediaPending = false;
            if (host == null || !speakerEntity.equals(id) || !ok || !(data instanceof Map)) return;
            Map<?, ?> snapshot = (Map<?, ?>) data;
            String state = String.valueOf(snapshot.get("state"));
            Object attrsObj = snapshot.get("attributes");
            Map<?, ?> attrs = attrsObj instanceof Map ? (Map<?, ?>) attrsObj : Collections.emptyMap();
            applyMediaState(state, attrs);
        }));
    }

    private void applyMediaState(String state, Map<?, ?> attrs) {
        this.playerState = state == null ? "idle" : state;
        String title = String.valueOf(attrs.get("media_title") != null ? attrs.get("media_title") : "");
        String artist = String.valueOf(attrs.get("media_artist") != null ? attrs.get("media_artist") : "");
        String album = String.valueOf(attrs.get("media_album_name") != null ? attrs.get("media_album_name") : "");
        String picture = String.valueOf(attrs.get("entity_picture") != null ? attrs.get("entity_picture") : "");
        if (picture.isEmpty() && attrs.get("entity_picture_local") != null) {
            picture = String.valueOf(attrs.get("entity_picture_local"));
        }

        Object vol = attrs.get("volume_level");
        if (vol instanceof Number) this.currentVolume = ((Number) vol).floatValue();

        Object dur = attrs.get("media_duration");
        if (dur instanceof Number) this.trackDuration = ((Number) dur).doubleValue();

        Object pos = attrs.get("media_position");
        if (pos instanceof Number) {
            this.trackPosition = ((Number) pos).doubleValue();
            this.trackPositionAnchor = SystemClock.elapsedRealtime();
        }

        String trackKey = artist + " - " + title;
        if (!trackKey.equals(dismissedTrackId)) {
            // New track started: reset user dismiss flag
            userDismissed = false;
        }

        boolean isPlaying = "playing".equalsIgnoreCase(playerState);
        this.trackTitle = title;
        this.trackArtist = artist;
        this.trackAlbum = album;

        if (!picture.equals(artworkUrl)) {
            this.artworkUrl = picture;
            loadArtwork(picture);
        }

        if (visualView != null) {
            visualView.updateTrack(trackTitle, trackArtist, currentCover, isPlaying, estimatedPosition());
        }

        // Fetch lyrics if track changed
        if (!trackKey.equals(lyricsTrackKey)) {
            lyricsTrackKey = trackKey;
            fetchLyrics(attrs);
        }
    }

    private void fetchLyrics(Map<?, ?> attrs) {
        if (ioPool == null || lyricsPending) return;
        lyricsPending = true;
        ioPool.execute(() -> {
            MusicLyrics parsed = null;
            Object lrcAttr = attrs.get("lyrics");
            if (lrcAttr instanceof String) {
                parsed = MusicLyrics.parse((String) lrcAttr, "");
            }
            final MusicLyrics finalLyrics = (parsed != null && !parsed.lines.isEmpty())
                    ? parsed : MusicLyrics.parse("", "");
            main.post(() -> {
                lyricsPending = false;
                lyrics = finalLyrics;
                if (lyricsView != null) lyricsView.setLyrics(lyrics);
            });
        });
    }

    private void loadArtwork(String urlStr) {
        if (urlStr == null || urlStr.isEmpty() || ioPool == null) {
            currentCover = null;
            if (visualView != null) visualView.updateTrack(trackTitle, trackArtist, null, "playing".equalsIgnoreCase(playerState), estimatedPosition());
            return;
        }
        ioPool.execute(() -> {
            Bitmap bmp = null;
            try {
                String fullUrl = urlStr;
                if (urlStr.startsWith("/") && !haBaseUrl.isEmpty()) {
                    fullUrl = haBaseUrl.replaceAll("/+$", "") + urlStr;
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(in);
                }
            } catch (Throwable ignored) {}
            final Bitmap loaded = bmp;
            main.post(() -> {
                currentCover = loaded;
                if (visualView != null) {
                    visualView.updateTrack(trackTitle, trackArtist, currentCover, "playing".equalsIgnoreCase(playerState), estimatedPosition());
                }
            });
        });
    }

    private double estimatedPosition() {
        if (!"playing".equalsIgnoreCase(playerState)) return trackPosition;
        return trackPosition + Math.max(0, SystemClock.elapsedRealtime() - trackPositionAnchor) / 1000.0;
    }

    private void pollVisibility() {
        if (host == null || visibilityEntity.isEmpty() || visibilityPending) return;
        final String id = visibilityEntity;
        visibilityPending = true;
        host.executeCommand("getHaEntityState", Collections.singletonMap("entityId", id), (ok, data, error) -> main.post(() -> {
            visibilityPending = false;
            if (host == null || !visibilityEntity.equals(id)) return;
            visibilityState = ok && data instanceof Map ? String.valueOf(((Map<?, ?>) data).get("state")) : "unavailable";
        }));
    }

    private boolean isVisibilityAllowed() {
        if ("Active".equals(visibilityCondition)) {
            return "on".equalsIgnoreCase(visibilityState) || "true".equalsIgnoreCase(visibilityState) || "playing".equalsIgnoreCase(visibilityState);
        } else if ("Inactive".equals(visibilityCondition)) {
            return "off".equalsIgnoreCase(visibilityState) || "false".equalsIgnoreCase(visibilityState) || "unavailable".equalsIgnoreCase(visibilityState);
        } else if ("State equals".equals(visibilityCondition)) {
            return visibilityState.equalsIgnoreCase(visibilityValue);
        } else if ("State not equals".equals(visibilityCondition)) {
            return !visibilityState.equalsIgnoreCase(visibilityValue);
        }
        return true; // Always
    }

    private void updatePresentation() {
        boolean isPlaying = "playing".equalsIgnoreCase(playerState);
        boolean isPaused = showPaused && "paused".equalsIgnoreCase(playerState);
        boolean musicEligible = onlyWhilePlaying ? (isPlaying || isPaused) : true;

        boolean shouldShow = false;
        if (manualActive) {
            shouldShow = true;
        } else if (!userDismissed && isVisibilityAllowed()) {
            if ("Immediately when playing".equals(activationMode)) {
                shouldShow = musicEligible;
            } else if ("When screensaver starts".equals(activationMode)) {
                shouldShow = kioskScreensaverActive && musicEligible;
            } else if ("Manual / HA switch only".equals(activationMode)) {
                shouldShow = false;
            }
        }

        if (shouldShow) {
            if (!isOverlayActive) showOverlay();
        } else {
            if (isOverlayActive) hideOverlay();
        }
    }

    private void showOverlay() {
        bringKioskToFront();
        isOverlayActive = true;
        publishState();

        Activity act = activeKioskActivity();
        if (act != null && !act.isFinishing() && (Build.VERSION.SDK_INT < 17 || !act.isDestroyed())) {
            rebuildOverlayViews(act);
            postponeKioskScreensaver();
        } else {
            // Activity might still be transitioning to foreground; retry in 300ms
            main.postDelayed(() -> {
                Activity retry = activeKioskActivity();
                if (retry != null && !retry.isFinishing()) {
                    rebuildOverlayViews(retry);
                    postponeKioskScreensaver();
                }
            }, 300);
        }
    }

    private void hideOverlay() {
        isOverlayActive = false;
        if (rootContainer != null) {
            try {
                View parent = (View) rootContainer.getParent();
                if (parent instanceof FrameLayout) {
                    ((FrameLayout) parent).removeView(rootContainer);
                }
            } catch (Throwable ignored) {}
            rootContainer = null;
        }
        visualView = null;
        lyricsView = null;
        publishState();
    }

    private void rebuildOverlayViews(Activity act) {
        if (act == null || act.isFinishing() || (Build.VERSION.SDK_INT >= 17 && act.isDestroyed())) return;

        View content = act.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;

        if (rootContainer != null) {
            ((FrameLayout) content).removeView(rootContainer);
        }

        rootContainer = new FrameLayout(act);
        rootContainer.setBackgroundColor(0xFF0D1014);

        boolean isSplit = "hybrid_vinyl".equals(effect) || "hybrid_ferrofluid".equals(effect);
        boolean hasLyrics = isLyricsEffect();

        // 1. Visualizer View (Full or Left Stage)
        visualView = new MusicVisualView(act);
        visualView.configure(effect, vinylStyle, ferrofluidColor, backgroundTheme,
                showPlaybackControls, volumeControls, tapDismiss, settingEconomy, gain);
        visualView.updateTrack(trackTitle, trackArtist, currentCover, "playing".equalsIgnoreCase(playerState), estimatedPosition());
        visualView.setListener(new MusicVisualView.Listener() {
            @Override public void onPlayPause() { callHaService("media_player", "media_play_pause"); }
            @Override public void onNext() { callHaService("media_player", "media_next_track"); }
            @Override public void onPrevious() { callHaService("media_player", "media_previous_track"); }
            @Override public void onVolumeAdjust(int delta) {
                float target = Math.max(0f, Math.min(1f, currentVolume + (delta / 100.0f)));
                callHaServiceWithData("media_player", "volume_set", Collections.singletonMap("volume_level", target));
            }
            @Override public void onDismiss() {
                userDismissed = true;
                manualActive = false;
                dismissedTrackId = trackArtist + " - " + trackTitle;
                hideOverlay();
                if (host != null) {
                    host.executeCommand("stopScreensaver", Collections.emptyMap(), (ok, d, e) -> {});
                }
            }
        });

        rootContainer.addView(visualView, new FrameLayout.LayoutParams(-1, -1));

        // 2. Lyrics View (Right Stage in Split, or Full Screen in Cotodama)
        if (hasLyrics) {
            lyricsView = new MusicLyricsView(act, isSplit);
            lyricsView.setLyrics(lyrics);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            if (isSplit) {
                int screenW = act.getResources().getDisplayMetrics().widthPixels;
                lp.width = (int) (screenW * 0.58f);
                lp.gravity = Gravity.RIGHT | Gravity.TOP;
            }
            rootContainer.addView(lyricsView, lp);
        }

        ((FrameLayout) content).addView(rootContainer, new FrameLayout.LayoutParams(-1, -1));
        rootContainer.setZ(100000f);
        rootContainer.bringToFront();
    }

    private Activity activeKioskActivity() {
        Activity a = activeActivity.get();
        if (a != null && !a.isFinishing() && (Build.VERSION.SDK_INT < 17 || !a.isDestroyed())) {
            return a;
        }
        Activity found = findResumedActivity();
        if (found != null) {
            activeActivity = new WeakReference<>(found);
        }
        return found;
    }

    private Activity findResumedActivity() {
        try {
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Method currentThread = threadClass.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread == null) return null;

            Field activitiesField = threadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesObject = activitiesField.get(thread);
            if (!(activitiesObject instanceof Map)) return null;

            String pkg = context != null ? context.getPackageName() : null;
            Activity fallback = null;
            for (Object record : ((Map<?, ?>) activitiesObject).values()) {
                if (record == null) continue;
                Class<?> recordClass = record.getClass();

                Field activityField = recordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Object activityValue = activityField.get(record);
                if (!(activityValue instanceof Activity)) continue;
                Activity activity = (Activity) activityValue;
                if ((pkg != null && !activity.getPackageName().equals(pkg)) ||
                        activity.isFinishing() ||
                        (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    continue;
                }

                if (activity.hasWindowFocus()) return activity;

                boolean paused = false;
                boolean stopped = false;
                try {
                    Field pausedField = recordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    paused = pausedField.getBoolean(record);
                } catch (Throwable ignored) {}
                try {
                    Field stoppedField = recordClass.getDeclaredField("stopped");
                    stoppedField.setAccessible(true);
                    stopped = stoppedField.getBoolean(record);
                } catch (Throwable ignored) {}

                if (!paused && !stopped) return activity;
                if (!stopped && fallback == null) fallback = activity;
            }
            return fallback;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void bringKioskToFront() {
        if (context != null) {
            try {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(launch);
                }
            } catch (Throwable ignored) {}
        }
        if (host != null) {
            try {
                host.executeCommand("bringToFront", Collections.emptyMap(), (ok, d, e) -> {});
            } catch (Throwable ignored) {}
        }
    }

    private boolean isLyricsEffect() {
        return "cotodama".equals(effect) || "hybrid_vinyl".equals(effect) || "hybrid_ferrofluid".equals(effect);
    }

    private void callHaService(String domain, String service) {
        callHaServiceWithData(domain, service, Collections.emptyMap());
    }

    private void callHaServiceWithData(String domain, String service, Map<String, Object> extra) {
        if (host == null || speakerEntity.isEmpty()) return;
        Map<String, Object> data = new HashMap<>(extra);
        data.put("entity_id", speakerEntity);
        Map<String, Object> args = new HashMap<>();
        args.put("domain", domain);
        args.put("service", service);
        args.put("serviceData", data);
        host.executeCommand("callHaService", args, (ok, res, err) -> {});
    }

    private void postponeKioskScreensaver() {
        if (host != null) {
            host.executeCommand("postponeScreensaver", Collections.emptyMap(), (ok, d, e) -> {});
        }
    }

    private void registerAudioReceiver() {
        audioReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (!isOverlayActive) return;
                float[] bands = intent.getFloatArrayExtra("bands");
                if (visualView != null) visualView.acceptAudio(bands);
                if (lyricsView != null) lyricsView.acceptAudio(bands);
            }
        };
        IntentFilter filter = new IntentFilter("me.jxl.kiosk.plugins.PARTY_AUDIO_FRAME");
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(audioReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(audioReceiver, filter);
    }

    private void registerLifecycle() {
        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity activity) {
                    activeActivity = new WeakReference<>(activity);
                    if (isOverlayActive || manualActive) {
                        rebuildOverlayViews(activity);
                        postponeKioskScreensaver();
                    }
                }
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
                @Override public void onActivityDestroyed(Activity activity) {
                    if (activeActivity.get() == activity) {
                        activeActivity.clear();
                    }
                }
            });
        }
    }

    private static Context resolveContext(PluginHost host) {
        try {
            Method m = host.getClass().getMethod("applicationContext");
            Object c = m.invoke(host);
            if (c instanceof Context) return (Context) c;
        } catch (Throwable ignored) {}
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method curApp = atClass.getMethod("currentApplication");
            return (Context) curApp.invoke(null);
        } catch (Throwable ignored) {}
        return null;
    }

    private static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k); return v != null ? String.valueOf(v).trim() : def;
    }
    private static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : def;
    }
    private static int num(Map<String, Object> m, String k, int def) {
        Object v = m.get(k); return v instanceof Number ? ((Number) v).intValue() : def;
    }
}
