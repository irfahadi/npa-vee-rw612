package com.nxp.example.smartgreenhouse.models.actuator;

import java.util.Calendar;
import java.util.TimeZone;

public final class ActuatorDisplayFormatter {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final int MINUTES_PER_HOUR = 60;
    private static final long JAKARTA_OFFSET_MILLIS = 7L * 60L * 60L * 1000L;
    private static final String EMPTY_VALUE = "-";

    private static final String[] MONTH_NAMES = {
            "Januari",
            "Februari",
            "Maret",
            "April",
            "Mei",
            "Juni",
            "Juli",
            "Agustus",
            "September",
            "Oktober",
            "November",
            "Desember"
    };

    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

    private ActuatorDisplayFormatter() {}

    public static synchronized String formatTime(long timestamp) {
        if (timestamp <= 0L) return EMPTY_VALUE;

        UTC_CALENDAR.setTimeInMillis(timestamp + JAKARTA_OFFSET_MILLIS);

        int hour = UTC_CALENDAR.get(Calendar.HOUR_OF_DAY);
        int minute = UTC_CALENDAR.get(Calendar.MINUTE);

        return formatTwoDigits(hour) + ":" + formatTwoDigits(minute);
    }

    public static synchronized String formatDate(long timestamp) {
        if (timestamp <= 0L) return EMPTY_VALUE;

        UTC_CALENDAR.setTimeInMillis(timestamp + JAKARTA_OFFSET_MILLIS);

        int day = UTC_CALENDAR.get(Calendar.DAY_OF_MONTH);
        int monthIndex = UTC_CALENDAR.get(Calendar.MONTH);
        int year = UTC_CALENDAR.get(Calendar.YEAR);

        if (monthIndex < 0 || monthIndex >= MONTH_NAMES.length) return EMPTY_VALUE;

        return day + " " + MONTH_NAMES[monthIndex] + " " + year;
    }

    public static String formatDuration(long durationMillis) {
        if (durationMillis < 0L) durationMillis = 0L;

        long totalMinutes = durationMillis / MILLIS_PER_MINUTE;

        if (totalMinutes < MINUTES_PER_HOUR) return totalMinutes + " menit";

        long hours = totalMinutes / MINUTES_PER_HOUR;
        long remainingMinutes = totalMinutes % MINUTES_PER_HOUR;

        if (remainingMinutes == 0L) return hours + " jam";

        return hours + " jam " + remainingMinutes + " menit";
    }

    private static String formatTwoDigits(int value) {
        if (value < 10) return "0" + value;
        return String.valueOf(value);
    }
}