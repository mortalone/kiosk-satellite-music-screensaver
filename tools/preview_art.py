#!/usr/bin/env python3
"""Composition smoke preview of production ArtRenderer using a Java2D Canvas adapter.
Not an Android/GPU pixel-exact test: vertex colours are interpolated; gradients and texture filtering are approximations.
Usage: python tools/preview_art.py /tmp/art-preview.png
"""
from pathlib import Path
import subprocess,tempfile,sys
root=Path(__file__).resolve().parents[1]
stubs={
'Color':'''public class Color {public static final int WHITE=-1;public static int rgb(int r,int g,int b){return 0xff000000|r<<16|g<<8|b;}public static void colorToHSV(int c,float[] out){float[] v=java.awt.Color.RGBtoHSB((c>>16)&255,(c>>8)&255,c&255,null);out[0]=v[0]*360;out[1]=v[1];out[2]=v[2];}public static int HSVToColor(float[] h){return HSVToColor(255,h);}public static int HSVToColor(int a,float[] h){return (a<<24)|(java.awt.Color.HSBtoRGB(h[0]/360,h[1],h[2])&0xffffff);}}''',
'Paint':'''public class Paint {public enum Style{FILL,STROKE}public enum Cap{ROUND}int color=-1;float width=1;Style style=Style.FILL;Shader shader;PorterDuffColorFilter filter;public void setColorFilter(PorterDuffColorFilter f){filter=f;}public Paint(int f){}public void setColor(int c){color=c;}public void setAlpha(int a){color=(color&0xffffff)|(a<<24);}public void setStyle(Style s){style=s;}public void setStrokeWidth(float w){width=w;}public void setStrokeCap(Cap c){}public void setShader(Shader s){shader=s;}}''',
'PorterDuff':'''public class PorterDuff{public enum Mode{MULTIPLY}}''',
'PorterDuffColorFilter':'''public class PorterDuffColorFilter{int color;public PorterDuffColorFilter(int c,PorterDuff.Mode m){color=c;}}''',
'Matrix':'''public class Matrix{float sx=1,sy=1,x,y;public void setScale(float a,float b){sx=a;sy=b;x=0;y=0;}public void postTranslate(float a,float b){x+=a;y+=b;}}''',
'Shader':'''public class Shader{public enum TileMode{CLAMP}Matrix matrix=new Matrix();public void setLocalMatrix(Matrix m){matrix=m;}}''',
'RadialGradient':'''public class RadialGradient extends Shader{int[] colors;float[] stops;public RadialGradient(float x,float y,float r,int[] c,float[] s,TileMode t){colors=c;stops=s;}}''',
'RectF':'''public class RectF{float l,t,r,b;public void set(float a,float c,float d,float e){l=a;t=c;r=d;b=e;}}''',
'Path':'''public class Path{public enum Direction{CW}java.awt.geom.Path2D.Float shape=new java.awt.geom.Path2D.Float();public void reset(){shape.reset();}public void moveTo(float x,float y){shape.moveTo(x,y);}public void lineTo(float x,float y){shape.lineTo(x,y);}public void cubicTo(float a,float b,float c,float d,float e,float f){shape.curveTo(a,b,c,d,e,f);}public void close(){shape.closePath();}public void addCircle(float x,float y,float r,Direction d){shape.append(new java.awt.geom.Ellipse2D.Float(x-r,y-r,r*2,r*2),false);}}''',
'Bitmap':'''public class Bitmap{public enum Config{ARGB_8888}public java.awt.image.BufferedImage image;public static Bitmap createBitmap(int[] a,int w,int h,Config c){Bitmap b=createBitmap(w,h,c);b.setPixels(a,0,w,0,0,w,h);return b;}public static Bitmap createBitmap(int w,int h,Config c){Bitmap b=new Bitmap();b.image=new java.awt.image.BufferedImage(w,h,2);return b;}public void setPixels(int[] a,int o,int stride,int x,int y,int w,int h){image.setRGB(x,y,w,h,a,o,stride);}}''',
'MeshPaint':'''public class MeshPaint implements java.awt.Paint {
 final float[] v,uv; final int[] c;final java.awt.image.BufferedImage image;
 MeshPaint(float[] points,int[] colors,java.awt.image.BufferedImage texture,float[] tex){v=points;c=colors;image=texture;uv=tex;}
 public int getTransparency(){return java.awt.Transparency.TRANSLUCENT;}
 public java.awt.PaintContext createContext(java.awt.image.ColorModel model,java.awt.Rectangle bounds,java.awt.geom.Rectangle2D user,java.awt.geom.AffineTransform transform,java.awt.RenderingHints hints){
 final double[] p=new double[6];for(int i=0;i<6;i++)p[i]=v[i];transform.transform(p,0,p,0,3);
 final double det=(p[3]-p[5])*(p[0]-p[4])+(p[4]-p[2])*(p[1]-p[5]);
 return new java.awt.PaintContext(){public void dispose(){}public java.awt.image.ColorModel getColorModel(){return java.awt.image.ColorModel.getRGBdefault();}
 public java.awt.image.Raster getRaster(int x,int y,int w,int h){java.awt.image.BufferedImage out=new java.awt.image.BufferedImage(w,h,2);int[] data=((java.awt.image.DataBufferInt)out.getRaster().getDataBuffer()).getData();
 for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){double px=x+xx+.5,py=y+yy+.5;double a=((p[3]-p[5])*(px-p[4])+(p[4]-p[2])*(py-p[5]))/det,b=((p[5]-p[1])*(px-p[4])+(p[0]-p[4])*(py-p[5]))/det,d=1-a-b;int tex=-1;
 if(image!=null){double u=a*uv[0]+b*uv[2]+d*uv[4],vv=a*uv[1]+b*uv[3]+d*uv[5];int tx=Math.max(0,Math.min(image.getWidth()-1,(int)(u*image.getWidth()))),ty=Math.max(0,Math.min(image.getHeight()-1,(int)(vv*image.getHeight())));tex=image.getRGB(tx,ty);}
 int result=0;for(int shift=0;shift<=24;shift+=8){double channel=(((c[0]>>>shift)&255)*a+((c[1]>>>shift)&255)*b+((c[2]>>>shift)&255)*d);int channelInt=(int)Math.max(0,Math.min(255,channel));if(image!=null)channelInt=channelInt*((tex>>>shift)&255)/255;result|=channelInt<<shift;}data[yy*w+xx]=result;}return out.getRaster();}};}
}''',
'Canvas':'''public class Canvas{
 public enum VertexMode{TRIANGLES}
 java.awt.Graphics2D g;java.util.Stack<java.awt.Graphics2D> stack=new java.util.Stack<java.awt.Graphics2D>();
 public Canvas(Bitmap b){g=b.image.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);}
 public boolean isHardwareAccelerated(){return false;}
 public void save(){stack.push(g);g=(java.awt.Graphics2D)g.create();}public void restore(){g.dispose();g=stack.pop();}
 public void translate(float x,float y){g.translate(x,y);}public void scale(float x,float y){g.scale(x,y);}
 public void clipRect(float l,float t,float r,float b){g.clip(new java.awt.geom.Rectangle2D.Float(l,t,r-l,b-t));}
 public void clipPath(Path p){g.clip(p.shape);}
 void setup(Paint p){g.setStroke(new java.awt.BasicStroke(Math.max(.0001f,p.width),java.awt.BasicStroke.CAP_ROUND,java.awt.BasicStroke.JOIN_ROUND));
  if(p.shader instanceof RadialGradient){RadialGradient s=(RadialGradient)p.shader;java.awt.Color[] cs=new java.awt.Color[s.colors.length];for(int i=0;i<cs.length;i++)cs[i]=new java.awt.Color(s.colors[i],true);g.setPaint(new java.awt.RadialGradientPaint(s.matrix.x,s.matrix.y,s.matrix.sx,s.stops,cs));}
  else g.setColor(new java.awt.Color(p.color,true));}
 void shape(java.awt.Shape s,Paint p){setup(p);if(p.style==Paint.Style.FILL)g.fill(s);else g.draw(s);}
 public void drawCircle(float x,float y,float r,Paint p){shape(new java.awt.geom.Ellipse2D.Float(x-r,y-r,r*2,r*2),p);}
 public void drawPath(Path p,Paint q){shape(p.shape,q);}
 public void drawLine(float x,float y,float a,float b,Paint p){setup(p);g.draw(new java.awt.geom.Line2D.Float(x,y,a,b));}
 public void drawRect(float l,float t,float r,float b,Paint p){shape(new java.awt.geom.Rectangle2D.Float(l,t,r-l,b-t),p);}
 public void drawBitmap(Bitmap b,Object source,RectF r,Paint p){java.awt.geom.AffineTransform tx=new java.awt.geom.AffineTransform();tx.translate(r.l,r.t);tx.scale((r.r-r.l)/b.image.getWidth(),(r.b-r.t)/b.image.getHeight());g.drawImage(b.image,tx,null);}

 public void drawBitmapMesh(Bitmap bitmap,int mw,int mh,float[] verts,int offset,int[] cs,int co,Paint paint){
  g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
  for(int y=0;y<mh;y++)for(int x=0;x<mw;x++)for(int tri=0;tri<2;tri++){
   int a=y*(mw+1)+x,b=a+1,d=a+mw+1;if(tri==1){a=b;b=d+1;}
   int[] ids={a,b,d},col={cs[a],cs[b],cs[d]};float[] v=new float[6],uv=new float[6];java.awt.geom.Path2D.Float path=new java.awt.geom.Path2D.Float();
   for(int k=0;k<3;k++){v[k*2]=verts[ids[k]*2];v[k*2+1]=verts[ids[k]*2+1];uv[k*2]=(ids[k]%(mw+1))/(float)mw;uv[k*2+1]=(ids[k]/(mw+1))/(float)mh;if(k==0)path.moveTo(v[k*2],v[k*2+1]);else path.lineTo(v[k*2],v[k*2+1]);}path.closePath();
   g.setPaint(new MeshPaint(v,col,bitmap.image,uv));g.fill(path);
  }
  g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
 }
 public void drawVertices(VertexMode m,int n,float[] v,int vo,Object tex,int to,int[] colors,int co,short[] ix,int io,int count,Paint p){
  // Disable polygon AA to avoid seams between triangles in this approximate adapter.
  g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
  for(int j=0;j<count;j+=3){java.awt.geom.Path2D.Float t=new java.awt.geom.Path2D.Float();int[] cc=new int[3];for(int k=0;k<3;k++){int i=ix[j+k];if(k==0)t.moveTo(v[2*i],v[2*i+1]);else t.lineTo(v[2*i],v[2*i+1]);cc[k]=colors[i];}t.closePath();int c=0;for(int shift=0;shift<=24;shift+=8){int sum=0;for(int k=0;k<3;k++)sum+=(cc[k]>>>shift)&255;c|=(sum/3)<<shift;}g.setPaint(new MeshPaint(new float[]{v[ix[j]*2],v[ix[j]*2+1],v[ix[j+1]*2],v[ix[j+1]*2+1],v[ix[j+2]*2],v[ix[j+2]*2+1]},cc,null,null));g.fill(t);}
  g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
 }
}'''}
main='''package me.jxl.kiosk.plugins.musicscreensaver;
import android.graphics.*;
public class ArtPreview{
 public static void main(String[]args)throws Exception{
  double seconds=args.length>2?Double.parseDouble(args[2]):0;
  if(args.length>1&&args[1].equals("vinyl")){
   String[] styles={"Splatter","Mosaic","Colour Sectors","Sunburst","Marble","Translucent"};
   java.awt.image.BufferedImage out=new java.awt.image.BufferedImage(1200,820,2);java.awt.Graphics2D gg=out.createGraphics();gg.setColor(new java.awt.Color(9,12,19));gg.fillRect(0,0,1200,820);
   for(int i=0;i<styles.length;i++){
    Bitmap b=Bitmap.createBitmap(400,410,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);BacklitVinylRenderer r=new BacklitVinylRenderer();r.setPattern("Backlit "+styles[i]);
    r.draw(c,200,195,180,null,0xff5ed4cf,3+seconds,.4f,.2f,.3f);while(r.isPreparing())Thread.sleep(10);
    r.draw(c,200,195,180,null,0xff5ed4cf,3+seconds,.4f,.2f,.3f);
    int x=i%3*400,y=i/3*410;gg.drawImage(b.image,x,y,null);gg.setColor(java.awt.Color.WHITE);gg.drawString(styles[i],x+20,y+402);
   }
   javax.imageio.ImageIO.write(out,"png",new java.io.File(args[0]));return;
  }
  String[] effects={"bloom","silk","chrome","aurora","coral","sand_table","prism_party"};
  int w=args.length>1?620:420,h=args.length>1?420:620;java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(w*4,h*2,2);
  java.awt.Graphics2D g=sheet.createGraphics();g.setColor(new java.awt.Color(10,15,22));g.fillRect(0,0,w*4,h*2);
  for(int i=0;i<effects.length;i++){
   Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
   ArtRenderer r=new ArtRenderer();r.setQuality("High");
   java.lang.reflect.Field field=ArtRenderer.class.getDeclaredField("motion");field.setAccessible(true);ArtMotion motion=(ArtMotion)field.get(r);for(int f=0;f<(int)(seconds*25);f++){motion.step(.04f,.4f,.3f,.2f,.6f,true);motion.stepSand(.04f);}
   r.draw(c,effects[i],w,h,.04f,.4f,.3f,.2f,.6f,true);
   int x=i%4*w,y=i/4*h;g.drawImage(b.image,x,y,null);g.setColor(java.awt.Color.WHITE);g.drawString(effects[i],x+20,y+h-30);
  }
  javax.imageio.ImageIO.write(sheet,"png",new java.io.File(args[0]));
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp);pkg=t/'android/graphics';pkg.mkdir(parents=True)
 for name,content in stubs.items():(pkg/(name+'.java')).write_text('package android.graphics;\n'+content)
 (t/'android/os').mkdir()
 (t/'android/os/Build.java').write_text('package android.os;public class Build{public static class VERSION{public static final int SDK_INT=35;}}')
 (t/'ArtPreview.java').write_text(main)
 src=root/'src/me/jxl/kiosk/plugins/musicscreensaver'
 subprocess.run(['javac','--release','8','-d',tmp,*map(str,(t/'android').rglob('*.java')),*[str(src/(n+'.java')) for n in ['ArtRenderer','ArtMotion','RadialLight','VinylMaterial','BloomSurface','BacklitVinylRenderer']],str(t/'ArtPreview.java')],check=True)
 subprocess.run(['java','-Djava.awt.headless=true','-cp',tmp,'me.jxl.kiosk.plugins.musicscreensaver.ArtPreview',*sys.argv[1:]],check=True)
