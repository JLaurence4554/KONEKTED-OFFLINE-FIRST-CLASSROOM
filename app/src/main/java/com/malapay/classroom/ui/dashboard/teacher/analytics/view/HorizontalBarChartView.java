package com.malapay.classroom.ui.dashboard.teacher.analytics.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.malapay.classroom.data.local.model.QuizScoreDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom horizontal bar chart view that draws labeled bars for each
 * score distribution bucket. Used on the analytics dashboard to show
 * how students are distributed across score ranges.
 */
public class HorizontalBarChartView extends View {

    private final List<QuizScoreDistribution> data = new ArrayList<>();
    private float barHeight = 24f;
    private float barSpacing = 12f;
    private float labelWidth = 160f;
    private float countWidth = 40f;
    private float textSize = 12f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private int maxCount = 1; // avoid division by zero

    public HorizontalBarChartView(Context context) {
        super(context);
        init();
    }

    public HorizontalBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HorizontalBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.LEFT);

        countPaint.setStyle(Paint.Style.FILL);
        countPaint.setTextAlign(Paint.Align.RIGHT);

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFFEFF4F1); // surface_variant for bar background
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float density = getResources().getDisplayMetrics().density;
        float totalHeight = (data.size() * (barHeight * density + barSpacing * density)) + 16f * density;
        int height = (int) totalHeight;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (data.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;
        float bh = barHeight * density;
        float bs = barSpacing * density;
        float lw = labelWidth * density;
        float cw = countWidth * density;
        float ts = textSize * density;
        float topPad = 4f * density;
        float barRadius = 6f * density;

        // Find max count for scaling
        maxCount = 1;
        for (QuizScoreDistribution item : data) {
            if (item.getCount() > maxCount) maxCount = item.getCount();
        }

        float barAreaLeft = lw;
        float barAreaRight = getWidth() - cw - 8f * density;

        labelPaint.setTextSize(ts);
        countPaint.setTextSize(ts);

        for (int i = 0; i < data.size(); i++) {
            QuizScoreDistribution item = data.get(i);
            float y = topPad + i * (bh + bs);

            // Draw label on the left
            labelPaint.setColor(0xFF17332A); // text_primary
            canvas.drawText(item.getLabel(), 8f * density, y + bh * 0.65f, labelPaint);

            // Draw bar background
            barRect.set(barAreaLeft, y, barAreaRight, y + bh);
            canvas.drawRoundRect(barRect, barRadius, barRadius, bgPaint);

            // Draw filled bar
            float barWidth = (barAreaRight - barAreaLeft) * ((float) item.getCount() / maxCount);
            if (barWidth > 0) {
                barPaint.setColor(item.getColorRes());
                RectF filledRect = new RectF(barAreaLeft, y, barAreaLeft + barWidth, y + bh);
                canvas.drawRoundRect(filledRect, barRadius, barRadius, barPaint);
            }

            // Draw count on the right
            countPaint.setColor(0xFF5A6B63); // text_secondary
            String countText = String.valueOf(item.getCount());
            canvas.drawText(countText, getWidth() - 8f * density, y + bh * 0.65f, countPaint);
        }
    }

    /** Set the distribution data and trigger a redraw. */
    public void setData(List<QuizScoreDistribution> data) {
        this.data.clear();
        if (data != null) {
            this.data.addAll(data);
        }
        requestLayout();
        invalidate();
    }
}
