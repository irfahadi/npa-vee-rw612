package com.nxp.example.smartgreenhouse.controllers;

import com.nxp.example.smartgreenhouse.models.wifi.WifiProvisioningState;
import com.nxp.example.smartgreenhouse.services.time.TimeService;
import com.nxp.example.smartgreenhouse.services.wifi.WifiProvisioningService;
import com.nxp.example.smartgreenhouse.views.MainPage;
import com.nxp.example.smartgreenhouse.views.overview.HeaderOverview;
import com.nxp.example.smartgreenhouse.views.wifi.WifiProvisioningModal;

import ej.bon.Timer;
import ej.bon.TimerTask;
import ej.microui.MicroUI;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HeaderController {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: HEADER CONTROLLER]");

    private final MainPage mainPage;
    private final TimeService timeService;
    private final WifiProvisioningService wifiProvisioningService;

    private Runnable periodicTask;
    private Timer periodicTimer;
    private Runnable wifiConnectedTask;
    private Runnable wifiProvisioningStartedTask;

    private volatile boolean wifiProvisioningUiActive;

    public HeaderController(MainPage mainPage) {
        if (mainPage == null) {
            throw new NullPointerException("mainPage tidak boleh null.");
        }

        this.mainPage = mainPage;
        this.timeService = new TimeService();

        this.wifiProvisioningService = new WifiProvisioningService(
                new WifiProvisioningService.Listener() {
                    @Override
                    public void onStateChanged(int state) {
                        LOGGER.log(Level.INFO, "WiFi provisioning state changed | state=" + state);
                    }

                    @Override
                    public void onWifiConnectionStatusChanged(final boolean connected) {
                        HeaderController.this.updateWifiConnectionStatus(connected);
                    }

                    @Override
                    public void onProvisioningReady(final String ssid, final String password, final String portalUrl, final int networkCount) {
                        LOGGER.log(Level.INFO, "WiFi provisioning ready | SSID=" + ssid + " | portal=" + portalUrl + " | networkCount=" + networkCount);

                        if (!HeaderController.this.wifiProvisioningUiActive) {
                            return;
                        }

                        MicroUI.callSerially(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        HeaderController.this.mainPage.showWifiProvisioningReady(ssid, password, portalUrl, networkCount);
                                    }
                                }
                        );
                    }

                    @Override
                    public void onConnecting(final String ssid) {
                        LOGGER.log(Level.INFO, "WiFi connecting | SSID=" + ssid);

                        if (!HeaderController.this.wifiProvisioningUiActive) {
                            return;
                        }

                        MicroUI.callSerially(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        HeaderController.this.mainPage.showWifiConnecting(ssid);
                                    }
                                }
                        );
                    }

                    @Override
                    public void onConnected(String ssid) {
                        LOGGER.log(Level.INFO, "WiFi connected | SSID=" + ssid);

                        if (HeaderController.this.wifiProvisioningUiActive) {
                            HeaderController.this.wifiProvisioningUiActive = false;

                            MicroUI.callSerially(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            HeaderController.this.mainPage.closeWifiProvisioningModal();
                                        }
                                    }
                            );
                        }

                        HeaderController.this.runWifiConnectedTask();
                    }

                    @Override
                    public void onFailed(String message) {
                        LOGGER.log(Level.WARNING, "WiFi process failed | message=" + message);

                        if (!HeaderController.this.wifiProvisioningUiActive) {
                            return;
                        }

                        MicroUI.callSerially(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        HeaderController.this.mainPage.showWifiProvisioningFailed();
                                    }
                                }
                        );
                    }
                }
        );

        this.periodicTask = null;
        this.periodicTimer = null;
        this.wifiConnectedTask = null;
        this.wifiProvisioningStartedTask = null;
        this.wifiProvisioningUiActive = false;
    }

    public void init() {
        this.mainPage.updateWifiConnectionStatus(false);
        registerWifiClickListener();
        registerWifiProvisioningBackListener();
        startClock();
        startPeriodicTask();
        this.wifiProvisioningService.tryAutoConnect();
    }

    public void setPeriodicTask(Runnable periodicTask) {
        this.periodicTask = periodicTask;
    }

    public void setWifiConnectedTask(Runnable wifiConnectedTask) {
        this.wifiConnectedTask = wifiConnectedTask;
    }

    public void setWifiProvisioningStartedTask(Runnable wifiProvisioningStartedTask) {
        this.wifiProvisioningStartedTask = wifiProvisioningStartedTask;
    }

    public boolean synchronizeTime() {
        return this.timeService.synchronizeTime();
    }

    private void registerWifiClickListener() {
        this.mainPage.setOnWifiClick(
                new HeaderOverview.onWifiClickListener() {
                    @Override
                    public void onClicked() {
                        HeaderController.this.startProvisioningFromUser();
                    }
                }
        );
    }

    private void registerWifiProvisioningBackListener() {
        this.mainPage.setOnWifiProvisioningBackListener(
                new WifiProvisioningModal.OnBackListener() {
                    @Override
                    public void onBack() {
                        HeaderController.this.cancelProvisioningFromUser();
                    }
                }
        );
    }

    private void startProvisioningFromUser() {
        if (this.wifiProvisioningService.isBusy()) {
            LOGGER.log(Level.WARNING, "WiFi provisioning request ignored | service is busy");
            return;
        }

        this.wifiProvisioningUiActive = true;
        this.mainPage.showWifiProvisioningStarting();

        if (!runWifiProvisioningStartedTask()) {
            this.wifiProvisioningUiActive = false;
            this.mainPage.closeWifiProvisioningModal();
            LOGGER.log(Level.WARNING, "WiFi provisioning cancelled | preparation task failed");
            return;
        }

        boolean started = this.wifiProvisioningService.startProvisioning();

        if (!started) {
            this.wifiProvisioningUiActive = false;
            this.mainPage.closeWifiProvisioningModal();
            LOGGER.log(Level.WARNING, "WiFi provisioning could not be started");
        }
    }

    private void cancelProvisioningFromUser() {
        if (!this.wifiProvisioningUiActive) {
            return;
        }

        if (this.wifiProvisioningService.getState() == WifiProvisioningState.CONNECTING) {
            LOGGER.log(Level.WARNING, "WiFi provisioning Back ignored | connection is already in progress");
            return;
        }

        boolean accepted = this.wifiProvisioningService.cancelProvisioning();

        if (!accepted) {
            LOGGER.log(Level.WARNING, "WiFi provisioning Back could not be processed");
            return;
        }

        LOGGER.log(Level.INFO, "WiFi provisioning Back accepted");

        this.wifiProvisioningUiActive = false;
        this.mainPage.closeWifiProvisioningModal();
    }

    private boolean runWifiProvisioningStartedTask() {
        Runnable task = this.wifiProvisioningStartedTask;

        if (task == null) {
            return true;
        }

        try {
            task.run();
            return true;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi provisioning started task failed | error=" + exception);
            return false;
        }
    }

    private void runWifiConnectedTask() {
        Runnable task = this.wifiConnectedTask;

        if (task == null) {
            return;
        }

        try {
            task.run();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi connected task failed | error=" + exception);
        }
    }

    private void updateWifiConnectionStatus(final boolean connected) {
        MicroUI.callSerially(
                new Runnable() {
                    @Override
                    public void run() {
                        HeaderController.this.mainPage.updateWifiConnectionStatus(connected);
                    }
                }
        );
    }

    private void startClock() {
        this.timeService.startClock(
                new TimeService.ClockListener() {
                    @Override
                    public void onTimeChanged(final String currentTime) {
                        MicroUI.callSerially(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        HeaderController.this.mainPage.updateTime(currentTime);
                                    }
                                }
                        );
                    }
                }
        );
    }

    private void startPeriodicTask() {
        if (this.periodicTimer != null) {
            return;
        }

        this.periodicTimer = new Timer();
        this.periodicTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        Runnable task = HeaderController.this.periodicTask;

                        if (task == null) {
                            return;
                        }

                        try {
                            task.run();
                        } catch (RuntimeException exception) {
                            LOGGER.log(Level.WARNING, "Periodic application task failed: " + exception);
                        }
                    }
                },
                0,
                5000
        );
    }

}