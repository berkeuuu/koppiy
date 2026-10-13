package com.ekrankopya.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Çocukları satır satır dizen, sığmayanı alt satıra atan basit yerleşim. Böl penceresinde kullanılır. */
public class FlowLayout extends ViewGroup {

    private final int gap;

    public FlowLayout(Context context, int gap) {
        super(context);
        this.gap = gap;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) {
                continue;
            }
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += rowH + gap;
                rowH = 0;
            }
            x += cw + gap;
            rowH = Math.max(rowH, ch);
        }
        int h = y + rowH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) {
                continue;
            }
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += rowH + gap;
                rowH = 0;
            }
            int left = getPaddingLeft() + x;
            int top = getPaddingTop() + y;
            c.layout(left, top, left + cw, top + ch);
            x += cw + gap;
            rowH = Math.max(rowH, ch);
        }
    }
}
