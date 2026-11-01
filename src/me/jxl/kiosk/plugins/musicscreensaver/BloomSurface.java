// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

/** Continuous three-dimensional ruffled petal; sampled independently of mesh resolution. */
final class BloomSurface {
    static void point(double u,double v,int layer,int petal,int count,double time,double energy,double beat,float[] out){
        double theta=petal*Math.PI*2/count+layer*.61+.035*Math.sin(time*.13+layer);
        double open=.5+.5*Math.sin(time*.24+layer*.32);
        double length=(.92-layer*.235)*( .88+.12*open+.04*energy+.035*beat);
        double envelope=Math.pow(Math.max(0,Math.sin(Math.PI*u*.76)),.65);
        double width=(.54-layer*.09)*envelope*(.68+.32*u);
        double coarse=Math.sin(v*8+u*3+petal*1.27-time*.29);
        double ribs=Math.sin(v*43+u*2+petal*.7+time*.16);
        double curl=(.13+.09*(1-open))*u*u*(.6+coarse);
        double fringe=Math.pow(u,3)*(.026*Math.sin(v*55+time*.22)+.024*coarse);
        double radial=.035+length*u+fringe-.13*v*v*u*u;
        double lateral=v*width*(1+.06*coarse);
        double z=.21*Math.sin(u*Math.PI)+curl+.022*ribs*envelope*(.3+.7*u)
                +.10*v*v*envelope+.045*Math.sin(time*.31+petal)*u*u;
        double ca=Math.cos(theta),sa=Math.sin(theta);
        out[0]=(float)(ca*radial-sa*lateral);
        out[1]=(float)(sa*radial+ca*lateral);
        out[2]=(float)(z+layer*.035);
    }
}
