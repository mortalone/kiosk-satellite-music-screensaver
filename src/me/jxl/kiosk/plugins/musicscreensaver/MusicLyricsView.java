// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import java.util.ArrayList;

/** Phrase-based kinetic typography. LRC line timestamps are not word timestamps. */
final class MusicLyricsView extends View {
    private MusicLyrics lyrics=new MusicLyrics(new ArrayList<>(),false);
    private final boolean split;
    private final TextPaint ink=new TextPaint(3);
    private StaticLayout[] blocks=new StaticLayout[0];
    private final float[] heights=new float[8];
    private int selected=-2,style;
    private float totalHeight,blockWidth,energy;
    private double position;
    private long anchor,changedAt,audioAt;
    private boolean playing;
    private String musicReaction="Diskret";
    private AudioEnvelope beat=new AudioEnvelope();
    private float reaction;
    void setMusicReaction(String value){musicReaction=value;invalidate();}

    private final ArrayList<float[]> pieces=new ArrayList<>();
    private float entranceBudget=1.2f;
    private boolean motionLetters;

    private String currentTrack="", status="Henter sangtekst…", chosenStyle="Random";
    private final java.util.Random random=new java.util.Random();
    void setStatus(String value){status=value;invalidate();}
    void setStyle(String value){if(chosenStyle.equals(value))return;chosenStyle=value;chooseStyle();compose();}
    private void chooseStyle(){
        if("Random".equals(chosenStyle)){style=(style+1+random.nextInt(6))%7;}
        else style="Words Fly".equals(chosenStyle)?4:"Words Drop".equals(chosenStyle)?5:"Letters Assemble".equals(chosenStyle)?6:"Stacked".equals(chosenStyle)?1:"Diagonal".equals(chosenStyle)?2:"Contrast".equals(chosenStyle)?3:0;
    }

