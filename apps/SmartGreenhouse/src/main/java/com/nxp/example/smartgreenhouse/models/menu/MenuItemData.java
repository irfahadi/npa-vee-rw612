package com.nxp.example.smartgreenhouse.models.menu;

import com.nxp.example.smartgreenhouse.models.sensor.SensorDefinition;
import ej.microui.display.Image;

public class MenuItemData {

    public static final int TYPE_SENSOR = 0;
    public static final int TYPE_ACTUATOR = 1;

    private final int menuId;
    private final int type;
    private final Image icon;
    private final String title;
    private final int sensorId;
    private final int actuatorId;

    private MenuItemData(
            int menuId,
            int type,
            Image icon,
            String title,
            int sensorId,
            int actuatorId
    ) {
        this.menuId = menuId;
        this.type = type;
        this.icon = icon;
        this.title = title;
        this.sensorId = sensorId;
        this.actuatorId = actuatorId;
    }

    public static MenuItemData fromSensorDefinition(SensorDefinition definition) {
        return new MenuItemData(
                definition.getSensorId(),
                TYPE_SENSOR,
                definition.getIcon(),
                definition.getTitle(),
                definition.getSensorId(),
                -1
        );
    }

    public static MenuItemData createActuator(int actuatorId, Image icon, String title) {
        return new MenuItemData(
                actuatorId,
                TYPE_ACTUATOR,
                icon,
                title,
                -1,
                actuatorId
        );
    }

    public int getMenuId() {
        return this.menuId;
    }

    public int getType() {
        return this.type;
    }

    public Image getIcon() {
        return this.icon;
    }

    public String getTitle() {
        return this.title;
    }

    public int getSensorId() {
        return this.sensorId;
    }

    public int getActuatorId() {
        return this.actuatorId;
    }

    public boolean isSensor() {
        return this.type == TYPE_SENSOR;
    }

    public boolean isActuator() {
        return this.type == TYPE_ACTUATOR;
    }
}