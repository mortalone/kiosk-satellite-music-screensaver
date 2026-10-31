// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.graphics.*;

/** Stationary translucent pigment over cached, annular backlighting. */
final class BacklitVinylRenderer {
    String mode="Music";
    private String pattern="Backlit Splatter";
    private final Paint paint=new Paint(3);
    private final Path clip=new Path();
    private final RectF rect=new RectF();
    private final RadialLight ring=new RadialLight(
            new int[]{0xFF10121D,0xFF423B58,0xFFFFFFFF,0xFFF7F4FF,0xFF8C8B9C,0xFF303440,0xFF151924},
            new float[]{0,.22f,.34f,.48f,.72f,.94f,1});
    private final RadialLight halo=new RadialLight(
            new int[]{0,0x306B8DA2,0},new float[]{0,.81f,1});
    private final RadialLight spot=new RadialLight(
            new int[]{0xA0FFFFFF,0x30E4F4FF,0},new float[]{0,.38f,1});
    private final RadialLight sheen=new RadialLight(
            new int[]{0,0x03FFFFFF,0x30FFFFFF,0},new float[]{0,.90f,.985f,1});
    private final PorterDuffColorFilter[] colors=new PorterDuffColorFilter[180];
    private PorterDuffColorFilter albumFilter;
    private int albumColor;
    // One worker at a time. Stale results are discarded when settings change.
    private volatile Bitmap pigment;
    private volatile boolean building;
    private volatile int revision;
    private int requestedRevision=-1;
    private int textureSize=640,grooveCount=65;
    BacklitVinylRenderer() {
        float[] hsv={0,.65f,1};
        for(int i=0;i<colors.length;i++) {
            hsv[0]=i*2; colors[i]=new PorterDuffColorFilter(Color.HSVToColor(hsv),PorterDuff.Mode.MULTIPLY);
        }
    }
    void setPattern(String value) {
        if(value!=null&&!pattern.equals(value)){pattern=value;revision++;}
    }
    void setQuality(String quality) {
        int size="Eco".equals(quality)?256:"Ultra".equals(quality)?1024:"Native".equals(quality)?1536:640;
        grooveCount="Eco".equals(quality)?24:"Ultra".equals(quality)?96:"Native".equals(quality)?128:65;
        if(size!=textureSize){textureSize=size;revision++;}
    }
    boolean isPreparing(){return building||requestedRevision!=revision;}
    private void prepareTexture() {
        if(building||requestedRevision==revision)return;
        final int generation=revision,n=textureSize;
        final String design=pattern;
        requestedRevision=generation;building=true;
        Thread worker=new Thread(new Runnable(){public void run(){
            try {
                int[] pixels=new int[n*n];
                for(int y=0;y<n;y++) {
                    if(generation!=revision)return;
                    for(int x=0;x<n;x++)pixels[y*n+x]=VinylMaterial.pixel(design,
                            (x+.5-n*.5)/(n*.5),(y+.5-n*.5)/(n*.5));
                }
                Bitmap next=Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888);
                if(generation==revision)pigment=next;
                // Previous bitmap is left to GC: the render thread may still reference it.
            } finally {building=false;}
        }},"vinyl-material");
        worker.setPriority(Thread.MIN_PRIORITY);worker.start();
    }
    void draw(Canvas c,float cx,float cy,float r,Bitmap cover,int palette,double time,float bass,float pulse,float treble){
        prepareTexture();
        boolean music="Music".equals(mode);
        float kick=music?Math.min(1,Math.max(0,pulse)):0;
        float energy=music?Math.min(1,Math.max(0,bass)):0;
        // Coherent colour moods. The pressing and its centre label never rotate.
        int colorIndex=(int)((time*(music?22:9)+195)%360+360)%360/2;
        PorterDuffColorFilter filter=colors[colorIndex];
        if("Album".equals(mode)) {
            if(albumFilter==null||albumColor!=palette){
                albumColor=palette;float[] hsv=new float[3];Color.colorToHSV(palette,hsv);
                hsv[1]=Math.min(.65f,hsv[1]);hsv[2]=1;
                albumFilter=new PorterDuffColorFilter(Color.HSVToColor(hsv),PorterDuff.Mode.MULTIPLY);
            }
            filter=albumFilter;
        }
        c.save();paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setColorFilter(filter);
        paint.setAlpha((int)(150+105*kick));paint.setShader(halo.at(cx,cy,r*(1.12f+.07f*kick)));
        c.drawCircle(cx,cy,r*(1.12f+.07f*kick),paint);
        clip.reset();clip.addCircle(cx,cy,r,Path.Direction.CW);c.clipPath(clip);
        paint.setAlpha(255);paint.setShader(null);paint.setColor(0xFF10121C);c.drawCircle(cx,cy,r,paint);
        paint.setColor(Color.WHITE);paint.setAlpha((int)(music?175+45*energy+35*kick:235));
        paint.setShader(ring.at(cx,cy,r*(1+.055f*kick)));c.drawCircle(cx,cy,r,paint);
        // Broad local light underneath the pigment, not sparkling particles above it.
        float sx=cx+r*.27f*(float)Math.cos(time*.13),sy=cy+r*.22f*(float)Math.sin(time*.17);
        paint.setColorFilter(null);paint.setAlpha((int)(music?40+105*kick+45*Math.min(1,Math.max(0,treble)):80));
        paint.setShader(spot.at(sx,sy,r*.62f));c.drawCircle(sx,sy,r*.62f,paint);
        paint.setShader(null);paint.setAlpha(255);
        Bitmap material=pigment;
        if(material!=null){rect.set(cx-r,cy-r,cx+r,cy+r);c.drawBitmap(material,null,rect,paint);}
        paint.setShader(sheen.at(cx,cy,r));c.drawCircle(cx,cy,r,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.6f);paint.setColor(0x0CFFFFFF);
        for(int i=0;i<grooveCount;i++)c.drawCircle(cx,cy,r*(.27f+i*.704f/Math.max(1,grooveCount-1)),paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF161820);float label=r*.24f;c.drawCircle(cx,cy,label,paint);
        if(cover!=null){c.save();clip.reset();clip.addCircle(cx,cy,label,Path.Direction.CW);c.clipPath(clip);rect.set(cx-label,cy-label,cx+label,cy+label);paint.setColor(Color.WHITE);c.drawBitmap(cover,null,rect,paint);c.restore();}
        paint.setColor(0xFF101318);c.drawCircle(cx,cy,r*.018f,paint);c.restore();
    }
}
