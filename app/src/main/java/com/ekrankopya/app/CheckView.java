package com.ekrankopya.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Seri modda kopyalayınca görünen solgun yeşil daire içindeki beyaz tik. */
public class CheckView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tick = new Path();

    public CheckView(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float side = Math.min(w, h);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB343A047);
        canvas.drawCircle(w / 2f, h / 2f, side / 2f, paint);

        tick.reset();
        tick.moveTo(w * 0.28f, h * 0.52f);
        tick.lineTo(w * 0.43f, h * 0.67f);
        tick.lineTo(w * 0.73f, h * 0.35f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(side * 0.09f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFFFFFFF);
        canvas.drawPath(tick, paint);
    }
}
