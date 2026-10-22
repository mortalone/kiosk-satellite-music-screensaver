// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;
import org.json.JSONObject;

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
    private final Map<String,Object> savedSettings=new HashMap<>();
    private String vinylStyle = "With Ambient Glow";
    private String ferrofluidColor = "Obsidian Black";
    private String backgroundTheme = "Album Tinted";
    private boolean showPlaybackControls = true;
    private String volumeControls = "Slider";
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
    private final PlaybackClock playbackClock=new PlaybackClock();
    private double trackPosition = 0;
    private long trackPositionAnchor = 0;
    private float currentVolume = 0.5f;
    private String activeQueueId = "";

    private MusicLyrics lyrics = MusicLyrics.parse("", "");
    private String lyricsTrackKey = "";
    private BroadcastReceiver dreamReceiver;
    private boolean nativeDreaming, musicScreensaverSession;
    private int lyricsGeneration;
    private String lyricsStatus="Henter sangtekst…", lyricsStyle="Random", fluidQuality="High", vinylLight="Music";

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
                    lyricsView.setPlaying("playing".equalsIgnoreCase(playerState));
                    lyricsView.updatePosition(estimatedPosition());
                }
                if (overruleScreensaver && SystemClock.elapsedRealtime() - lastScreensaverPostpone > 12000) {
                    lastScreensaverPostpone = SystemClock.elapsedRealtime();
                    postponeKioskScreensaver();
                }
                notifyAudioPresentation();
                if(visualView!=null)try{host.publishTextSensor("audio_status","Lyddata",visualView.audioStatus());}catch(Exception ignored){}
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
        registerDreamReceiver();

        // Retrieve HA base URL from Kiosk host or FlutterSharedPreferences
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
        lyricsGeneration++;
        main.removeCallbacksAndMessages(null);
        notifyAudioPresentationOff();
        if (audioReceiver != null && context != null) {
            try { context.unregisterReceiver(audioReceiver); } catch (Throwable ignored) {}
            audioReceiver = null;
        }
        if(dreamReceiver!=null&&context!=null){try{context.unregisterReceiver(dreamReceiver);}catch(Exception ignored){}dreamReceiver=null;}
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
        savedSettings.clear();savedSettings.putAll(settings);
        String previousEffect=effect;
        speakerEntity = str(settings, "speakerEntity", "");
        activationMode = str(settings, "activationMode", "When screensaver starts");
        onlyWhilePlaying = bool(settings, "onlyWhilePlaying", true);
        showPaused = bool(settings, "showPaused", true);
        effect = str(settings, "effect", "hybrid_vinyl");
        vinylStyle = str(settings, "vinylStyle", "With Ambient Glow");
        lyricsStyle=str(settings,"lyricsStyle","Random");
        fluidQuality=str(settings,"fluidQuality","High");
        vinylLight=str(settings,"vinylLight","Music");
        ferrofluidColor = str(settings, "ferrofluidColor", "Obsidian Black");
        backgroundTheme = str(settings, "backgroundTheme", "Album Tinted");
        showPlaybackControls = bool(settings, "showPlaybackControls", true);
        volumeControls = str(settings, "volumeControls", "Slider");
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
            visualView.setVolumeLevel(currentVolume);
            visualView.setFluidQuality(fluidQuality);
            visualView.setVinylLight(vinylLight);
        }
        if(rootContainer!=null)rootContainer.setKeepScreenOn(overruleScreensaver);
        if(isOverlayActive&&!previousEffect.equals(effect))main.post(() -> {
            if(!isOverlayActive)return;
            Activity activity=activeKioskActivity();if(activity!=null)rebuildOverlayViews(activity);
        });
        if (lyricsView!=null) lyricsView.setStyle(lyricsStyle);
        if (isOverlayActive) notifyAudioPresentation();
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
            volumeControls = "Off".equals(volumeControls) ? "Slider" : "Off";
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
        if(effect.equals(newEffect))return;
        this.effect = newEffect;
        if(host!=null)try{savedSettings.put("effect",newEffect);host.saveSettings(new HashMap<>(savedSettings));}catch(Exception error){host.log("Could not persist visualization: "+error.getMessage());}
        if (visualView != null) {
            visualView.configure(effect, vinylStyle, ferrofluidColor, backgroundTheme,
                    showPlaybackControls, volumeControls, tapDismiss, settingEconomy, gain);
        }
        if (isOverlayActive) {
            Activity act = activeKioskActivity();
            if (act != null) rebuildOverlayViews(act);
            notifyAudioPresentation();
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
        if (vol instanceof Number) {
            this.currentVolume = ((Number) vol).floatValue();
            if (visualView != null) visualView.setVolumeLevel(currentVolume);
        }

        Object queue = attrs.get("active_queue");
        if (queue != null && !String.valueOf(queue).trim().isEmpty()) {
            this.activeQueueId = String.valueOf(queue).trim();
        }

        Object dur = attrs.get("media_duration");
        if (dur instanceof Number) this.trackDuration = ((Number) dur).doubleValue();

        Object pos = attrs.get("media_position");
        String trackKey = artist + " - " + title;
        String timestamp=String.valueOf(attrs.get("media_position_updated_at"));
        Long timestampMs=null;
        try{timestampMs=java.time.Instant.parse(timestamp).toEpochMilli();}catch(Exception ignored){}
        playbackClock.update(trackKey+"|"+String.valueOf(attrs.get("media_content_id")),"playing".equalsIgnoreCase(playerState),pos instanceof Number?((Number)pos).doubleValue():null,
                timestamp,timestampMs,SystemClock.elapsedRealtime(),System.currentTimeMillis());

        if (!trackKey.equals(dismissedTrackId)) {
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
        if (lyricsView != null) {
            lyricsView.setTrackInfo(trackTitle, trackArtist);
            lyricsView.setPlaying("playing".equalsIgnoreCase(playerState));
            lyricsView.updatePosition(estimatedPosition());
        }

        // Fetch lyrics if track changed
        if (!trackKey.equals(lyricsTrackKey) && (!title.isEmpty() || !artist.isEmpty())) {
            lyricsTrackKey = trackKey;
            fetchLyrics(attrs, artist, title, album, trackDuration);
        }
    }

    private void fetchLyrics(Map<?, ?> attrs, String artist, String title, String album, double duration) {
        if(ioPool==null)return;
        final int generation=++lyricsGeneration;
        lyrics=MusicLyrics.parse("", "");lyricsStatus="Henter sangtekst…";
        if(lyricsView!=null){lyricsView.setLyrics(lyrics);lyricsView.setStatus(lyricsStatus);}
        ioPool.execute(() -> {
            MusicLyrics parsed=null;String status="Ingen sangtekst fundet";
            try {
                for(String key:new String[]{"lyrics","lrc","song_lyrics","media_lyrics"}) {
                    Object value=attrs.get(key);
                    if(value instanceof String){parsed=MusicLyrics.parse((String)value,(String)value);if(!parsed.lines.isEmpty())break;}
                }
                if((parsed==null||parsed.lines.isEmpty())&&!title.isEmpty()&&!artist.isEmpty()) {
                    String query="artist_name="+URLEncoder.encode(artist,"UTF-8")+"&track_name="+URLEncoder.encode(title,"UTF-8");
                    String body=lyricsRequest("https://lrclib.net/api/get?"+query+"&album_name="+URLEncoder.encode(album,"UTF-8")+"&duration="+Math.round(duration));
                    JSONObject item=body==null?null:new JSONObject(body);
                    if(item==null) {
                        String result=lyricsRequest("https://lrclib.net/api/search?"+query);
                        if(result!=null){
                            org.json.JSONArray candidates=new org.json.JSONArray(result);
                            double best=Double.MAX_VALUE;
                            for(int i=0;i<candidates.length();i++){
                                JSONObject candidate=candidates.getJSONObject(i);
                                if(!MusicLyrics.sameName(title,candidate.optString("trackName"))||!MusicLyrics.sameName(artist,candidate.optString("artistName")))continue;
                                double delta=duration>0?Math.abs(duration-candidate.optDouble("duration",0)):0;
                                if(duration>0&&delta>8)continue;
                                double score=delta+(candidate.optString("syncedLyrics","").isEmpty()?100:0);
                                if(score<best){best=score;item=candidate;}
                            }
                        }
                    }
                    if(item!=null){
                        parsed=MusicLyrics.parse(item.optString("syncedLyrics",""),item.optString("plainLyrics",""));
                        if(item.optBoolean("instrumental",false))status="Instrumentalt nummer";
                    }
                }
            }catch(Exception error){status="Sangtekst kunne ikke hentes – prøver igen ved næste nummer";}
            final MusicLyrics result=parsed==null?MusicLyrics.parse("", ""):parsed;
            final String message=status;
            main.post(() -> {
                if(generation!=lyricsGeneration||host==null)return;
                lyrics=result;lyricsStatus=message;
                if(lyricsView!=null){lyricsView.setLyrics(lyrics);lyricsView.setStatus(lyricsStatus);lyricsView.setTrackInfo(trackTitle,trackArtist);lyricsView.setPlaying("playing".equalsIgnoreCase(playerState));lyricsView.updatePosition(estimatedPosition());}
            });
        });
    }

    private String lyricsRequest(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        try {
            conn.setConnectTimeout(5000);conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent","KioskSatellite-MusicScreensaver/0.1.8");
            int code=conn.getResponseCode();if(code==404)return null;
            if(code!=200)throw new java.io.IOException("Lyrics HTTP "+code);
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1){out.write(buffer,0,count);if(out.size()>2097152)throw new java.io.IOException("Lyrics response too large");}
                return out.toString("UTF-8");
            }
        }finally{conn.disconnect();}
    }

    private String resolveHaBaseUrl() {
        if (haBaseUrl != null && !haBaseUrl.isEmpty()) return haBaseUrl;
        if (context != null) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
                for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                    String k = entry.getKey().toLowerCase();
                    Object v = entry.getValue();
                    if (v instanceof String) {
                        String s = ((String) v).trim();
                        if ((s.startsWith("http://") || s.startsWith("https://")) &&
                                (k.contains("ha") || k.contains("url") || k.contains("server") || k.contains("host") || k.contains("home_assistant"))) {
                            haBaseUrl = s.replaceAll("/+$", "");
                            return haBaseUrl;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        String maUrl = readMaUrl();
        if (!maUrl.isEmpty()) {
            try {
                URL u = new URL(maUrl.replaceFirst("^ws:", "http:").replaceFirst("^wss:", "https:"));
                return u.getProtocol() + "://" + u.getHost() + ":8123";
            } catch (Throwable ignored) {}
        }
        return "";
    }

    private String resolveHaToken() {
        if (context == null) return "";
        try {
            SharedPreferences prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String k = entry.getKey().toLowerCase();
                if (k.contains("token") || k.contains("auth")) {
                    Object v = entry.getValue();
                    if (v instanceof String && ((String) v).length() > 20) {
                        return ((String) v).trim();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return readMaToken();
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
                if (urlStr.startsWith("/")) {
                    String base = resolveHaBaseUrl();
                    if (!base.isEmpty()) {
                        fullUrl = base.replaceAll("/+$", "") + urlStr;
                    }
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "KioskSatellite-MusicScreensaver/0.1.6");
                String token = resolveHaToken();
                if (!token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
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
        return playbackClock.at(SystemClock.elapsedRealtime());
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
        return true;
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
                shouldShow = (kioskScreensaverActive || nativeDreaming || musicScreensaverSession) && musicEligible;
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
        musicScreensaverSession=true;
        bringKioskToFront();
        isOverlayActive = true;
        publishState();
        notifyAudioPresentation();

        Activity act = activeKioskActivity();
        if (act != null && !act.isFinishing() && (Build.VERSION.SDK_INT < 17 || !act.isDestroyed())) {
            rebuildOverlayViews(act);
            postponeKioskScreensaver();
        } else {
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
        musicScreensaverSession=false;
        isOverlayActive = false;
        notifyAudioPresentationOff();
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
        rootContainer.setKeepScreenOn(overruleScreensaver);

        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        boolean isPortrait = dm.heightPixels >= dm.widthPixels;
        boolean isSplit = !isPortrait && ("hybrid_vinyl".equals(effect) || "hybrid_ferrofluid".equals(effect));
        boolean hasLyrics = isLyricsEffect();

        lyricsView=null;
        // 1. Visualizer View
        visualView = new MusicVisualView(act);
        visualView.configure(effect, vinylStyle, ferrofluidColor, backgroundTheme,
                showPlaybackControls, volumeControls, tapDismiss, settingEconomy, gain);
        visualView.setVolumeLevel(currentVolume);
        visualView.setFluidQuality(fluidQuality);
        visualView.setVinylLight(vinylLight);
        visualView.updateTrack(trackTitle, trackArtist, currentCover, "playing".equalsIgnoreCase(playerState), estimatedPosition());
        visualView.setListener(new MusicVisualView.Listener() {
            @Override public void onPlayPause() {
                boolean isPlaying = "playing".equalsIgnoreCase(playerState);
                String targetQueue = (activeQueueId != null && !activeQueueId.isEmpty()) ? activeQueueId : speakerEntity;
                if (!targetQueue.isEmpty()) {
                    sendMaPlayerCommand(isPlaying ? "player_queues/pause" : "player_queues/play",
                            Collections.singletonMap("queue_id", targetQueue));
                }
            }
            @Override public void onNext() {
                String targetQueue = (activeQueueId != null && !activeQueueId.isEmpty()) ? activeQueueId : speakerEntity;
                if (!targetQueue.isEmpty()) {
                    sendMaPlayerCommand("player_queues/next",
                            Collections.singletonMap("queue_id", targetQueue));
                }
            }
            @Override public void onPrevious() {
                String targetQueue = (activeQueueId != null && !activeQueueId.isEmpty()) ? activeQueueId : speakerEntity;
                if (!targetQueue.isEmpty()) {
                    sendMaPlayerCommand("player_queues/previous",
                            Collections.singletonMap("queue_id", targetQueue));
                }
            }
            @Override public void onVolumeAdjust(int delta) {
                float target = Math.max(0f, Math.min(1f, currentVolume + (delta / 100.0f)));
                onVolumeSet(target);
            }
            @Override public void onVolumeSet(float fraction) {
                currentVolume = Math.max(0f, Math.min(1f, fraction));
                if (visualView != null) visualView.setVolumeLevel(currentVolume);
                int level = Math.round(currentVolume * 100f);
                String targetQueue = (activeQueueId != null && !activeQueueId.isEmpty()) ? activeQueueId : speakerEntity;
                if (!targetQueue.isEmpty()) {
                    Map<String, Object> args = new HashMap<>();
                    args.put("player_id", targetQueue);
                    args.put("volume_level", level);
                    sendMaPlayerCommand("players/cmd/group_volume", args);
                }
            }
            @Override public void onDismiss() {
                userDismissed = true;
                manualActive = false;
                dismissedTrackId = trackArtist + " - " + trackTitle;
                hideOverlay();
                if (host != null) {
                    try {
                        host.executeCommand("stopScreensaver", Collections.emptyMap(), (ok, d, e) -> {});
                    } catch (Throwable ignored) {}
                }
            }
        });

        rootContainer.addView(visualView, new FrameLayout.LayoutParams(-1, -1));

        // 2. Lyrics View
        if (hasLyrics) {
            lyricsView = new MusicLyricsView(act, isSplit);
            lyricsView.setStyle(lyricsStyle);
            lyricsView.setStatus(lyricsStatus);
            lyricsView.setLyrics(lyrics);
            lyricsView.setTrackInfo(trackTitle, trackArtist);
            lyricsView.setPlaying("playing".equalsIgnoreCase(playerState));
            lyricsView.updatePosition(estimatedPosition());

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            if (isSplit) {
                lp.width = (int) (dm.widthPixels * 0.56f);
                lp.gravity = Gravity.RIGHT | Gravity.TOP;
            } else if ("cotodama".equals(effect)) {
                lp.gravity = Gravity.FILL;
                lp.bottomMargin = MusicUi.dp(act, 100);
            } else if (isPortrait) {
                // In portrait hybrid mode: place lyrics beneath visualizer disc above controls
                lp.topMargin = (int) (dm.heightPixels * 0.52f);
                lp.bottomMargin = (int) (dm.heightPixels * 0.22f);
                lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
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

    private void registerDreamReceiver(){
        if(context==null)return;
        dreamReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
            nativeDreaming=Intent.ACTION_DREAMING_STARTED.equals(intent.getAction());
            if(nativeDreaming&&isOverlayActive&&overruleScreensaver)bringKioskToFront();
            main.post(MusicScreensaverPlugin.this::updatePresentation);
        }};
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_DREAMING_STARTED);filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(dreamReceiver,filter,Context.RECEIVER_EXPORTED);
        else context.registerReceiver(dreamReceiver,filter);
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
    }

    private boolean isLyricsEffect() {
        return "cotodama".equals(effect) || "hybrid_vinyl".equals(effect) || "hybrid_ferrofluid".equals(effect);
    }

    private String readMaUrl() {
        if (context == null) return "";
        try {
            SharedPreferences prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
            return prefs.getString("flutter.ks.sendspin.ma_url", "").trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String readMaToken() {
        if (context == null) return "";
        try {
            SharedPreferences prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
            return prefs.getString("flutter.ks.sendspin.ma_token", "").trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void sendMaPlayerCommand(String command, Map<String, Object> args) {
        if (ioPool == null || context == null) return;
        final String base = readMaUrl().replaceFirst("^ws:", "http:").replaceFirst("^wss:", "https:").replaceAll("/+$", "");
        final String token = readMaToken();
        final String queue = (activeQueueId != null && !activeQueueId.isEmpty()) ? activeQueueId : speakerEntity;
        if (base.isEmpty() || token.isEmpty() || queue.isEmpty()) return;

        ioPool.execute(() -> {
            try {
                URL url = new URL(base + "/api");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(3500);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject request = new JSONObject();
                request.put("message_id", "music-screensaver");
                request.put("command", command);
                JSONObject jsonArgs = new JSONObject(args);
                request.put("args", jsonArgs);

                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body);
                }
                conn.getResponseCode();
            } catch (Throwable ignored) {}
        });
    }

    private void postponeKioskScreensaver() {
        if (host != null) {
            try {
                host.executeCommand("postponeScreensaver", Collections.emptyMap(), (ok, d, e) -> {});
            } catch (Throwable ignored) {}
        }
    }

    private void notifyAudioPresentation() {
        if (context == null) return;
        try {
            context.getSharedPreferences("party_mode_presentation", Context.MODE_PRIVATE).edit()
                    .putBoolean("party_fullscreen", isOverlayActive)
                    .putLong("party_until_ms", isOverlayActive ? System.currentTimeMillis() + 60000 : 0)
                    .putString("effect", effect)
                    .putInt("gain", gain)
                    .putInt("fps", settingEconomy ? 20 : 25)
                    .apply();
            context.sendBroadcast(new Intent("me.jxl.kiosk.plugins.PARTY_PRESENTATION_CHANGED").setPackage(context.getPackageName()));
        } catch (Throwable ignored) {}
    }

    private void notifyAudioPresentationOff() {
        if (context == null) return;
        try {
            context.getSharedPreferences("party_mode_presentation", Context.MODE_PRIVATE).edit()
                    .putBoolean("party_fullscreen", false)
                    .putLong("party_until_ms", 0)
                    .apply();
            context.sendBroadcast(new Intent("me.jxl.kiosk.plugins.PARTY_PRESENTATION_CHANGED").setPackage(context.getPackageName()));
        } catch (Throwable ignored) {}
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
