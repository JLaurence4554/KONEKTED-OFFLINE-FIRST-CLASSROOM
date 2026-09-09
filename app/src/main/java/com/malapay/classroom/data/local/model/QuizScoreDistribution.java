package com.malapay.classroom.data.local.model;

/**
 * Represents a single bucket in the score distribution histogram.
 * Used by the HorizontalBarChartView to render bars.
 */
public class QuizScoreDistribution {

    /** Display label for the bucket (e.g. "Excellent (80-100%)"). */
    private final String label;

    /** Number of quiz results that fall into this bucket. */
    private final int count;

    /** Hex color for the bar (e.g. "#087A4A" for green). */
    private final int colorRes;

    public QuizScoreDistribution(String label, int count, int colorRes) {
        this.label = label;
        this.count = count;
        this.colorRes = colorRes;
    }

    public String getLabel() { return label; }
    public int getCount() { return count; }
    public int getColorRes() { return colorRes; }
}
