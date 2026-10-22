// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import java.util.Random;

/** Bounded viscous masses, rendered as an implicit surface. Not a physical ferrohydrodynamics solver. */
final class FluidModel {
    static final int COUNT = 9;
    final float[] x = new float[COUNT], y = new float[COUNT];
    final float[] vx = new float[COUNT], vy = new float[COUNT], radius = new float[COUNT];
    private final Random random;
    private float magnetX, magnetY, targetX, targetY, untilChange, previousBass, cooldown;
    FluidModel(long seed) {
        random = new Random(seed);
        for (int i=0;i<COUNT;i++) {
            x[i]=(random.nextFloat()-.5f)*.22f; y[i]=(random.nextFloat()-.5f)*.22f;
            radius[i]=i<4?.115f:.045f + .008f*(i%3);
        }
    }
    void step(float dt, float bass, float mid, float treble) {
        dt=Math.max(0,Math.min(.05f,dt));
        bass=finite(bass);mid=finite(mid);treble=finite(treble);
        untilChange-=dt;cooldown-=dt;
        float onset=Math.max(0,bass-previousBass);previousBass=bass;
        if(untilChange<=0 || (onset>.075f && cooldown<=0)) {
            targetX=(random.nextFloat()-.5f)*.75f;
            targetY=(random.nextFloat()-.5f)*.65f;
            untilChange=.7f+random.nextFloat()*2.3f;
            if(onset>.075f && cooldown<=0) {
                // A different mass receives the impulse; never a radial ring or fixed orbit.
                int i=random.nextInt(COUNT);
                vx[i]+=(random.nextFloat()-.5f)*Math.min(3.4f,onset*16);
                vy[i]-=Math.min(2.8f,onset*12);
                cooldown=.18f;
            }
        }
        magnetX+=(targetX-magnetX)*dt*1.8f;magnetY+=(targetY-magnetY)*dt*1.8f;
        float energy=Math.min(1,bass*.65f+mid*.35f);
        for(int i=0;i<COUNT;i++) {
            float fx=-x[i]*2.8f+(magnetX-x[i])*(1.2f+energy*5f);
            float fy=-y[i]*2.8f+(magnetY-y[i])*(1.2f+energy*5f)+.28f*(1-energy);
            for(int j=0;j<COUNT;j++) if(i!=j) {
                float dx=x[j]-x[i],dy=y[j]-y[i],d=(float)Math.sqrt(dx*dx+dy*dy+.0001f);
                float spacing=(radius[i]+radius[j])*(.55f+energy*.7f);
                float force=(d-spacing)*(d<.32f?1.8f:.12f);
                fx+=dx/d*force;fy+=dy/d*force;
            }
            // Small uncorrelated agitation, damped by viscosity; silence settles.
            fx+=(random.nextFloat()-.5f)*treble*3;
            fy+=(random.nextFloat()-.5f)*treble*3;
            float damping=(float)Math.exp(-dt*3.6);
            vx[i]=(vx[i]+fx*dt)*damping;vy[i]=(vy[i]+fy*dt)*damping;
            x[i]+=vx[i]*dt;y[i]+=vy[i]*dt;
            float d=(float)Math.sqrt(x[i]*x[i]+y[i]*y[i]);
            float bound=.79f-radius[i];
            if(d>bound){x[i]*=bound/d;y[i]*=bound/d;float dot=(vx[i]*x[i]+vy[i]*y[i])/(bound*bound);if(dot>0){vx[i]-=1.4f*dot*x[i];vy[i]-=1.4f*dot*y[i];}}
        }
    }
    private float finite(float v){return Float.isNaN(v)||Float.isInfinite(v)?0:Math.max(0,Math.min(1.5f,v));}
    float field(float px,float py) {
        float f=0;
        for(int i=0;i<COUNT;i++){float dx=px-x[i],dy=py-y[i];f+=radius[i]*radius[i]/(dx*dx+dy*dy+.0007f);}
        return f;
    }
}
