package com.malapay.classroom.ui.dashboard.teacher.analytics.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A custom circular progress view that draws a ring with a percentage value
 * in the center. Used on the analytics dashboard for summary stats like
 * average score and attendance rate.
 */
public class CircularProgressView extends View {

    private float progress = 0f;         // 0.0 - 1.0
    private int trackColor = 0xFFE8F0EB;  // light gray-green background ring
    private int progressColor = 0xFF087A4A; // primary green
    private int textColor = 0xFF17332A;    // dark text
    private float progressStrokeWidth = 18f;
    private float textSize = 36f;
    private String label = "";            // optional label below percentage

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float sw = progressStrokeWidth * density;
        float pad = sw / 2f + 8f * density;
        float size = Math.min(getWidth(), getHeight());

        arcRect.set(pad, pad, size - pad, size - pad);

        trackPaint.setStrokeWidth(sw);
        trackPaint.setColor(trackColor);

        progressPaint.setStrokeWidth(sw);
        progressPaint.setColor(progressColor);

        float radius = (size - 2 * pad) / 2f;
        float centerX = size / 2f;
        float centerY = size / 2f;

        // Draw track (background ring)
        canvas.drawArc(arcRect, 0, 360, false, trackPaint);

        // Draw progress arc (starts from top, goes clockwise)
        float sweepAngle = progress * 360f;
        if (sweepAngle > 0) {
            canvas.drawArc(arcRect, -90, sweepAngle, false, progressPaint);
        }

        // Draw percentage text
        textPaint.setTextSize(textSize * density);
        textPaint.setColor(textColor);
        textPaint.setFakeBoldText(true);
        String percentText = Math.round(progress * 100) + "%";
        float textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(percentText, centerX, textY, textPaint);

        // Draw optional label below
        if (label != null && !label.isEmpty()) {
            labelPaint.setTextSize(11f * density);
            labelPaint.setColor(0xFF5A6B63); // text_secondary
            labelPaint.setFakeBoldText(false);
            canvas.drawText(label, centerX, textY + 20f * density, labelPaint);
        }
    }

    /** Set progress (0.0 to 1.0) and optionally trigger redraw. */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    /** Set the color of the progress arc. */
    public void setProgressColor(int color) {
        this.progressColor = color;
        invalidate();
    }

    /** Set the label displayed below the percentage. */
    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }
}
