package com.nxp.example.smartgreenhouse.models.sensor;

import java.util.Calendar;
import java.util.TimeZone;

public final class SensorHistoryEntry {

    private static final long JAKARTA_OFFSET_MILLIS = 7L * 60L * 60L * 1000L;
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

    private final String shortTime;
    private final String fullTime;
    private final SensorData sensorData;

    public SensorHistoryEntry(String shortTime, String fullTime, SensorData sensorData) {
        this.shortTime = shortTime == null ? "" : shortTime;
        this.fullTime = fullTime == null ? "" : fullTime;
        this.sensorData = copySensorData(sensorData);
    }

    public SensorHistoryEntry(long utcTimestamp, SensorData sensorData) {
        this.shortTime = formatShortTime(utcTimestamp);
        this.fullTime = formatFullTime(utcTimestamp);
        this.sensorData = copySensorData(sensorData);
    }

    public String getShortTime() {
        return this.shortTime;
    }

    public String getFullTime() {
        return this.fullTime;
    }

    public int getNodeId() {
        if (this.sensorData == null) return -1;
        return this.sensorData.getNodeId();
    }

    public SensorData getSensorData() {
        return this.sensorData;
    }

    private static synchronized String formatShortTime(long utcTimestamp) {
        if (utcTimestamp <= 0L) return "-";

        UTC_CALENDAR.setTimeInMillis(utcTimestamp + JAKARTA_OFFSET_MILLIS);

        int hour = UTC_CALENDAR.get(Calendar.HOUR_OF_DAY);
        int minute = UTC_CALENDAR.get(Calendar.MINUTE);

        return twoDigits(hour) + ":" + twoDigits(minute);
    }

    private static synchronized String formatFullTime(long utcTimestamp) {
        if (utcTimestamp <= 0L) return "-";

        UTC_CALENDAR.setTimeInMillis(utcTimestamp + JAKARTA_OFFSET_MILLIS);

        int day = UTC_CALENDAR.get(Calendar.DAY_OF_MONTH);
        int monthIndex = UTC_CALENDAR.get(Calendar.MONTH);
        int year = UTC_CALENDAR.get(Calendar.YEAR);
        int hour = UTC_CALENDAR.get(Calendar.HOUR_OF_DAY);
        int minute = UTC_CALENDAR.get(Calendar.MINUTE);

        if (monthIndex < 0 || monthIndex >= MONTH_NAMES.length) return "-";

        return day + " " + MONTH_NAMES[monthIndex] + " " + year + " | " + twoDigits(hour) + ":" + twoDigits(minute);
    }

    private static String twoDigits(int value) {
        if (value < 10) return "0" + value;
        return String.valueOf(value);
    }

    private static SensorData copySensorData(SensorData source) {
        if (source == null) return null;

        SensorData copy = new SensorData(source.getNodeId(), source.getTargetId());

        copy.setN(source.getN());
        copy.setP(source.getP());
        copy.setK(source.getK());
        copy.setSm(source.getSm());
        copy.setpH(source.getpH());
        copy.setSt(source.getSt());
        copy.setEc(source.getEc());
        copy.setAt(source.getAt());
        copy.setAh(source.getAh());
        copy.setLux(source.getLux());

        return copy;
    }
}