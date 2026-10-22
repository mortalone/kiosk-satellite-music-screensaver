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

        System.out.println("MusicScreensaver unit tests passed successfully!");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError("Test failed: " + msg);
    }
}
