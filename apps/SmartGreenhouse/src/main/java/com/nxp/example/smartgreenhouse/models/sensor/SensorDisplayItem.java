package com.nxp.example.smartgreenhouse.models.sensor;

import com.nxp.example.smartgreenhouse.utils.SensorValueFormatter;

public class SensorDisplayItem {

    private final SensorDefinition definition;
    private final float value;
    private final SensorStatus sensorStatus;
    private final boolean available;

    public SensorDisplayItem(SensorDefinition definition, float value, SensorStatus sensorStatus) {
        this(definition, value, sensorStatus, true);
    }

    public SensorDisplayItem(SensorDefinition definition, float value, SensorStatus sensorStatus, boolean available) {
        this.definition = definition;
        this.value = value;
        this.sensorStatus = sensorStatus;
        this.available = available;
    }

    public SensorDefinition getDefinition() {
        return definition;
    }

    public float getValue() {
        return value;
    }

    public SensorStatus getSensorStatus() {
        return sensorStatus;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public String getFormattedValue() {
        if (!this.available) return "-";
        return SensorValueFormatter.format(this.definition, this.value);
    }
}