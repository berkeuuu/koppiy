package com.ekrankopya.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ana ekran: açma/kapama anahtarı, kurulum sihirbazı ve ayarlar. */
public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF4338CA;
    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFC62828;
    private static final int BG = 0xFFF4F4FA;

    private static class Step {
        final String id;
        final int title;
        final int text;
        final boolean optional;
        final Runnable open;
        final boolean auto; // true: sistemden okunabiliyor

        Step(String id, int title, int text, boolean optional, boolean auto, Runnable open) {
            this.id = id;
            this.title = title;
            this.text = text;
            this.optional = optional;
            this.auto = auto;
            this.open = open;
        }
    }

    private SharedPreferences prefs;
    private KoppiySwitch master;
    private LinearLayout content;
    private TextView tabMain;
    private TextView tabSettings;
    private int tab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        buildFrame();
        showTab(0);
        askModeFirstTime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        master.setChecked(prefs.getBoolean(Prefs.ENABLED, true), false);
        showTab(tab);
    }

    // ---------------------------------------------------------------- iskelet

    private void buildFrame() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 16), Util.dp(this, 6));

        LogoView logo = new LogoView(this);
        header.addView(logo, new LinearLayout.LayoutParams(Util.dp(this, 36), Util.dp(this, 36)));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1A1A2E);
        title.setPadding(Util.dp(this, 10), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        master = new KoppiySwitch(this);
        master.setChecked(prefs.getBoolean(Prefs.ENABLED, true), false);
        master.setListener(new KoppiySwitch.Listener() {
            @Override
            public void onChanged(boolean on) {
                prefs.edit().putBoolean(Prefs.ENABLED, on).apply();
            }
        });
        header.addView(master);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabMain = tabView(R.string.tab_main, 0);
        tabSettings = tabView(R.string.tab_settings, 1);
        tabs.addView(tabMain, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(tabSettings, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 16);
        content.setPadding(pad, Util.dp(this, 8), pad, pad * 2);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private TextView tabView(int label, final int index) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Util.dp(this, 12), 0, Util.dp(this, 12));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(index);
            }
        });
        return t;
    }

    private void showTab(int index) {
        tab = index;
        styleTab(tabMain, index == 0);
        styleTab(tabSettings, index == 1);
        content.removeAllViews();
        if (index == 0) {
            buildMain();
        } else {
            buildSettings();
        }
    }

    private void styleTab(TextView t, boolean selected) {
        t.setTextColor(selected ? ACCENT : 0xFF777788);
        t.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        GradientDrawable d = new GradientDrawable();
        d.setColor(selected ? 0xFFE8E7FB : Color.TRANSPARENT);
        t.setBackground(d);
    }

    private void askModeFirstTime() {
        if (prefs.contains(Prefs.MODE)) {
            return;
        }
        String[] items = {
                getString(R.string.first_mode_bubble),
                getString(R.string.first_mode_assist),
                getString(R.string.first_mode_both)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.first_title)
                .setCancelable(false)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        int mode = which == 0 ? Prefs.MODE_BUBBLE
                                : which == 1 ? Prefs.MODE_ASSIST : Prefs.MODE_BOTH;
                        prefs.edit().putInt(Prefs.MODE, mode).apply();
                        if (tab == 1) {
                            showTab(1);
                        }
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- yardımcılar

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = Util.dp(this, 14);
        c.setPadding(p, p, p, p);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.WHITE);
        d.setCornerRadius(Util.dp(this, 14));
        c.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Util.dp(this, 10);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(CharSequence s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private TextView heading(int res) {
        TextView t = text(getString(res), 13, ACCENT, true);
        t.setAllCaps(true);
        t.setPadding(Util.dp(this, 4), Util.dp(this, 18), 0, 0);
        return t;
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private Intent appDetails() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
    }

    // ---------------------------------------------------------------- algılama

    private boolean accessibilityOn() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) {
            return false;
        }
        ComponentName me = new ComponentName(this, CopyService.class);
        TextUtils.SimpleStringSplitter sp = new TextUtils.SimpleStringSplitter(':');
        sp.setString(s);
        while (sp.hasNext()) {
            ComponentName c = ComponentName.unflattenFromString(sp.next());
            if (me.equals(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean batteryOk() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean assistantOn() {
        String[] keys = {"assistant", "voice_interaction_service"};
        for (String k : keys) {
            try {
                String v = Settings.Secure.getString(getContentResolver(), k);
                if (v != null && v.contains(getPackageName())) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String brand() {
        return (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER).toLowerCase(Locale.ROOT);
    }

    private boolean hasAutostartStep() {
        String b = brand();
        return b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
                || b.contains("oppo") || b.contains("realme") || b.contains("oneplus")
                || b.contains("vivo") || b.contains("huawei") || b.contains("honor");
    }

    private void openAutostart() {
        String b = brand();
        List<String[]> tries = new ArrayList<>();
        if (b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")) {
            tries.add(new String[]{"com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"});
        } else if (b.contains("oppo") || b.contains("realme") || b.contains("oneplus")) {
            tries.add(new String[]{"com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"});
            tries.add(new String[]{"com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"});
        } else if (b.contains("vivo")) {
            tries.add(new String[]{"com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"});
        } else if (b.contains("huawei") || b.contains("honor")) {
            tries.add(new String[]{"com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"});
        }
        for (String[] t : tries) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(t[0], t[1]));
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        safeStart(appDetails());
    }

    // ---------------------------------------------------------------- ana sekme

    private List<Step> steps() {
        List<Step> l = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(new Step("restricted", R.string.step_restricted_title, R.string.step_restricted_text,
                    false, false, new Runnable() {
                @Override
                public void run() {
                    safeStart(appDetails());
                }
            }));
        }
        l.add(new Step("access", R.string.step_access_title, R.string.step_access_text,
                false, true, new Runnable() {
            @Override
            public void run() {
                safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        l.add(new Step("battery", R.string.step_battery_title, R.string.step_battery_text,
                false, true, new Runnable() {
            @Override
            public void run() {
                safeStart(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));
        if (hasAutostartStep()) {
            l.add(new Step("autostart", R.string.step_autostart_title, R.string.step_autostart_text,
                    false, false, new Runnable() {
                @Override
                public void run() {
                    openAutostart();
                }
            }));
        }
        l.add(new Step("assist", R.string.step_assist_title, R.string.step_assist_text,
                true, true, new Runnable() {
            @Override
            public void run() {
                safeStart(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
            }
        }));
        l.add(new Step("lock", R.string.step_lock_title, R.string.step_lock_text,
                true, false, null));
        return l;
    }

    private boolean isDone(Step s) {
        boolean manual = prefs.getBoolean(Prefs.STEP_PREFIX + s.id, false);
        switch (s.id) {
            case "restricted":
                return manual || accessibilityOn();
            case "access":
                return accessibilityOn();
            case "battery":
                return batteryOk();
            case "assist":
                return manual || assistantOn();
            default:
                return manual;
        }
    }

    private String progressText(int done, int total) {
        String[] suf = {"ı", "i", "si", "ü", "ü", "i", "sı", "si", "i", "u"};
        String tail = done + "'" + (done >= 0 && done < suf.length ? suf[done] : "i");
        return getString(R.string.wizard_progress, total, tail);
    }

    private void buildMain() {
        boolean accOn = accessibilityOn();
        if (accOn && CopyService.instance == null) {
            LinearLayout warn = card();
            ((GradientDrawable) warn.getBackground()).setColor(0xFFFDECEA);
            warn.addView(text(getString(R.string.warn_stuck_title), 16, RED, true));
            TextView t = text(getString(R.string.warn_stuck_text), 14, 0xFF5A2A27, false);
            t.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 8));
            warn.addView(t);
            Button fix = new Button(this);
            fix.setText(R.string.btn_fix);
            fix.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            });
            warn.addView(fix);
            content.addView(warn);
        }

        List<Step> steps = steps();
        int done = 0;
        for (Step s : steps) {
            if (isDone(s)) {
                done++;
            }
        }

        LinearLayout head = card();
        head.addView(text(getString(R.string.wizard_title), 18, 0xFF1A1A2E, true));
        TextView prog = text(done == steps.size() ? getString(R.string.wizard_all_done)
                : progressText(done, steps.size()), 14,
                done == steps.size() ? GREEN : 0xFF555566, false);
        prog.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 8));
        head.addView(prog);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(steps.size());
        bar.setProgress(done);
        head.addView(bar);
        if (done < steps.size()) {
            TextView pp = text(getString(R.string.wizard_protect), 13, 0xFF777788, false);
            pp.setPadding(0, Util.dp(this, 10), 0, 0);
            head.addView(pp);
        }
        content.addView(head);

        for (final Step s : steps) {
            content.addView(stepCard(s));
        }

        LinearLayout help = card();
        help.addView(text(getString(R.string.help_title), 16, 0xFF1A1A2E, true));
        TextView ht = text(getString(R.string.help_text), 14, 0xFF444455, false);
        ht.setPadding(0, Util.dp(this, 4), 0, 0);
        help.addView(ht);
        content.addView(help);

        LinearLayout tile = card();
        tile.addView(text(getString(R.string.tile_title), 16, 0xFF1A1A2E, true));
        TextView tt = text(getString(R.string.tile_text), 14, 0xFF444455, false);
        tt.setPadding(0, Util.dp(this, 4), 0, 0);
        tile.addView(tt);
        content.addView(tile);
    }

    private View stepCard(final Step s) {
        LinearLayout c = card();
        boolean done = isDone(s);
        String title = getString(s.title) + (s.optional ? " " + getString(R.string.optional) : "");
        if (done) {
            c.addView(text("✓  " + title, 15, GREEN, true));
            return c;
        }
        c.addView(text(title, 15, 0xFF1A1A2E, true));
        TextView body = text(getString(s.text), 14, 0xFF444455, false);
        body.setPadding(0, Util.dp(this, 4), 0, Util.dp(this, 6));
        c.addView(body);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (s.open != null) {
            Button open = new Button(this);
            open.setText(R.string.btn_open);
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    s.open.run();
                }
            });
            row.addView(open);
        }
        // Sistemden okunamayan (ya da okuması güvenilmeyen) adımlar elle işaretlenir
        if (!s.id.equals("access") && !s.id.equals("battery")) {
            Button ok = new Button(this);
            ok.setText(R.string.btn_done);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean(Prefs.STEP_PREFIX + s.id, true).apply();
                    showTab(0);
                }
            });
            row.addView(ok);
        }
        c.addView(row);
        return c;
    }

    // ---------------------------------------------------------------- ayarlar sekmesi

    private void buildSettings() {
        content.addView(heading(R.string.set_display));
        LinearLayout look = card();
        addSize(look);
        addAlpha(look);
        look.addView(label(R.string.set_theme));
        addRadios(look, Prefs.THEME, Prefs.THEME_PURPLE,
                new int[]{R.string.theme_dark, R.string.theme_purple,
                        R.string.theme_light, R.string.theme_auto},
                new int[]{Prefs.THEME_DARK, Prefs.THEME_PURPLE,
                        Prefs.THEME_LIGHT, Prefs.THEME_AUTO});
        content.addView(look);

        content.addView(heading(R.string.set_behavior));
        LinearLayout beh = card();
        beh.addView(label(R.string.set_mode));
        addRadios(beh, Prefs.MODE, Prefs.MODE_BOTH,
                new int[]{R.string.mode_bubble, R.string.mode_assist, R.string.mode_both},
                new int[]{Prefs.MODE_BUBBLE, Prefs.MODE_ASSIST, Prefs.MODE_BOTH});
        addSwitch(beh, R.string.set_haptic, Prefs.HAPTIC, true);
        addSwitch(beh, R.string.set_snap, Prefs.SNAP, true);
        addSwitch(beh, R.string.set_hide_games, Prefs.HIDE_GAMES, true);
        addSwitch(beh, R.string.set_split, Prefs.SPLIT, true);
        addSwitch(beh, R.string.set_fast, Prefs.FAST, false);
        addSwitch(beh, R.string.set_all_desc, Prefs.ALL_DESC, false);
        content.addView(beh);

        content.addView(heading(R.string.set_hidden));
        LinearLayout hid = card();
        TextView hint = text(getString(R.string.set_hidden_hint), 13, 0xFF777788, false);
        hint.setPadding(0, 0, 0, Util.dp(this, 6));
        hid.addView(hint);
        buildHiddenList(hid);
        content.addView(hid);
    }

    private TextView label(int res) {
        TextView t = text(getString(res), 15, 0xFF1A1A2E, true);
        t.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 4));
        return t;
    }

    private void addSize(LinearLayout parent) {
        parent.addView(label(R.string.set_size));
        final LogoView preview = new LogoView(this);
        int size = prefs.getInt(Prefs.SIZE, 52);
        final LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setMinimumHeight(Util.dp(this, 84));
        box.addView(preview, new LinearLayout.LayoutParams(Util.dp(this, size), Util.dp(this, size)));
        parent.addView(box);

        final TextView value = text(size + " dp", 13, 0xFF777788, false);
        SeekBar sb = new SeekBar(this);
        sb.setMax(44); // 36..80
        sb.setProgress(size - 36);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + 36;
                value.setText(v + " dp");
                preview.setLayoutParams(new LinearLayout.LayoutParams(Util.dp(MainActivity.this, v),
                        Util.dp(MainActivity.this, v)));
                if (fromUser) {
                    prefs.edit().putInt(Prefs.SIZE, v).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        parent.addView(sb);
        parent.addView(value);
    }

    private void addAlpha(LinearLayout parent) {
        parent.addView(label(R.string.set_alpha));
        int a = prefs.getInt(Prefs.ALPHA, 45);
        final TextView value = text("%" + a, 13, 0xFF777788, false);
        SeekBar sb = new SeekBar(this);
        sb.setMax(85); // 15..100
        sb.setProgress(a - 15);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + 15;
                value.setText("%" + v);
                if (fromUser) {
                    prefs.edit().putInt(Prefs.ALPHA, v).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        parent.addView(sb);
        parent.addView(value);
    }

    private void addSwitch(LinearLayout parent, int labelRes, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(labelRes);
        sw.setTextSize(15);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setPadding(0, Util.dp(this, 10), 0, Util.dp(this, 10));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(key, checked).apply();
            }
        });
        parent.addView(sw);
    }

    private void addRadios(LinearLayout parent, final String key, int def,
                           int[] labels, final int[] values) {
        RadioGroup g = new RadioGroup(this);
        int current = prefs.getInt(key, def);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextSize(15);
            rb.setId(100 + i);
            g.addView(rb);
            if (values[i] == current) {
                rb.setChecked(true);
            }
        }
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int id) {
                int idx = id - 100;
                if (idx >= 0 && idx < values.length) {
                    prefs.edit().putInt(key, values[idx]).apply();
                }
            }
        });
        parent.addView(g);
    }

    private void buildHiddenList(LinearLayout parent) {
        Set<String> set = prefs.getStringSet(Prefs.HIDDEN_APPS, null);
        if (set == null || set.isEmpty()) {
            parent.addView(text(getString(R.string.set_hidden_none), 14, 0xFF555566, false));
            return;
        }
        PackageManager pm = getPackageManager();
        for (final String pkg : new ArrayList<>(set)) {
            String name = pkg;
            try {
                name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Exception ignored) {
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = text(name, 15, 0xFF1A1A2E, false);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button rm = new Button(this);
            rm.setText(R.string.btn_remove);
            rm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Set<String> copy = new HashSet<>(
                            prefs.getStringSet(Prefs.HIDDEN_APPS, new HashSet<String>()));
                    copy.remove(pkg);
                    prefs.edit().putStringSet(Prefs.HIDDEN_APPS, copy).apply();
                    showTab(1);
                }
            });
            row.addView(rm);
            parent.addView(row);
        }
    }
}
