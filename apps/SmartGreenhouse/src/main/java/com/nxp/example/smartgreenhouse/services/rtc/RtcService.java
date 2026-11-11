package com.nxp.example.smartgreenhouse.services.rtc;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RtcService {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: RTC SERVICE]");

    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("GMT");

    private boolean initialized;

    public RtcService() {
        this.initialized = false;
    }

    public synchronized boolean initialize() {
        if (this.initialized) {
            return true;
        }

        LOGGER.log(Level.INFO, "Initializing DS3231 through native bridge.");
        this.initialized = RtcNative.init();
        if (this.initialized) {
            LOGGER.log(Level.INFO, "DS3231 native bridge initialized successfully.");
        } else {
            LOGGER.log(Level.WARNING, "DS3231 native bridge initialization failed.");
        }

        return this.initialized;
    }

    public synchronized boolean isTimeValid() {
        if (!initialize()) {
            return false;
        }

        boolean valid = RtcNative.isTimeValid();

        LOGGER.log(valid ? Level.INFO : Level.WARNING, "RTC validity check | valid=" + valid);
        return valid;
    }

    public synchronized long readUtcMillis() {
        if (!initialize()) {
            return 0L;
        }

        int[] dateTime = new int[RtcNative.DATETIME_LENGTH];

        if (!RtcNative.readDateTime(dateTime)) {
            LOGGER.log(Level.WARNING, "Unable to read RTC date/time.");
            return 0L;
        }

        int year = dateTime[RtcNative.INDEX_YEAR];
        int month = dateTime[RtcNative.INDEX_MONTH];
        int date = dateTime[RtcNative.INDEX_DATE];
        int day = dateTime[RtcNative.INDEX_DAY];
        int hour = dateTime[RtcNative.INDEX_HOUR];
        int minute = dateTime[RtcNative.INDEX_MINUTE];
        int second = dateTime[RtcNative.INDEX_SECOND];

        LOGGER.log(
                Level.INFO,
                "RTC read"
                        + " | year=" + year
                        + " | month=" + month
                        + " | date=" + date
                        + " | day=" + day
                        + " | hour=" + hour
                        + " | minute=" + minute
                        + " | second=" + second
        );

        Calendar calendar = Calendar.getInstance(UTC_TIME_ZONE);

        calendar.clear();
        calendar.set(year, month - 1, date, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }

    public synchronized boolean writeUtcMillis(long utcMillis) {
        if (utcMillis <= 0L) {
            return false;
        }

        if (!initialize()) {
            return false;
        }

        Calendar calendar = Calendar.getInstance(UTC_TIME_ZONE);
        calendar.setTimeInMillis(utcMillis);

        int[] dateTime = new int[RtcNative.DATETIME_LENGTH];

        dateTime[RtcNative.INDEX_YEAR] = calendar.get(Calendar.YEAR);

        dateTime[RtcNative.INDEX_MONTH] = calendar.get(Calendar.MONTH) + 1;

        dateTime[RtcNative.INDEX_DATE] = calendar.get(Calendar.DAY_OF_MONTH);

        dateTime[RtcNative.INDEX_DAY] = calendar.get(Calendar.DAY_OF_WEEK);

        dateTime[RtcNative.INDEX_HOUR] = calendar.get(Calendar.HOUR_OF_DAY);

        dateTime[RtcNative.INDEX_MINUTE] = calendar.get(Calendar.MINUTE);

        dateTime[RtcNative.INDEX_SECOND] = calendar.get(Calendar.SECOND);

        boolean successful = RtcNative.writeDateTime(dateTime);

        LOGGER.log(successful ? Level.INFO : Level.WARNING, "RTC UTC write completed" + " | successful=" + successful + " | utcMillis=" + utcMillis);

        return successful;
    }

    public void startBridgeSmokeTest() {
        Thread worker =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                runBridgeSmokeTest();
                            }
                        },
                        "rtc-bridge-test"
                );

        worker.start();
    }

    private void runBridgeSmokeTest() {
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException exception) {
            LOGGER.log(Level.WARNING, "RTC bridge test delay interrupted" + " | error=" + exception);
            return;
        }

        LOGGER.log(Level.INFO, "=====================================");
        LOGGER.log(Level.INFO, "Starting Java to DS3231 bridge test.");
        LOGGER.log(Level.INFO, "=====================================");
        if (!initialize()) {
            LOGGER.log(Level.WARNING, "RTC BRIDGE TEST FAILED | initialization.");
            return;
        }

        boolean valid = isTimeValid();

        LOGGER.log(Level.INFO, "RTC bridge validity result" + " | valid=" + valid);
        long utcMillis = readUtcMillis();

        if (utcMillis <= 0L) {
            LOGGER.log(Level.WARNING, "RTC BRIDGE TEST FAILED | date/time read.");
            return;
        }
        LOGGER.log(Level.INFO, "RTC BRIDGE TEST PASSED" + " | utcMillis=" + utcMillis);
        LOGGER.log(Level.INFO, "=====================================");
    }
}