// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.graphics.*;

/** Procedural translucent pressing: stationary pigment, moving light underneath. */
final class BacklitVinylRenderer {
    String mode="Music";
    private final Paint paint=new Paint(3);
    private final Path clip=new Path();
    private final RectF rect=new RectF();
    private final Matrix matrix=new Matrix();
    private Bitmap pigment;
    private SweepGradient light;
    private final float[] hsv={0,.78f,1};
    private int lastPalette;
    private float oldX,oldY;
    private static double hash(int x,int y){int h=x*374761393+y*668265263;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0x7fffffff)/2147483647.0;}
    private static double noise(double x,double y){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);double fx=x-ix,fy=y-iy;
        fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        double top=hash(ix,iy)*(1-fx)+hash(ix+1,iy)*fx;
        double bottom=hash(ix,iy+1)*(1-fx)+hash(ix+1,iy+1)*fx;
        return top*(1-fy)+bottom*fy;
    }
    private void texture(){
        int n=640;int[] pixels=new int[n*n];
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            double px=(x-n*.5)/(n*.5),py=(y-n*.5)/(n*.5),r=Math.hypot(px,py),a=Math.atan2(py,px);
            if(r>1)continue;
            double warp=noise(px*5+10,py*5+10)*2;
            double veins=noise(px*13+warp,py*13-warp);
            double flecks=.65*noise(px*87+warp*4,py*87-warp*4)+.35*noise(px*169,py*169);
            double density=(flecks-.47)*9+(veins-.5)*1.8;
            int alpha=(int)Math.max(8,Math.min(245,120+density*190));
            if(r>.95)alpha=Math.max(alpha,(int)(150+(r-.95)*1500));
            pixels[y*n+x]=(alpha<<24)|0x101321;
        }
        pigment=Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888);
    }
    void draw(Canvas c,float cx,float cy,float r,Bitmap cover,int palette,double time,float bass){
        if(pigment==null)texture();
        if(light==null||lastPalette!=palette||oldX!=cx||oldY!=cy){
            Color.colorToHSV(palette,hsv);float hue=hsv[0];
            int[] colors=new int[7];
            for(int i=0;i<6;i++){hsv[0]=(hue+i*60)%360;hsv[1]=.72f;hsv[2]=1;colors[i]=Color.HSVToColor(hsv);}colors[6]=colors[0];
            light=new SweepGradient(cx,cy,colors,null);lastPalette=palette;oldX=cx;oldY=cy;
        }
        float intensity="Music".equals(mode)?.55f+Math.min(.45f,bass*.5f):.85f;
        c.save();
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setAlpha(255);
        paint.setShader(new RadialGradient(cx,cy,r*1.18f,new int[]{0x004D8CA8,0x4056A8CE,0},new float[]{0,.8f,1},Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,r*1.18f,paint);
        matrix.setRotate((float)(time*("Album".equals(mode)?2:9)),cx,cy);light.setLocalMatrix(matrix);
        paint.setShader(light);paint.setAlpha((int)(255*intensity));c.drawCircle(cx,cy,r,paint);
        paint.setShader(null);paint.setAlpha(255);
        if("Album".equals(mode)){paint.setColor((palette&0xffffff)|0x88000000);c.drawCircle(cx,cy,r,paint);paint.setColor(Color.WHITE);}
        rect.set(cx-r,cy-r,cx+r,cy+r);c.drawBitmap(pigment,null,rect,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.65f);paint.setColor(0x22FFFFFF);
        for(int i=0;i<65;i++)c.drawCircle(cx,cy,r*(.27f+i*.011f),paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF161820);float label=r*.24f;c.drawCircle(cx,cy,label,paint);
        if(cover!=null){c.save();clip.reset();clip.addCircle(cx,cy,label,Path.Direction.CW);c.clipPath(clip);rect.set(cx-label,cy-label,cx+label,cy+label);paint.setColor(Color.WHITE);c.drawBitmap(cover,null,rect,paint);c.restore();}
        paint.setColor(0xFF101318);c.drawCircle(cx,cy,r*.018f,paint);
        c.restore();
    }
}
