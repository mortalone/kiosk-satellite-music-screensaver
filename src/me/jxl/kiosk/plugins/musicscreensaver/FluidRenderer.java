// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.graphics.*;

/** Reused low-resolution surface texture, shaded from its gradient. */
final class FluidRenderer {
    private final FluidModel model=new FluidModel(System.nanoTime());
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF dest=new RectF();
    private Bitmap bitmap;
    private float[] field;
    private int[] pixels;
    private int size;
    private int quality=384;
    void setQuality(String value){quality="Native".equals(value)?1024:"Ultra".equals(value)?640:"Eco".equals(value)?192:384;}
    private RadialGradient cavity;
    private float oldCx,oldCy,oldR;
    private int oldColor;
    void setPulse(float pulse){model.setPulse(pulse);}
    void step(float dt,float bass,float mid,float treble){model.step(dt,bass,mid,treble);}
    void draw(Canvas c,float cx,float cy,float r,boolean economy,int tint) {
        int n=Math.min(quality,Math.max(192,(int)(r*2)));
        if(n!=size){size=n;bitmap=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888);field=new float[n*n];pixels=new int[n*n];}
        // Broad light fades into the scene: no bezel, outline, or front glass wedge.
        if(cavity==null||oldCx!=cx||oldCy!=cy||oldR!=r||oldColor!=tint){
            cavity=new RadialGradient(cx-r*.10f,cy-r*.12f,r*1.18f,
                new int[]{0xFFF1F0EB,0xFFE5E4DF,(tint&0xFFFFFF)|0xB0000000,0x00000000},
                new float[]{0,.55f,.8f,1},Shader.TileMode.CLAMP);
            oldCx=cx;oldCy=cy;oldR=r;oldColor=tint;
        }
        paint.setShader(cavity);c.drawCircle(cx,cy,r*1.3f,paint);paint.setShader(null);
        float scale=2f/(n-1);
        for(int y=0;y<n;y++)for(int x=0;x<n;x++)field[y*n+x]=model.field(x*scale-1,y*scale-1);
        for(int y=1;y<n-1;y++)for(int x=1;x<n-1;x++) {
            int k=y*n+x;float f=field[k];
            float edge=Math.max(0,Math.min(1,(f-(1f-6f/n))/(12f/n)));
            if(edge<=0){pixels[k]=0;continue;}
            float gx=(field[k+1]-field[k-1])*.5f,gy=(field[k+n]-field[k-n])*.5f;
            float len=(float)Math.sqrt(gx*gx+gy*gy+.00001f);
            float nx=-gx/len,ny=-gy/len;
            // Rounded surface normals and two studio reflections, following each lobe.
            float slope=Math.min(.98f,1f/(float)Math.sqrt(Math.max(1,f)));
            float nz=(float)Math.sqrt(Math.max(0,1-slope*slope));
            float light=Math.max(0,-nx*slope*.45f-ny*slope*.55f+nz*.70f);
            float spec=light*light;spec*=spec;spec*=spec;spec*=spec;
            int shade=Math.min(235,8+(int)(18*nz+155*spec));
            pixels[k]=((int)(edge*255)<<24)|(shade<<16)|((shade+2)<<8)|(shade+5);
        }
        bitmap.setPixels(pixels,0,n,0,0,n,n);
        dest.set(cx-r,cy-r,cx+r,cy+r);c.drawBitmap(bitmap,null,dest,paint);
    }
}
