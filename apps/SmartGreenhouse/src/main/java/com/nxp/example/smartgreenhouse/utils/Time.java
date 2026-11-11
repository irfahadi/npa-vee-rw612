package com.nxp.example.smartgreenhouse.utils;

import ej.bon.Util;

import java.util.Calendar;
import java.util.TimeZone;

public final class Time {

    private static final long JAKARTA_OFFSET_MILLIS = 7L * 60L * 60L * 1000L;
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    private Time() {}

    public static String formatCurrentTime() {
        return formatJakartaTime(Util.currentTimeMillis());
    }

    public static synchronized String formatJakartaTime(long utcTimestamp) {
        if (utcTimestamp <= 0L) return "--:--";

        UTC_CALENDAR.setTimeInMillis(utcTimestamp + JAKARTA_OFFSET_MILLIS);

        int hour = UTC_CALENDAR.get(Calendar.HOUR_OF_DAY);
        int minute = UTC_CALENDAR.get(Calendar.MINUTE);

        return twoDigits(hour) + ":" + twoDigits(minute);
    }

    public static synchronized String formatJakartaDateTime(long utcTimestamp) {
        if (utcTimestamp <= 0L) return "-";

        UTC_CALENDAR.setTimeInMillis(utcTimestamp + JAKARTA_OFFSET_MILLIS);

        int date = UTC_CALENDAR.get(Calendar.DAY_OF_MONTH);
        int month = UTC_CALENDAR.get(Calendar.MONTH) + 1;
        int year = UTC_CALENDAR.get(Calendar.YEAR);
        int hour = UTC_CALENDAR.get(Calendar.HOUR_OF_DAY);
        int minute = UTC_CALENDAR.get(Calendar.MINUTE);

        return twoDigits(date) + "/" + twoDigits(month) + "/" + year + " | " + twoDigits(hour) + ":" + twoDigits(minute);
    }

    private static String twoDigits(int value) {
        if (value < 10) return "0" + value;
        return String.valueOf(value);
    }
}