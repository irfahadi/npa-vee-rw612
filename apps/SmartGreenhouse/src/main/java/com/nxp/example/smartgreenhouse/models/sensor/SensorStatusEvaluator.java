package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorStatusEvaluator {

    private SensorStatusEvaluator() {}

    public static SensorStatus evaluate(int sensorId, float value) {
        SensorThreshold threshold = SensorThresholdProvider.getBySensorId(sensorId);
        if (threshold == null) {
            return SensorStatus.OPTIMAL;
        }
        if (threshold.isOptimal(value)) {
            return SensorStatus.OPTIMAL;
        }
        if (threshold.isWarning(value)) {
            return SensorStatus.WASPADA;
        }
        return SensorStatus.BAHAYA;
    }
}