package com.malapay.classroom.util;

import android.text.format.DateUtils;

/**
 * Simple utility for formatting timestamps as relative time strings
 * (e.g. "2 hours ago", "Yesterday", etc.).
 */
public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Returns a human-readable relative time string like "5 minutes ago"
     * or "Jan 1, 2026" for older dates.
     */
    public static String getRelativeTime(long epochMillis) {
        long now = System.currentTimeMillis();
        long diff = now - epochMillis;

        if (diff < DateUtils.MINUTE_IN_MILLIS) {
            return "Just now";
        } else if (diff < DateUtils.HOUR_IN_MILLIS) {
            long mins = diff / DateUtils.MINUTE_IN_MILLIS;
            return mins + " min" + (mins == 1 ? "" : "s") + " ago";
        } else if (diff < DateUtils.DAY_IN_MILLIS) {
            long hours = diff / DateUtils.HOUR_IN_MILLIS;
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else if (diff < 2 * DateUtils.DAY_IN_MILLIS) {
            return "Yesterday";
        } else {
            return DateUtils.getRelativeTimeSpanString(
                    epochMillis, now, DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_NO_YEAR).toString();
        }
    }
}
