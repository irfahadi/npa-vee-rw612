package com.nxp.example.smartgreenhouse.services.mqtt;

import com.nxp.example.smartgreenhouse.controllers.ActuatorDetailController;
import com.nxp.example.smartgreenhouse.models.actuator.ActuatorDataStore;
import com.nxp.example.smartgreenhouse.models.sensor.SensorData;
import com.nxp.example.smartgreenhouse.services.diagnostics.SystemDiagNative;
import com.nxp.example.smartgreenhouse.services.lora.LoRaNative;

import ej.bon.Util;
import ej.microui.MicroUI;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gateway MQTT kolaborasi Sink Node (FRDM-RW612).
 *
 * Fitur:
 * - Mendukung mode 1-broker (default) dan mode 2-broker (Edge Wi-Fi lokal + Cloud backhaul).
 * - Priority RAM queue (PRIO_CRITICAL=0 > PRIO_HIGH=1 > PRIO_NORMAL=2 > PRIO_LOW=3).
 * - Store-and-Forward FIFO buffer + in-order replay saat koneksi backhaul pulih.
 * - Penjadwalan auto-OFF aktuator berdasarkan dur_s dan publikasi diagnostik sys/diag (1 Hz).
 */
public final class MqttSubscribeService {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: MQTT SUBSCRIBE SERVICE]");

    private static final String CLIENT_ID = "clientku";
    private static final String EDGE_CLIENT_ID = "clientku-edge";
    private static final String TOPIC_FILTER = "gh01/node/255/status/+";
    private static final int SUBSCRIBE_QOS = 0;
    private static final String VALVE_STATUS_TOPIC_PREFIX = "gh01/node/255/status/valve";
    private static final String VALVE_CONTROL_TOPIC_PREFIX = "gh01/node/255/control/valve";
    private static final int CONTROL_QOS = 0;
    private static final boolean CONTROL_RETAINED = false;
    private static final int MIN_VALVE_ID = 1;
    private static final int MAX_VALVE_ID = 2;
    private static final long CONNECTION_CHECK_INTERVAL_MS = 1000L;
    private static final long RECONNECT_INITIAL_DELAY_MS = 2000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30000L;
    private static final long REPLAY_INTERVAL_MS = 15L;
    private static final long DIAG_INTERVAL_MS = 1000L;
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
    // Cloud / Sink Node -> Unit Kontrol
    private static final String ACTUATOR_CONTROL_TOPIC = "gh01/actuator/control";
    // Stress test & Diagnostics
    private static final String STRESS_LOAD_TOPIC = "gh01/stress/load";
    private static final String STRESS_LOAD_OUT_TOPIC = "gh01/stress/load/out";
    private static final String SYS_DIAG_TOPIC = "gh01/sys/diag";

    private static final int COLLABORATION_QOS = 1;
    private static final int TELEMETRY_QOS = 0;
    private static final int TELEMETRY_ENV_MAX_BYTES = 192;
    private static final int MISSION_COMMAND_MAX_BYTES = 192;
    private static final int MISSION_STATUS_MAX_BYTES = 128;
    private static final int MISSION_RESULT_MAX_BYTES = 256;
    private static final int ACTUATOR_CONTROL_MAX_BYTES = 128;
    private static final int STRESS_LOAD_MAX_BYTES = 320;

    // Mixed-criticality priorities (smaller = higher priority)
    private static final int PRIO_CRITICAL = 0;
    private static final int PRIO_HIGH = 1;
    private static final int PRIO_NORMAL = 2;
    private static final int PRIO_LOW = 3;

    // Capacities sized for FRDM-RW612 128 KB Java heap
    private static final int RAM_QUEUE_CAPACITY = 48;
    private static final int MAX_RAM_PUBLISH_BATCH = 8;
    private static final int STORE_FORWARD_CAPACITY = 128;
    private static final int EDGE_OUTBOUND_CAPACITY = 16;
    private static final int PUMP_TIMER_INDEX = 0;

    private final ActuatorDataStore actuatorDataStore;
    private final ActuatorDetailController actuatorDetailController;

    private MqttClient cloudClient;
    private MqttClient edgeClient;
    private String cloudBrokerUri;
    private String edgeBrokerUri;

    private boolean workerStarted;
    private boolean edgeWorkerStarted;
    private volatile boolean serviceRunning;
    private volatile boolean cloudSubscribed;
    private volatile boolean edgeSubscribed;
    private volatile boolean provisioningPaused;

    private final boolean[] pendingValveCommands;
    private final boolean[] pendingValveStates;

    // Priority RAM queue for Cloud-bound / single-broker outbound traffic
    private final String[] ramTopics;
    private final byte[][] ramPayloads;
    private final int[] ramPriorities;
    private final int[] ramQos;
    private final boolean[] ramBufferable;
    private final long[] ramOrders;
    private int ramCount;
    private long nextRamOrder;

    // Store-and-Forward FIFO ring buffer for Cloud outage / backhaul disconnect
    private final String[] sfTopics;
    private final byte[][] sfPayloads;
    private final int[] sfPriorities;
    private final int[] sfQos;
    private int sfHead;
    private int sfCount;

    // Edge-bound outbound FIFO queue (when dual-broker mode is active)
    private final String[] edgeTopics;
    private final byte[][] edgePayloads;
    private final int[] edgeQos;
    private int edgeHead;
    private int edgeCount;

    // Telemetry / diagnostics counters
    private long txSuccessCount;
    private long dropCount;
    private long lastDiagPublishedAt;
    private long lastReplayAt;

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
        this.cloudClient = null;
        this.edgeClient = null;
        this.cloudBrokerUri = null;
        this.edgeBrokerUri = null;
        this.workerStarted = false;
        this.edgeWorkerStarted = false;
        this.serviceRunning = false;
        this.cloudSubscribed = false;
        this.edgeSubscribed = false;
        this.provisioningPaused = false;

        this.pendingValveCommands = new boolean[MAX_VALVE_ID + 1];
        this.pendingValveStates = new boolean[MAX_VALVE_ID + 1];

        this.ramTopics = new String[RAM_QUEUE_CAPACITY];
        this.ramPayloads = new byte[RAM_QUEUE_CAPACITY][];
        this.ramPriorities = new int[RAM_QUEUE_CAPACITY];
        this.ramQos = new int[RAM_QUEUE_CAPACITY];
        this.ramBufferable = new boolean[RAM_QUEUE_CAPACITY];
        this.ramOrders = new long[RAM_QUEUE_CAPACITY];
        this.ramCount = 0;
        this.nextRamOrder = 0L;

        this.sfTopics = new String[STORE_FORWARD_CAPACITY];
        this.sfPayloads = new byte[STORE_FORWARD_CAPACITY][];
        this.sfPriorities = new int[STORE_FORWARD_CAPACITY];
        this.sfQos = new int[STORE_FORWARD_CAPACITY];
        this.sfHead = 0;
        this.sfCount = 0;

        this.edgeTopics = new String[EDGE_OUTBOUND_CAPACITY];
        this.edgePayloads = new byte[EDGE_OUTBOUND_CAPACITY][];
        this.edgeQos = new int[EDGE_OUTBOUND_CAPACITY];
        this.edgeHead = 0;
        this.edgeCount = 0;

        this.txSuccessCount = 0L;
        this.dropCount = 0L;
        this.lastDiagPublishedAt = 0L;
        this.lastReplayAt = 0L;
        this.actuatorOffDeadlines = new long[MAX_VALVE_ID + 1];
    }

    public synchronized void start() {
        if (this.workerStarted) {
            LOGGER.log(Level.INFO, "MQTT connection worker is already running.");
            return;
        }

        this.cloudBrokerUri = BrokerConfig.loadBrokerUri();
        this.edgeBrokerUri = BrokerConfig.loadEdgeBrokerUri(this.cloudBrokerUri);

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
            return;
        }

        if (this.edgeBrokerUri != null && !this.edgeWorkerStarted) {
            startEdgeWorker();
        }
    }

    private synchronized void startEdgeWorker() {
        if (this.edgeWorkerStarted || this.edgeBrokerUri == null) {
            return;
        }

        this.edgeWorkerStarted = true;
        Thread edgeWorker = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        MqttSubscribeService.this.runEdgeConnectionLoop();
                    }
                },
                "mqtt-edge-worker"
        );

        try {
            edgeWorker.start();
        } catch (Error error) {
            this.edgeWorkerStarted = false;
            LOGGER.log(Level.SEVERE, "Unable to start MQTT edge worker | error=" + error);
        }
    }

    public void pauseForWifiProvisioning() {
        synchronized (this) {
            this.provisioningPaused = true;
            this.cloudSubscribed = false;
            this.edgeSubscribed = false;
            notifyAll();
        }

        failAllPendingValveCommands();
        LOGGER.log(Level.INFO, "MQTT pause requested for WiFi provisioning.");
    }

    public void resumeAfterWifiProvisioning() {
        boolean startWorker;
        boolean needEdgeWorker;

        synchronized (this) {
            this.provisioningPaused = false;
            this.cloudBrokerUri = BrokerConfig.loadBrokerUri();
            this.edgeBrokerUri = BrokerConfig.loadEdgeBrokerUri(this.cloudBrokerUri);
            startWorker = !this.workerStarted;
            needEdgeWorker = !startWorker && this.edgeBrokerUri != null && !this.edgeWorkerStarted;
            notifyAll();
        }

        if (startWorker) {
            LOGGER.log(Level.INFO, "MQTT starting after WiFi connection.");
            start();
            return;
        }

        if (needEdgeWorker) {
            startEdgeWorker();
        }

        LOGGER.log(Level.INFO, "MQTT resumed after WiFi connection.");
    }

    private void runConnectionLoop() {
        long reconnectDelay = RECONNECT_INITIAL_DELAY_MS;

        while (this.serviceRunning) {
            try {
                if (this.provisioningPaused) {
                    disconnectCloudClientForProvisioning();
                    waitWhileProvisioningPaused();
                    continue;
                }

                if (!this.serviceRunning) {
                    break;
                }

                ensureCloudClientCreated();

                MqttClient client = this.cloudClient;

                if (client == null) {
                    throw new IllegalStateException("MQTT client was not created.");
                }

                if (this.provisioningPaused) {
                    continue;
                }

                if (!client.isConnected()) {
                    this.cloudSubscribed = false;
                }

                if (!client.isConnected() || !this.cloudSubscribed) {
                    connectAndSubscribeCloudOnce();

                    if (this.provisioningPaused) {
                        continue;
                    }

                    reconnectDelay = RECONNECT_INITIAL_DELAY_MS;
                }

                while (this.serviceRunning && !this.provisioningPaused && client.isConnected() && this.cloudSubscribed) {
                    processPendingControlCommands();
                    processActuatorOffDeadlines();
                    processRamQueue();
                    processStoreAndForwardReplay();
                    publishDiagnosticsIfDue();

                    synchronized (this) {
                        boolean workPending = this.pendingValveCommands[1]
                                || this.pendingValveCommands[2]
                                || this.ramCount > 0
                                || (this.sfCount > 0 && Util.platformTimeMillis() - this.lastReplayAt >= REPLAY_INTERVAL_MS);

                        if (this.serviceRunning && !this.provisioningPaused && client.isConnected() && this.cloudSubscribed && !workPending) {
                            wait(nextWaitMilliseconds());
                        }
                    }
                }
            } catch (InterruptedException exception) {
                LOGGER.log(Level.WARNING, "MQTT worker was interrupted | error=" + exception);
                break;
            } catch (MqttException exception) {
                LOGGER.log(Level.WARNING, "MQTT operation failed | reasonCode=" + exception.getReasonCode() + " | message=" + exception.getMessage() + " | cause=" + exception.getCause());
                this.cloudSubscribed = false;
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Unexpected MQTT worker error | error=" + exception);
            }

            if (!this.serviceRunning) {
                break;
            }

            if (this.provisioningPaused) {
                continue;
            }

            // While disconnected, drain RAM queue into Store-and-Forward buffer so no messages are lost
            drainRamQueueToStoreAndForwardOffline();

            LOGGER.log(Level.INFO, "MQTT reconnect scheduled | delayMs=" + reconnectDelay + " | sfBuffered=" + getSfCount());

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
            this.cloudSubscribed = false;
        }

        LOGGER.log(Level.INFO, "MQTT connection worker stopped.");
    }

    private void runEdgeConnectionLoop() {
        long reconnectDelay = RECONNECT_INITIAL_DELAY_MS;

        while (this.serviceRunning) {
            try {
                if (this.provisioningPaused || this.edgeBrokerUri == null) {
                    disconnectEdgeClient();
                    waitWhileProvisioningOrNoEdge();
                    continue;
                }

                ensureEdgeClientCreated();
                MqttClient client = this.edgeClient;

                if (client == null) {
                    continue;
                }

                if (!client.isConnected()) {
                    this.edgeSubscribed = false;
                }

                if (!client.isConnected() || !this.edgeSubscribed) {
                    connectAndSubscribeEdgeOnce();

                    if (this.provisioningPaused) {
                        continue;
                    }

                    reconnectDelay = RECONNECT_INITIAL_DELAY_MS;
                }

                while (this.serviceRunning && !this.provisioningPaused && this.edgeBrokerUri != null && client.isConnected() && this.edgeSubscribed) {
                    processEdgeOutboundQueue();

                    synchronized (this) {
                        if (this.serviceRunning && !this.provisioningPaused && this.edgeBrokerUri != null
                                && client.isConnected() && this.edgeSubscribed && this.edgeCount == 0) {
                            wait(CONNECTION_CHECK_INTERVAL_MS);
                        }
                    }
                }
            } catch (InterruptedException exception) {
                LOGGER.log(Level.WARNING, "MQTT edge worker interrupted | error=" + exception);
                break;
            } catch (MqttException exception) {
                LOGGER.log(Level.WARNING, "MQTT edge operation failed | reasonCode=" + exception.getReasonCode() + " | message=" + exception.getMessage());
                this.edgeSubscribed = false;
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Unexpected MQTT edge worker error | error=" + exception);
            }

            if (!this.serviceRunning) {
                break;
            }

            if (this.provisioningPaused || this.edgeBrokerUri == null) {
                continue;
            }

            try {
                waitForReconnectDelay(reconnectDelay);
            } catch (InterruptedException exception) {
                break;
            }

            reconnectDelay = reconnectDelay * 2L;

            if (reconnectDelay > RECONNECT_MAX_DELAY_MS) {
                reconnectDelay = RECONNECT_MAX_DELAY_MS;
            }
        }

        synchronized (this) {
            this.edgeWorkerStarted = false;
            this.edgeSubscribed = false;
        }
    }

    private void disconnectCloudClientForProvisioning() {
        MqttClient client;

        synchronized (this) {
            this.cloudSubscribed = false;
            client = this.cloudClient;
        }

        if (client == null || !client.isConnected()) {
            discardCloudClientForBrokerReload();
            return;
        }

        try {
            client.disconnect();
            LOGGER.log(Level.INFO, "MQTT cloud disconnected for WiFi provisioning.");
        } catch (MqttException exception) {
            LOGGER.log(Level.WARNING, "MQTT cloud disconnect during WiFi provisioning failed | reasonCode=" + exception.getReasonCode());
        } finally {
            discardCloudClientForBrokerReload();
        }
    }

    private void disconnectEdgeClient() {
        MqttClient client;

        synchronized (this) {
            this.edgeSubscribed = false;
            client = this.edgeClient;
            this.edgeClient = null;
        }

        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException ignored) {
            }
        }
    }

    private synchronized void discardCloudClientForBrokerReload() {
        this.cloudClient = null;
        this.cloudSubscribed = false;
    }

    private void waitWhileProvisioningPaused() throws InterruptedException {
        synchronized (this) {
            while (this.serviceRunning && this.provisioningPaused) {
                wait();
            }
        }
    }

    private void waitWhileProvisioningOrNoEdge() throws InterruptedException {
        synchronized (this) {
            while (this.serviceRunning && (this.provisioningPaused || this.edgeBrokerUri == null)) {
                wait();
            }
        }
    }

    private void waitForReconnectDelay(long delayMilliseconds) throws InterruptedException {
        long deadline = Util.platformTimeMillis() + delayMilliseconds;

        synchronized (this) {
            while (this.serviceRunning && !this.provisioningPaused) {
                drainRamQueueToStoreAndForwardLocked();
                processActuatorOffDeadlinesLocked();

                long remaining = deadline - Util.platformTimeMillis();

                if (remaining <= 0L) {
                    return;
                }

                long step = remaining > 500L ? 500L : remaining;
                wait(step);
            }
        }
    }

    private synchronized void ensureCloudClientCreated() throws MqttException {
        if (this.cloudClient != null) {
            return;
        }

        this.cloudBrokerUri = BrokerConfig.loadBrokerUri();
        this.edgeBrokerUri = BrokerConfig.loadEdgeBrokerUri(this.cloudBrokerUri);
        this.cloudClient = new MqttClient(this.cloudBrokerUri, CLIENT_ID);
        this.cloudClient.setCallback(createCallback(false));
    }

    private synchronized void ensureEdgeClientCreated() throws MqttException {
        if (this.edgeClient != null || this.edgeBrokerUri == null) {
            return;
        }

        this.edgeClient = new MqttClient(this.edgeBrokerUri, EDGE_CLIENT_ID);
        this.edgeClient.setCallback(createCallback(true));
    }

    private MqttCallback createCallback(final boolean isEdgeClient) {
        return new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                if (isEdgeClient) {
                    MqttSubscribeService.this.edgeSubscribed = false;
                } else {
                    MqttSubscribeService.this.cloudSubscribed = false;
                    MqttSubscribeService.this.failAllPendingValveCommands();
                }

                synchronized (MqttSubscribeService.this) {
                    MqttSubscribeService.this.notifyAll();
                }

                String tag = isEdgeClient ? "MQTT edge" : "MQTT cloud";

                if (cause instanceof MqttException) {
                    MqttException mqttException = (MqttException) cause;
                    LOGGER.log(
                            Level.WARNING,
                            tag + " connection lost"
                                    + " | reasonCode=" + mqttException.getReasonCode()
                                    + " | message=" + mqttException.getMessage()
                    );
                } else {
                    LOGGER.log(Level.WARNING, tag + " connection lost | cause=" + cause);
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                byte[] payload = message.getPayload();
                String payloadText = payload == null ? "" : new String(payload);

                try {
                    dispatchMessage(topic, payloadText, isEdgeClient);
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.WARNING, "MQTT message handling failed | topic=" + topic + " | error=" + exception);
                }
            }
        };
    }

    private void connectAndSubscribeCloudOnce() throws MqttException {
        MqttClient client = this.cloudClient;

        if (client == null) {
            throw new IllegalStateException("MQTT cloud client is null.");
        }

        if (this.provisioningPaused) {
            return;
        }

        if (!client.isConnected()) {
            LOGGER.log(Level.INFO, "Connecting to Cloud MQTT broker | URI=" + this.cloudBrokerUri + " | clientId=" + CLIENT_ID);
            client.connect(buildConnectOptions());

            if (this.provisioningPaused) {
                this.cloudSubscribed = false;

                if (client.isConnected()) {
                    client.disconnect();
                }

                return;
            }

            LOGGER.log(Level.INFO, "Cloud MQTT broker connected | URI=" + this.cloudBrokerUri);
        }

        if (!this.cloudSubscribed && !this.provisioningPaused) {
            client.subscribe(TOPIC_FILTER, SUBSCRIBE_QOS);
            client.subscribe(MISSION_COMMAND_TOPIC, COLLABORATION_QOS);
            client.subscribe(ACTUATOR_CONTROL_TOPIC, COLLABORATION_QOS);

            if (this.edgeBrokerUri == null) {
                // Single-broker mode: Cloud client also receives local agent & stress topics
                client.subscribe(AGENT_MISSION_STATUS_TOPIC, COLLABORATION_QOS);
                client.subscribe(AGENT_MISSION_RESULT_TOPIC, COLLABORATION_QOS);
                client.subscribe(STRESS_LOAD_TOPIC, TELEMETRY_QOS);
            }

            this.cloudSubscribed = true;
            LOGGER.log(Level.INFO, "Cloud MQTT subscriptions active | sfPending=" + getSfCount());
        }
    }

    private void connectAndSubscribeEdgeOnce() throws MqttException {
        MqttClient client = this.edgeClient;

        if (client == null || this.provisioningPaused) {
            return;
        }

        if (!client.isConnected()) {
            LOGGER.log(Level.INFO, "Connecting to Edge MQTT broker | URI=" + this.edgeBrokerUri + " | clientId=" + EDGE_CLIENT_ID);
            client.connect(buildConnectOptions());

            if (this.provisioningPaused) {
                this.edgeSubscribed = false;

                if (client.isConnected()) {
                    client.disconnect();
                }

                return;
            }

            LOGGER.log(Level.INFO, "Edge MQTT broker connected | URI=" + this.edgeBrokerUri);
        }

        if (!this.edgeSubscribed && !this.provisioningPaused) {
            client.subscribe(TOPIC_FILTER, SUBSCRIBE_QOS);
            client.subscribe(AGENT_MISSION_STATUS_TOPIC, COLLABORATION_QOS);
            client.subscribe(AGENT_MISSION_RESULT_TOPIC, COLLABORATION_QOS);
            client.subscribe(STRESS_LOAD_TOPIC, TELEMETRY_QOS);

            this.edgeSubscribed = true;
            LOGGER.log(Level.INFO, "Edge MQTT subscriptions active.");
        }
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setConnectionTimeout(10);
        connectOptions.setKeepAliveInterval(60);

        if (AUTHENTICATION_ENABLED) {
            connectOptions.setUserName(USERNAME);
            connectOptions.setPassword(PASSWORD.toCharArray());
        }

        return connectOptions;
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

        if (this.edgeBrokerUri != null && isEdgeReady()) {
            this.pendingValveStates[valveId] = targetOpen;
            this.pendingValveCommands[valveId] = true;
            this.actuatorOffDeadlines[valveId] = 0L;
            notifyAll();
            return true;
        }

        if (this.cloudClient == null || !this.cloudClient.isConnected()) {
            LOGGER.log(
                    Level.WARNING,
                    "Valve control rejected | MQTT is not connected | valveId=" + valveId
            );
            return false;
        }

        this.pendingValveStates[valveId] = targetOpen;
        this.pendingValveCommands[valveId] = true;
        this.actuatorOffDeadlines[valveId] = 0L;
        notifyAll();

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

        MqttClient targetClient = (this.edgeBrokerUri != null && isEdgeReady()) ? this.edgeClient : this.cloudClient;

        if (targetClient == null || !targetClient.isConnected()) {
            boolean removed = clearPendingValveCommand(valveId, targetOpen);

            if (removed) {
                notifyValveControlPublishFailed(valveId, targetOpen);
            }

            return;
        }

        String topic = VALVE_CONTROL_TOPIC_PREFIX + valveId;
        String payloadText = targetOpen ? "1" : "0";

        try {
            targetClient.publish(topic, payloadText.getBytes(), CONTROL_QOS, CONTROL_RETAINED);
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

    private void dispatchMessage(String topic, String payloadText, boolean fromEdgeClient) {
        if (topic == null) {
            return;
        }

        if (topic.startsWith(ACTUATOR_STATUS_TOPIC_PREFIX)) {
            handleValveStatus(topic, payloadText);
        } else if (MISSION_COMMAND_TOPIC.equals(topic)) {
            relayCommandToAgent(topic, payloadText);
        } else if (AGENT_MISSION_STATUS_TOPIC.equals(topic)) {
            relayMissionToCloud(topic, MISSION_STATUS_TOPIC, payloadText, MISSION_STATUS_MAX_BYTES, "st", PRIO_HIGH, fromEdgeClient);
        } else if (AGENT_MISSION_RESULT_TOPIC.equals(topic)) {
            relayMissionToCloud(topic, MISSION_RESULT_TOPIC, payloadText, MISSION_RESULT_MAX_BYTES, "tray_id", PRIO_HIGH, fromEdgeClient);
        } else if (ACTUATOR_CONTROL_TOPIC.equals(topic)) {
            handleActuatorControl(payloadText);
        } else if (STRESS_LOAD_TOPIC.equals(topic)) {
            handleStressLoad(payloadText);
        } else {
            LOGGER.log(Level.WARNING, "Unsupported MQTT topic | topic=" + topic);
        }
    }

    /**
     * Publishes one LoRa sensor sample to the cloud on telemetry/env.
     * Safe to call from any thread; buffered into Store-and-Forward when backhaul is offline.
     */
    public boolean publishTelemetry(SensorData data, int sequence, int rssi, long timestampMillis) {
        if (data == null) {
            return false;
        }

        long tsMillis = timestampMillis > 0L ? timestampMillis : Util.currentTimeMillis();
        StringBuilder json = new StringBuilder(TELEMETRY_ENV_MAX_BYTES);

        json.append("{\"h_id\":\"").append(HOUSE_ID).append("\",\"n_id\":").append(data.getNodeId());
        json.append(",\"seq\":").append(sequence);
        json.append(",\"ts\":").append(tsMillis);
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
            incrementDropCount();
            return false;
        }

        return enqueueRamQueue(TELEMETRY_ENV_TOPIC, payload, PRIO_NORMAL, TELEMETRY_QOS, true);
    }

    private static void appendTenths(StringBuilder builder, float value) {
        int scaled = Math.round(value * 10.0f);

        if (scaled < 0) {
            builder.append('-');
            scaled = -scaled;
        }

        builder.append(scaled / 10).append('.').append(scaled % 10);
    }

    private void relayCommandToAgent(String sourceTopic, String payloadText) {
        byte[] payload = payloadText == null ? new byte[0] : payloadText.getBytes();

        if (payload.length == 0 || payload.length > MISSION_COMMAND_MAX_BYTES) {
            LOGGER.log(Level.WARNING, "Command dropped | invalid size | topic=" + sourceTopic + " | bytes=" + payload.length);
            incrementDropCount();
            return;
        }

        String missionId = JsonLite.getString(payloadText, "m_id");

        if (missionId == null || !JsonLite.hasKey(payloadText, "cmd")) {
            LOGGER.log(Level.WARNING, "Command dropped | missing m_id or cmd | topic=" + sourceTopic);
            incrementDropCount();
            return;
        }

        if (this.edgeBrokerUri != null) {
            enqueueEdgeOutbound(AGENT_MISSION_COMMAND_TOPIC, payload, COLLABORATION_QOS);
        } else {
            enqueueRamQueue(AGENT_MISSION_COMMAND_TOPIC, payload, PRIO_CRITICAL, COLLABORATION_QOS, false);
        }

        LOGGER.log(Level.INFO, "[MQTT] Command relayed | m_id=" + missionId + " | to=" + AGENT_MISSION_COMMAND_TOPIC);
    }

    /** Forwards a mission message from Collaboration Agent toward Cloud (with Store-and-Forward support). */
    private void relayMissionToCloud(String sourceTopic, String targetTopic, String payloadText, int maxBytes, String requiredKey, int priority, boolean fromEdgeClient) {
        byte[] payload = payloadText == null ? new byte[0] : payloadText.getBytes();

        if (payload.length == 0 || payload.length > maxBytes) {
            LOGGER.log(Level.WARNING, "Mission message dropped | invalid size | topic=" + sourceTopic + " | bytes=" + payload.length + " | max=" + maxBytes);
            incrementDropCount();
            return;
        }

        String missionId = JsonLite.getString(payloadText, "m_id");

        if (missionId == null || !JsonLite.hasKey(payloadText, requiredKey)) {
            LOGGER.log(Level.WARNING, "Mission message dropped | missing m_id or " + requiredKey + " | topic=" + sourceTopic);
            incrementDropCount();
            return;
        }

        boolean queued = enqueueRamQueue(targetTopic, payload, priority, COLLABORATION_QOS, true);

        if (queued) {
            if (fromEdgeClient && !isCloudReady()) {
                LOGGER.log(Level.INFO, "[S&F] Buffered offline | m_id=" + missionId + " | topic=" + targetTopic);
            } else {
                LOGGER.log(Level.INFO, "[MQTT] Mission relayed | m_id=" + missionId + " | from=" + sourceTopic + " | to=" + targetTopic);
            }
        }
    }

    private void handleStressLoad(String payloadText) {
        byte[] payload = payloadText == null ? new byte[0] : payloadText.getBytes();

        if (payload.length == 0 || payload.length > STRESS_LOAD_MAX_BYTES) {
            incrementDropCount();
            return;
        }

        int crit = JsonLite.getInt(payloadText, "c", 1);
        int priority;

        if (crit >= 3) {
            priority = PRIO_HIGH;
        } else if (crit == 2) {
            priority = PRIO_NORMAL;
        } else {
            priority = PRIO_LOW;
        }

        String targetTopic = (this.edgeBrokerUri != null) ? STRESS_LOAD_TOPIC : STRESS_LOAD_OUT_TOPIC;
        enqueueRamQueue(targetTopic, payload, priority, TELEMETRY_QOS, false);
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
        byte[] payload = (targetOn ? "1" : "0").getBytes();

        if (this.edgeBrokerUri != null && isEdgeReady()) {
            return enqueueEdgeOutbound(topic, payload, CONTROL_QOS);
        }

        return enqueueRamQueue(topic, payload, PRIO_CRITICAL, CONTROL_QOS, true);
    }

    /** Switches OFF every actuator whose "dur_s" window has elapsed. */
    private void processActuatorOffDeadlines() {
        boolean[] expiredFlags = new boolean[this.actuatorOffDeadlines.length];

        synchronized (this) {
            collectExpiredActuatorDeadlinesLocked(expiredFlags);
        }

        for (int index = 0; index < expiredFlags.length; index++) {
            if (expiredFlags[index]) {
                LOGGER.log(Level.INFO, "[MQTT] Actuator duration elapsed | switching OFF | index=" + index);
                enqueueActuatorCommand(index, false);
            }
        }
    }

    private void processActuatorOffDeadlinesLocked() {
        long now = Util.platformTimeMillis();

        for (int index = 0; index < this.actuatorOffDeadlines.length; index++) {
            long deadline = this.actuatorOffDeadlines[index];

            if (deadline != 0L && now >= deadline) {
                this.actuatorOffDeadlines[index] = 0L;
                String topic = index == PUMP_TIMER_INDEX ? PUMP_CONTROL_TOPIC : VALVE_CONTROL_TOPIC_PREFIX + index;
                byte[] payload = "0".getBytes();

                if (this.edgeBrokerUri != null && isEdgeReadyLocked()) {
                    enqueueEdgeOutboundLocked(topic, payload, CONTROL_QOS);
                } else {
                    enqueueRamQueueLocked(topic, payload, PRIO_CRITICAL, CONTROL_QOS, true);
                }
            }
        }
    }

    private void collectExpiredActuatorDeadlinesLocked(boolean[] expiredFlags) {
        long now = Util.platformTimeMillis();

        for (int index = 0; index < this.actuatorOffDeadlines.length; index++) {
            long deadline = this.actuatorOffDeadlines[index];

            if (deadline != 0L && now >= deadline) {
                this.actuatorOffDeadlines[index] = 0L;
                expiredFlags[index] = true;
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

        if (this.sfCount > 0) {
            long replayWait = REPLAY_INTERVAL_MS - (now - this.lastReplayAt);

            if (replayWait < waitMilliseconds) {
                waitMilliseconds = replayWait;
            }
        }

        long diagWait = DIAG_INTERVAL_MS - (now - this.lastDiagPublishedAt);

        if (diagWait < waitMilliseconds) {
            waitMilliseconds = diagWait;
        }

        return waitMilliseconds < 1L ? 1L : waitMilliseconds;
    }

    private synchronized boolean isCloudReady() {
        return !this.provisioningPaused && this.cloudSubscribed && this.cloudClient != null && this.cloudClient.isConnected();
    }

    private synchronized boolean isEdgeReady() {
        return isEdgeReadyLocked();
    }

    private boolean isEdgeReadyLocked() {
        return !this.provisioningPaused && this.edgeSubscribed && this.edgeClient != null && this.edgeClient.isConnected();
    }

    private synchronized void incrementDropCount() {
        this.dropCount++;
    }

    private synchronized int getSfCount() {
        return this.sfCount;
    }

    private synchronized boolean enqueueRamQueue(String topic, byte[] payload, int priority, int qos, boolean bufferable) {
        boolean queued = enqueueRamQueueLocked(topic, payload, priority, qos, bufferable);
        notifyAll();
        return queued;
    }

    private boolean enqueueRamQueueLocked(String topic, byte[] payload, int priority, int qos, boolean bufferable) {
        if (this.ramCount < RAM_QUEUE_CAPACITY) {
            int idx = this.ramCount;
            this.ramTopics[idx] = topic;
            this.ramPayloads[idx] = payload;
            this.ramPriorities[idx] = priority;
            this.ramQos[idx] = qos;
            this.ramBufferable[idx] = bufferable;
            this.ramOrders[idx] = ++this.nextRamOrder;
            this.ramCount++;
            return true;
        }

        // Priority-aware eviction: evict lowest priority (highest priority int, oldest order)
        int worstIndex = -1;
        int worstPriority = priority;
        long oldestWorstOrder = Long.MAX_VALUE;

        for (int index = 0; index < this.ramCount; index++) {
            int p = this.ramPriorities[index];

            if (p > worstPriority || (p == worstPriority && p > priority && this.ramOrders[index] < oldestWorstOrder)) {
                worstPriority = p;
                oldestWorstOrder = this.ramOrders[index];
                worstIndex = index;
            }
        }

        if (worstIndex < 0) {
            this.dropCount++;
            LOGGER.log(Level.WARNING, "RAM queue full | dropped incoming topic=" + topic + " | prio=" + priority);
            return false;
        }

        this.dropCount++;
        LOGGER.log(Level.WARNING, "RAM queue full | evicted prio=" + this.ramPriorities[worstIndex] + " for topic=" + topic);
        this.ramTopics[worstIndex] = topic;
        this.ramPayloads[worstIndex] = payload;
        this.ramPriorities[worstIndex] = priority;
        this.ramQos[worstIndex] = qos;
        this.ramBufferable[worstIndex] = bufferable;
        this.ramOrders[worstIndex] = ++this.nextRamOrder;
        return true;
    }

    private int dequeueBestRamIndexLocked() {
        if (this.ramCount == 0) {
            return -1;
        }

        int bestIndex = 0;

        for (int index = 1; index < this.ramCount; index++) {
            int p = this.ramPriorities[index];
            int bestP = this.ramPriorities[bestIndex];

            if (p < bestP || (p == bestP && this.ramOrders[index] < this.ramOrders[bestIndex])) {
                bestIndex = index;
            }
        }

        return bestIndex;
    }

    private void removeRamIndexLocked(int index) {
        int last = this.ramCount - 1;

        if (index != last) {
            this.ramTopics[index] = this.ramTopics[last];
            this.ramPayloads[index] = this.ramPayloads[last];
            this.ramPriorities[index] = this.ramPriorities[last];
            this.ramQos[index] = this.ramQos[last];
            this.ramBufferable[index] = this.ramBufferable[last];
            this.ramOrders[index] = this.ramOrders[last];
        }

        this.ramTopics[last] = null;
        this.ramPayloads[last] = null;
        this.ramCount = last;
    }

    private void drainRamQueueToStoreAndForwardOffline() {
        synchronized (this) {
            drainRamQueueToStoreAndForwardLocked();
        }
    }

    private void drainRamQueueToStoreAndForwardLocked() {
        while (this.ramCount > 0) {
            int best = dequeueBestRamIndexLocked();
            String topic = this.ramTopics[best];
            byte[] payload = this.ramPayloads[best];
            int priority = this.ramPriorities[best];
            int qos = this.ramQos[best];
            boolean bufferable = this.ramBufferable[best];
            removeRamIndexLocked(best);

            if (bufferable) {
                appendStoreAndForwardLocked(topic, payload, priority, qos);
            } else {
                this.dropCount++;
            }
        }
    }

    private boolean appendStoreAndForwardLocked(String topic, byte[] payload, int priority, int qos) {
        if (this.sfCount == STORE_FORWARD_CAPACITY) {
            // Prefer evicting the oldest telemetry (lowest criticality) before dropping mission data
            int evictPos = findOldestLowPrioritySfOffsetLocked(priority);

            if (evictPos < 0) {
                this.dropCount++;
                LOGGER.log(Level.WARNING, "S&F buffer full | dropped topic=" + topic);
                return false;
            }

            removeSfOffsetLocked(evictPos);
            this.dropCount++;
        }

        int tail = (this.sfHead + this.sfCount) % STORE_FORWARD_CAPACITY;
        this.sfTopics[tail] = topic;
        this.sfPayloads[tail] = payload;
        this.sfPriorities[tail] = priority;
        this.sfQos[tail] = qos;
        this.sfCount++;
        return true;
    }

    private void prependStoreAndForwardLocked(String topic, byte[] payload, int priority, int qos) {
        if (this.sfCount == STORE_FORWARD_CAPACITY) {
            int evictPos = findOldestLowPrioritySfOffsetLocked(priority);

            if (evictPos >= 0) {
                removeSfOffsetLocked(evictPos);
                this.dropCount++;
            } else {
                this.dropCount++;
                return;
            }
        }

        this.sfHead = (this.sfHead - 1 + STORE_FORWARD_CAPACITY) % STORE_FORWARD_CAPACITY;
        this.sfTopics[this.sfHead] = topic;
        this.sfPayloads[this.sfHead] = payload;
        this.sfPriorities[this.sfHead] = priority;
        this.sfQos[this.sfHead] = qos;
        this.sfCount++;
    }

    private int findOldestLowPrioritySfOffsetLocked(int incomingPriority) {
        int worstOffset = -1;
        int worstPriority = incomingPriority;

        for (int offset = 0; offset < this.sfCount; offset++) {
            int idx = (this.sfHead + offset) % STORE_FORWARD_CAPACITY;
            int p = this.sfPriorities[idx];

            if (p > worstPriority) {
                worstPriority = p;
                worstOffset = offset;
            }
        }

        if (worstOffset >= 0) {
            return worstOffset;
        }

        return this.sfCount > 0 ? 0 : -1;
    }

    private void removeSfOffsetLocked(int offset) {
        for (int pos = offset; pos < this.sfCount - 1; pos++) {
            int dst = (this.sfHead + pos) % STORE_FORWARD_CAPACITY;
            int src = (this.sfHead + pos + 1) % STORE_FORWARD_CAPACITY;
            this.sfTopics[dst] = this.sfTopics[src];
            this.sfPayloads[dst] = this.sfPayloads[src];
            this.sfPriorities[dst] = this.sfPriorities[src];
            this.sfQos[dst] = this.sfQos[src];
        }

        int last = (this.sfHead + this.sfCount - 1) % STORE_FORWARD_CAPACITY;
        this.sfTopics[last] = null;
        this.sfPayloads[last] = null;
        this.sfCount--;
    }

    private void processRamQueue() throws MqttException {
        int processed = 0;

        while (!this.provisioningPaused && processed < MAX_RAM_PUBLISH_BATCH) {
            final String topic;
            final byte[] payload;
            final int priority;
            final int qos;
            final boolean bufferable;

            synchronized (this) {
                if (this.ramCount == 0) {
                    return;
                }

                int best = dequeueBestRamIndexLocked();
                topic = this.ramTopics[best];
                payload = this.ramPayloads[best];
                priority = this.ramPriorities[best];
                qos = this.ramQos[best];
                bufferable = this.ramBufferable[best];
                removeRamIndexLocked(best);

                if (!isCloudReady() || (bufferable && this.sfCount > 0)) {
                    if (bufferable) {
                        appendStoreAndForwardLocked(topic, payload, priority, qos);
                    } else {
                        this.dropCount++;
                    }
                    processed++;
                    continue;
                }
            }

            MqttClient client = this.cloudClient;

            if (client == null || !client.isConnected()) {
                if (bufferable) {
                    synchronized (this) {
                        appendStoreAndForwardLocked(topic, payload, priority, qos);
                    }
                }
                return;
            }

            try {
                client.publish(topic, payload, qos, false);
                synchronized (this) {
                    this.txSuccessCount++;
                }
            } catch (MqttException exception) {
                if (bufferable) {
                    synchronized (this) {
                        appendStoreAndForwardLocked(topic, payload, priority, qos);
                    }
                }
                throw exception;
            }

            processed++;
        }
    }

    private void processStoreAndForwardReplay() throws MqttException {
        if (this.provisioningPaused) {
            return;
        }

        long now = Util.platformTimeMillis();
        final String topic;
        final byte[] payload;
        final int priority;
        final int qos;

        synchronized (this) {
            if (this.sfCount == 0 || !isCloudReady()) {
                return;
            }

            if (now - this.lastReplayAt < REPLAY_INTERVAL_MS) {
                return;
            }

            topic = this.sfTopics[this.sfHead];
            payload = this.sfPayloads[this.sfHead];
            priority = this.sfPriorities[this.sfHead];
            qos = this.sfQos[this.sfHead];
            this.sfTopics[this.sfHead] = null;
            this.sfPayloads[this.sfHead] = null;
            this.sfHead = (this.sfHead + 1) % STORE_FORWARD_CAPACITY;
            this.sfCount--;
            this.lastReplayAt = now;
        }

        MqttClient client = this.cloudClient;

        if (client == null || !client.isConnected()) {
            synchronized (this) {
                prependStoreAndForwardLocked(topic, payload, priority, qos);
            }
            return;
        }

        try {
            client.publish(topic, payload, qos, false);

            synchronized (this) {
                this.txSuccessCount++;
            }

            LOGGER.log(Level.INFO, "[S&F] Replayed | topic=" + topic + " | remaining=" + getSfCount());
        } catch (MqttException exception) {
            synchronized (this) {
                prependStoreAndForwardLocked(topic, payload, priority, qos);
            }
            throw exception;
        }
    }

    private void publishDiagnosticsIfDue() throws MqttException {
        long now = Util.platformTimeMillis();

        final int ramQ;
        final int sfQ;
        final long drops;
        final long txOk;

        synchronized (this) {
            if (!isCloudReady() || this.sfCount > 0) {
                return;
            }

            if (now - this.lastDiagPublishedAt < DIAG_INTERVAL_MS) {
                return;
            }

            this.lastDiagPublishedAt = now;
            ramQ = this.ramCount;
            sfQ = this.sfCount;
            drops = this.dropCount;
            txOk = this.txSuccessCount;
        }

        long javaHeapFree = Runtime.getRuntime().freeMemory();
        int nativeHeapFree = SystemDiagNative.getFreeNativeHeapBytes();
        int nativeHeapMin = SystemDiagNative.getMinFreeNativeHeapBytes();
        int cpuPct = SystemDiagNative.getCpuLoadPercent();
        int loraDrops = LoRaNative.getDroppedCount();
        long ts = Util.currentTimeMillis();

        StringBuilder json = new StringBuilder(192);
        json.append("{\"h_id\":\"").append(HOUSE_ID).append('\"');
        json.append(",\"ram_q\":").append(ramQ);
        json.append(",\"sf_q\":").append(sfQ);
        json.append(",\"drops\":").append(drops);
        json.append(",\"lora_drops\":").append(loraDrops);
        json.append(",\"tx_ok\":").append(txOk);
        json.append(",\"heap_free\":").append(javaHeapFree);
        json.append(",\"native_free\":").append(nativeHeapFree);
        json.append(",\"native_min\":").append(nativeHeapMin);
        json.append(",\"cpu_pct\":").append(cpuPct);
        json.append(",\"up\":1");
        json.append(",\"ts\":").append(ts);
        json.append('}');

        MqttClient client = this.cloudClient;

        if (client != null && client.isConnected()) {
            client.publish(SYS_DIAG_TOPIC, json.toString().getBytes(), TELEMETRY_QOS, false);
        }
    }

    private synchronized boolean enqueueEdgeOutbound(String topic, byte[] payload, int qos) {
        boolean queued = enqueueEdgeOutboundLocked(topic, payload, qos);
        notifyAll();
        return queued;
    }

    private boolean enqueueEdgeOutboundLocked(String topic, byte[] payload, int qos) {
        if (this.edgeCount == EDGE_OUTBOUND_CAPACITY) {
            this.edgeHead = (this.edgeHead + 1) % EDGE_OUTBOUND_CAPACITY;
            this.edgeCount--;
            this.dropCount++;
        }

        int tail = (this.edgeHead + this.edgeCount) % EDGE_OUTBOUND_CAPACITY;
        this.edgeTopics[tail] = topic;
        this.edgePayloads[tail] = payload;
        this.edgeQos[tail] = qos;
        this.edgeCount++;
        return true;
    }

    private void processEdgeOutboundQueue() throws MqttException {
        while (!this.provisioningPaused && this.edgeBrokerUri != null) {
            final String topic;
            final byte[] payload;
            final int qos;

            synchronized (this) {
                if (this.edgeCount == 0) {
                    return;
                }

                topic = this.edgeTopics[this.edgeHead];
                payload = this.edgePayloads[this.edgeHead];
                qos = this.edgeQos[this.edgeHead];
                this.edgeTopics[this.edgeHead] = null;
                this.edgePayloads[this.edgeHead] = null;
                this.edgeHead = (this.edgeHead + 1) % EDGE_OUTBOUND_CAPACITY;
                this.edgeCount--;
            }

            MqttClient client = this.edgeClient;

            if (client == null || !client.isConnected()) {
                return;
            }

            client.publish(topic, payload, qos, false);
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
        return this.cloudClient != null && this.cloudClient.isConnected();
    }
}
