package me.jxl.kiosk.plugins.musicscreensaver;

/** Monotonic playback estimate; repeated HA snapshots must not rewind it. */
final class PlaybackClock {
    private double base,lastReported=Double.NaN;
    private long anchor;
    private String track="",stamp="";
    private boolean playing;
    double at(long now){return base+(playing?Math.max(0,now-anchor)/1000.0:0);}
    void update(String key,boolean active,Double reported,String timestamp,Long timestampMs,long now,long wall){
        boolean changed=!track.equals(key);
        boolean fresh=reported!=null&&(changed||Double.compare(reported,lastReported)!=0||!stamp.equals(timestamp));
        if(changed){base=0;anchor=now;lastReported=Double.NaN;stamp="";}
        else if(playing!=active){base=at(now);anchor=now;}
        if(fresh){
            base=Math.max(0,reported);
            if(active&&timestampMs!=null)base+=Math.max(0,wall-timestampMs)/1000.0;
            anchor=now;lastReported=reported;stamp=timestamp;
        }
        track=key;playing=active;
    }
}
