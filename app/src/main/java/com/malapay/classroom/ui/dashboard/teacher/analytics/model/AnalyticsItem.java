package com.malapay.classroom.ui.dashboard.teacher.analytics.model;

import androidx.annotation.Nullable;

import com.malapay.classroom.data.local.model.ClassAnalyticsSummary;
import com.malapay.classroom.data.local.model.QuizScoreDistribution;
import com.malapay.classroom.data.local.model.StudentPerformance;

import java.util.List;

/**
 * Union type for the analytics RecyclerView adapter. Each item represents
 * one section of the analytics dashboard: summary stats, bar chart, or
 * student performance list.
 */
public class AnalyticsItem {

    public static final int TYPE_SUMMARY = 0;
    public static final int TYPE_BAR_CHART = 1;
    public static final int TYPE_STUDENT_LIST = 2;

    private final int type;

    @Nullable private final ClassAnalyticsSummary summary;
    @Nullable private final List<QuizScoreDistribution> distribution;
    @Nullable private final List<StudentPerformance> students;

    private AnalyticsItem(int type, @Nullable ClassAnalyticsSummary summary,
                          @Nullable List<QuizScoreDistribution> distribution,
                          @Nullable List<StudentPerformance> students) {
        this.type = type;
        this.summary = summary;
        this.distribution = distribution;
        this.students = students;
    }

    public static AnalyticsItem summary(ClassAnalyticsSummary summary) {
        return new AnalyticsItem(TYPE_SUMMARY, summary, null, null);
    }

    public static AnalyticsItem barChart(List<QuizScoreDistribution> distribution) {
        return new AnalyticsItem(TYPE_BAR_CHART, null, distribution, null);
    }

    public static AnalyticsItem studentList(List<StudentPerformance> students) {
        return new AnalyticsItem(TYPE_STUDENT_LIST, null, null, students);
    }

    public int getType() { return type; }
    @Nullable public ClassAnalyticsSummary getSummary() { return summary; }
    @Nullable public List<QuizScoreDistribution> getDistribution() { return distribution; }
    @Nullable public List<StudentPerformance> getStudents() { return students; }
}
