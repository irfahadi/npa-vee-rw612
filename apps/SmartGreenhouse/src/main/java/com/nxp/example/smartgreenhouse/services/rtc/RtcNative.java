package com.nxp.example.smartgreenhouse.services.rtc;

public final class RtcNative {

    public static final int DATETIME_LENGTH = 7;

    public static final int INDEX_YEAR = 0;
    public static final int INDEX_MONTH = 1;
    public static final int INDEX_DATE = 2;
    public static final int INDEX_DAY = 3;
    public static final int INDEX_HOUR = 4;
    public static final int INDEX_MINUTE = 5;
    public static final int INDEX_SECOND = 6;

    private RtcNative() {
    }

    public static boolean init() {
        return initNative();
    }

    public static boolean isTimeValid() {
        return isTimeValidNative();
    }

    public static boolean readDateTime(int[] destination) {
        if (destination == null || destination.length < DATETIME_LENGTH) {
            return false;
        }

        return readDateTimeNative(destination);
    }

    public static boolean writeDateTime(int[] source) {
        if (source == null || source.length < DATETIME_LENGTH) {
            return false;
        }

        return writeDateTimeNative(source);
    }

    private static native boolean initNative();

    private static native boolean isTimeValidNative();

    private static native boolean readDateTimeNative(int[] destination);

    private static native boolean writeDateTimeNative(int[] source);
}