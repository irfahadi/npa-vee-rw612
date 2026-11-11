package com.nxp.example.smartgreenhouse.services.mqtt;

import com.nxp.example.smartgreenhouse.controllers.ActuatorDetailController;
import com.nxp.example.smartgreenhouse.models.actuator.ActuatorDataStore;

import ej.microui.MicroUI;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

import ej.bon.Util;

public final class MqttSubscribeService {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: MQTT SUBSCRIBE SERVICE]");

    private static final String BROKER_URI = "tcp://168.110.214.70:1883";
    private static final String CLIENT_ID = "clientku";
    private static final String TOPIC_FILTER = "gh01/node/255/status/+";
    private static final int SUBSCRIBE_QOS = 0;
    private static final String VALVE_STATUS_TOPIC_PREFIX = "gh01/node/255/status/valve";
    private static final String VALVE_CONTROL_TOPIC_PREFIX = "gh01/node/255/control/valve";
    private static final int CONTROL_QOS = 0;
    private static final boolean CONTROL_RETAINED = false;
    private static final int MIN_VALVE_ID = 1;
    private static final int MAX_VALVE_ID = 2;
    private static final long CONNECTION_CHECK_INTERVAL_MS = 5000L;
    private static final long RECONNECT_INITIAL_DELAY_MS = 2000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30000L;
    private static final boolean AUTHENTICATION_ENABLED = false;
    private static final String USERNAME = "";
    private static final String PASSWORD = "";

    private final ActuatorDataStore actuatorDataStore;
    private final ActuatorDetailController actuatorDetailController;

    private MqttClient mqttClient;
    private boolean workerStarted;
    private volatile boolean serviceRunning;
    private volatile boolean subscribed;
    private volatile boolean provisioningPaused;

    private final boolean[] pendingValveCommands;
    private final boolean[] pendingValveStates;

    public MqttSubscribeService(ActuatorDataStore actuatorDataStore, ActuatorDetailController actuatorDetailController) {
        if (actuatorDataStore == null) {
            throw new NullPointerException("actuatorDataStore tidak boleh null.");
        }

        if (actuatorDetailController == null) {
            throw new NullPointerException("actuatorDetailController tidak boleh null.");
        }

        this.actuatorDataStore = actuatorDataStore;
        this.actuatorDetailController = actuatorDetailController;
        this.mqttClient = null;
        this.workerStarted = false;
        this.serviceRunning = false;
        this.subscribed = false;
        this.provisioningPaused = false;
        this.pendingValveCommands = new boolean[MAX_VALVE_ID + 1];
        this.pendingValveStates = new boolean[MAX_VALVE_ID + 1];
    }

    public synchronized void start() {
        if (this.workerStarted) {
            LOGGER.log(Level.INFO, "MQTT connection worker is already running.");
            return;
        }

        this.workerStarted = true;
        this.serviceRunning = true;

        Thread mqttWorker = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        MqttSubscribeService.this.runConnectionLoop();
                    }
                },
                "mqtt-worker"
        );

        try {
            mqttWorker.start();
        } catch (Error error) {
            this.workerStarted = false;
            this.serviceRunning = false;
            LOGGER.log(Level.SEVERE, "Unable to start MQTT worker | error=" + error);
        }
    }

    public void pauseForWifiProvisioning() {
        synchronized (this) {
            this.provisioningPaused = true;
            this.subscribed = false;
            notifyAll();
        }

        failAllPendingValveCommands();
        LOGGER.log(Level.INFO, "MQTT pause requested for WiFi provisioning.");
    }

    public void resumeAfterWifiProvisioning() {
        boolean startWorker;

        synchronized (this) {
            this.provisioningPaused = false;
            startWorker = !this.workerStarted;
            notifyAll();
        }

        if (startWorker) {
            LOGGER.log(Level.INFO, "MQTT starting after WiFi connection.");
            start();
            return;
        }

        LOGGER.log(Level.INFO, "MQTT resumed after WiFi connection.");
    }

    private void runConnectionLoop() {
        long reconnectDelay = RECONNECT_INITIAL_DELAY_MS;

        while (this.serviceRunning) {
            try {
                if (this.provisioningPaused) {
                    disconnectClientForProvisioning();
                    waitWhileProvisioningPaused();
                    continue;
                }

                if (!this.serviceRunning) {
                    break;
                }

                ensureClientCreated();

                MqttClient client = this.mqttClient;

                if (client == null) {
                    throw new IllegalStateException("MQTT client was not created.");
                }

                if (this.provisioningPaused) {
                    continue;
                }

                if (!client.isConnected()) {
                    this.subscribed = false;
                }

                if (!client.isConnected() || !this.subscribed) {
                    connectAndSubscribeOnce();

                    if (this.provisioningPaused) {
                        continue;
                    }

                    reconnectDelay = RECONNECT_INITIAL_DELAY_MS;
                }

                while (this.serviceRunning && !this.provisioningPaused && client.isConnected() && this.subscribed) {
                    processPendingControlCommands();

                    synchronized (this) {
                        boolean commandPending = this.pendingValveCommands[1] || this.pendingValveCommands[2];

                        if (this.serviceRunning && !this.provisioningPaused && client.isConnected() && this.subscribed && !commandPending) {
                            wait(CONNECTION_CHECK_INTERVAL_MS);
                        }
                    }
                }
            } catch (InterruptedException exception) {
                LOGGER.log(Level.WARNING, "MQTT worker was interrupted | error=" + exception);
                break;
            } catch (MqttException exception) {
                LOGGER.log(Level.WARNING, "MQTT operation failed | reasonCode=" + exception.getReasonCode() + " | message=" + exception.getMessage() + " | cause=" + exception.getCause());
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Unexpected MQTT worker error | error=" + exception);
            }

            if (!this.serviceRunning) {
                break;
            }

            if (this.provisioningPaused) {
                continue;
            }

            LOGGER.log(Level.INFO, "MQTT reconnect scheduled | delayMs=" + reconnectDelay);

            try {
                waitForReconnectDelay(reconnectDelay);
            } catch (InterruptedException exception) {
                LOGGER.log(Level.WARNING, "MQTT reconnect delay was interrupted | error=" + exception);
                break;
            }

            if (this.provisioningPaused) {
                continue;
            }

            reconnectDelay = reconnectDelay * 2L;

            if (reconnectDelay > RECONNECT_MAX_DELAY_MS) {
                reconnectDelay = RECONNECT_MAX_DELAY_MS;
            }
        }

        synchronized (this) {
            this.workerStarted = false;
            this.serviceRunning = false;
            this.subscribed = false;
        }

        LOGGER.log(Level.INFO, "MQTT connection worker stopped.");
    }

    private void disconnectClientForProvisioning() {
        MqttClient client;

        synchronized (this) {
            this.subscribed = false;
            client = this.mqttClient;
        }

        if (client == null || !client.isConnected()) {
            LOGGER.log(Level.INFO, "MQTT paused for WiFi provisioning | client is not connected.");
            return;
        }

        try {
            client.disconnect();
            LOGGER.log(Level.INFO, "MQTT disconnected for WiFi provisioning.");
        } catch (MqttException exception) {
            LOGGER.log(Level.WARNING, "MQTT disconnect during WiFi provisioning failed | reasonCode=" + exception.getReasonCode() + " | message=" + exception.getMessage());
        }
    }

    private void waitWhileProvisioningPaused() throws InterruptedException {
        synchronized (this) {
            while (this.serviceRunning && this.provisioningPaused) {
                wait();
            }
        }
    }

    private void waitForReconnectDelay(long delayMilliseconds) throws InterruptedException {
        long deadline = Util.platformTimeMillis() + delayMilliseconds;

        synchronized (this) {
            while (this.serviceRunning && !this.provisioningPaused) {
                long remaining = deadline - Util.platformTimeMillis();

                if (remaining <= 0L) {
                    return;
                }

                wait(remaining);
            }
        }
    }

    private synchronized void ensureClientCreated() throws MqttException {
        if (this.mqttClient != null) {
            return;
        }

        this.mqttClient = new MqttClient(BROKER_URI, CLIENT_ID);

        this.mqttClient.setCallback(
                new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        MqttSubscribeService.this.subscribed = false;
                        MqttSubscribeService.this.failAllPendingValveCommands();

                        synchronized (MqttSubscribeService.this) {
                            MqttSubscribeService.this.notifyAll();
                        }

                        if (cause instanceof MqttException) {
                            MqttException mqttException = (MqttException) cause;

                            LOGGER.log(
                                    Level.WARNING,
                                    "MQTT connection lost"
                                            + " | reasonCode=" + mqttException.getReasonCode()
                                            + " | message=" + mqttException.getMessage()
                                            + " | rootCause=" + mqttException.getCause()
                            );
                        } else {
                            LOGGER.log(Level.WARNING, "MQTT connection lost | cause=" + cause);
                        }
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        byte[] payload = message.getPayload();
                        String payloadText = payload == null ? "" : new String(payload);

                        LOGGER.log(
                                Level.INFO,
                                "\r\n"
                                        + "[MQTT] Message received"
                                        + "\r\n"
                                        + "[MQTT] Topic   : " + topic
                                        + "\r\n"
                                        + "[MQTT] QoS     : " + message.getQos()
                                        + "\r\n"
                                        + "[MQTT] Payload : " + payloadText
                        );

                        handleValveStatus(topic, payloadText);
                    }
                }
        );
    }

    private void connectAndSubscribeOnce() throws MqttException {
        MqttClient client = this.mqttClient;

        if (client == null) {
            throw new IllegalStateException("MQTT client is null.");
        }

        if (this.provisioningPaused) {
            return;
        }

        if (!client.isConnected()) {
            LOGGER.log(Level.INFO, "Connecting to MQTT broker | URI=" + BROKER_URI + " | clientId=" + CLIENT_ID);

            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setConnectionTimeout(10);
            connectOptions.setKeepAliveInterval(60);

            if (AUTHENTICATION_ENABLED) {
                connectOptions.setUserName(USERNAME);
                connectOptions.setPassword(PASSWORD.toCharArray());
            }

            client.connect(connectOptions);

            if (this.provisioningPaused) {
                this.subscribed = false;

                if (client.isConnected()) {
                    client.disconnect();
                }

                return;
            }

            LOGGER.log(Level.INFO, "MQTT broker connected | URI=" + BROKER_URI);
        }

        if (!this.subscribed && !this.provisioningPaused) {
            LOGGER.log(Level.INFO, "Subscribing to MQTT topic | topic=" + TOPIC_FILTER + " | qos=" + SUBSCRIBE_QOS);

            client.subscribe(TOPIC_FILTER, SUBSCRIBE_QOS);
            this.subscribed = true;

            LOGGER.log(Level.INFO, "MQTT subscription successful | topic=" + TOPIC_FILTER);
        }
    }

    public synchronized boolean requestValveControl(int valveId, boolean targetOpen) {
        if (valveId < MIN_VALVE_ID || valveId > MAX_VALVE_ID) {
            LOGGER.log(Level.WARNING, "Invalid valve control request | valveId=" + valveId);
            return false;
        }

        if (this.provisioningPaused) {
            LOGGER.log(Level.WARNING, "Valve control rejected | MQTT is paused for WiFi provisioning | valveId=" + valveId);
            return false;
        }

        if (this.mqttClient == null || !this.mqttClient.isConnected()) {
            LOGGER.log(
                    Level.WARNING,
                    "Valve control rejected"
                            + " | MQTT is not connected"
                            + " | valveId=" + valveId
                            + " | target=" + (targetOpen ? "ON" : "OFF")
            );

            return false;
        }

        this.pendingValveStates[valveId] = targetOpen;
        this.pendingValveCommands[valveId] = true;
        notifyAll();

        LOGGER.log(
                Level.INFO,
                "[MQTT] Valve control queued"
                        + " | valveId=" + valveId
                        + " | trayId=" + valveId
                        + " | payload=" + (targetOpen ? "1" : "0")
        );

        return true;
    }

    private void processPendingControlCommands() throws MqttException {
        if (this.provisioningPaused) {
            return;
        }

        publishPendingValveCommand(1);
        publishPendingValveCommand(2);
    }

    private void publishPendingValveCommand(int valveId) throws MqttException {
        if (this.provisioningPaused) {
            return;
        }

        final boolean commandPending;
        final boolean targetOpen;

        synchronized (this) {
            commandPending = this.pendingValveCommands[valveId];
            targetOpen = this.pendingValveStates[valveId];
        }

        if (!commandPending) {
            return;
        }

        MqttClient client = this.mqttClient;

        if (client == null || !client.isConnected()) {
            boolean removed = clearPendingValveCommand(valveId, targetOpen);

            if (removed) {
                notifyValveControlPublishFailed(valveId, targetOpen);
            }

            return;
        }

        String topic = VALVE_CONTROL_TOPIC_PREFIX + valveId;
        String payloadText = targetOpen ? "1" : "0";

        LOGGER.log(
                Level.INFO,
                "[MQTT] Publishing valve control"
                        + " | topic=" + topic
                        + " | payload=" + payloadText
                        + " | qos=" + CONTROL_QOS
                        + " | retained=" + CONTROL_RETAINED
        );

        try {
            client.publish(topic, payloadText.getBytes(), CONTROL_QOS, CONTROL_RETAINED);
        } catch (MqttException exception) {
            boolean removed = clearPendingValveCommand(valveId, targetOpen);

            if (removed) {
                notifyValveControlPublishFailed(valveId, targetOpen);
            }

            throw exception;
        }

        boolean removed = clearPendingValveCommand(valveId, targetOpen);

        if (removed) {
            notifyValveControlPublished(valveId, targetOpen);
        }

        LOGGER.log(Level.INFO, "[MQTT] Valve control published | topic=" + topic + " | payload=" + payloadText);
    }

    private synchronized boolean clearPendingValveCommand(int valveId, boolean targetOpen) {
        if (!this.pendingValveCommands[valveId]) {
            return false;
        }

        if (this.pendingValveStates[valveId] != targetOpen) {
            return false;
        }

        this.pendingValveCommands[valveId] = false;
        return true;
    }

    private void notifyValveControlPublished(final int valveId, final boolean targetOpen) {
        MicroUI.callSerially(
                new Runnable() {
                    @Override
                    public void run() {
                        MqttSubscribeService.this.actuatorDetailController.onValveControlPublished(valveId, targetOpen);
                    }
                }
        );
    }

    private void notifyValveControlPublishFailed(final int valveId, final boolean targetOpen) {
        LOGGER.log(Level.WARNING, "[MQTT] Valve control publish failed | valveId=" + valveId + " | target=" + (targetOpen ? "ON" : "OFF"));

        MicroUI.callSerially(
                new Runnable() {
                    @Override
                    public void run() {
                        MqttSubscribeService.this.actuatorDetailController.onValveControlPublishFailed(valveId, targetOpen);
                    }
                }
        );
    }

    private void failAllPendingValveCommands() {
        for (int valveId = MIN_VALVE_ID; valveId <= MAX_VALVE_ID; valveId++) {
            final boolean pending;
            final boolean targetOpen;

            synchronized (this) {
                pending = this.pendingValveCommands[valveId];
                targetOpen = this.pendingValveStates[valveId];

                if (pending) {
                    this.pendingValveCommands[valveId] = false;
                }
            }

            if (pending) {
                notifyValveControlPublishFailed(valveId, targetOpen);
            }
        }
    }

    private void handleValveStatus(String topic, String payloadText) {
        final int valveId = parseValveId(topic);

        if (valveId < MIN_VALVE_ID || valveId > MAX_VALVE_ID) {
            LOGGER.log(Level.WARNING, "Unsupported actuator topic | topic=" + topic);
            return;
        }

        if (payloadText == null) {
            LOGGER.log(Level.WARNING, "Empty MQTT actuator payload | topic=" + topic);
            return;
        }

        String normalizedPayload = payloadText.trim();
        final boolean open;

        if ("ON".equalsIgnoreCase(normalizedPayload) || "1".equals(normalizedPayload)) {
            open = true;
        } else if ("OFF".equalsIgnoreCase(normalizedPayload) || "0".equals(normalizedPayload)) {
            open = false;
        } else {
            LOGGER.log(Level.WARNING, "Unsupported actuator payload | topic=" + topic + " | payload=" + payloadText);
            return;
        }

        final long receivedTimestamp = Util.currentTimeMillis();

        MicroUI.callSerially(
                new Runnable() {
                    @Override
                    public void run() {
                        boolean updated = MqttSubscribeService.this.actuatorDataStore.updateValveState(valveId, open, receivedTimestamp);

                        if (!updated) {
                            LOGGER.log(Level.WARNING, "Valve was not found | valveId=" + valveId);
                            return;
                        }

                        MqttSubscribeService.this.actuatorDetailController.refreshIfOpen();

                        LOGGER.log(
                                Level.INFO,
                                "[MQTT] Valve status applied"
                                        + " | valveId=" + valveId
                                        + " | trayId=" + valveId
                                        + " | state=" + (open ? "ON" : "OFF")
                                        + " | timestamp=" + receivedTimestamp
                        );
                    }
                }
        );
    }

    private static int parseValveId(String topic) {
        if (topic == null || !topic.startsWith(VALVE_STATUS_TOPIC_PREFIX)) {
            return -1;
        }

        String valveIdText = topic.substring(VALVE_STATUS_TOPIC_PREFIX.length());

        if (valveIdText.length() == 0) {
            return -1;
        }

        for (int index = 0; index < valveIdText.length(); index++) {
            char character = valveIdText.charAt(index);

            if (character < '0' || character > '9') {
                return -1;
            }
        }

        try {
            return Integer.parseInt(valveIdText);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public synchronized boolean isConnected() {
        return this.mqttClient != null && this.mqttClient.isConnected();
    }
}