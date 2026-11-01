// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

/** Frame-rate independent, bounded motion. Decorative time is separate from audio energy. */
final class ArtMotion {
    double time, sandPhase=Math.PI*18+1.15;
    float bass, mid, high, beat;
    static float unit(float x){return Float.isFinite(x)?Math.max(0,Math.min(1,x)):0;}
    void step(float seconds,float low,float middle,float top,float pulse,boolean playing){
        double dt=Float.isFinite(seconds)?Math.max(0,Math.min(.08,seconds)):0;
        time+=dt;
        bass=follow(bass,playing?unit(low):0,dt,.45);
        mid=follow(mid,playing?unit(middle):0,dt,.65);
        high=follow(high,playing?unit(top):0,dt,.28);
        beat=follow(beat,playing?unit(pulse):0,dt,pulse>beat?.07:.40);
    }
    void stepSand(float seconds){
        double dt=Float.isFinite(seconds)?Math.max(0,Math.min(.08,seconds)):0;
        // Approximately constant pen speed even at the rosette's tight turns.
        double dr=.76*2.013*Math.cos(2.013*sandPhase);
        double r=.76*Math.sin(2.013*sandPhase);
        sandPhase+=dt*(.045+.018*bass)/Math.max(.35,Math.hypot(dr,r));
    }
    private static float follow(float current,float target,double dt,double tau){
        return current+(target-current)*(float)(1-Math.exp(-dt/tau));
    }
    double sandX(){return .76*Math.sin(2.013*sandPhase)*Math.cos(sandPhase);}
    double sandY(){return .76*Math.sin(2.013*sandPhase)*Math.sin(sandPhase);}
    static boolean supports(String effect){
        return "bloom".equals(effect)||"silk".equals(effect)||"chrome".equals(effect)
            ||"aurora".equals(effect)||"coral".equals(effect)||"sand_table".equals(effect)
            ||"prism_party".equals(effect);
    }
}
