package com.malapay.classroom.ui.dashboard.teacher.analytics.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.malapay.classroom.data.local.model.StudentQuizHistory;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple line chart that plots quiz scores over time. Each data point
 * represents one quiz submission, with the X-axis being submission order
 * and the Y-axis being the score (0-100). Used on the student analytics
 * dashboard to show performance trends.
 */
public class ScoreLineChartView extends View {

    private final List<StudentQuizHistory> data = new ArrayList<>();

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private int lineColor = 0xFF087A4A;
    private int fillColor = 0x20087A4A; // semi-transparent green

    public ScoreLineChartView(Context context) {
        super(context);
        init();
    }

    public ScoreLineChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScoreLineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(0xFFE2E9E5); // outline_light

        fillPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Fixed height for the chart
        int height = (int) (200 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float paddingLeft = 40 * density;
        float paddingRight = 16 * density;
        float paddingTop = 20 * density;
        float paddingBottom = 30 * density;

        float chartLeft = paddingLeft;
        float chartRight = getWidth() - paddingRight;
        float chartTop = paddingTop;
        float chartBottom = getHeight() - paddingBottom;
        float chartWidth = chartRight - chartLeft;
        float chartHeight = chartBottom - chartTop;

        // Draw grid lines (20%, 40%, 60%, 80%)
        for (int i = 1; i <= 4; i++) {
            float y = chartBottom - (chartHeight * i / 5f);
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);
        }

        // Draw Y-axis labels (0, 20, 40, 60, 80, 100)
        labelPaint.setTextSize(10 * density);
        labelPaint.setColor(0xFF96A39C); // text_hint
        for (int i = 0; i <= 5; i++) {
            float y = chartBottom - (chartHeight * i / 5f);
            canvas.drawText(String.valueOf(i * 20), chartLeft - 8 * density, y + 4 * density, labelPaint);
        }

        if (data.isEmpty()) {
            // Draw "No data" text in center
            labelPaint.setTextSize(14 * density);
            labelPaint.setColor(0xFF96A39C);
            canvas.drawText("No quiz data yet", getWidth() / 2f, getHeight() / 2f, labelPaint);
            return;
        }

        float pointSpacing = chartWidth / Math.max(data.size() - 1, 1);

        linePaint.setColor(lineColor);
        dotPaint.setColor(lineColor);
        fillPaint.setColor(fillColor);

        linePath.reset();
        fillPath.reset();

        boolean firstPoint = true;

        for (int i = 0; i < data.size(); i++) {
            StudentQuizHistory item = data.get(i);
            float x = chartLeft + (i * pointSpacing);
            float y = chartBottom - (chartHeight * item.getScore() / 100f);

            if (firstPoint) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, chartBottom);
                fillPath.lineTo(x, y);
                firstPoint = false;
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }

            // Draw dot
            canvas.drawCircle(x, y, 5 * density, dotPaint);
            canvas.drawCircle(x, y, 3 * density, new Paint(Paint.ANTI_ALIAS_FLAG) {{
                setColor(0xFFFFFFFF);
                setStyle(Paint.Style.FILL);
            }});

            // Draw score label below dot
            labelPaint.setTextSize(9 * density);
            labelPaint.setColor(0xFF5A6B63); // text_secondary
            canvas.drawText(item.getScore() + "%", x, chartBottom + 14 * density, labelPaint);
        }

        // Close the fill path
        float lastX = chartLeft + ((data.size() - 1) * pointSpacing);
        fillPath.lineTo(lastX, chartBottom);
        fillPath.close();

        // Draw fill
        canvas.drawPath(fillPath, fillPaint);

        // Draw line
        canvas.drawPath(linePath, linePaint);
    }

    /** Set the quiz history data and trigger a redraw. */
    public void setData(List<StudentQuizHistory> data) {
        this.data.clear();
        if (data != null) {
            this.data.addAll(data);
        }
        invalidate();
    }

    /** Set the line color. */
    public void setLineColor(int color) {
        this.lineColor = color;
        this.fillColor = (color & 0x00FFFFFF) | 0x20000000; // 12% opacity
        invalidate();
    }
}
