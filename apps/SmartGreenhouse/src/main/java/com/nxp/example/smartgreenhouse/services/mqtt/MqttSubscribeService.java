package com.nxp.example.smartgreenhouse.services.mqtt;

import com.nxp.example.smartgreenhouse.controllers.ActuatorDetailController;
import com.nxp.example.smartgreenhouse.models.actuator.ActuatorDataStore;
import com.nxp.example.smartgreenhouse.models.sensor.SensorData;

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

    // Collaboration data contract (WiFi / Internet). All payloads are flat JSON objects.
    private static final String HOUSE_ID = "GH01";
    private static final String ACTUATOR_STATUS_TOPIC_PREFIX = "gh01/node/255/status/";
    private static final String PUMP_STATUS_TOPIC = "gh01/node/255/status/pump";
    private static final String PUMP_CONTROL_TOPIC = "gh01/node/255/control/pump";

    // Sensor LoRa -> Sink Node -> Cloud
    private static final String TELEMETRY_ENV_TOPIC = "gh01/telemetry/env";
    // Cloud -> Sink Node -> Collaboration Agent
    private static final String MISSION_COMMAND_TOPIC = "gh01/mission/command";
    private static final String AGENT_MISSION_COMMAND_TOPIC = "gh01/agent/mission/command";
    // Collaboration Agent -> Sink Node -> Cloud
    private static final String AGENT_MISSION_STATUS_TOPIC = "gh01/agent/mission/status";
    private static final String MISSION_STATUS_TOPIC = "gh01/mission/status";
    private static final String AGENT_MISSION_RESULT_TOPIC = "gh01/agent/mission/result";
    private static final String MISSION_RESULT_TOPIC = "gh01/mission/result";
    // Cloud -> Sink Node -> Unit Kontrol
    private static final String ACTUATOR_CONTROL_TOPIC = "gh01/actuator/control";

    private static final String[] COLLABORATION_TOPIC_FILTERS = {
            MISSION_COMMAND_TOPIC,
            AGENT_MISSION_STATUS_TOPIC,
            AGENT_MISSION_RESULT_TOPIC,
            ACTUATOR_CONTROL_TOPIC
    };

    private static final int COLLABORATION_QOS = 0;
    private static final int TELEMETRY_ENV_MAX_BYTES = 192;
    private static final int MISSION_COMMAND_MAX_BYTES = 192;
    private static final int MISSION_STATUS_MAX_BYTES = 128;
    private static final int MISSION_RESULT_MAX_BYTES = 256;
    private static final int ACTUATOR_CONTROL_MAX_BYTES = 128;

    private static final int OUTBOUND_QUEUE_CAPACITY = 8;
    private static final int PUMP_TIMER_INDEX = 0;

    private final ActuatorDataStore actuatorDataStore;
    private final ActuatorDetailController actuatorDetailController;

    private MqttClient mqttClient;
    private boolean workerStarted;
    private volatile boolean serviceRunning;
    private volatile boolean subscribed;
    private volatile boolean provisioningPaused;

    private final boolean[] pendingValveCommands;
    private final boolean[] pendingValveStates;

    // Outbound messages are queued here and published by the MQTT worker only,
    // so nothing is ever published from the MQTT callback thread.
    private final String[] outboundTopics;
    private final byte[][] outboundPayloads;
    private int outboundHead;
    private int outboundCount;

    // Auto-OFF deadlines (platform time, 0 = none) for "dur_s". Index 0 = pump, 1..n = valve id.
    private final long[] actuatorOffDeadlines;

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
        this.outboundTopics = new String[OUTBOUND_QUEUE_CAPACITY];
        this.outboundPayloads = new byte[OUTBOUND_QUEUE_CAPACITY][];
        this.outboundHead = 0;
        this.outboundCount = 0;
        this.actuatorOffDeadlines = new long[MAX_VALVE_ID + 1];
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
                    processActuatorOffDeadlines();
                    processOutboundQueue();

                    synchronized (this) {
                        boolean commandPending = this.pendingValveCommands[1] || this.pendingValveCommands[2] || this.outboundCount > 0;

                        if (this.serviceRunning && !this.provisioningPaused && client.isConnected() && this.subscribed && !commandPending) {
                            wait(nextWaitMilliseconds());
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

                        try {
                            dispatchMessage(topic, payloadText);
                        } catch (RuntimeException exception) {
                            LOGGER.log(Level.WARNING, "MQTT message handling failed | topic=" + topic + " | error=" + exception);
                        }
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

            for (int index = 0; index < COLLABORATION_TOPIC_FILTERS.length; index++) {
                client.subscribe(COLLABORATION_TOPIC_FILTERS[index], COLLABORATION_QOS);
                LOGGER.log(Level.INFO, "MQTT subscription successful | topic=" + COLLABORATION_TOPIC_FILTERS[index]);
            }

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
        this.actuatorOffDeadlines[valveId] = 0L;
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

    private void dispatchMessage(String topic, String payloadText) {
        if (topic == null) {
            return;
        }

        if (topic.startsWith(ACTUATOR_STATUS_TOPIC_PREFIX)) {
            handleValveStatus(topic, payloadText);
        } else if (MISSION_COMMAND_TOPIC.equals(topic)) {
            relayMission(topic, AGENT_MISSION_COMMAND_TOPIC, payloadText, MISSION_COMMAND_MAX_BYTES, "cmd");
        } else if (AGENT_MISSION_STATUS_TOPIC.equals(topic)) {
            relayMission(topic, MISSION_STATUS_TOPIC, payloadText, MISSION_STATUS_MAX_BYTES, "st");
        } else if (AGENT_MISSION_RESULT_TOPIC.equals(topic)) {
            relayMission(topic, MISSION_RESULT_TOPIC, payloadText, MISSION_RESULT_MAX_BYTES, "tray_id");
        } else if (ACTUATOR_CONTROL_TOPIC.equals(topic)) {
            handleActuatorControl(payloadText);
        } else {
            LOGGER.log(Level.WARNING, "Unsupported MQTT topic | topic=" + topic);
        }
    }

    /**
     * Publishes one LoRa sensor sample to the cloud on telemetry/env.
     * Safe to call from any thread; the sample is dropped when MQTT is not connected.
     */
    public boolean publishTelemetry(SensorData data, int sequence, int rssi, long timestampMillis) {
        if (data == null) {
            return false;
        }

        if (!isReadyToPublish()) {
            return false;
        }

        StringBuilder json = new StringBuilder(TELEMETRY_ENV_MAX_BYTES);

        json.append("{\"h_id\":\"").append(HOUSE_ID).append("\",\"n_id\":").append(data.getNodeId());
        json.append(",\"seq\":").append(sequence);
        json.append(",\"ts\":").append(timestampMillis / 1000L);
        json.append(",\"sm\":");
        appendTenths(json, data.getSm());
        json.append(",\"st\":");
        appendTenths(json, data.getSt());
        json.append(",\"ec\":").append(Math.round(data.getEc()));
        json.append(",\"ph\":");
        appendTenths(json, data.getpH());
        json.append(",\"n\":").append(Math.round(data.getN()));
        json.append(",\"p\":").append(Math.round(data.getP()));
        json.append(",\"k\":").append(Math.round(data.getK()));
        json.append(",\"at\":");
        appendTenths(json, data.getAt());
        json.append(",\"ah\":");
        appendTenths(json, data.getAh());
        json.append(",\"lux\":").append(Math.round(data.getLux()));
        json.append(",\"rssi\":").append(rssi);
        json.append('}');

        byte[] payload = json.toString().getBytes();

        if (payload.length > TELEMETRY_ENV_MAX_BYTES) {
            LOGGER.log(Level.WARNING, "Telemetry dropped | payload too large | bytes=" + payload.length);
            return false;
        }

        return enqueueOutbound(TELEMETRY_ENV_TOPIC, payload);
    }

    private static void appendTenths(StringBuilder builder, float value) {
        int scaled = Math.round(value * 10.0f);

        if (scaled < 0) {
            builder.append('-');
            scaled = -scaled;
        }

        builder.append(scaled / 10).append('.').append(scaled % 10);
    }

    /** Forwards a mission message unchanged between the cloud side and the Collaboration Agent side. */
    private void relayMission(String sourceTopic, String targetTopic, String payloadText, int maxBytes, String requiredKey) {
        byte[] payload = payloadText == null ? new byte[0] : payloadText.getBytes();

        if (payload.length == 0 || payload.length > maxBytes) {
            LOGGER.log(Level.WARNING, "Mission message dropped | invalid size | topic=" + sourceTopic + " | bytes=" + payload.length + " | max=" + maxBytes);
            return;
        }

        String missionId = JsonLite.getString(payloadText, "m_id");

        if (missionId == null || !JsonLite.hasKey(payloadText, requiredKey)) {
            LOGGER.log(Level.WARNING, "Mission message dropped | missing m_id or " + requiredKey + " | topic=" + sourceTopic);
            return;
        }

        if (enqueueOutbound(targetTopic, payload)) {
            LOGGER.log(Level.INFO, "[MQTT] Mission message relayed | m_id=" + missionId + " | from=" + sourceTopic + " | to=" + targetTopic);
        }
    }

    /**
     * Handles actuator/control from the cloud and translates it to the existing
     * Unit Kontrol protocol ("1"/"0" on gh01/node/255/control/...).
     */
    private void handleActuatorControl(String payloadText) {
        if (payloadText == null || payloadText.length() == 0 || payloadText.getBytes().length > ACTUATOR_CONTROL_MAX_BYTES) {
            LOGGER.log(Level.WARNING, "Actuator control dropped | invalid size");
            return;
        }

        String actuatorId = JsonLite.getString(payloadText, "act_id");
        String action = JsonLite.getString(payloadText, "action");
        int durationSeconds = JsonLite.getInt(payloadText, "dur_s", 0);

        if (actuatorId == null || action == null) {
            LOGGER.log(Level.WARNING, "Actuator control dropped | missing act_id or action");
            return;
        }

        final int timerIndex;

        if ("PUMP".equalsIgnoreCase(actuatorId)) {
            timerIndex = PUMP_TIMER_INDEX;
        } else if (actuatorId.length() > 5 && actuatorId.substring(0, 5).equalsIgnoreCase("VALVE")) {
            timerIndex = parseValveId(VALVE_STATUS_TOPIC_PREFIX + actuatorId.substring(5));

            if (timerIndex < MIN_VALVE_ID || timerIndex > MAX_VALVE_ID) {
                LOGGER.log(Level.WARNING, "Actuator control dropped | unknown act_id=" + actuatorId);
                return;
            }
        } else {
            LOGGER.log(Level.WARNING, "Actuator control dropped | unknown act_id=" + actuatorId);
            return;
        }

        final boolean targetOn;

        if ("ON".equalsIgnoreCase(action)) {
            targetOn = true;
        } else if ("OFF".equalsIgnoreCase(action)) {
            targetOn = false;
        } else {
            LOGGER.log(Level.WARNING, "Actuator control dropped | unknown action=" + action);
            return;
        }

        if (!enqueueActuatorCommand(timerIndex, targetOn)) {
            return;
        }

        synchronized (this) {
            if (targetOn && durationSeconds > 0) {
                this.actuatorOffDeadlines[timerIndex] = Util.platformTimeMillis() + durationSeconds * 1000L;
            } else {
                this.actuatorOffDeadlines[timerIndex] = 0L;
            }

            notifyAll();
        }

        LOGGER.log(
                Level.INFO,
                "[MQTT] Actuator control accepted"
                        + " | act_id=" + actuatorId
                        + " | action=" + (targetOn ? "ON" : "OFF")
                        + " | dur_s=" + durationSeconds
                        + " | trig=" + JsonLite.getString(payloadText, "trig")
                        + " | seq=" + JsonLite.getInt(payloadText, "seq", -1)
        );
    }

    private boolean enqueueActuatorCommand(int timerIndex, boolean targetOn) {
        String topic = timerIndex == PUMP_TIMER_INDEX ? PUMP_CONTROL_TOPIC : VALVE_CONTROL_TOPIC_PREFIX + timerIndex;

        return enqueueOutbound(topic, (targetOn ? "1" : "0").getBytes());
    }

    /** Switches OFF every actuator whose "dur_s" window has elapsed. */
    private void processActuatorOffDeadlines() {
        long now = Util.platformTimeMillis();

        for (int index = 0; index < this.actuatorOffDeadlines.length; index++) {
            boolean expired;

            synchronized (this) {
                long deadline = this.actuatorOffDeadlines[index];
                expired = deadline != 0L && now >= deadline;

                if (expired) {
                    this.actuatorOffDeadlines[index] = 0L;
                }
            }

            if (expired) {
                LOGGER.log(Level.INFO, "[MQTT] Actuator duration elapsed | switching OFF | index=" + index);
                enqueueActuatorCommand(index, false);
            }
        }
    }

    /** Must be called while holding the monitor of this object. */
    private long nextWaitMilliseconds() {
        long waitMilliseconds = CONNECTION_CHECK_INTERVAL_MS;
        long now = Util.platformTimeMillis();

        for (int index = 0; index < this.actuatorOffDeadlines.length; index++) {
            long deadline = this.actuatorOffDeadlines[index];

            if (deadline != 0L && deadline - now < waitMilliseconds) {
                waitMilliseconds = deadline - now;
            }
        }

        return waitMilliseconds < 1L ? 1L : waitMilliseconds;
    }

    private synchronized boolean isReadyToPublish() {
        return !this.provisioningPaused && this.subscribed && this.mqttClient != null && this.mqttClient.isConnected();
    }

    private synchronized boolean enqueueOutbound(String topic, byte[] payload) {
        if (!isReadyToPublish()) {
            LOGGER.log(Level.WARNING, "MQTT publish rejected | not connected | topic=" + topic);
            return false;
        }

        if (this.outboundCount == OUTBOUND_QUEUE_CAPACITY) {
            LOGGER.log(Level.WARNING, "MQTT outbound queue full | dropping oldest | topic=" + this.outboundTopics[this.outboundHead]);
            this.outboundHead = (this.outboundHead + 1) % OUTBOUND_QUEUE_CAPACITY;
            this.outboundCount--;
        }

        int tail = (this.outboundHead + this.outboundCount) % OUTBOUND_QUEUE_CAPACITY;

        this.outboundTopics[tail] = topic;
        this.outboundPayloads[tail] = payload;
        this.outboundCount++;
        notifyAll();

        return true;
    }

    private void processOutboundQueue() throws MqttException {
        while (!this.provisioningPaused) {
            final String topic;
            final byte[] payload;

            synchronized (this) {
                if (this.outboundCount == 0) {
                    return;
                }

                topic = this.outboundTopics[this.outboundHead];
                payload = this.outboundPayloads[this.outboundHead];
                this.outboundTopics[this.outboundHead] = null;
                this.outboundPayloads[this.outboundHead] = null;
                this.outboundHead = (this.outboundHead + 1) % OUTBOUND_QUEUE_CAPACITY;
                this.outboundCount--;
            }

            MqttClient client = this.mqttClient;

            if (client == null || !client.isConnected()) {
                LOGGER.log(Level.WARNING, "MQTT publish dropped | not connected | topic=" + topic);
                continue;
            }

            client.publish(topic, payload, COLLABORATION_QOS, false);
            LOGGER.log(Level.INFO, "[MQTT] Published | topic=" + topic + " | bytes=" + payload.length);
        }
    }

    private void handleValveStatus(String topic, String payloadText) {
        if (payloadText == null) {
            LOGGER.log(Level.WARNING, "Empty MQTT actuator payload | topic=" + topic);
            return;
        }

        final boolean pump = PUMP_STATUS_TOPIC.equals(topic);
        final int valveId = pump ? -1 : parseValveId(topic);

        if (!pump && (valveId < MIN_VALVE_ID || valveId > MAX_VALVE_ID)) {
            LOGGER.log(Level.WARNING, "Unsupported actuator topic | topic=" + topic);
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
                        if (pump) {
                            MqttSubscribeService.this.actuatorDataStore.updatePumpState(open, receivedTimestamp);
                            MqttSubscribeService.this.actuatorDetailController.refreshIfOpen();
                            LOGGER.log(Level.INFO, "[MQTT] Pump status applied | state=" + (open ? "ON" : "OFF"));
                            return;
                        }

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