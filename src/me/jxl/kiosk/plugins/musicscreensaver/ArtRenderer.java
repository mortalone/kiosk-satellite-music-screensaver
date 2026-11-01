// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.graphics.*;

/** Procedural wall art. All geometry is live; no video, network assets or per-frame bitmaps. */
final class ArtRenderer {
    private final ArtMotion motion=new ArtMotion();
    private final Paint paint=new Paint(3);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private final float[] hsv=new float[3];
    private final RadialLight aura=new RadialLight(new int[]{0x183F817E,0x08274556,0},new float[]{0,.5f,1});
    private final RadialLight silver=new RadialLight(new int[]{0xFFFFFFFF,0xFFD1D6D7,0xFF555F69,0xFF111924},new float[]{0,.15f,.55f,1});
    private int detail=1;
    // Reused grid and indexed triangle arrays: no geometry allocations during animation.
    private final float[] vertices=new float[8192];
    private final int[] colors=new int[4096];
    private final short[] indices=new short[24000];
    private int gridU=-1,gridV=-1,indexCount;
    private Bitmap sand;
    private Canvas sandCanvas;
    private int sandSize;
    private double sandAge,lastSandTime=-1;
    private float penX,penY;
    private String lastEffect="";
    void setQuality(String quality){
        int next="Eco".equals(quality)?0:"Ultra".equals(quality)?2:"Native".equals(quality)?3:1;
        if(next!=detail){detail=next;sand=null;sandCanvas=null;}
    }
    void draw(Canvas c,String effect,float w,float h,float dt,float bass,float mid,float high,float pulse,boolean playing){
        motion.step(dt,bass,mid,high,pulse,playing);
        boolean portrait=h>=w;
        float cx=w*.5f,cy=h*(portrait?.35f:.39f);
        float radius=Math.min(w*.44f,h*(portrait?.255f:.33f));
        c.save();c.translate(cx,cy);c.scale(radius,radius);
        // Restrict art to its own stage: it cannot cover labels, controls or lyric panels.
        c.clipRect(-1.12f,-1.12f,1.12f,1.12f);
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
        paint.setShader(aura.at(0,0,1.1f));c.drawCircle(0,0,1.1f,paint);paint.setShader(null);
        if(!lastEffect.equals(effect)){lastEffect=effect;lastSandTime=motion.time;}
        if("bloom".equals(effect))bloom(c);
        else if("silk".equals(effect))silk(c,false);
        else if("aurora".equals(effect))silk(c,true);
        else if("chrome".equals(effect))chrome(c);
        else if("coral".equals(effect))coral(c);
        else if("sand_table".equals(effect))sand(c,dt);
        else if("prism_party".equals(effect))party(c);
        c.restore();
    }
    private void grid(int u,int v){
        if(gridU==u&&gridV==v)return;
        gridU=u;gridV=v;indexCount=0;
        for(int j=0;j<v;j++)for(int i=0;i<u;i++){
            int p=j*(u+1)+i;
            indices[indexCount++]=(short)p;indices[indexCount++]=(short)(p+1);indices[indexCount++]=(short)(p+u+1);
            indices[indexCount++]=(short)(p+1);indices[indexCount++]=(short)(p+u+2);indices[indexCount++]=(short)(p+u+1);
        }
    }
    private int color(double hue,double saturation,double value,int alpha){
        hsv[0]=(float)((hue%360+360)%360);hsv[1]=(float)Math.max(0,Math.min(1,saturation));hsv[2]=(float)Math.max(0,Math.min(1,value));
        return Color.HSVToColor(Math.max(0,Math.min(255,alpha)),hsv);
    }
    private void mesh(Canvas c){
        paint.setColor(Color.WHITE);paint.setAlpha(255);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        if(android.os.Build.VERSION.SDK_INT<29 && c.isHardwareAccelerated()){
            for(int n=0;n<indexCount;n+=3){
                int a=indices[n],b=indices[n+1],d=indices[n+2];
                path.reset();path.moveTo(vertices[a*2],vertices[a*2+1]);
                path.lineTo(vertices[b*2],vertices[b*2+1]);path.lineTo(vertices[d*2],vertices[d*2+1]);path.close();
                paint.setColor(colors[b]);c.drawPath(path,paint);
            }
            return;
        }
        c.drawVertices(Canvas.VertexMode.TRIANGLES,(gridU+1)*(gridV+1)*2,vertices,0,null,0,colors,0,indices,0,indexCount,paint);
    }
    private void bloom(Canvas c){
        int u=12+detail*6,v=8+detail*3;grid(u,v);
        double time=motion.time;
        // Outer petals first, then nested whorls; camera remains still.
        for(int layer=0;layer<3;layer++){
            int petals=layer==0?9:layer==1?7:6;
            double length=(.96-layer*.235)*(1+.035*motion.bass+.035*motion.beat);
            for(int p=0;p<petals;p++){
                double angle=p*Math.PI*2/petals+layer*.49+.035*Math.sin(time*.09+layer);
                double ca=Math.cos(angle),sa=Math.sin(angle);
                for(int j=0;j<=v;j++)for(int i=0;i<=u;i++){
                    double t=i/(double)u,s=j/(double)v*2-1;
                    double width=Math.pow(Math.sin(Math.PI*t),.72)*(.34-layer*.035);
                    double fold=Math.sin(s*13+t*6+time*.21+p)*(.015+.035*t);
                    double curl=.18*t*t*Math.sin(time*.14+p*1.7)+.08*s*s*t;
                    double radial=.055+length*t+fold;
                    double lateral=s*width*(.8+.2*t);
                    double z=.10*Math.sin(t*Math.PI)+curl+fold*(1+.35*motion.mid);
                    int k=j*(u+1)+i;
                    vertices[k*2]=(float)(ca*radial-sa*lateral);
                    vertices[k*2+1]=(float)((sa*radial+ca*lateral)*.88-z);
                    double highlight=.72+.19*Math.cos(s*13+t*6+time*.21+p)+.09*Math.sin(angle);
                    double hue=318+58*Math.sin(angle*.7+layer*.6)+30*t+8*Math.sin(time*.035);
                    colors[k]=color(hue,.38+.23*t,highlight,255);
                }
                mesh(c);
            }
        }
        // Seed-like luminous core, not a flat centre disc.
        int seeds=detail==0?55:110;
        for(int n=0;n<seeds;n++){
            double a=n*2.399963,r=.105*Math.sqrt(n/(double)seeds);
            paint.setColor(color(275+n*.4,.32,.55+.35*(1-r/.105),235));
            c.drawCircle((float)(Math.cos(a)*r),(float)(Math.sin(a)*r-.015),.009f,paint);
        }
    }
    private void silk(Canvas c,boolean northern){
        int u=30+detail*14,v=northern?8:10+detail*2;grid(u,v);
        double time=motion.time;
        int ribbons=northern?4:3;
        for(int band=0;band<ribbons;band++){
            for(int j=0;j<=v;j++)for(int i=0;i<=u;i++){
                double t=i/(double)u,s=j/(double)v;
                double x=t*2-1;
                double wave=Math.sin(t*6.1+time*.15+band*.95);
                double centre=(band-1)*.20+wave*(.18+.05*motion.mid);
                double width=northern?.48:.14+.12*Math.sin(t*5+time*.13+band);
                double fold=Math.sin(t*24+time*.16+band)*.022;
                int k=j*(u+1)+i;
                vertices[k*2]=(float)x;
                vertices[k*2+1]=(float)(centre+(s-.5)*width+fold*s);
                double edge=Math.pow(Math.sin(Math.PI*s),northern?1.6:.4);
                double light=.58+.35*Math.cos(s*5+t*11+time*.2+band);
                colors[k]=color(northern?155+band*28+t*38:205+band*52+t*32,
                        northern?.52:.35,light+.05*motion.high,(int)((northern?125:230)*edge*Math.pow(Math.sin(Math.PI*t),.35)));
            }
            mesh(c);
        }
        if(northern){
            for(int i=0;i<38;i++){
                float x=(float)(VinylMaterial.hash(i,78)*2-1),y=(float)(VinylMaterial.hash(i,39)*1.7-.9);
                paint.setColor(0x6098BABE);c.drawCircle(x,y,.0018f,paint);
            }
        }
    }
    private void chrome(Canvas c){
        int u=40+detail*16,v=20+detail*8;grid(u,v);
        double time=motion.time;
        // Visible hemisphere with analytical environment reflections and an organic silhouette.
        for(int j=0;j<=v;j++)for(int i=0;i<=u;i++){
            double angle=i/(double)u*Math.PI*2,r=j/(double)v;
            double warp=1+.075*Math.sin(angle*3+time*.17)+.045*Math.sin(angle*5-time*.11);
            warp+=.04*motion.beat*Math.cos(angle*4+time*.08);
            double x=Math.cos(angle)*r,y=Math.sin(angle)*r,z=Math.sqrt(Math.max(0,1-r*r));
            int k=j*(u+1)+i;
            vertices[k*2]=(float)(x*warp*.79);vertices[k*2+1]=(float)(y*warp*.83);
            double reflection=y*.7+z*.65+.10*Math.sin(x*7+time*.13)+.06*motion.mid*Math.sin(angle*6);
            double strip=Math.exp(-Math.pow((reflection-.50)/.065,2));
            double rim=Math.pow(1-z,4);
            double secondStrip=Math.exp(-Math.pow((reflection+.16)/.11,2));
            double luminance=.07+.24*z+.70*strip+.34*rim+.42*secondStrip;
            colors[k]=color(205+12*Math.sin(angle),.06+.17*(1-strip),luminance,255);
        }
        mesh(c);
    }
    private void coral(Canvas c){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        int branches=detail==0?5:7;
        for(int i=0;i<branches;i++)branch(c,0,.55f,(float)(-Math.PI/2+(i-(branches-1)*.5)*.27),.42f,detail==0?4:5,i*.7);
        paint.setStyle(Paint.Style.FILL);
    }
    private void branch(Canvas c,float x,float y,float a,float length,int depth,double seed){
        double sway=.07*Math.sin(motion.time*.14+seed)+.035*motion.mid*Math.sin(seed*3);
        float endX=x+(float)Math.cos(a+sway)*length,endY=y+(float)Math.sin(a+sway)*length;
        path.reset();path.moveTo(x,y);
        path.cubicTo(x+(endX-x)*.20f-.025f,y+(endY-y)*.35f,endX+.02f,endY+length*.25f,endX,endY);
        paint.setStrokeWidth(.0035f+depth*.0022f);
        double travel=.5+.5*Math.sin(seed*2-depth*.8-motion.time*.4);
        paint.setColor(color(167+seed*7,.42,.40+.32*travel+.10*motion.beat,225));c.drawPath(path,paint);
        if(depth>0){
            branch(c,endX,endY,a-.37f,length*.69f,depth-1,seed+1.17);
            branch(c,endX,endY,a+.40f,length*.67f,depth-1,seed+2.73);
        }else{
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xA9ADDCCC);c.drawCircle(endX,endY,.006f+.002f*motion.high,paint);paint.setStyle(Paint.Style.STROKE);
        }
    }
    private void sand(Canvas c,float dt){
        motion.stepSand(dt);
        if(sand==null){
            sandSize=detail==0?320:detail==1?512:detail==2?768:1024;
            int[] pixels=new int[sandSize*sandSize];
            for(int y=0;y<sandSize;y++)for(int x=0;x<sandSize;x++){
                int grain=(int)(VinylMaterial.hash(x,y)*14);
                pixels[y*sandSize+x]=Color.rgb(172+grain,155+grain,124+grain);
            }
            sand=Bitmap.createBitmap(sandSize,sandSize,Bitmap.Config.ARGB_8888);
            sand.setPixels(pixels,0,sandSize,0,0,sandSize,sandSize);sandCanvas=new Canvas(sand);
            // An initial, mathematically drawn rosette makes the table composed from first use.
            // The ball then continues this same continuous path, not a prerecorded animation.
            float previousX=0,previousY=0;
            for(int i=0;i<=2400;i++){
                double phase=motion.sandPhase*i/2400.0;
                float px=(float)(.76*Math.sin(2.013*phase)*Math.cos(phase));
                float py=(float)(.76*Math.sin(2.013*phase)*Math.sin(phase));
                if(i>0)groove(previousX,previousY,px,py);
                previousX=px;previousY=py;
            }
            penX=(float)motion.sandX();penY=(float)motion.sandY();sandAge=0;lastSandTime=motion.time;
        }
        sandAge+=Math.min(.08,Math.max(0,dt));
        float x=(float)motion.sandX(),y=(float)motion.sandY();
        // Update only 15 times/s, retaining physical grooves between frames.
        if(motion.time-lastSandTime>=1.0/15){
            if(sandAge>1200){ // Gradually rake the old drawing away; never a sudden reset.
                paint.setColor(0x04B6A384);sandCanvas.drawRect(0,0,sandSize,sandSize,paint);
                if(sandAge>1220)sandAge=0;
            }
            groove(penX,penY,x,y);
            penX=x;penY=y;lastSandTime=motion.time;
        }
        c.save();path.reset();path.addCircle(0,0,.94f,Path.Direction.CW);c.clipPath(path);
        paint.setColor(Color.WHITE);rect.set(-1,-1,1,1);c.drawBitmap(sand,null,rect,paint);c.restore();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.012f);paint.setColor(0xFF38342D);c.drawCircle(0,0,.946f,paint);paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x60403827);c.drawCircle(x+.009f,y+.012f,.025f,paint);
        paint.setColor(Color.WHITE);paint.setShader(silver.at(x-.007f,y-.007f,.037f));c.drawCircle(x,y,.021f,paint);paint.setShader(null);
    }
    private void groove(float x0,float y0,float x1,float y1){
        float scale=sandSize*.5f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(sandSize*.007f);paint.setColor(0xFF7C6D54);
        sandCanvas.drawLine((x0+1)*scale,(y0+1)*scale,(x1+1)*scale,(y1+1)*scale,paint);
        paint.setStrokeWidth(sandSize*.0022f);paint.setColor(0xFFE1CCA5);
        sandCanvas.drawLine((x0+1)*scale-1,(y0+1)*scale-1,(x1+1)*scale-1,(y1+1)*scale-1,paint);
    }
    private void party(Canvas c){
        double time=motion.time;
        // Beat changes geometry and colour accents, never a full-screen strobe.
        int rays=12+detail*4;
        for(int i=0;i<rays;i++){
            double angle=i*Math.PI*2/rays+time*.10;
            double spread=.035+.025*motion.mid,length=.65+.25*motion.bass+.10*motion.beat;
            path.reset();path.moveTo(0,0);
            path.lineTo((float)(Math.cos(angle-spread)*length),(float)(Math.sin(angle-spread)*length));
            path.lineTo((float)(Math.cos(angle+spread)*length),(float)(Math.sin(angle+spread)*length));path.close();
            paint.setColor(color(i*360.0/rays+time*5,.65,.9,(int)(25+55*motion.beat)));c.drawPath(path,paint);
        }
        int rings=4,points=6;
        for(int ring=rings;ring>=1;ring--){
            double r=.11+ring*.13+motion.beat*.045;
            double rot=time*(ring%2==0?.09:-.07)+ring*.32;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.003f+motion.high*.003f);
            for(int i=0;i<points;i++){
                double a=i*Math.PI*2/points+rot,b=(i+1)*Math.PI*2/points+rot;
                paint.setColor(color(190+ring*36+time*4,.58,.95,235));
                c.drawLine((float)(Math.cos(a)*r),(float)(Math.sin(a)*r),(float)(Math.cos(b)*r),(float)(Math.sin(b)*r),paint);
                c.drawLine((float)(Math.cos(a)*r),(float)(Math.sin(a)*r),(float)(Math.cos(a+.65)*r*.53),(float)(Math.sin(a+.65)*r*.53),paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
