package com.ekrankopya.app;

import android.content.Context;
import android.content.res.Configuration;

/** Üç hazır tema: koyu, mor (ilk renk), parlak. Dördüncü seçenek gece gündüze göre seçer. */
final class Palette {
    int bubbleBg;
    int bubblePaper;
    int bubbleLine;
    int bubbleHi;
    int bubbleRing;

    int barBg;
    int panelBg;
    int panelText;
    int panelHint;

    int neutralBg;
    int neutralText;
    int accentBg;
    int accentText;
    int copyBg;
    int copyText;

    int chipBg;
    int chipText;

    int boxIdleFill;
    int boxIdleStroke;
    int boxSelFill;
    int boxSelStroke;

    static Palette of(Context c) {
        int t = Prefs.get(c).getInt(Prefs.THEME, Prefs.THEME_PURPLE);
        if (t == Prefs.THEME_AUTO) {
            int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            t = night == Configuration.UI_MODE_NIGHT_YES ? Prefs.THEME_DARK : Prefs.THEME_LIGHT;
        }

        Palette p = new Palette();
        p.bubbleHi = 0xFFFFC107;
        p.copyBg = 0xFF2E7D32;
        p.copyText = 0xFFFFFFFF;
        p.boxIdleFill = 0x2290CAF9;
        p.boxIdleStroke = 0xFF42A5F5;
        p.boxSelFill = 0x99FFC107;
        p.boxSelStroke = 0xFFFFA000;

        if (t == Prefs.THEME_DARK) {
            p.bubbleBg = 0xFF1C1C21;
            p.bubblePaper = 0xFFE6E6EA;
            p.bubbleLine = 0xFF1C1C21;
            p.bubbleRing = 0xFF5C5C66;
            p.barBg = 0xF0161619;
            p.panelBg = 0xF0161619;
            p.panelText = 0xFFF2F2F5;
            p.panelHint = 0xFF9AA0A6;
            p.neutralBg = 0xFF2C2C33;
            p.neutralText = 0xFFF2F2F5;
            p.accentBg = 0xFF3B3F63;
            p.accentText = 0xFFFFFFFF;
            p.chipBg = 0xFF2C2C33;
            p.chipText = 0xFFF2F2F5;
        } else if (t == Prefs.THEME_LIGHT) {
            p.bubbleBg = 0xFFFFFFFF;
            p.bubblePaper = 0xFF4338CA;
            p.bubbleLine = 0xFFFFFFFF;
            p.bubbleRing = 0xFFC5CAE9;
            p.barBg = 0xF5FFFFFF;
            p.panelBg = 0xF5FFFFFF;
            p.panelText = 0xFF1F2937;
            p.panelHint = 0xFF6B7280;
            p.neutralBg = 0xFFE3E6EF;
            p.neutralText = 0xFF1F2937;
            p.accentBg = 0xFFC7D2FE;
            p.accentText = 0xFF1F2937;
            p.chipBg = 0xFFE3E6EF;
            p.chipText = 0xFF1F2937;
        } else {
            p.bubbleBg = 0xFF4338CA;
            p.bubblePaper = 0xFFFFFFFF;
            p.bubbleLine = 0xFF4338CA;
            p.bubbleRing = 0x00000000;
            p.barBg = 0xEE263238;
            p.panelBg = 0xF0263238;
            p.panelText = 0xFFFFFFFF;
            p.panelHint = 0xFFB0BEC5;
            p.neutralBg = 0xFF455A64;
            p.neutralText = 0xFFFFFFFF;
            p.accentBg = 0xFF3949AB;
            p.accentText = 0xFFFFFFFF;
            p.chipBg = 0xFF455A64;
            p.chipText = 0xFFFFFFFF;
        }
        return p;
    }
}
