package com.ekrankopya.app;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CopyService extends AccessibilityService {

    private static class Item {
        Rect bounds;
        String text;
        boolean selected;
        View view;
    }

    /** Böl penceresindeki tek parça (satır, cümle ya da kelime). */
    private static class Piece {
        String text;
        String sep;
        boolean selected;
        TextView view;
    }

    /** Uzun yazıyı sınırlı yükseklikte kaydırılabilir tutar. */
    private static class MaxHeightScrollView extends ScrollView {
        private final int maxHeight;

        MaxHeightScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        }
    }

    /** Servis çalışırken kendisi, değilse null. Asistan hareketi ve kutucuk bunu kullanır. */
    static CopyService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private Palette pal;

    // Logo düğmesi
    private LogoView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private boolean bubbleAdded;
    private boolean hiddenForApp;
    private String currentPkg;
    private final Map<String, Boolean> gameCache = new HashMap<>();

    // Seçim ekranı
    private FrameLayout overlay;
    private Button copyButton;
    private Button selectAllButton;
    private final List<Item> items = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    // Böl penceresi
    private final List<View> splitViews = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();
    private Button splitCopyButton;

    /**
     * Asistan hareketinden (orta tuşa basılı tutma) seçim modunu başlatır.
     * 0 = başladı, 1 = servis çalışmıyor, 2 = Koppiy ana anahtardan kapatılmış.
     */
    static int trigger() {
        final CopyService s = instance;
        if (s == null) {
            return 1;
        }
        if (!s.prefs.getBoolean(Prefs.ENABLED, true)) {
            return 2;
        }
        s.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                s.showSelection(2);
            }
        }, 350);
        return 0;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = Prefs.get(this);
        pal = Palette.of(this);
        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                applyPrefs();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        if (bubble == null) {
            createBubble();
        }
        applyPrefs();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Sadece hangi uygulamanın açık olduğunu izliyoruz (oyunlarda düğmeyi gizlemek için).
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence p = event.getPackageName();
        if (p == null) {
            return;
        }
        String pkg = p.toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")
                || pkg.equals(currentPkg)) {
            return;
        }
        currentPkg = pkg;
        evaluateHidden();
    }

    @Override
    public void onInterrupt() {
        closeOverlay();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (bubble != null && bubbleAdded) {
            clampBubble();
            try {
                wm.updateViewLayout(bubble, bubbleParams);
            } catch (Exception ignored) {
            }
            applyPrefs();
        }
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        if (prefs != null && prefListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        closeOverlay();
        if (bubble != null && wm != null) {
            try {
                wm.removeView(bubble);
            } catch (Exception ignored) {
            }
            bubble = null;
            bubbleAdded = false;
        }
        super.onDestroy();
    }

    // ---------- Logo düğmesi ----------

    private void createBubble() {
        int size = Util.dp(this, prefs.getInt(Prefs.SIZE, 52));

        bubble = new LogoView(this);
        bubble.setPalette(pal);

        bubbleParams = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = prefs.getInt(Prefs.BUBBLE_X, Util.dp(this, 4));
        bubbleParams.y = prefs.getInt(Prefs.BUBBLE_Y, Util.dp(this, 240));
        clampBubble();
        bubble.setAlpha(idleAlpha());

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float touchX;
            private float touchY;
            private boolean moved;
            private boolean longPressed;

            private final Runnable longPress = new Runnable() {
                @Override
                public void run() {
                    if (!moved) {
                        longPressed = true;
                        hideCurrentApp();
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleParams.x;
                        startY = bubbleParams.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        longPressed = false;
                        bubble.animate().cancel();
                        bubble.setAlpha(1f);
                        handler.postDelayed(longPress, 800);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - touchX;
                        float dy = e.getRawY() - touchY;
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                            moved = true;
                            handler.removeCallbacks(longPress);
                        }
                        if (moved) {
                            bubbleParams.x = startX + (int) dx;
                            bubbleParams.y = startY + (int) dy;
                            clampBubble();
                            wm.updateViewLayout(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPress);
                        if (moved) {
                            snapAndSave();
                        } else if (e.getAction() == MotionEvent.ACTION_UP && !longPressed) {
                            Util.buzz(CopyService.this);
                            showSelection(0);
                        }
                        fadeBubble();
                        return true;
                    default:
                        return false;
                }
            }
        });

        wm.addView(bubble, bubbleParams);
        bubbleAdded = true;
    }

    private float idleAlpha() {
        int percent = prefs.getInt(Prefs.ALPHA, 45);
        return Math.max(0.15f, Math.min(1f, percent / 100f));
    }

    /** Dokunma bitince düğme biraz bekleyip yeniden solar. */
    private void fadeBubble() {
        if (bubble == null) {
            return;
        }
        bubble.animate().alpha(idleAlpha()).setStartDelay(1200).setDuration(350).start();
    }

    private void clampBubble() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, dm.widthPixels - bubbleParams.width));
        bubbleParams.y = Math.max(0, Math.min(bubbleParams.y, dm.heightPixels - bubbleParams.height));
    }

    /** Bırakınca kenara yapışma ayarı açıksa en yakın kenara kayar, sonra yeri kaydedilir. */
    private void snapAndSave() {
        if (!prefs.getBoolean(Prefs.SNAP, true)) {
            savePosition();
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = Util.dp(this, 4);
        boolean left = bubbleParams.x + bubbleParams.width / 2 < dm.widthPixels / 2;
        int target = left ? margin : dm.widthPixels - bubbleParams.width - margin;
        ValueAnimator a = ValueAnimator.ofInt(bubbleParams.x, target);
        a.setDuration(160);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (bubble == null || !bubbleAdded) {
                    return;
                }
                bubbleParams.x = (Integer) animation.getAnimatedValue();
                try {
                    wm.updateViewLayout(bubble, bubbleParams);
                } catch (Exception ignored) {
                }
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                savePosition();
            }
        });
        a.start();
    }

    private void savePosition() {
        prefs.edit()
                .putInt(Prefs.BUBBLE_X, bubbleParams.x)
                .putInt(Prefs.BUBBLE_Y, bubbleParams.y)
                .apply();
    }

    /** Ayarlar değişince boyut, saydamlık, tema ve görünürlüğü yeniler. */
    private void applyPrefs() {
        if (bubble == null) {
            return;
        }
        pal = Palette.of(this);
        bubble.setPalette(pal);

        int size = Util.dp(this, prefs.getInt(Prefs.SIZE, 52));
        if (bubbleParams.width != size || bubbleParams.height != size) {
            bubbleParams.width = size;
            bubbleParams.height = size;
            clampBubble();
            if (bubbleAdded) {
                try {
                    wm.updateViewLayout(bubble, bubbleParams);
                } catch (Exception ignored) {
                }
            }
        }
        bubble.setAlpha(idleAlpha());
        evaluateHidden();
    }

    private void updateBubbleVisibility() {
        if (bubble == null) {
            return;
        }
        boolean enabled = prefs.getBoolean(Prefs.ENABLED, true);
        int mode = prefs.getInt(Prefs.MODE, Prefs.MODE_BOTH);
        boolean show = enabled && mode != Prefs.MODE_ASSIST && overlay == null && !hiddenForApp;
        bubble.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ---------- Oyunlarda ve seçilen uygulamalarda gizleme ----------

    private void evaluateHidden() {
        boolean hide = false;
        if (currentPkg != null) {
            if (prefs.getBoolean(Prefs.HIDE_GAMES, true) && isGame(currentPkg)) {
                hide = true;
            }
            Set<String> manual = prefs.getStringSet(Prefs.HIDDEN_APPS, null);
            if (manual != null && manual.contains(currentPkg)) {
                hide = true;
            }
        }
        hiddenForApp = hide;
        updateBubbleVisibility();
    }

    @SuppressWarnings("deprecation")
    private boolean isGame(String pkg) {
        Boolean cached = gameCache.get(pkg);
        if (cached != null) {
            return cached;
        }
        boolean game = false;
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            if (Build.VERSION.SDK_INT >= 26) {
                game = ai.category == ApplicationInfo.CATEGORY_GAME;
            }
            if (!game) {
                game = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            }
        } catch (Exception ignored) {
        }
        gameCache.put(pkg, game);
        return game;
    }

    /** Düğmeye basılı tutunca açık olan uygulamada düğme gizlenir. */
    private void hideCurrentApp() {
        if (currentPkg == null) {
            toast(R.string.toast_no_app);
            return;
        }
        Set<String> set = new HashSet<>();
        Set<String> old = prefs.getStringSet(Prefs.HIDDEN_APPS, null);
        if (old != null) {
            set.addAll(old);
        }
        set.add(currentPkg);
        prefs.edit().putStringSet(Prefs.HIDDEN_APPS, set).apply();
        Util.buzz(this);
        toast(R.string.toast_app_hidden);
    }

    // ---------- Ekrandaki yazıları toplama ----------

    private void showSelection(final int retries) {
        if (overlay != null) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        CharSequence pkg = root == null ? null : root.getPackageName();
        boolean ownWindow = pkg != null && pkg.toString().equals(getPackageName());

        // Asistan penceresi kapanırken kısa bir süre kendi penceremiz görünebilir, tekrar dene.
        if ((root == null || ownWindow) && retries > 0) {
            if (root != null && Build.VERSION.SDK_INT < 33) {
                root.recycle();
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showSelection(retries - 1);
                }
            }, 300);
            return;
        }

        if (root == null) {
            toast(R.string.toast_read_fail);
            return;
        }
        items.clear();
        seen.clear();
        boolean showAllDesc = prefs.getBoolean(Prefs.ALL_DESC, false);
        collect(root, showAllDesc);
        if (Build.VERSION.SDK_INT < 33) {
            root.recycle();
        }
        if (items.isEmpty()) {
            toast(R.string.toast_no_text);
            return;
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                if (a.bounds.top != b.bounds.top) {
                    return a.bounds.top < b.bounds.top ? -1 : 1;
                }
                if (a.bounds.left != b.bounds.left) {
                    return a.bounds.left < b.bounds.left ? -1 : 1;
                }
                return 0;
            }
        });
        buildOverlay();
    }

    private void collect(AccessibilityNodeInfo node, boolean showAllDesc) {
        if (node == null || !node.isVisibleToUser()) {
            return;
        }

        CharSequence t = null;
        if (!node.isPassword()) {
            t = node.getText();
            if ((t == null || t.length() == 0) && node.getChildCount() == 0) {
                t = descriptionOf(node, showAllDesc);
            }
        }

        if (t != null && t.toString().trim().length() > 0) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.width() > 0 && r.height() > 0) {
                String text = t.toString();
                String key = text + "|" + r.toShortString();
                if (seen.add(key)) {
                    Item it = new Item();
                    it.bounds = r;
                    it.text = text;
                    items.add(it);
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collect(child, showAllDesc);
                if (Build.VERSION.SDK_INT < 33) {
                    child.recycle();
                }
            }
        }
    }

    /**
     * Yazısı olmayan öğelerin içerik açıklamasını alır. Resim ve simge düğmelerinin
     * kısa açıklamaları (Beğen, Paylaş gibi) kopyalanacak yazı olmadığı için atlanır.
     */
    private CharSequence descriptionOf(AccessibilityNodeInfo node, boolean showAllDesc) {
        CharSequence d = node.getContentDescription();
        if (d == null || showAllDesc) {
            return d;
        }
        CharSequence cn = node.getClassName();
        String cls = cn == null ? "" : cn.toString();
        if (cls.contains("ImageView") || cls.contains("ImageButton")) {
            return null;
        }
        if (node.isClickable() && d.toString().trim().length() < 20) {
            return null;
        }
        return d;
    }

    // ---------- Seçim ekranı ----------

    private void buildOverlay() {
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x55000000);
        overlay.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        updateBubbleVisibility();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        wm.addView(overlay, lp);

        // Pencere yerleştikten sonra kutuları doğru konuma koyuyoruz.
        overlay.post(new Runnable() {
            @Override
            public void run() {
                if (overlay == null) {
                    return;
                }
                int[] loc = new int[2];
                overlay.getLocationOnScreen(loc);

                for (final Item it : items) {
                    View box = new View(CopyService.this);
                    box.setBackground(boxDrawable(false));
                    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                            it.bounds.width(), it.bounds.height());
                    p.leftMargin = it.bounds.left - loc[0];
                    p.topMargin = it.bounds.top - loc[1];
                    box.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Seri mod: dokunulan yazı hemen kopyalanır ve ekran kapanır.
                            if (prefs.getBoolean(Prefs.FAST, false)) {
                                copyText(it.text);
                                return;
                            }
                            it.selected = !it.selected;
                            it.view.setBackground(boxDrawable(it.selected));
                            updateButtons();
                        }
                    });
                    it.view = box;
                    overlay.addView(box, p);
                }

                overlay.addView(buildBar());
                updateButtons();
            }
        });
    }

    private View buildBar() {
        final boolean fast = prefs.getBoolean(Prefs.FAST, false);
        boolean split = prefs.getBoolean(Prefs.SPLIT, true) && !fast;

        // Düğmeler satırı
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        if (!fast) {
            selectAllButton = makeButton(R.string.btn_select_all, pal.neutralBg, pal.neutralText,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean allSelected = true;
                            for (Item it : items) {
                                if (!it.selected) {
                                    allSelected = false;
                                    break;
                                }
                            }
                            for (Item it : items) {
                                it.selected = !allSelected;
                                it.view.setBackground(boxDrawable(it.selected));
                            }
                            updateButtons();
                        }
                    });

            Button editButton = makeButton(R.string.btn_edit, pal.accentBg, pal.accentText,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openEdit();
                        }
                    });

            copyButton = makeButton(R.string.btn_copy, pal.copyBg, pal.copyText,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            copySelected();
                        }
                    });

            row.addView(selectAllButton);
            row.addView(editButton);
            row.addView(copyButton);
        }

        Button closeButton = makeButton(R.string.btn_close, pal.neutralBg, pal.neutralText,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        closeOverlay();
                    }
                });
        row.addView(closeButton);

        // Çubuğun tamamı: üstte sürükleme tutamacı, altta düğmeler
        final LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.barBg);
        bg.setCornerRadius(Util.dp(this, 16));
        container.setBackground(bg);
        container.setPadding(Util.dp(this, 6), 0, Util.dp(this, 6), Util.dp(this, 6));

        FrameLayout handle = new FrameLayout(this);
        View pill = new View(this);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(pal.panelHint);
        pillBg.setCornerRadius(Util.dp(this, 2));
        pill.setBackground(pillBg);
        handle.addView(pill, new FrameLayout.LayoutParams(
                Util.dp(this, 44), Util.dp(this, 5), Gravity.CENTER));
        container.addView(handle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Util.dp(this, 24)));
        container.addView(row);

        // Böl düğmesi: ana düğmelerin altında ince bir satır
        if (split) {
            Button splitButton = makeButton(R.string.btn_split, pal.neutralBg, pal.neutralText,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openSplit();
                        }
                    });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Util.dp(this, 36));
            sp.setMargins(Util.dp(this, 3), Util.dp(this, 4), Util.dp(this, 3), 0);
            splitButton.setLayoutParams(sp);
            container.addView(splitButton);
        }

        final DisplayMetrics dm = getResources().getDisplayMetrics();
        final int screenHeight = dm.heightPixels;
        int savedBottom = prefs.getInt(Prefs.BAR_BOTTOM, Util.dp(this, 64));
        savedBottom = Math.max(Util.dp(this, 8), Math.min(savedBottom, screenHeight - Util.dp(this, 140)));

        final FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        p.setMargins(Util.dp(this, 12), 0, Util.dp(this, 12), savedBottom);
        container.setLayoutParams(p);

        // Tutamacı yukarı aşağı sürükleyince çubuk yer değiştirir ve yeri hatırlanır.
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int startBottom;
            private float startY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startBottom = p.bottomMargin;
                        startY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int max = screenHeight - container.getHeight() - Util.dp(CopyService.this, 8);
                        int nb = startBottom - (int) (e.getRawY() - startY);
                        p.bottomMargin = Math.max(Util.dp(CopyService.this, 8), Math.min(nb, max));
                        container.setLayoutParams(p);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        prefs.edit().putInt(Prefs.BAR_BOTTOM, p.bottomMargin).apply();
                        return true;
                    default:
                        return false;
                }
            }
        });
        return container;
    }

    private Button makeButton(int textRes, int bgColor, int textColor, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(textRes);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(textColor);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(Util.dp(this, 44));
        b.setPadding(Util.dp(this, 4), 0, Util.dp(this, 4), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(Util.dp(this, 12));
        b.setBackground(bg);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(Util.dp(this, 3), 0, Util.dp(this, 3), 0);
        b.setLayoutParams(p);
        return b;
    }

    private GradientDrawable boxDrawable(boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(Util.dp(this, 4));
        if (selected) {
            d.setColor(pal.boxSelFill);
            d.setStroke(Util.dp(this, 2), pal.boxSelStroke);
        } else {
            d.setColor(pal.boxIdleFill);
            d.setStroke(Util.dp(this, 2), pal.boxIdleStroke);
        }
        return d;
    }

    private void updateButtons() {
        if (copyButton == null || selectAllButton == null) {
            return;
        }
        int n = 0;
        for (Item it : items) {
            if (it.selected) {
                n++;
            }
        }
        copyButton.setText(getString(R.string.btn_copy) + " (" + n + ")");
        selectAllButton.setText(n == items.size() ? R.string.btn_deselect_all : R.string.btn_select_all);
    }

    private String selectedText() {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            if (it.selected) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(it.text);
            }
        }
        return sb.toString();
    }

    // ---------- Düzenle ----------

    /** Seçili yazıyı küçük bir düzenleme penceresinde açar (klavye sorunsuz çalışsın diye ayrı ekran). */
    private void openEdit() {
        String text = selectedText();
        if (text.length() == 0) {
            toast(R.string.toast_select_first);
            return;
        }
        Intent i = new Intent(this, EditActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(EditActivity.EXTRA_TEXT, text);
        startActivity(i);
        closeOverlay();
    }

    // ---------- Böl ----------

    private void openSplit() {
        if (overlay == null || !splitViews.isEmpty()) {
            return;
        }
        pieces.clear();
        for (Item it : items) {
            if (it.selected) {
                addPieces(it.text);
            }
        }
        if (pieces.isEmpty()) {
            toast(R.string.toast_select_first);
            return;
        }

        // Arkadaki kutulara yanlışlıkla dokunulmasın diye karartma katmanı
        View scrim = new View(this);
        scrim.setBackgroundColor(0xAA000000);
        scrim.setClickable(true);
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splitViews.add(scrim);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.panelBg);
        bg.setCornerRadius(Util.dp(this, 16));
        panel.setBackground(bg);
        panel.setPadding(Util.dp(this, 12), Util.dp(this, 12), Util.dp(this, 12), Util.dp(this, 12));

        TextView hint = new TextView(this);
        hint.setText(R.string.split_hint);
        hint.setTextColor(pal.panelHint);
        hint.setTextSize(13);
        hint.setPadding(0, 0, 0, Util.dp(this, 8));
        panel.addView(hint);

        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
        ScrollView scroll = new MaxHeightScrollView(this, maxHeight);
        FlowLayout flow = new FlowLayout(this, Util.dp(this, 6));
        for (final Piece pc : pieces) {
            TextView chip = new TextView(this);
            chip.setText(pc.text);
            chip.setTextSize(15);
            chip.setPadding(Util.dp(this, 10), Util.dp(this, 7), Util.dp(this, 10), Util.dp(this, 7));
            pc.view = chip;
            styleChip(pc);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pc.selected = !pc.selected;
                    styleChip(pc);
                    updateSplitButtons();
                }
            });
            flow.addView(chip);
        }
        scroll.addView(flow);
        panel.addView(scroll);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Util.dp(this, 10), 0, 0);
        row.addView(makeButton(R.string.btn_select_all, pal.neutralBg, pal.neutralText,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean all = true;
                        for (Piece pc : pieces) {
                            if (!pc.selected) {
                                all = false;
                                break;
                            }
                        }
                        for (Piece pc : pieces) {
                            pc.selected = !all;
                            styleChip(pc);
                        }
                        updateSplitButtons();
                    }
                }));
        splitCopyButton = makeButton(R.string.btn_copy, pal.copyBg, pal.copyText,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        copySplit();
                    }
                });
        row.addView(splitCopyButton);
        row.addView(makeButton(R.string.btn_back, pal.neutralBg, pal.neutralText,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        closeSplit();
                    }
                }));
        panel.addView(row);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        pp.setMargins(Util.dp(this, 16), 0, Util.dp(this, 16), 0);
        overlay.addView(panel, pp);
        splitViews.add(panel);
        updateSplitButtons();
    }

    /** Yazıyı önce satırlara, olmazsa cümlelere, o da olmazsa kelimelere böler. */
    private void addPieces(String text) {
        List<String> parts = new ArrayList<>();
        String sep = "\n";

        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.length() > 0) {
                parts.add(t);
            }
        }
        if (parts.size() < 2) {
            parts.clear();
            for (String sentence : text.trim().split("(?<=[.!?…])\\s+")) {
                String t = sentence.trim();
                if (t.length() > 0) {
                    parts.add(t);
                }
            }
        }
        if (parts.size() < 2) {
            parts.clear();
            sep = " ";
            for (String word : text.trim().split("\\s+")) {
                if (word.length() > 0) {
                    parts.add(word);
                }
            }
        }
        for (String s : parts) {
            Piece pc = new Piece();
            pc.text = s;
            pc.sep = sep;
            pieces.add(pc);
        }
    }

    private void styleChip(Piece pc) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(Util.dp(this, 10));
        if (pc.selected) {
            d.setColor(pal.boxSelFill);
            d.setStroke(Util.dp(this, 2), pal.boxSelStroke);
            pc.view.setTextColor(0xFF1F2937);
        } else {
            d.setColor(pal.chipBg);
            d.setStroke(Util.dp(this, 2), pal.chipBg);
            pc.view.setTextColor(pal.chipText);
        }
        pc.view.setBackground(d);
    }

    private void updateSplitButtons() {
        if (splitCopyButton == null) {
            return;
        }
        int n = 0;
        for (Piece pc : pieces) {
            if (pc.selected) {
                n++;
            }
        }
        splitCopyButton.setText(getString(R.string.btn_copy) + " (" + n + ")");
    }

    private void copySplit() {
        StringBuilder sb = new StringBuilder();
        for (Piece pc : pieces) {
            if (pc.selected) {
                if (sb.length() > 0) {
                    sb.append(pc.sep);
                }
                sb.append(pc.text);
            }
        }
        if (sb.length() == 0) {
            toast(R.string.toast_select_first);
            return;
        }
        copyText(sb.toString());
    }

    private void closeSplit() {
        for (View v : splitViews) {
            if (overlay != null) {
                overlay.removeView(v);
            }
        }
        splitViews.clear();
        pieces.clear();
        splitCopyButton = null;
    }

    // ---------- Kopyalama ve kapatma ----------

    private void copySelected() {
        String text = selectedText();
        if (text.length() == 0) {
            toast(R.string.toast_select_first);
            return;
        }
        copyText(text);
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Koppiy", text));
        Util.buzz(this);
        boolean fast = prefs.getBoolean(Prefs.FAST, false);
        closeOverlay();
        if (fast) {
            showCheck();
        } else {
            toast(R.string.toast_copied);
        }
    }

    /** Seri modda ekranın ortasında solgun yeşil tik ve "Kopyalandı" yazısı görünüp kaybolur. */
    private void showCheck() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        CheckView check = new CheckView(this);
        box.addView(check, new LinearLayout.LayoutParams(Util.dp(this, 76), Util.dp(this, 76)));

        TextView label = new TextView(this);
        label.setText(R.string.copied_label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(17);
        label.setTypeface(null, Typeface.BOLD);
        label.setShadowLayer(6f, 0f, 2f, 0xFF000000);
        label.setPadding(0, Util.dp(this, 8), 0, 0);
        box.addView(label);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        box.setAlpha(0f);
        try {
            wm.addView(box, lp);
        } catch (Exception e) {
            return;
        }
        box.animate().alpha(0.9f).setDuration(140).withEndAction(new Runnable() {
            @Override
            public void run() {
                box.animate().alpha(0f).setStartDelay(550).setDuration(350)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    wm.removeView(box);
                                } catch (Exception ignored) {
                                }
                            }
                        }).start();
            }
        }).start();
    }

    private void closeOverlay() {
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
        copyButton = null;
        selectAllButton = null;
        splitCopyButton = null;
        splitViews.clear();
        pieces.clear();
        items.clear();
        updateBubbleVisibility();
    }

    // ---------- Yardımcılar ----------

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
