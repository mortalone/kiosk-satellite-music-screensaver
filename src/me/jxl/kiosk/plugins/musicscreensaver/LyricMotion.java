// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Entrance choreography only: line timestamps do not provide word timing. */
final class LyricMotion {
    static List<int[]> ranges(String text, boolean letters){
        List<int[]> result=new ArrayList<>();
        if(letters){
            BreakIterator iterator=BreakIterator.getCharacterInstance(Locale.ROOT);iterator.setText(text);
            int start=iterator.first();
            for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next())
                if(!text.substring(start,end).trim().isEmpty())result.add(new int[]{start,end});
        }else{
            Matcher m=Pattern.compile("\\S+").matcher(text);
            while(m.find())result.add(new int[]{m.start(),m.end()});
        }
        return result;
    }
    static float budget(double lineSeconds){
        return (float)Math.max(.01,Math.min(1.6,lineSeconds*.38));
    }
    static float progress(float age,int index,int count,float budget){
        float duration=Math.min(.48f,budget*.60f);
        float delay=count<2?0:(budget-duration)*index/(count-1);
        return Math.max(0,Math.min(1,(age-delay)/Math.max(.001f,duration)));
    }
}
