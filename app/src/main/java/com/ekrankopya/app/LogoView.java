package com.ekrankopya.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Koppiy logosu: yuvarlak zemin üzerinde seçili satırı sarı olan iki kâğıt. */
public class LogoView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Palette palette;

    public LogoView(Context context) {
        super(context);
        palette = Palette.of(context);
    }

    void setPalette(Palette p) {
        palette = p;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float side = Math.min(w, h);
        float s = side / 108f;
        float k = 1.15f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.bubbleBg);
        canvas.drawCircle(w / 2f, h / 2f, side / 2f - 1f, paint);

        if ((palette.bubbleRing >>> 24) != 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, side / 40f));
            paint.setColor(palette.bubbleRing);
            canvas.drawCircle(w / 2f, h / 2f, side / 2f - 2f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        int back = (palette.bubblePaper & 0x00FFFFFF) | 0x66000000;
        box(canvas, w, h, s * k, 30, 26, 64, 70, 5, back);
        box(canvas, w, h, s * k, 40, 36, 76, 80, 5, palette.bubblePaper);
        box(canvas, w, h, s * k, 44, 53, 72, 61, 0, palette.bubbleHi);
        box(canvas, w, h, s * k, 46, 46, 70, 50, 0, palette.bubbleLine);
        box(canvas, w, h, s * k, 46, 55, 70, 59, 0, palette.bubbleLine);
        box(canvas, w, h, s * k, 46, 64, 60, 68, 0, palette.bubbleLine);
    }

    /** 108x108 çizim alanındaki koordinatları görünüm boyutuna çevirip kutuyu çizer. */
    private void box(Canvas canvas, float w, float h, float scale,
                     float l, float t, float r, float b, float radius, int color) {
        float cx = w / 2f;
        float cy = h / 2f;
        rect.set(cx + (l - 54f) * scale, cy + (t - 54f) * scale,
                cx + (r - 54f) * scale, cy + (b - 54f) * scale);
        paint.setColor(color);
        if (radius > 0f) {
            canvas.drawRoundRect(rect, radius * scale, radius * scale, paint);
        } else {
            canvas.drawRect(rect, paint);
        }
    }
}
