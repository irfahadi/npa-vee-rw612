package com.nxp.example.smartgreenhouse.services.lora;

import com.nxp.example.smartgreenhouse.controllers.OverviewController;
import com.nxp.example.smartgreenhouse.controllers.SensorDetailController;
import com.nxp.example.smartgreenhouse.models.sensor.SensorData;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDataStore;
import com.nxp.example.smartgreenhouse.models.sensor.SensorHistoryEntry;
import com.nxp.example.smartgreenhouse.models.sensor.SensorHistoryStore;
import com.nxp.example.smartgreenhouse.utils.Time;

import ej.microui.MicroUI;

import ej.bon.Util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoRaHardwareService {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: LORA HARDWARE SERVICE]");

    private final SensorDataStore sensorDataStore;
    private final SensorHistoryStore sensorHistoryStore;
    private final OverviewController overviewController;
    private final SensorDetailController sensorDetailController;

    private final int[] snapshot;

    private int lastSequence;

    public LoRaHardwareService(SensorDataStore sensorDataStore, SensorHistoryStore sensorHistoryStore, OverviewController overviewController, SensorDetailController sensorDetailController) {
        if (sensorDataStore == null) throw new NullPointerException("sensorDataStore tidak boleh null.");
        if (sensorHistoryStore == null) throw new NullPointerException("sensorHistoryStore tidak boleh null.");
        if (overviewController == null) throw new NullPointerException("overviewController tidak boleh null.");
        if (sensorDetailController == null) throw new NullPointerException("sensorDetailController tidak boleh null.");

        this.sensorDataStore = sensorDataStore;
        this.sensorHistoryStore = sensorHistoryStore;
        this.overviewController = overviewController;
        this.sensorDetailController = sensorDetailController;
        this.snapshot = new int[LoRaNative.SNAPSHOT_SIZE];
        this.lastSequence = -1;
    }

    public void poll() {
        boolean dataAvailable = LoRaNative.readLatest(this.snapshot);

        if (!dataAvailable) {
            return;
        }

        int sequence = this.snapshot[LoRaNative.INDEX_SEQUENCE];

        if (sequence == this.lastSequence) {
            return;
        }

        this.lastSequence = sequence;

        final SensorData sensorData = createSensorData(this.snapshot);
        final long receivedTimestamp = Util.currentTimeMillis();
        final String shortTime = Time.formatJakartaTime(receivedTimestamp);
        final String fullTime = Time.formatJakartaDateTime(receivedTimestamp);
        final int packetSequence = sequence;
        final int packetRssi = this.snapshot[LoRaNative.INDEX_RSSI];

        MicroUI.callSerially(
                new Runnable() {
                    @Override
                    public void run() {
                        LoRaHardwareService.this.sensorDataStore.upsert(sensorData);
                        LoRaHardwareService.this.sensorHistoryStore.add(new SensorHistoryEntry(shortTime, fullTime, sensorData));
                        LoRaHardwareService.this.overviewController.refresh();
                        LoRaHardwareService.this.sensorDetailController.refreshIfOpen();

                        LOGGER.log(Level.INFO, "LoRa data applied | sequence=" + packetSequence + " | nodeId=" + sensorData.getNodeId() + " | targetId=" + sensorData.getTargetId() + " | N=" + sensorData.getN() + " | P=" + sensorData.getP() + " | K=" + sensorData.getK() + " | RSSI=" + packetRssi + " dBm");
                    }
                }
        );
    }

    private SensorData createSensorData(int[] source) {
        int nodeId = source[LoRaNative.INDEX_NODE_ID];
        int nodeTarget = source[LoRaNative.INDEX_NODE_TARGET];

        SensorData data = new SensorData(nodeId, nodeTarget);

        data.setSm(source[LoRaNative.INDEX_SOIL_MOISTURE] / 10.0f);
        data.setSt(source[LoRaNative.INDEX_SOIL_TEMPERATURE] / 10.0f);
        data.setEc(source[LoRaNative.INDEX_CONDUCTIVITY]);
        data.setpH(source[LoRaNative.INDEX_SOIL_PH] / 10.0f);
        data.setN(source[LoRaNative.INDEX_NITROGEN]);
        data.setP(source[LoRaNative.INDEX_PHOSPHORUS]);
        data.setK(source[LoRaNative.INDEX_POTASSIUM]);
        data.setAt(source[LoRaNative.INDEX_AIR_TEMPERATURE] / 10.0f);
        data.setAh(source[LoRaNative.INDEX_AIR_HUMIDITY] / 10.0f);
        data.setLux(source[LoRaNative.INDEX_LIGHT_INTENSITY]);

        return data;
    }
}