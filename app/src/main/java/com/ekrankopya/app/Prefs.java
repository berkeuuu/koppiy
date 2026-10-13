package com.ekrankopya.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Tüm ayar anahtarları tek yerde. */
final class Prefs {
    private Prefs() {
    }

    static final String FILE = "koppiy";

    /** Ana anahtar: kapalıyken logo düğmesi ve orta tuş çalışmaz. */
    static final String ENABLED = "enabled";

    /** Açılış tercihi: logo düğmesi, orta tuş (asistan) ya da ikisi. */
    static final String MODE = "open_mode";
    static final int MODE_BUBBLE = 0;
    static final int MODE_ASSIST = 1;
    static final int MODE_BOTH = 2;

    static final String SIZE = "size_dp";
    static final String ALPHA = "alpha_idle";
    static final String SNAP = "snap";
    static final String HAPTIC = "haptic";
    static final String HIDE_GAMES = "hide_games";
    static final String SPLIT = "show_split";
    static final String FAST = "fast_mode";
    static final String ALL_DESC = "show_all_desc";

    static final String THEME = "theme";
    static final int THEME_DARK = 0;
    static final int THEME_PURPLE = 1;
    static final int THEME_LIGHT = 2;
    static final int THEME_AUTO = 3;

    static final String BAR_BOTTOM = "bar_bottom";
    static final String BUBBLE_X = "bubble_x";
    static final String BUBBLE_Y = "bubble_y";
    static final String HIDDEN_APPS = "hidden_apps";
    static final String WIZARD_DONE = "wizard_done";

    /** Sihirbazda elle "Yaptım" denen adımlar: STEP_PREFIX + adım kimliği. */
    static final String STEP_PREFIX = "step_";

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
