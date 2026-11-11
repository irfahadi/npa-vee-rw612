package com.nxp.example.smartgreenhouse.models.sensor;

import ej.microui.display.Image;

public class SensorDefinition {
    private final int sensorId;
    private final Image icon;
    private final String title;
    private final String unit;
    private final int decimalPlace;
    private final boolean isVisible;

    public SensorDefinition(int sensorId, Image icon, String title, String unit, int decimalPlace, boolean isVisible) {
        this.sensorId = sensorId;
        this.icon = icon;
        this.title = title;
        this.unit = unit;
        this.decimalPlace = decimalPlace;
        this.isVisible = isVisible;
    }

    public int getSensorId() {
        return sensorId;
    }

    public Image getIcon() {
        return icon;
    }

    public String getTitle() {
        return title;
    }

    public String getUnit() {
        return unit;
    }

    public int getDecimalPlace() {
        return decimalPlace;
    }

    public boolean isVisible() {
        return isVisible;
    }
}
