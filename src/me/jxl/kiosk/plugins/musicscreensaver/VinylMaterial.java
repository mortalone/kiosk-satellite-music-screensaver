// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

/** Static pressing pigment, sampled in disc coordinates (independent of display resolution). */
final class VinylMaterial {
    static double hash(int x,int y) {
        int h=x*374761393+y*668265263; h=(h^(h>>>13))*1274126177;
        return ((h^(h>>>16))&0x7fffffff)/2147483647.0;
    }
    static double noise(double x,double y) {
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y); double fx=x-ix,fy=y-iy;
        fx=fx*fx*(3-2*fx); fy=fy*fy*(3-2*fy);
        return (hash(ix,iy)*(1-fx)+hash(ix+1,iy)*fx)*(1-fy)
                +(hash(ix,iy+1)*(1-fx)+hash(ix+1,iy+1)*fx)*fy;
    }
    static double clamp(double v) { return Math.max(0,Math.min(1,v)); }
    static double smooth(double low,double high,double v) {
        v=clamp((v-low)/(high-low)); return v*v*(3-2*v);
    }
    static int pixel(String pattern,double x,double y) {
        double r=Math.hypot(x,y); if(r>1)return 0;
        double a=Math.atan2(y,x);
        double warp=noise(x*5+13,y*5+7);
        double grain=noise(x*110,y*110);
        double islands=noise(x*27+warp*3,y*27-warp*3);
        double streak=noise(Math.cos(a)*78+3,Math.sin(a)*78+7);
        double density=smooth(.38,.62,islands*.85+grain*.15);
        int tint=0x101323;
        if("Backlit Mosaic".equals(pattern)) {
            // Distorted cellular chips; dark borders, cyan glass and amber inclusions.
            double u=x*22+warp*1.2,v=y*22+noise(x*7,y*7);
            int ix=(int)Math.floor(u),iy=(int)Math.floor(v);
            double first=100,second=100,seed=0;
            for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++) {
                double dx=ix+i+.18+.64*hash(ix+i,iy+j)-u;
                double dy=iy+j+.18+.64*hash(iy+j+83,ix+i+19)-v;
                double d=dx*dx+dy*dy;
                if(d<first){second=first;first=d;seed=hash(ix+i+147,iy+j+91);}
                else if(d<second)second=d;
            }
            double border=1-smooth(.025,.14,Math.sqrt(second)-Math.sqrt(first));
            density=.30+.65*border;
            tint=seed>.58?0xC6AF18:seed>.22?0x288C9F:0x112A39;
        } else if("Backlit Marble".equals(pattern)) {
            double veins=Math.sin(x*15+warp*12+noise(x*13,y*13)*2);
            density=.12+.84*smooth(-.2,.72,veins);
            tint=0x302442;
        } else if("Backlit Sunburst".equals(pattern)) {
            // Irregular radial streaks with varying lengths, not evenly spaced spokes.
            double ray=noise(Math.cos(a)*43,Math.sin(a)*43);
            density=smooth(.32,.63,ray+noise(x*16,y*16)*.19);
            density*=.28+.72*smooth(.26,.46+ray*.45,r);
            tint=0x132C32;
        } else if("Backlit Colour Sectors".equals(pattern)) {
            double sector=Math.sin(a*2+warp*.65);
            density=.25+.5*smooth(.1,.7,sector);
            density=Math.max(density,smooth(.54,.67,islands)*.85);
            tint=sector>0?0x702B59:0x247566;
        } else if("Backlit Translucent".equals(pattern)) {
            density=.08+.15*warp+.05*grain;
            tint=0x695779;
        }
        // Smoky radial material near the rim. Keep it translucent instead of a hard black ring.
        density=clamp(density+(streak-.35)*.32*smooth(.72,.99,r));
        int alpha=(int)(18+226*density);
        return (alpha<<24)|tint;
    }
}
