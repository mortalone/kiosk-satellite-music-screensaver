// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

/** Frame-rate independent, bounded motion. Decorative time is separate from audio energy. */
final class ArtMotion {
    double time, sandPhase=Math.PI*6+1.15;
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
        // Limit the fastest of the four pens, including the tight turns.
        double derivative=.65;
        for(int ball=0;ball<4;ball++){
            double dx=(tableX(sandPhase+.001,ball)-tableX(sandPhase,ball))/.001;
            double dy=(tableY(sandPhase+.001,ball)-tableY(sandPhase,ball))/.001;
            derivative=Math.max(derivative,Math.hypot(dx,dy));
        }
        sandPhase+=dt*(.055+.018*bass)/derivative;
    }
    private static float follow(float current,float target,double dt,double tau){
        return current+(target-current)*(float)(1-Math.exp(-dt/tau));
    }
    static double tableX(double phase,int ball){
        double p=phase*(1+ball*.013)+ball*1.7,a=ball*Math.PI*.5;
        double x=.46*Math.cos(p)+.29*Math.cos(9.02*p)+.055*Math.cos(21.03*p);
        double y=.46*Math.sin(p)-.29*Math.sin(9.02*p)+.055*Math.sin(21.03*p);
        return x*Math.cos(a)-y*Math.sin(a);
    }
    static double tableY(double phase,int ball){
        double p=phase*(1+ball*.013)+ball*1.7,a=ball*Math.PI*.5;
        double x=.46*Math.cos(p)+.29*Math.cos(9.02*p)+.055*Math.cos(21.03*p);
        double y=.46*Math.sin(p)-.29*Math.sin(9.02*p)+.055*Math.sin(21.03*p);
        return x*Math.sin(a)+y*Math.cos(a);
    }
    double sandX(){return sandX(0);}
    double sandY(){return sandY(0);}
    double sandX(int ball){return tableX(sandPhase,ball);}
    double sandY(int ball){return tableY(sandPhase,ball);}
    static boolean supports(String effect){
        return "bloom".equals(effect)||"silk".equals(effect)||"chrome".equals(effect)
            ||"aurora".equals(effect)||"coral".equals(effect)||"sand_table".equals(effect)
            ||"prism_party".equals(effect);
    }
}
