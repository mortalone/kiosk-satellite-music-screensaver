package me.jxl.kiosk.plugins.musicscreensaver;

/** PARTY frames are emitted after SpeakerSync buffering; `at` is dispatch time. */
final class AudioFrameTiming {
    static boolean accept(boolean demo,long at,long now){
        return !demo && at<=now && now-at<=250;
    }
}
