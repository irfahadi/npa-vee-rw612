package com.nxp.example.smartgreenhouse.controllers;

import com.nxp.example.smartgreenhouse.models.actuator.ActuatorDataStore;
import com.nxp.example.smartgreenhouse.models.sensor.SensorData;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDataStore;
import com.nxp.example.smartgreenhouse.models.sensor.SensorHistoryStore;
import com.nxp.example.smartgreenhouse.services.lora.LoRaHardwareService;
import com.nxp.example.smartgreenhouse.services.mqtt.MqttSubscribeService;
import com.nxp.example.smartgreenhouse.services.watchdog.WatchdogNative;
import com.nxp.example.smartgreenhouse.state.AppState;
import com.nxp.example.smartgreenhouse.views.MainPage;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AppController {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: APP CONTROLLER]");

    private final MainPage mainPage;

    private final HeaderController headerController;
    private final OverviewController overviewController;
    private final MenuController menuController;
    private final SensorDetailController sensorDetailController;
    private final ActuatorDetailController actuatorDetailController;

    private final SensorHistoryStore sensorHistoryStore;
    private final ActuatorDataStore actuatorDataStore;

    private final LoRaHardwareService loRaHardwareService;
    private final MqttSubscribeService mqttSubscribeService;

    public AppController() {
        this.mainPage = new MainPage();

        AppState appState = new AppState();
        SensorDataStore sensorDataStore = new SensorDataStore();
        initializeSensorPlaceholders(sensorDataStore);

        this.sensorHistoryStore = new SensorHistoryStore();
        this.actuatorDataStore = new ActuatorDataStore();

        this.headerController = new HeaderController(this.mainPage);
        this.overviewController = new OverviewController(this.mainPage, appState, sensorDataStore);
        this.sensorDetailController = new SensorDetailController(this.mainPage, appState, sensorDataStore, this.sensorHistoryStore);
        this.actuatorDetailController = new ActuatorDetailController(this.mainPage, appState, this.actuatorDataStore);
        this.menuController = new MenuController(this.mainPage, appState, this.sensorDetailController, this.actuatorDetailController);

        this.loRaHardwareService = new LoRaHardwareService(sensorDataStore, this.sensorHistoryStore, this.overviewController, this.sensorDetailController);
        this.mqttSubscribeService = new MqttSubscribeService(this.actuatorDataStore, this.actuatorDetailController);

        this.actuatorDetailController.setMqttSubscribeService(this.mqttSubscribeService);

        this.headerController.setPeriodicTask(
                new Runnable() {
                    @Override
                    public void run() {
                        WatchdogNative.refresh();
                        AppController.this.loRaHardwareService.poll();
                    }
                }
        );

        this.headerController.setWifiProvisioningStartedTask(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.log(Level.INFO, "WiFi provisioning starting | pausing MQTT");
                        AppController.this.mqttSubscribeService.pauseForWifiProvisioning();
                    }
                }
        );

        this.headerController.setWifiConnectedTask(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.log(Level.INFO, "WiFi connected | checking time service state");

                        try {
                            boolean timeReady = AppController.this.headerController.synchronizeTime();

                            if (timeReady) {
                                LOGGER.log(Level.INFO, "Time service ready.");
                            } else {
                                LOGGER.log(Level.WARNING, "Time service not synchronized | MQTT will still be started");
                            }
                        } catch (RuntimeException exception) {
                            LOGGER.log(Level.WARNING, "Time synchronization failed | MQTT will still be started | error=" + exception);
                        }

                        AppController.this.mqttSubscribeService.resumeAfterWifiProvisioning();
                    }
                }
        );
    }

    public MainPage getMainPage() {
        return this.mainPage;
    }

    private void initializeSensorPlaceholders(SensorDataStore sensorDataStore) {
        SensorData tray1 = new SensorData(1, 1);
        SensorData tray2 = new SensorData(2, 1);
        tray1.setAvailable(false);
        tray2.setAvailable(false);
        sensorDataStore.replaceAll(new SensorData[]{tray1, tray2});
    }

    public void start() {
        this.headerController.init();
        this.overviewController.init();
        this.overviewController.refresh();
        this.menuController.init();
        this.sensorDetailController.init();
        this.actuatorDetailController.init();
    }
}