    MusicLyricsView(Context context,boolean split){super(context);this.split=split;setWillNotDraw(false);setContentDescription("Sangtekst");setClickable(false);}
    void setTrackInfo(String title,String artist){String value=(artist==null?"":artist+" · ")+(title==null?"":title);if(!value.equals(currentTrack)){currentTrack=value;if(lyrics.lines.isEmpty())compose();}}
    void setLyrics(MusicLyrics value){lyrics=value==null?new MusicLyrics(new ArrayList<>(),false):value;selected=-2;updatePosition(position);}
    void setPlaying(boolean value){if(playing==value)return;position=elapsed();anchor=SystemClock.elapsedRealtime();playing=value;if(!value){beat=new AudioEnvelope();reaction=0;}invalidate();}
    void updatePosition(double seconds){position=Math.max(0,seconds);anchor=SystemClock.elapsedRealtime();select();invalidate();}
    void acceptAudio(float[] bands,long at){
        float sum=0;int n=bands==null?0:Math.min(6,bands.length);
        for(int i=0;i<n;i++)if(Float.isFinite(bands[i]))sum+=Math.max(0,Math.min(1.8f,bands[i]));
        energy=n==0?0:sum/n;audioAt=at;beat.feed(energy,at);invalidate();
    }
    private double elapsed(){return position+(playing?Math.max(0,SystemClock.elapsedRealtime()-anchor)/1000.0:0);}
    private void select(){int next=lyrics.synced?lyrics.index(elapsed()):-1;if(next!=selected){selected=next;chooseStyle();changedAt=SystemClock.elapsedRealtime();compose();}}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){compose();}
    private void compose(){
        if(getWidth()<1||getHeight()<1)return;
        String text;
        if(lyrics.lines.isEmpty())text=currentTrack.isEmpty()?"♪":currentTrack;
        else if(!lyrics.synced)text=lyrics.lines.get(0).text; // No invented timed scrolling.
        else if(selected<0)text="♪";
        else text=lyrics.lines.get(selected).text.trim();
        if(text.isEmpty())text="♪";

        String[] words=text.split("\\s+");
        int groups=style==0||style>=4?1:Math.min(style==1?3:style==2?4:2,words.length);
        blocks=new StaticLayout[groups];
        blockWidth=getWidth()*(style==2?.72f:.82f);
        float size=Math.min(getWidth()*(split?.10f:.105f),getHeight()*.14f);
        // Fit the complete phrase before animating. Never discard words to obtain a layout.
        for(int attempt=0;attempt<20;attempt++){
            totalHeight=0;
            for(int i=0;i<groups;i++){
                int from=i*words.length/groups,to=(i+1)*words.length/groups;
                StringBuilder part=new StringBuilder();for(int j=from;j<to;j++){if(j>from)part.append(' ');part.append(words[j]);}
                TextPaint p=new TextPaint(3);p.setColor(Color.WHITE);
                p.setTypeface(Typeface.create(style==0||style==2?"serif":"sans-serif-condensed",(style==1||i==groups-1)?Typeface.BOLD:Typeface.NORMAL));
                p.setTextSize(size*(style==3&&i==1?1.5f:1));
                blocks[i]=StaticLayout.Builder.obtain(part,0,part.length(),p,Math.max(1,(int)blockWidth))
                    .setAlignment(style==0||style>=4?Layout.Alignment.ALIGN_CENTER:style==2&&i%2==1?Layout.Alignment.ALIGN_OPPOSITE:Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).setLineSpacing(size*.1f,1).build();
                heights[i]=blocks[i].getHeight()+size*.18f;totalHeight+=heights[i];
            }
            if(totalHeight<=getHeight()*.7f)break;
            size*=.86f;
        }
        pieces.clear();
        if(style>=4){
            motionLetters=style==6&&text.codePointCount(0,text.length())<=48;
            StaticLayout layout=blocks[0];
            java.util.List<int[]> ranges=LyricMotion.ranges(layout.getText().toString(),motionLetters);
            // Clip the already shaped layout rather than drawing isolated characters.
            // This preserves ligatures, wrapping and right-to-left text positioning.
            for(int[] range:ranges){
                for(int line=layout.getLineForOffset(range[0]);line<=layout.getLineForOffset(range[1]-1);line++){
                    int start=Math.max(range[0],layout.getLineStart(line));
                    int end=Math.min(range[1],layout.getLineEnd(line));
                    float left=layout.getPrimaryHorizontal(start),right=layout.getPrimaryHorizontal(end);
                    if(end==layout.getLineEnd(line))right=layout.getParagraphDirection(line)==1?layout.getLineRight(line):layout.getLineLeft(line);
                    pieces.add(new float[]{Math.min(left,right),layout.getLineTop(line),Math.max(left,right),layout.getLineBottom(line)});
                }
            }
            double duration=4;
            if(lyrics.synced&&selected>=0&&selected+1<lyrics.lines.size())duration=Math.max(.02,lyrics.lines.get(selected+1).at-lyrics.lines.get(selected).at);
            entranceBudget=LyricMotion.budget(duration);
        }
        invalidate();
    }
    private void drawPieces(Canvas c,float age){
        StaticLayout layout=blocks[0];
        float x=(getWidth()-blockWidth)/2,y=(getHeight()-totalHeight)*.48f;
        if((age>=entranceBudget&&!(reaction>.005f&&(style==4||style==6)))||pieces.size()>96){
            c.save();c.translate(x,y);layout.getPaint().setAlpha(255);layout.draw(c);c.restore();return;
        }
        for(int i=0;i<pieces.size();i++){
            float t=LyricMotion.progress(age,i,pieces.size(),entranceBudget);
            if(t<=0)continue;
            float remaining=(1-t)*(1-t)*(1-t);
            float[] p=pieces.get(i);float cx=(p[0]+p[2])/2,cy=(p[1]+p[3])/2;
            float dx=style==5?0:((i%2==0)?-1:1)*getWidth()*(motionLetters?.12f:.20f)*remaining;
            float dy=(style==5?-1:(i%3==0?-1:1))*Math.min(getHeight()*.16f,80)*remaining;
            c.save();c.translate(x+dx+(cx-blockWidth*.5f)*reaction*.08f,y+dy);
            c.rotate((i%2==0?-1:1)*(motionLetters?35:12)*remaining,cx,cy);
            float scale=1-(motionLetters?.4f:.16f)*remaining;c.scale(scale,scale,cx,cy);
            c.clipRect(p[0],p[1],p[2],p[3]);
            layout.getPaint().setAlpha((int)(255*Math.min(1,t*3)));layout.draw(c);c.restore();
        }
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);select();
        if(blocks.length==0)return;
        long now=SystemClock.elapsedRealtime();float age=(now-changedAt)/1000f;
        float y=(getHeight()-totalHeight)*.48f;
        reaction=playing&&!"Fra".equals(musicReaction)?beat.pulse(now)*("Livlig".equals(musicReaction)?1f:.4f):0;
        c.save();
        float centerX=getWidth()*.5f,centerY=getHeight()*.48f;
        if(style==1||style==5)c.translate(0,-getHeight()*.035f*reaction);
        else if(style==2)c.rotate(3.5f*reaction,centerX,centerY);
        else if(style==0||style==3)c.scale(1+.075f*reaction,1+.075f*reaction,centerX,centerY);
        for(StaticLayout block:blocks){
            block.getPaint().setStyle(style==3&&reaction>.02f?android.graphics.Paint.Style.FILL_AND_STROKE:android.graphics.Paint.Style.FILL);
            block.getPaint().setStrokeWidth(block.getPaint().getTextSize()*.018f*reaction);
        }
        if(style>=4){
            float motionAge=lyrics.synced&&selected>=0?(float)Math.max(0,elapsed()-lyrics.lines.get(selected).at):age;
            drawPieces(c,motionAge);
        }else{
        // The stagger is an entrance effect, not a claim of word-level karaoke timing.
        for(int i=0;i<blocks.length;i++){
            float t=Math.max(0,Math.min(1,(age-i*.065f)/.5f));float ease=1-(1-t)*(1-t)*(1-t);
            float x=(getWidth()-blockWidth)/2;
            if(style==2)x+=(i%2==0?-1:1)*getWidth()*.035f;
            float slide=(1-ease)*Math.min(36,getWidth()*.06f);
            c.save();c.clipRect(0,0,getWidth(),getHeight());
            c.translate(x+(style==2?(i%2==0?-slide:slide):0),y+slide);
            if(style==2)c.rotate(-4,blockWidth*.5f,heights[i]*.5f);
            float pulse=0;
            c.scale(1+pulse,1+pulse,blockWidth*.5f,heights[i]*.5f);
            blocks[i].getPaint().setAlpha((int)(255*ease));blocks[i].draw(c);c.restore();y+=heights[i];
        }
        }
        c.restore();
        ink.setColor(0xFFB7B8BC);ink.setTextSize(Math.min(14*getResources().getDisplayMetrics().scaledDensity,getWidth()*.035f));ink.setTextAlign(android.graphics.Paint.Align.CENTER);
        if(!lyrics.synced)c.drawText(lyrics.lines.isEmpty()?status:"Tekst uden tidskoder",getWidth()/2f,getHeight()*.94f,ink);
        if(isShown()&&(playing||age<Math.max(1,entranceBudget)))postInvalidateDelayed(40);
    }
}
