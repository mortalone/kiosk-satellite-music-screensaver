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

        // Test 3: Null and empty handling
        MusicLyrics empty = MusicLyrics.parse(null, null);
        check(!empty.synced && empty.lines.isEmpty(), "Empty lyrics handling");

        System.out.println("MusicScreensaver unit tests passed successfully!");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError("Test failed: " + msg);
    }
}
