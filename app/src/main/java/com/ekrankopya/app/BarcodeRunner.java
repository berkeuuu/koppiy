package com.ekrankopya.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migpack "Okutmanız Gereken Barkodlar" ekranında barkodları okuyup alttaki alana tek tek
 * yazar ve Ekle'ye basar. Her adımı doğrular, bir şey ters giderse durur.
 * "Teslim Almayı Bitir" varsayılan olarak önce sorar; çıkan uyarıları (donuk çantası gibi)
 * hiçbir zaman kendisi onaylamaz.
 */
final class BarcodeRunner {

    private static final Locale TR = new Locale("tr", "TR");
    private static final Pattern BARCODE = Pattern.compile("(?<!\\d)\\d{12,20}(?!\\d)");
    private static final Pattern ORDER = Pattern.compile("(\\d{6,12})\\s*numaral");

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(TR);
    }

    // ------------------------------------------------------------------ ekran görüntüsü (düğüm ağacı)

    static final class Rec {
        AccessibilityNodeInfo node;
        Rec parent;
        final List<Rec> children = new ArrayList<>();
        String cls = "";
        String text;
        String desc;
        String hint;
        String id;
        final Rect b = new Rect();
        boolean click;
        boolean scroll;
        boolean edit;
        boolean checked;
        boolean selected;
        boolean visible;
        int depth;

        /** Aramalar için yazı, açıklama ve ipucunun küçük harfli birleşimi. */
        String all() {
            return lower(text) + "\n" + lower(desc) + "\n" + lower(hint);
        }
    }

    static final class Found {
        final String code;
        final Rec rec;

        Found(String code, Rec rec) {
            this.code = code;
            this.rec = rec;
        }
    }

    static final class Snapshot {
        final List<Rec> all = new ArrayList<>();

        static Snapshot take(AccessibilityNodeInfo root) {
            Snapshot s = new Snapshot();
            s.build(root, null, 0);
            return s;
        }

        private Rec build(AccessibilityNodeInfo n, Rec parent, int depth) {
            if (n == null || depth > 60 || all.size() > 4000) {
                return null;
            }
            Rec r = new Rec();
            r.node = n;
            r.parent = parent;
            r.depth = depth;
            CharSequence cn = n.getClassName();
            r.cls = cn == null ? "" : cn.toString();
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            r.text = t == null ? null : t.toString();
            r.desc = d == null ? null : d.toString();
            if (Build.VERSION.SDK_INT >= 26) {
                CharSequence h = n.getHintText();
                r.hint = h == null ? null : h.toString();
            }
            r.id = n.getViewIdResourceName();
            n.getBoundsInScreen(r.b);
            r.click = n.isClickable();
            r.scroll = n.isScrollable();
            r.edit = n.isEditable() || r.cls.contains("EditText");
            r.checked = n.isChecked();
            r.selected = n.isSelected();
            r.visible = n.isVisibleToUser();
            all.add(r);
            int count = n.getChildCount();
            for (int i = 0; i < count; i++) {
                Rec c = build(n.getChild(i), r, depth + 1);
                if (c != null) {
                    r.children.add(c);
                }
            }
            return r;
        }

        boolean isBarcodeScreen() {
            for (Rec r : all) {
                String l = r.all();
                if (l.contains("gereken barkod") || l.contains("kasa barkodunu")) {
                    return true;
                }
            }
            return false;
        }

        String orderNo() {
            for (Rec r : all) {
                for (String s : new String[]{r.text, r.desc}) {
                    Matcher m = ORDER.matcher(lower(s));
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
            return null;
        }

        /** Ekranda görünen barkodlar, soldan sağa. */
        List<Found> founds(String order) {
            List<Found> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Rec r : all) {
                if (!r.visible || r.edit || r.b.width() <= 0) {
                    continue;
                }
                for (String s : new String[]{r.text, r.desc}) {
                    if (s == null) {
                        continue;
                    }
                    Matcher m = BARCODE.matcher(s);
                    while (m.find()) {
                        String c = m.group();
                        if (order != null && !c.startsWith(order)) {
                            continue;
                        }
                        if (seen.add(c)) {
                            out.add(new Found(c, r));
                        }
                    }
                }
            }
            Collections.sort(out, new Comparator<Found>() {
                @Override
                public int compare(Found a, Found b) {
                    if (a.rec.b.left != b.rec.b.left) {
                        return a.rec.b.left < b.rec.b.left ? -1 : 1;
                    }
                    return Integer.compare(a.rec.b.top, b.rec.b.top);
                }
            });
            return out;
        }

        /** Görünen barkodların konumlarıyla imzası: kaydırma olup olmadığını anlamak için. */
        String signature(String order) {
            StringBuilder sb = new StringBuilder();
            for (Found f : founds(order)) {
                sb.append(f.code).append('@').append(f.rec.b.left / 4).append(',');
            }
            return sb.toString();
        }

        private void codesIn(Rec r, Set<String> out) {
            for (String s : new String[]{r.text, r.desc}) {
                if (s == null) {
                    continue;
                }
                Matcher m = BARCODE.matcher(s);
                while (m.find()) {
                    out.add(m.group());
                }
            }
            for (Rec c : r.children) {
                codesIn(c, out);
            }
        }

        private boolean hasSignal(Rec r) {
            if (r.checked || r.selected) {
                return true;
            }
            String l = r.all();
            String[] kw = {"tamam", "okundu", "okutuldu", "onay", "check", "done"};
            for (String k : kw) {
                if (l.contains(k)) {
                    return true;
                }
            }
            for (Rec c : r.children) {
                if (hasSignal(c)) {
                    return true;
                }
            }
            return false;
        }

        /** Kartta "okutuldu" işareti (tik) görünüyor mu? Sadece tek barkod içeren kapsayıcılara bakar. */
        boolean scanned(Found f) {
            Rec a = f.rec;
            for (int lvl = 0; lvl < 4 && a != null; lvl++, a = a.parent) {
                Set<String> codes = new HashSet<>();
                codesIn(a, codes);
                if (codes.size() > 1) {
                    break;
                }
                if (hasSignal(a)) {
                    return true;
                }
            }
            return false;
        }

        Rec findEdit() {
            Rec best = null;
            for (Rec r : all) {
                if (r.edit && r.visible && (best == null || r.b.top > best.b.top)) {
                    best = r;
                }
            }
            return best;
        }

        /** Yazısı tam olarak verilen düğme (örn. "ekle"); ekranda en altta olanı seçer. */
        Rec findButton(String exactLower) {
            Rec best = null;
            for (Rec r : all) {
                if (r.edit) {
                    continue;
                }
                String t = lower(r.text).trim();
                String d = lower(r.desc).trim();
                if ((t.equals(exactLower) || d.equals(exactLower)) && (best == null || r.b.top > best.b.top)) {
                    best = r;
                }
            }
            return best;
        }

        Rec findContains(String sub) {
            Rec best = null;
            for (Rec r : all) {
                if (r.all().contains(sub) && (best == null || r.b.top > best.b.top)) {
                    best = r;
                }
            }
            return best;
        }

        boolean hasText(String sub) {
            for (Rec r : all) {
                if (r.all().contains(sub)) {
                    return true;
                }
            }
            return false;
        }

        Rec findScrollable(String order) {
            List<Found> f = founds(order);
            if (!f.isEmpty()) {
                for (Rec a = f.get(0).rec; a != null; a = a.parent) {
                    if (a.scroll) {
                        return a;
                    }
                }
            }
            Rec best = null;
            for (Rec r : all) {
                if (r.scroll && r.visible && (best == null || r.b.width() > best.b.width())) {
                    best = r;
                }
            }
            return best;
        }

        Set<String> labels() {
            Set<String> out = new HashSet<>();
            for (Rec r : all) {
                if (r.text != null && !r.text.trim().isEmpty()) {
                    out.add(r.text.trim());
                }
                if (r.desc != null && !r.desc.trim().isEmpty()) {
                    out.add(r.desc.trim());
                }
            }
            return out;
        }
    }

    static boolean click(Rec r) {
        for (Rec x = r; x != null; x = x.parent) {
            if (x.click && x.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }
        return r.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    /** Barkod ekranı mı? (Dokunma anında hızlı kontrol.) */
    static boolean looksLikeBarcodeScreen(AccessibilityNodeInfo root) {
        try {
            return Snapshot.take(root).isBarcodeScreen();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ tanı dökümü

    private static String cut(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }

    /** "Ekranı incele": ekranın düğüm ağacını ve barkod çözümlemesini metin olarak verir. */
    static String dump(AccessibilityNodeInfo root) {
        Snapshot s = Snapshot.take(root);
        StringBuilder sb = new StringBuilder();
        sb.append("Koppiy ekran dökümü\n");
        sb.append("Cihaz: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android SDK ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("Paket: ").append(root.getPackageName()).append('\n');
        sb.append("\n--- Çözümleme ---\n");
        sb.append("Barkod ekranı: ").append(s.isBarcodeScreen() ? "evet" : "hayır").append('\n');
        String order = s.orderNo();
        sb.append("Sipariş no: ").append(order).append('\n');
        List<Found> fs = s.founds(order);
        sb.append("Görünen barkodlar: ").append(fs.size()).append('\n');
        for (Found f : fs) {
            sb.append("  ").append(f.code).append("  okutuldu işareti: ")
                    .append(s.scanned(f) ? "var" : "yok").append('\n');
        }
        Rec e = s.findEdit();
        sb.append("Yazma alanı: ").append(e == null ? "YOK" : e.cls + " id=" + e.id).append('\n');
        Rec ekle = s.findButton("ekle");
        sb.append("Ekle düğmesi: ").append(ekle == null ? "YOK"
                : "var, tıklanabilir=" + ekle.click).append('\n');
        Rec bitir = s.findContains("teslim almayı bitir");
        sb.append("Bitir düğmesi: ").append(bitir == null ? "YOK"
                : "var, tıklanabilir=" + bitir.click).append('\n');
        Rec sc = s.findScrollable(order);
        sb.append("Kaydırılabilir düğüm: ").append(sc == null ? "YOK"
                : sc.cls + " bounds=" + sc.b.toShortString()).append('\n');
        sb.append("\n--- Düğümler (C tıklanır, S kayar, E yazılır, K işaretli, L seçili, V görünür) ---\n");
        int lines = 0;
        for (Rec r : s.all) {
            if (lines++ > 700) {
                sb.append("... (kısaltıldı)\n");
                break;
            }
            for (int i = 0; i < Math.min(r.depth, 20); i++) {
                sb.append(' ');
            }
            String shortCls = r.cls.substring(r.cls.lastIndexOf('.') + 1);
            sb.append(shortCls).append(" [")
                    .append(r.click ? "C" : "").append(r.scroll ? "S" : "").append(r.edit ? "E" : "")
                    .append(r.checked ? "K" : "").append(r.selected ? "L" : "").append(r.visible ? "V" : "")
                    .append("]");
            if (r.id != null) {
                sb.append(" id=").append(r.id);
            }
            if (r.text != null) {
                sb.append(" text=\"").append(cut(r.text)).append('"');
            }
            if (r.desc != null) {
                sb.append(" desc=\"").append(cut(r.desc)).append('"');
            }
            if (r.hint != null) {
                sb.append(" hint=\"").append(cut(r.hint)).append('"');
            }
            sb.append(" b=").append(r.b.toShortString()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ çalıştırıcı

    private static final class Cancel extends RuntimeException {
    }

    private static final class Fail extends RuntimeException {
        Fail(String m) {
            super(m);
        }
    }

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final Palette pal;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile boolean running;
    private Thread worker;
    private View panel;

    BarcodeRunner(AccessibilityService svc, WindowManager wm) {
        this.svc = svc;
        this.wm = wm;
        this.prefs = Prefs.get(svc);
        this.pal = Palette.of(svc);
    }

    boolean isRunning() {
        return running;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        cancelled = false;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runInner();
                } catch (Cancel c) {
                    end("Durduruldum.");
                } catch (Fail f) {
                    end(f.getMessage());
                } catch (Throwable t) {
                    end("Beklenmeyen bir hata oldu (" + t.getClass().getSimpleName() + "). Durdum.");
                } finally {
                    running = false;
                }
            }
        }, "koppiy-barkod");
        worker.start();
    }

    private void stop() {
        cancelled = true;
        if (worker != null) {
            worker.interrupt();
        }
    }

    // ---- küçük pencere (durum, soru, sonuç)

    private void removePanelNow() {
        if (panel != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
    }

    private void hidePanel() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                removePanelNow();
            }
        });
    }

    private Button makeButton(String label, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(svc);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(Util.dp(svc, 18));
        b.setBackground(d);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Util.dp(svc, 44));
        lp.leftMargin = Util.dp(svc, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private void showPanel(final String text, final String[] labels, final Runnable[] actions,
                           final boolean primary) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                removePanelNow();
                LinearLayout box = new LinearLayout(svc);
                box.setOrientation(LinearLayout.VERTICAL);
                int p = Util.dp(svc, 16);
                box.setPadding(p, p, p, p);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(pal.panelBg);
                bg.setCornerRadius(Util.dp(svc, 18));
                box.setBackground(bg);

                TextView tv = new TextView(svc);
                tv.setText(text);
                tv.setTextColor(pal.panelText);
                tv.setTextSize(15);
                box.addView(tv);

                LinearLayout row = new LinearLayout(svc);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.END);
                row.setPadding(0, Util.dp(svc, 12), 0, 0);
                for (int i = 0; i < labels.length; i++) {
                    final Runnable a = actions[i];
                    boolean main = primary && i == 0;
                    row.addView(makeButton(labels[i],
                            main ? pal.copyBg : pal.neutralBg,
                            main ? pal.copyText : pal.neutralText,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    a.run();
                                }
                            }));
                }
                box.addView(row);

                DisplayMetrics dm = svc.getResources().getDisplayMetrics();
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        Math.min(Util.dp(svc, 320), dm.widthPixels - Util.dp(svc, 32)),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.CENTER;
                try {
                    wm.addView(box, lp);
                    panel = box;
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void status(String text) {
        showPanel(text, new String[]{"Dur"}, new Runnable[]{new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }}, false);
    }

    private void end(String text) {
        showPanel(text, new String[]{"Tamam"}, new Runnable[]{new Runnable() {
            @Override
            public void run() {
                hidePanel();
            }
        }}, true);
    }

    private boolean ask(String text, String yes, String no) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = new boolean[1];
        showPanel(text, new String[]{yes, no}, new Runnable[]{new Runnable() {
            @Override
            public void run() {
                result[0] = true;
                latch.countDown();
            }
        }, new Runnable() {
            @Override
            public void run() {
                result[0] = false;
                latch.countDown();
            }
        }}, true);
        try {
            while (!latch.await(200, TimeUnit.MILLISECONDS)) {
                if (cancelled) {
                    throw new Cancel();
                }
            }
        } catch (InterruptedException e) {
            throw new Cancel();
        }
        return result[0];
    }

    // ---- yardımcılar

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new Cancel();
        }
        if (cancelled) {
            throw new Cancel();
        }
    }

    private Snapshot snap() {
        AccessibilityNodeInfo r = svc.getRootInActiveWindow();
        return r == null ? null : Snapshot.take(r);
    }

    /** Barkod ekranının güncel görüntüsü; ekrandan çıkıldıysa durur. */
    private Snapshot needScreen() {
        for (int i = 0; i < 4; i++) {
            Snapshot s = snap();
            if (s != null) {
                if (!s.isBarcodeScreen()) {
                    throw new Fail("Barkod ekranından çıkıldı, durdum.");
                }
                return s;
            }
            sleep(300);
        }
        throw new Fail("Ekranı okuyamadım, durdum.");
    }

    private void swipe(boolean forward, Snapshot s, String order) {
        if (Build.VERSION.SDK_INT < 24) {
            return;
        }
        DisplayMetrics dm = svc.getResources().getDisplayMetrics();
        List<Found> f = s.founds(order);
        float y = f.isEmpty() ? dm.heightPixels * 0.2f : f.get(0).rec.b.centerY();
        float x1 = dm.widthPixels * (forward ? 0.85f : 0.15f);
        float x2 = dm.widthPixels * (forward ? 0.15f : 0.85f);
        Path p = new Path();
        p.moveTo(x1, y);
        p.lineTo(x2, y);
        GestureDescription.Builder gb = new GestureDescription.Builder();
        gb.addStroke(new GestureDescription.StrokeDescription(p, 0, 280));
        final CountDownLatch latch = new CountDownLatch(1);
        boolean ok = svc.dispatchGesture(gb.build(), new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription g) {
                latch.countDown();
            }

            @Override
            public void onCancelled(GestureDescription g) {
                latch.countDown();
            }
        }, null);
        if (!ok) {
            return;
        }
        try {
            latch.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new Cancel();
        }
    }

    /** Listeyi bir sayfa kaydırır; görünen kartlar değiştiyse true. */
    private boolean scrollStep(boolean forward, String order) {
        Snapshot s0 = needScreen();
        String sig0 = s0.signature(order);
        Rec sc = s0.findScrollable(order);
        if (sc != null) {
            boolean acted = sc.node.performAction(forward
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            if (acted) {
                sleep(600);
                if (!needScreen().signature(order).equals(sig0)) {
                    return true;
                }
            }
        }
        // Yedek: parmakla kaydırma hareketi
        swipe(forward, s0, order);
        sleep(700);
        return !needScreen().signature(order).equals(sig0);
    }

    private void collect(Snapshot s, String order, Map<String, Boolean> found) {
        for (Found f : s.founds(order)) {
            Boolean old = found.get(f.code);
            boolean sc = s.scanned(f);
            found.put(f.code, (old != null && old) || sc);
        }
    }

    private void waitToastGone() {
        long end = System.currentTimeMillis() + 3500;
        while (System.currentTimeMillis() < end) {
            Snapshot s = snap();
            if (s != null && !s.hasText("barkod tarandı")) {
                return;
            }
            sleep(250);
        }
        throw new Fail("\"Barkod tarandı\" yazısı ekranda kalıyor, sonucu doğrulayamıyorum. Durdum.");
    }

    private boolean waitSuccess() {
        long end = System.currentTimeMillis() + 4500;
        while (System.currentTimeMillis() < end) {
            sleep(200);
            Snapshot s = snap();
            if (s != null && s.hasText("barkod tarandı")) {
                return true;
            }
        }
        return false;
    }

    private void putText(String code) {
        Snapshot s = needScreen();
        Rec e = s.findEdit();
        if (e == null) {
            throw new Fail("Barkod yazma alanını bulamadım. Durdum.");
        }
        e.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, code);
        boolean ok = e.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        if (!ok) {
            click(e);
            sleep(400);
            Rec e2 = needScreen().findEdit();
            if (e2 != null) {
                ok = e2.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                e = e2;
            }
        }
        if (!ok) {
            throw new Fail("Alana yazamadım. Durdum.");
        }
        sleep(300);
        e.node.refresh();
        CharSequence t = e.node.getText();
        if (t != null) {
            String v = t.toString().replace(" ", "");
            if (v.matches("\\d{6,}") && !v.equals(code)) {
                throw new Fail("Alana yanlış numara yazıldı (" + v + "). Durdum.");
            }
        }
    }

    // ---- ana akış

    private void runInner() {
        status("Barkodlar okunuyor…");
        Snapshot first = needScreen();
        String order = first.orderNo();

        // 1) Listeyi en başa al
        for (int i = 0; i < 15; i++) {
            if (!scrollStep(false, order)) {
                break;
            }
        }

        // 2) Başından sonuna kaydırarak tüm barkodları topla
        Map<String, Boolean> found = new LinkedHashMap<>();
        collect(needScreen(), order, found);
        for (int i = 0; i < 40; i++) {
            if (!scrollStep(true, order)) {
                break;
            }
            collect(needScreen(), order, found);
        }
        if (found.isEmpty()) {
            throw new Fail("Ekranda barkod bulamadım. Ayarlar'daki \"Ekranı incele\" ile ekranı bana gönderebilirsin.");
        }

        // 3) Tam okunmayanları ayıkla: en sık görülen uzunluğa uymayanlar atlanır
        Map<Integer, Integer> lens = new LinkedHashMap<>();
        for (String c : found.keySet()) {
            Integer n = lens.get(c.length());
            lens.put(c.length(), n == null ? 1 : n + 1);
        }
        int modal = -1;
        int modalCount = 0;
        for (Map.Entry<Integer, Integer> e : lens.entrySet()) {
            if (e.getValue() > modalCount || (e.getValue() == modalCount && e.getKey() > modal)) {
                modal = e.getKey();
                modalCount = e.getValue();
            }
        }
        int skipped = 0;
        List<String> all = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : found.entrySet()) {
            if (e.getKey().length() != modal) {
                skipped++;
                continue;
            }
            all.add(e.getKey());
            if (!e.getValue()) {
                pending.add(e.getKey());
            }
        }
        int already = all.size() - pending.size();

        StringBuilder msg = new StringBuilder();
        msg.append(all.size()).append(" barkod bulundu");
        if (already > 0) {
            msg.append(", ").append(already).append(" tanesi zaten okutulmuş görünüyor");
        }
        if (skipped > 0) {
            msg.append(", ").append(skipped).append(" tanesi tam okunamadığı için atlandı");
        }
        msg.append(".");

        if (pending.isEmpty()) {
            msg.append(" Eklenecek barkod kalmamış.");
            finishStage(msg.toString());
            return;
        }
        msg.append(" ").append(pending.size()).append(" tanesi eklensin mi?");
        if (!ask(msg.toString(), "Ekle", "İptal")) {
            throw new Fail("Eklemedim.");
        }

        // 4) Tek tek ekle
        int n = pending.size();
        for (int i = 0; i < n; i++) {
            String code = pending.get(i);
            status("Ekleniyor " + (i + 1) + "/" + n + "\n" + code);
            waitToastGone();
            putText(code);
            sleep(250);
            Rec ekle = needScreen().findButton("ekle");
            if (ekle == null) {
                throw new Fail("Ekle düğmesini bulamadım. Durdum.");
            }
            if (!click(ekle)) {
                throw new Fail("Ekle düğmesine basamadım. Durdum.");
            }
            if (!waitSuccess()) {
                throw new Fail(code + " için \"Barkod tarandı\" yazısını göremedim. Eklenmemiş olabilir, durdum. "
                        + i + "/" + n + " eklendi.");
            }
            sleep(300);
        }
        finishStage(n + " barkod eklendi.");
    }

    private void finishStage(String done) {
        boolean askFinish = prefs.getBoolean(Prefs.BARCODE_ASK, true);
        if (askFinish) {
            if (!ask(done + " Teslim almayı bitirelim mi?", "Bitir", "Hayır")) {
                end(done + " Bitirmedim.");
                return;
            }
        }
        status("Teslim almayı bitiriyorum…");
        Snapshot s = needScreen();
        Rec btn = s.findContains("teslim almayı bitir");
        if (btn == null) {
            throw new Fail("\"Teslim Almayı Bitir\" düğmesini bulamadım. Durdum.");
        }
        Set<String> before = s.labels();
        if (!click(btn)) {
            throw new Fail("\"Teslim Almayı Bitir\" düğmesine basamadım.");
        }
        for (int i = 0; i < 12; i++) {
            sleep(400);
            Snapshot n = snap();
            if (n == null) {
                continue;
            }
            if (!n.isBarcodeScreen()) {
                end("Teslim alma bitti.");
                return;
            }
            // Yeni çıkan yazı varsa (örn. donuk çantası uyarısı) onu göster, ASLA onaylama.
            StringBuilder fresh = new StringBuilder();
            for (String l : n.labels()) {
                if (!before.contains(l) && !lower(l).contains("barkod tarandı") && !l.matches("[\\d\\s]*")) {
                    if (fresh.length() > 0) {
                        fresh.append(" / ");
                    }
                    fresh.append(l.replace('\n', ' '));
                }
            }
            if (fresh.length() > 0) {
                String f = fresh.length() > 200 ? fresh.substring(0, 200) + "…" : fresh.toString();
                end("Uygulama bir uyarı gösterdi: “" + f + "”\nOnay sende, ben dokunmadım.");
                return;
            }
        }
        end("Bitir'e bastım ama ekranda değişiklik göremedim. Kontrol et.");
    }
}
