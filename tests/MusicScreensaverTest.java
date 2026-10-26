// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

public final class MusicScreensaverTest {
    public static void main(String[] args) {
        // Test 1: LRC Parsing
        MusicLyrics lyrics = MusicLyrics.parse("[00:03.00]First line\n[00:08.50][00:12.00]Second line\n[00:16.00]Third line\n[offset:250]", "");
        check(lyrics.synced && lyrics.lines.size() == 4, "LRC sync and multiple timestamps");
        check(lyrics.index(0) == -1, "Before first line");
        check(lyrics.index(3.5) == 0, "First line match with offset");
        check(lyrics.index(8.8) == 1, "Second line match");
        check(lyrics.index(12.5) == 2, "Second timestamp for line 2");
        check(lyrics.index(17.0) == 3, "Third line match");

        // Test 2: Plain text fallback
        MusicLyrics plain = MusicLyrics.parse("", "Line 1\nLine 2\nLine 3");
        check(!plain.synced && plain.lines.size() == 3, "Plain lyrics fallback");

        check(MusicLyrics.sameName("Don't Go", "Don’t Go"),"Normalize typographic apostrophes in lyric lookup");
        check(!MusicLyrics.sameName("Song", "Song Live"),"Do not silently match another version");
        MusicLyrics entityPlain=MusicLyrics.parse("First line", "First line");
        check(!entityPlain.lines.isEmpty()&&!entityPlain.synced,"Preserve untimed entity lyrics");

        // Test 3: Null and empty handling
        MusicLyrics empty = MusicLyrics.parse(null, null);
        check(!empty.synced && empty.lines.isEmpty(), "Empty lyrics handling");

        FluidModel silent = new FluidModel(42), driven = new FluidModel(42);
        for (int frame=0;frame<15000;frame++) {
            silent.step(.04f,0,0,0);
            driven.step(.04f,frame%19==0?1.2f:.05f,.35f,.2f);
            for(int i=0;i<FluidModel.COUNT;i++) {
                check(Float.isFinite(driven.x[i]) && Float.isFinite(driven.y[i]), "Finite fluid state");
                check(Math.hypot(driven.x[i],driven.y[i]) <= .791, "Fluid remains in cavity");
            }
        }
        float difference=0;
        for(int i=0;i<FluidModel.COUNT;i++) difference+=Math.abs(silent.x[i]-driven.x[i])+Math.abs(silent.y[i]-driven.y[i]);
        check(difference>.02f,"Audio changes the fluid trajectory");
        driven.step(.04f,Float.NaN,Float.POSITIVE_INFINITY,-100);
        check(Float.isFinite(driven.field(0,0)),"Malformed audio cannot poison surface");
        // A surface must permit separate components, not merely deform one fixed outline.
        for(int i=0;i<FluidModel.COUNT;i++){driven.x[i]=i<4?-.55f:.55f;driven.y[i]=0;}
        check(driven.field(-.55f,0)>1 && driven.field(.55f,0)>1 && driven.field(0,0)<1,"Surface supports detached droplets");

        PlaybackClock clock=new PlaybackClock();
        clock.update("a",true,10.0,"t",null,1000,1000);
        clock.update("a",true,10.0,"t",null,6000,6000);
        check(Math.abs(clock.at(6000)-15)<.01,"Repeated position does not rewind playback");
        clock.update("a",false,10.0,"t",null,6000,6000);
        check(Math.abs(clock.at(9000)-15)<.01,"Pause freezes interpolated position");
        clock.update("a",true,10.0,"t",null,9000,9000);
        check(Math.abs(clock.at(11000)-17)<.01,"Resume continues from pause");
        clock.update("a",true,2.0,"new",10000L,11000,11000);
        check(Math.abs(clock.at(11000)-3)<.01,"Seek respects HA timestamp");
        clock.update("b",true,0.0,"next",null,12000,12000);
        check(clock.at(12000)==0,"New track resets clock");
        AudioEnvelope envelope=new AudioEnvelope();
        for(int i=0;i<30;i++)envelope.feed(.1f,i*40);
        check(envelope.pulse(1160)==0,"Sustained audio does not invent beats");
        envelope.feed(.5f,1200);
        check(envelope.pulse(1200)>.2f,"Bass transient creates pulse");
        check(envelope.pulse(1280)>0,"Pulse survives between frames");
        check(envelope.pulse(2000)==0,"Missing audio clears pulse");

        // Verify loud passage lockout immunity: kicks must register reliably over continuous bass
        for(int i=0;i<60;i++)envelope.feed(.75f, 2500 + i*40);
        int initialHits = envelope.getBeatCount();
        for (int bar = 0; bar < 4; bar++) {
            long t = 5000 + bar * 500;
            envelope.feed(.65f, t - 40);
            envelope.feed(.98f, t);
            envelope.feed(.70f, t + 40);
        }
        check(envelope.getBeatCount() >= initialHits + 4, "Loud sustained passages cannot lockout beat detection");

        java.util.List<int[]> words=LyricMotion.ranges("Hej verden!",false);
        check(words.size()==2&&words.get(1)[1]==11,"Word animation retains punctuation");
        java.util.List<int[]> letters=LyricMotion.ranges("a\u030a😀",true);
        check(letters.size()==2&&letters.get(0)[1]==2&&letters.get(1)[1]==4,"Keep combining accents and surrogate pairs together");
        float budget=LyricMotion.budget(.4);
        check(budget<.2&&LyricMotion.progress(budget,29,30,budget)>.999f,"Fast line finishes entrance early enough to read");
        check(LyricMotion.progress(0,0,5,1)==0,"Entrance starts hidden");
        check(LyricMotion.progress(.1f,4,5,1)==0,"Later words wait for stagger");
        check(LyricMotion.budget(20)<=1.6f,"Long lines cannot stretch entrance indefinitely");

        check(AudioFrameTiming.accept(false,1000,1100),"Accept recent synchronized dispatch");
        check(!AudioFrameTiming.accept(false,1000,1300),"Discard stale audio rather than play late beat");
        check(!AudioFrameTiming.accept(true,1000,1000),"Demo must not masquerade as real music");
        check(!AudioFrameTiming.accept(false,2000,1000),"Reject invalid future dispatch timestamp");

        System.out.println("MusicScreensaver unit tests passed successfully!");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError("Test failed: " + msg);
    }
}
