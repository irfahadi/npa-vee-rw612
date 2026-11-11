package com.nxp.example.smartgreenhouse.services.time;

import com.nxp.example.smartgreenhouse.services.rtc.RtcService;
import com.nxp.example.smartgreenhouse.utils.Time;

import android.net.SntpClient;

import ej.bon.Timer;
import ej.bon.TimerTask;
import ej.bon.Util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class TimeService {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: TIME SERVICE]");

    private static final String[] NTP_SERVERS = {"time.google.com", "0.pool.ntp.org"};
    private static final int NTP_TIMEOUT_MS = 5000;

    private final RtcService rtcService;

    private Timer clockTimer;

    private long timeBaseUtcMillis;
    private long timeBaseReferenceMillis;

    private boolean timeSynchronized;

    private boolean rtcInitializationStarted;
    private boolean rtcInitializationFinished;

    public TimeService() {
        this.rtcService = new RtcService();
        this.clockTimer = null;
        this.timeBaseUtcMillis = 0L;
        this.timeBaseReferenceMillis = 0L;
        this.timeSynchronized = false;
        this.rtcInitializationStarted = false;
        this.rtcInitializationFinished = false;
    }

    public interface ClockListener {
        void onTimeChanged(String currentTime);
    }

    public void startClock(final ClockListener listener) {
        if (this.clockTimer != null) {
            return;
        }

        startRtcInitialization();

        this.clockTimer = new Timer();
        this.clockTimer.schedule(
                new TimerTask() {
                    private long lastNotifiedMinute = Long.MIN_VALUE;
                    private boolean invalidTimeNotified;

                    @Override
                    public void run() {
                        long currentUtcMillis = getCurrentUtcMillis();

                        if (currentUtcMillis <= 0L) {
                            if (this.invalidTimeNotified) {
                                return;
                            }

                            this.invalidTimeNotified = true;
                            this.lastNotifiedMinute = Long.MIN_VALUE;

                            if (listener != null) {
                                listener.onTimeChanged("--:--");
                            }

                            return;
                        }

                        this.invalidTimeNotified = false;

                        long currentMinute = currentUtcMillis / 60000L;

                        if (currentMinute == this.lastNotifiedMinute) {
                            return;
                        }

                        this.lastNotifiedMinute = currentMinute;

                        if (listener != null) {
                            listener.onTimeChanged(Time.formatJakartaTime(currentUtcMillis));
                        }
                    }
                },
                0,
                1000
        );
    }

    public boolean synchronizeTime() {
        waitForRtcInitialization();

        if (isTimeSynchronized()) {
            LOGGER.log(Level.INFO, "Time already available from RTC | NTP synchronization skipped.");
            return true;
        }

        LOGGER.log(Level.INFO, "No valid RTC time available | NTP synchronization required.");

        return synchronizeFromNtp();
    }

    public synchronized boolean isTimeSynchronized() {
        return this.timeSynchronized;
    }

    private synchronized void startRtcInitialization() {
        if (this.rtcInitializationStarted) {
            return;
        }

        this.rtcInitializationStarted = true;

        Thread worker = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        initializeTimeFromRtc();
                    }
                },
                "rtc-time-init"
        );

        worker.start();
    }

    private void initializeTimeFromRtc() {
        try {
            LOGGER.log(Level.INFO, "Checking RTC as startup time source.");

            if (loadTimeFromRtcIfValid()) {
                LOGGER.log(Level.INFO, "Startup time loaded successfully from RTC | jakartaTime=" + Time.formatJakartaTime(getCurrentUtcMillis()));
            } else {
                LOGGER.log(Level.WARNING, "RTC startup time unavailable | waiting for WiFi/NTP.");
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "RTC startup initialization failed | error=" + exception);
        } finally {
            markRtcInitializationFinished();
        }
    }

    private boolean loadTimeFromRtcIfValid() {
        if (!this.rtcService.initialize()) {
            LOGGER.log(Level.WARNING, "RTC initialization failed.");
            return false;
        }

        if (!this.rtcService.isTimeValid()) {
            LOGGER.log(Level.WARNING, "RTC validity check failed.");
            return false;
        }

        long rtcUtcMillis = this.rtcService.readUtcMillis();

        if (rtcUtcMillis <= 0L) {
            LOGGER.log(Level.WARNING, "RTC returned invalid UTC timestamp.");
            return false;
        }

        setTimeBase(rtcUtcMillis, Util.platformTimeMillis());
        Util.setCurrentTimeMillis(rtcUtcMillis);

        LOGGER.log(
                Level.INFO,
                "Time loaded from RTC"
                        + " | utcMillis=" + rtcUtcMillis
                        + " | jakartaTime=" + Time.formatJakartaTime(rtcUtcMillis)
        );

        return true;
    }

    private boolean synchronizeFromNtp() {
        for (int index = 0; index < NTP_SERVERS.length; index++) {
            String server = NTP_SERVERS[index];

            LOGGER.log(Level.INFO, "Synchronizing time from NTP | server=" + server);

            try {
                SntpClient client = new SntpClient();
                boolean successful = client.requestTime(server, NTP_TIMEOUT_MS);

                if (!successful) {
                    LOGGER.log(Level.WARNING, "NTP request failed | server=" + server);
                    continue;
                }

                setTimeBase(client.getNtpTime(), client.getNtpTimeReference());

                long currentUtcMillis = getCurrentUtcMillis();

                Util.setCurrentTimeMillis(currentUtcMillis);

                LOGGER.log(
                        Level.INFO,
                        "NTP time synchronized"
                                + " | server=" + server
                                + " | utcMillis=" + currentUtcMillis
                                + " | jakartaTime=" + Time.formatJakartaTime(currentUtcMillis)
                                + " | roundTripMs=" + client.getRoundTripTime()
                );

                boolean rtcWritten = this.rtcService.writeUtcMillis(currentUtcMillis);

                if (rtcWritten) {
                    LOGGER.log(Level.INFO, "NTP UTC successfully written to RTC.");

                    boolean rtcValid = this.rtcService.isTimeValid();

                    LOGGER.log(
                            rtcValid ? Level.INFO : Level.WARNING,
                            "RTC validity after NTP write | valid=" + rtcValid
                    );
                } else {
                    LOGGER.log(Level.WARNING, "NTP time is available but RTC write failed.");
                }

                return true;
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "NTP synchronization error | server=" + server + " | error=" + exception);
            }
        }

        LOGGER.log(Level.WARNING, "Time synchronization failed.");
        return false;
    }

    private synchronized void markRtcInitializationFinished() {
        this.rtcInitializationFinished = true;
        notifyAll();
    }

    private synchronized void waitForRtcInitialization() {
        while (this.rtcInitializationStarted && !this.rtcInitializationFinished) {
            try {
                wait();
            } catch (InterruptedException exception) {
                LOGGER.log(Level.WARNING, "Waiting for RTC initialization interrupted | error=" + exception);
                return;
            }
        }
    }

    private synchronized void setTimeBase(long utcMillis, long referenceMillis) {
        this.timeBaseUtcMillis = utcMillis;
        this.timeBaseReferenceMillis = referenceMillis;
        this.timeSynchronized = true;
    }

    private synchronized long getCurrentUtcMillis() {
        if (!this.timeSynchronized) {
            return 0L;
        }

        long elapsedMillis = Util.platformTimeMillis() - this.timeBaseReferenceMillis;

        if (elapsedMillis < 0L) {
            elapsedMillis = 0L;
        }

        return this.timeBaseUtcMillis + elapsedMillis;
    }
}