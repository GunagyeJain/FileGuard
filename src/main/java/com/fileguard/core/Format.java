package com.fileguard.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small helpers for showing numbers and dates to the user. */
public final class Format {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private Format() {
    }

    /** 1536 -> "1.5 KB" */
    public static String bytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ENGLISH, "%.1f %s", value, UNITS[unit]);
    }

    /** 65_000 -> "1m 5s" */
    public static String duration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        Duration d = Duration.ofMillis(millis);
        if (d.toMinutes() == 0) {
            return String.format(Locale.ENGLISH, "%.1f s", millis / 1000.0);
        }
        return d.toMinutes() + "m " + d.toSecondsPart() + "s";
    }

    public static String dateTime(LocalDateTime time) {
        return time == null ? "-" : time.format(DATE_TIME);
    }

    public static String count(long n, String singular) {
        return count(n, singular, singular + "s");
    }

    public static String count(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
