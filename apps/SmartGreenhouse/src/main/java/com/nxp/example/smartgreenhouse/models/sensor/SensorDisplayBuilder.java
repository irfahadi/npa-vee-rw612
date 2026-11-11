package com.nxp.example.smartgreenhouse.models.sensor;

public class SensorDisplayBuilder {

    public static SensorDisplayItem build(SensorDefinition definition, SensorData data) {
        float value = extractValue(definition.getSensorId(), data);
        boolean available = data.isAvailable();
        SensorStatus status = available ? SensorStatusEvaluator.evaluate(definition.getSensorId(), value) : SensorStatus.OPTIMAL;
        return new SensorDisplayItem(definition, value, status, available);
    }

    private static float extractValue(int sensorId, SensorData data) {
        switch (sensorId) {
            case SensorId.NITROGEN:            return data.getN();
            case SensorId.FOSFOR:              return data.getP();
            case SensorId.KALIUM:              return data.getK();
            case SensorId.KELEMBAPAN_TANAH:    return data.getSm();
            case SensorId.SUHU_TANAH:          return data.getSt();
            case SensorId.PH_TANAH:            return data.getpH();
            case SensorId.SALINITAS_TANAH:     return data.getEc();
            case SensorId.SUHU_UDARA:          return data.getAt();
            case SensorId.KELEMBAPAN_UDARA:    return data.getAh();
            case SensorId.INTENSITAS_CAHAYA:   return data.getLux();
            default:                           return 0;
        }
    }
}