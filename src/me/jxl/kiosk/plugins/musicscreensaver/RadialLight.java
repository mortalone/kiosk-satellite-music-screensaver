package me.jxl.kiosk.plugins.musicscreensaver;

import android.graphics.Matrix;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Reusable unit-radius lighting shader; transforms do not rebuild its color stops. */
final class RadialLight {
    private final Matrix matrix = new Matrix();
    private final RadialGradient gradient;
    RadialLight(int[] colors, float[] stops) {
        gradient = new RadialGradient(0, 0, 1, colors, stops, Shader.TileMode.CLAMP);
    }
    Shader at(float x, float y, float radius) {
        matrix.setScale(Math.max(.001f, radius), Math.max(.001f, radius));
        matrix.postTranslate(x, y);
        gradient.setLocalMatrix(matrix);
        return gradient;
    }
}
