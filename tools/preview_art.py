#!/usr/bin/env python3
"""Composition smoke preview of production ArtRenderer using a Java2D Canvas adapter.
Not an Android/GPU pixel-exact test: mesh colours are averaged per triangle.
Usage: python tools/preview_art.py /tmp/art-preview.png
"""
from pathlib import Path
import subprocess,tempfile,sys
root=Path(__file__).resolve().parents[1]
stubs={
'Color':'''public class Color {public static final int WHITE=-1;public static int rgb(int r,int g,int b){return 0xff000000|r<<16|g<<8|b;}public static int HSVToColor(int a,float[] h){return (a<<24)|(java.awt.Color.HSBtoRGB(h[0]/360,h[1],h[2])&0xffffff);}}''',
'Paint':'''public class Paint {public enum Style{FILL,STROKE}public enum Cap{ROUND}int color=-1;float width=1;Style style=Style.FILL;Shader shader;public Paint(int f){}public void setColor(int c){color=c;}public void setAlpha(int a){color=(color&0xffffff)|(a<<24);}public void setStyle(Style s){style=s;}public void setStrokeWidth(float w){width=w;}public void setStrokeCap(Cap c){}public void setShader(Shader s){shader=s;}}''',
'Matrix':'''public class Matrix{float sx=1,sy=1,x,y;public void setScale(float a,float b){sx=a;sy=b;x=0;y=0;}public void postTranslate(float a,float b){x+=a;y+=b;}}''',
'Shader':'''public class Shader{public enum TileMode{CLAMP}Matrix matrix=new Matrix();public void setLocalMatrix(Matrix m){matrix=m;}}''',
'RadialGradient':'''public class RadialGradient extends Shader{int[] colors;float[] stops;public RadialGradient(float x,float y,float r,int[] c,float[] s,TileMode t){colors=c;stops=s;}}''',
'RectF':'''public class RectF{float l,t,r,b;public void set(float a,float c,float d,float e){l=a;t=c;r=d;b=e;}}''',
'Path':'''public class Path{public enum Direction{CW}java.awt.geom.Path2D.Float shape=new java.awt.geom.Path2D.Float();public void reset(){shape.reset();}public void moveTo(float x,float y){shape.moveTo(x,y);}public void lineTo(float x,float y){shape.lineTo(x,y);}public void cubicTo(float a,float b,float c,float d,float e,float f){shape.curveTo(a,b,c,d,e,f);}public void close(){shape.closePath();}public void addCircle(float x,float y,float r,Direction d){shape.append(new java.awt.geom.Ellipse2D.Float(x-r,y-r,r*2,r*2),false);}}''',
'Bitmap':'''public class Bitmap{public enum Config{ARGB_8888}public java.awt.image.BufferedImage image;public static Bitmap createBitmap(int w,int h,Config c){Bitmap b=new Bitmap();b.image=new java.awt.image.BufferedImage(w,h,2);return b;}public void setPixels(int[] a,int o,int stride,int x,int y,int w,int h){image.setRGB(x,y,w,h,a,o,stride);}}''',
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
 public void drawVertices(VertexMode m,int n,float[] v,int vo,Object tex,int to,int[] colors,int co,short[] ix,int io,int count,Paint p){
  // Disable polygon AA to avoid seams between triangles in this approximate adapter.
  g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
  for(int j=0;j<count;j+=3){java.awt.geom.Path2D.Float t=new java.awt.geom.Path2D.Float();int[] cc=new int[3];for(int k=0;k<3;k++){int i=ix[j+k];if(k==0)t.moveTo(v[2*i],v[2*i+1]);else t.lineTo(v[2*i],v[2*i+1]);cc[k]=colors[i];}t.closePath();int c=0;for(int shift=0;shift<=24;shift+=8){int sum=0;for(int k=0;k<3;k++)sum+=(cc[k]>>>shift)&255;c|=(sum/3)<<shift;}g.setColor(new java.awt.Color(c,true));g.fill(t);}
  g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
 }
}'''}
main='''package me.jxl.kiosk.plugins.musicscreensaver;
import android.graphics.*;
public class ArtPreview{
 public static void main(String[]args)throws Exception{
  String[] effects={"bloom","silk","chrome","aurora","coral","sand_table","prism_party"};
  int w=args.length>1?620:420,h=args.length>1?420:620;java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(w*4,h*2,2);
  java.awt.Graphics2D g=sheet.createGraphics();g.setColor(new java.awt.Color(10,15,22));g.fillRect(0,0,w*4,h*2);
  for(int i=0;i<effects.length;i++){
   Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
   ArtRenderer r=new ArtRenderer();r.setQuality("High");r.draw(c,effects[i],w,h,.04f,.4f,.3f,.2f,.6f,true);
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
 subprocess.run(['javac','--release','8','-d',tmp,*map(str,(t/'android').rglob('*.java')),*[str(src/(n+'.java')) for n in ['ArtRenderer','ArtMotion','RadialLight','VinylMaterial']],str(t/'ArtPreview.java')],check=True)
 subprocess.run(['java','-Djava.awt.headless=true','-cp',tmp,'me.jxl.kiosk.plugins.musicscreensaver.ArtPreview',*sys.argv[1:]],check=True)
