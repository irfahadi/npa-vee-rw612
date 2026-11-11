package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorThresholdProvider {

    private static final SensorThreshold[] THRESHOLDS = {
            new SensorThreshold(SensorId.NITROGEN, 10, 20, 50, 70),
            new SensorThreshold(SensorId.FOSFOR, 10, 15, 30, 50),
            new SensorThreshold(SensorId.KALIUM, 100, 150, 250, 350),
            new SensorThreshold(SensorId.KELEMBAPAN_TANAH, 20, 40, 80, 90),
            new SensorThreshold(SensorId.PH_TANAH, 5.5f, 6.0f, 7.0f, 7.5f),
            new SensorThreshold(SensorId.SUHU_TANAH, 15, 20, 30, 35),
            new SensorThreshold(SensorId.SALINITAS_TANAH, 0, 0, 2, 4),
            new SensorThreshold(SensorId.SUHU_UDARA, 15, 20, 30, 35),
            new SensorThreshold(SensorId.KELEMBAPAN_UDARA, 40, 50, 70, 80),
            new SensorThreshold(SensorId.INTENSITAS_CAHAYA, 5000, 10000, 50000, 70000)
    };

    private SensorThresholdProvider() {}

    public static SensorThreshold getBySensorId(int sensorId) {
        for (SensorThreshold threshold : THRESHOLDS) {
            if (threshold.getSensorId() == sensorId) {
                return threshold;
            }
        }

        return null;
    }
}