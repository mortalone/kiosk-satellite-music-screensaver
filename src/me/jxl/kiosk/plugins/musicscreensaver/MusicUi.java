// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.musicscreensaver;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.ImageView;
import android.widget.TextView;

final class MusicUi {
    static final int ACCENT = 0xFF65E5CF, INK = 0xFFF4F6F8, MUTED = 0xFF9CA7B6, SURFACE = 0xFF171D26;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, int color, int radius, boolean border) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(c, radius));
        if (border) bg.setStroke(dp(c, 1), 0xFF313C49);
        return bg;
    }

    static TextView text(Context c, String value, float size, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    static ImageView icon(Context c, String kind, String description) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new Glyph(kind));
        v.setContentDescription(description);
        v.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3365E5CF), shape(c, 0xD9202732, 24, false), null));
        return v;
    }

    static final class Glyph extends Drawable {
        final String kind;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Glyph(String kind) {
            this.kind = kind;
        }

        @Override
        public void draw(Canvas c) {
            int save = c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            p.setColor(INK);
            p.setStrokeWidth(2.0f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            path.reset();

            if ("play".equals(kind)) {
                p.setStyle(Paint.Style.FILL);
                path.moveTo(8, 4);
                path.lineTo(21, 12);
                path.lineTo(8, 20);
                path.close();
                c.drawPath(path, p);
            } else if ("pause".equals(kind)) {
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(6, 4, 10, 20, 1.5f, 1.5f, p);
                c.drawRoundRect(14, 4, 18, 20, 1.5f, 1.5f, p);
            } else if ("next".equals(kind)) {
                p.setStyle(Paint.Style.FILL);
                path.moveTo(5, 5);
                path.lineTo(15, 12);
                path.lineTo(5, 19);
                path.close();
                c.drawPath(path, p);
                c.drawRect(16, 5, 18.5f, 19, p);
            } else if ("prev".equals(kind)) {
                p.setStyle(Paint.Style.FILL);
                path.moveTo(19, 5);
                path.lineTo(9, 12);
                path.lineTo(19, 19);
                path.close();
                c.drawPath(path, p);
                c.drawRect(5.5f, 5, 8, 19, p);
            } else if ("minus".equals(kind)) {
                c.drawLine(5, 12, 19, 12, p);
            } else if ("plus".equals(kind)) {
                c.drawLine(5, 12, 19, 12, p);
                c.drawLine(12, 5, 12, 19, p);
            } else if ("close".equals(kind)) {
                c.drawLine(6, 6, 18, 18, p);
                c.drawLine(18, 6, 6, 18, p);
            }
            c.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { p.setColorFilter(filter); }
        @Override @SuppressWarnings("deprecation") public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
