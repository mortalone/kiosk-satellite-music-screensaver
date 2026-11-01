// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.graphics.*;

/** Cached optical material with animated surface geometry and transmitted light. */
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
    private int meshSize=36;
    private float[] meshVertices=new float[37*37*2];
    private int[] meshColors=new int[37*37];
    private final float[] lightHsv=new float[3];
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
        int grid="Eco".equals(quality)?20:"Ultra".equals(quality)?48:"Native".equals(quality)?64:36;
        if(meshSize!=grid){meshSize=grid;meshVertices=new float[(grid+1)*(grid+1)*2];meshColors=new int[(grid+1)*(grid+1)];}
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
                    for(int x=0;x<n;x++)pixels[y*n+x]=VinylMaterial.glassPixel(design,
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
        if(material!=null){
            float albumHue=0;Color.colorToHSV(palette,lightHsv);albumHue=lightHsv[0];
            for(int j=0;j<=meshSize;j++)for(int i=0;i<=meshSize;i++){
                double x=i*2.0/meshSize-1,y=j*2.0/meshSize-1;
                double rad=Math.hypot(x,y),angle=Math.atan2(y,x);
                double envelope=Math.max(0,1-rad)*Math.min(1,rad*4);
                double twist=envelope*(.06*Math.sin(time*.35+rad*7)+.035*Math.sin(time*.21+y*6)+kick*.025);
                double a=angle+twist;
                double ripple=envelope*.016*Math.sin(rad*18-time*.8+angle*2)*(1+energy);
                int n=j*(meshSize+1)+i;
                meshVertices[n*2]=cx+r*(float)((rad+ripple)*Math.cos(a));
                meshVertices[n*2+1]=cy+r*(float)((rad+ripple)*Math.sin(a));
                double sweep=.5+.5*Math.sin(x*3.5+y*2-time*.55);
                double caustic=Math.pow(.5+.5*Math.sin(rad*19+angle*3-time*1.1),5);
                double local=Math.exp(-((x-.45*Math.cos(time*.29))*(x-.45*Math.cos(time*.29))
                        +(y-.40*Math.sin(time*.23))*(y-.40*Math.sin(time*.23)))*4);
                lightHsv[0]=(float)(("Album".equals(mode)?albumHue:time*(music?17:8)+190)+sweep*58)%360;
                lightHsv[1]=(float)(.60-.40*local-.16*caustic);
                lightHsv[2]=(float)Math.min(1,.66+.20*sweep+.12*caustic+.12*local+.14*kick);
                meshColors[n]=Color.HSVToColor(lightHsv);
            }
            // Pigment, chip bevels and coloured sectors all receive the moving illumination.
            c.drawBitmapMesh(material,meshSize,meshSize,meshVertices,0,meshColors,0,paint);
        }
        paint.setShader(sheen.at(cx,cy,r));c.drawCircle(cx,cy,r,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.6f);paint.setColor(0x0CFFFFFF);
        for(int i=0;i<grooveCount;i++)c.drawCircle(cx,cy,r*(.27f+i*.704f/Math.max(1,grooveCount-1)),paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF161820);float label=r*.24f;c.drawCircle(cx,cy,label,paint);
        if(cover!=null){c.save();clip.reset();clip.addCircle(cx,cy,label,Path.Direction.CW);c.clipPath(clip);rect.set(cx-label,cy-label,cx+label,cy+label);paint.setColor(Color.WHITE);c.drawBitmap(cover,null,rect,paint);c.restore();}
        paint.setColor(0xFF101318);c.drawCircle(cx,cy,r*.018f,paint);c.restore();
    }
}
