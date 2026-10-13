package com.ekrankopya.app;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Sağa sola kayan aç/kapa anahtarı. Kapalıyken soluk gri ve "Kapalı" yazar,
 * açıkken yeşil ve "Açık" yazar.
 */
public class KoppiySwitch extends View {

    interface Listener {
        void onChanged(boolean on);
    }

    private static final int OFF_COLOR = 0xFFCFD8DC;
    private static final int ON_COLOR = 0xFF2E7D32;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ArgbEvaluator evaluator = new ArgbEvaluator();
    private boolean on;
    private float pos;
    private Listener listener;
    private ValueAnimator animator;

    public KoppiySwitch(Context context) {
        super(context);
        setClickable(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setChecked(!on, true);
                if (listener != null) {
                    listener.onChanged(on);
                }
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isChecked() {
        return on;
    }

    void setChecked(boolean value, boolean animate) {
        on = value;
        if (animator != null) {
            animator.cancel();
        }
        float target = value ? 1f : 0f;
        if (!animate) {
            pos = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(pos, target);
        animator.setDuration(160);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                pos = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Util.dp(getContext(), 104), Util.dp(getContext(), 40));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        int track = (Integer) evaluator.evaluate(pos, OFF_COLOR, ON_COLOR);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(track);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);

        float knobR = h / 2f - Util.dp(getContext(), 4);
        float cx = (h / 2f) + ((w - h) * pos);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, h / 2f, knobR, paint);

        paint.setTextSize(Util.dp(getContext(), 13));
        float baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f;

        // "Kapalı" yazısı sağ yarıda (anahtar solda iken), "Açık" yazısı sol yarıda
        paint.setColor(0xFF607D8B);
        paint.setAlpha((int) (255 * (1f - pos)));
        canvas.drawText(getContext().getString(R.string.switch_off), (w + h) / 2f, baseline, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setAlpha((int) (255 * pos));
        canvas.drawText(getContext().getString(R.string.switch_on), (w - h) / 2f, baseline, paint);
        paint.setAlpha(255);
    }
}
