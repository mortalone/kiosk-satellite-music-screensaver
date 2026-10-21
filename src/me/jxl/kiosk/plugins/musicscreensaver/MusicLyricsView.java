// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.content.Context;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class MusicLyricsView extends ScrollView {
    private final LinearLayout rows;
    private final List<TextView> labels = new ArrayList<>();
    private MusicLyrics lyrics = new MusicLyrics(new ArrayList<>(), false);
    private int selected = -2;
    private final boolean split;
    private long lastPulse;
    private int windowStart;
    private String currentTrack = "";

    MusicLyricsView(Context context, boolean split) {
        super(context);
        this.split = split;
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        int leftPad = split ? MusicUi.dp(context, 20) : MusicUi.dp(context, 32);
        int rightPad = split ? MusicUi.dp(context, 20) : MusicUi.dp(context, 32);
        rows.setPadding(leftPad, MusicUi.dp(context, 80), rightPad, MusicUi.dp(context, 120));
        addView(rows, new ScrollView.LayoutParams(-1, -2));
        setContentDescription("Sangtekst");
    }

    void setTrackInfo(String title, String artist) {
        this.currentTrack = (artist != null && !artist.isEmpty()) ? (artist + " – " + title) : (title != null ? title : "");
        if (lyrics.lines.isEmpty()) {
            rebuild();
        }
    }

    void setLyrics(MusicLyrics value) {
        this.lyrics = value == null ? new MusicLyrics(new ArrayList<>(), false) : value;
        this.selected = -2;
        rebuild();
    }

    private void rebuild() {
        rows.removeAllViews();
        labels.clear();
        if (lyrics.lines.isEmpty()) {
            TextView icon = MusicUi.text(getContext(), "♪", split ? 38 : 44, MusicUi.ACCENT);
            icon.setGravity(split ? Gravity.START : Gravity.CENTER);
            icon.setPadding(0, MusicUi.dp(getContext(), 40), 0, MusicUi.dp(getContext(), 12));
            rows.addView(icon);

            String status = currentTrack.isEmpty() ? "Lytter efter sangtekst..." : ("Spiller: " + currentTrack);
            TextView empty = MusicUi.text(getContext(), status, split ? 20 : 22, MusicUi.INK);
            empty.setGravity(split ? Gravity.START : Gravity.CENTER);
            rows.addView(empty);

            TextView hint = MusicUi.text(getContext(), "Synkroniserede tekster vises automatisk n\u00e5r de er tilg\u00e6ngelige", split ? 14 : 15, MusicUi.MUTED);
            hint.setGravity(split ? Gravity.START : Gravity.CENTER);
            hint.setPadding(0, MusicUi.dp(getContext(), 8), 0, 0);
            rows.addView(hint);
            return;
        }
        windowStart = 0;
        int end = Math.min(lyrics.lines.size(), 500);
        for (int i = 0; i < end; i++) {
            String text = lyrics.lines.get(i).text;
            TextView t = MusicUi.text(getContext(), text.isEmpty() ? "♪" : text, split ? 24 : 28,
                    lyrics.synced ? MusicUi.MUTED : MusicUi.INK);
            t.setGravity(split ? Gravity.START : Gravity.CENTER);
            t.setAlpha(lyrics.synced ? 0.35f : 0.95f);
            t.setPadding(0, MusicUi.dp(getContext(), split ? 10 : 12), 0, MusicUi.dp(getContext(), split ? 10 : 12));
            rows.addView(t);
            labels.add(t);
        }
    }

    void updatePosition(double seconds) {
        if (!lyrics.synced || labels.isEmpty()) return;
        int next = lyrics.index(seconds);
        if (next == selected) return;
        selected = next;
        for (int i = 0; i < labels.size(); i++) {
            boolean active = (i + windowStart) == next;
            TextView t = labels.get(i);
            t.setAlpha(active ? 1.0f : 0.35f);
            t.setScaleX(1.0f);
            t.setScaleY(1.0f);
            t.setShadowLayer(active ? 14f : 0f, 0, 0, 0x9965E5CF);
            t.setTextColor(active ? MusicUi.ACCENT : MusicUi.MUTED);
            t.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (next >= windowStart && next - windowStart < labels.size()) {
            TextView target = labels.get(next - windowStart);
            post(() -> smoothScrollTo(0, Math.max(0, target.getTop() - getHeight() / 3)));
        }
    }

    void acceptAudio(float[] bands) {
        if (!lyrics.synced || selected < windowStart || selected - windowStart >= labels.size()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastPulse < 60) return;
        lastPulse = now;
        float energy = 0;
        int count = bands == null ? 0 : Math.min(8, bands.length);
        for (int i = 0; i < count; i++) {
            if (!Float.isNaN(bands[i]) && !Float.isInfinite(bands[i])) {
                energy += Math.max(0, Math.min(1, bands[i]));
            }
        }
        energy = count == 0 ? 0 : energy / count;
        TextView active = labels.get(selected - windowStart);
        float scale = 1.0f + 0.06f * energy;
        active.setScaleX(scale);
        active.setScaleY(scale);
        active.setShadowLayer(4f + 14f * energy, 0, 0, 0xBB65E5CF);
    }
}
