package com.ekrankopya.app;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class Util {
    private Util() {
    }

    static int dp(Context c, int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Titreşim ayarı açıksa çok kısa bir titreşim verir. */
    @SuppressWarnings("deprecation")
    static void buzz(Context c) {
        if (!Prefs.get(c).getBoolean(Prefs.HAPTIC, true)) {
            return;
        }
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(18);
            }
        } catch (Exception ignored) {
        }
    }
}
