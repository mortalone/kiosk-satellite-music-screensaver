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

    MusicLyricsView(Context context, boolean split) {
        super(context);
        this.split = split;
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        int leftPad = split ? MusicUi.dp(context, 16) : MusicUi.dp(context, 32);
        rows.setPadding(leftPad, MusicUi.dp(context, 90), MusicUi.dp(context, 24), MusicUi.dp(context, 100));
        addView(rows, new ScrollView.LayoutParams(-1, -2));
        setContentDescription("Sangtekst");
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
            TextView empty = MusicUi.text(getContext(), "♪ Lytter...", split ? 24 : 26, MusicUi.MUTED);
            empty.setGravity(split ? Gravity.START : Gravity.CENTER);
            rows.addView(empty);
            return;
        }
        windowStart = 0;
        int end = Math.min(lyrics.lines.size(), 300);
        for (int i = 0; i < end; i++) {
            String text = lyrics.lines.get(i).text;
            TextView t = MusicUi.text(getContext(), text.isEmpty() ? "♪" : text, split ? 28 : 32,
                    lyrics.synced ? MusicUi.MUTED : MusicUi.INK);
            t.setGravity(split ? Gravity.START : Gravity.CENTER);
            t.setAlpha(lyrics.synced ? 0.45f : 0.95f);
            t.setPadding(0, MusicUi.dp(getContext(), split ? 12 : 14), 0, MusicUi.dp(getContext(), split ? 12 : 14));
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
            t.setAlpha(active ? 1.0f : 0.40f);
            t.setScaleX(1.0f);
            t.setScaleY(1.0f);
            t.setShadowLayer(active ? 12f : 0f, 0, 0, 0x8865E5CF);
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
        float scale = 1.0f + 0.08f * energy;
        active.setScaleX(scale);
        active.setScaleY(scale);
        active.setShadowLayer(4f + 16f * energy, 0, 0, 0xAA65E5CF);
    }
}
