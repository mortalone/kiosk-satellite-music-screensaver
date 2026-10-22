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
    private void texture(){
        int n=640;int[] pixels=new int[n*n];
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            double px=(x-n*.5)/(n*.5),py=(y-n*.5)/(n*.5),r=Math.hypot(px,py),a=Math.atan2(py,px);
            if(r>1)continue;
            double warp=Math.sin(px*9+Math.sin(py*11))*1.8+Math.sin(py*7-px*5);
            double veins=Math.sin(a*67+warp+r*22)+.6*Math.sin(a*113-r*47+warp*2);
            double flecks=Math.sin(px*155+warp*3)*Math.sin(py*137-warp*4)+.5*Math.sin(px*293+py*181);
            double density=.5+.22*veins+.32*flecks;
            int alpha=(int)Math.max(15,Math.min(245,128+density*95));
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
