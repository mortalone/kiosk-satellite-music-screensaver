package me.jxl.kiosk.plugins.musicscreensaver;

/** Band-domain adaptation of Twinkly BassEnvelope: relative rise, hold and decay. */
final class AudioEnvelope {
    private float baseline,previous,strength;
    private long last=-1,hit=-10000;
    void feed(float bass,long now){
        bass=Float.isFinite(bass)?Math.max(0,bass):0;
        if(last<0||now-last>500){baseline=bass;previous=bass;strength=0;last=now;return;}
        float dt=Math.max(.001f,Math.min(.25f,(now-last)/1000f));
        if(now-hit>=140&&bass>.008f&&bass>previous*1.10f&&bass>baseline*1.18f){
            strength=Math.min(1,Math.max(.25f,(bass-baseline)/Math.max(.02f,baseline)));hit=now;
        }
        baseline+=(bass-baseline)*(1-(float)Math.exp(-dt/.35f));previous=bass;last=now;
    }
    float pulse(long now){return last<0||now-last>500?0:strength*(float)Math.exp(-Math.max(0,now-hit)/180f);}
}
