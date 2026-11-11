package com.nxp.example.smartgreenhouse.models.menu;

import com.nxp.example.smartgreenhouse.models.actuator.ActuatorId;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDefinition;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDefinitionProvider;
import com.nxp.example.smartgreenhouse.style.Icons;
import ej.microui.display.Image;

public class SampleMenuItemData {

    public static MenuItemData[] createSampleMenuItems() {
        SensorDefinition[] sensorDefs = SensorDefinitionProvider.getAll();

        int actuatorCount = 1;
        int total = sensorDefs.length + actuatorCount;
        MenuItemData[] items = new MenuItemData[total];

        for (int i = 0; i < sensorDefs.length; i++) {
            items[i] = MenuItemData.fromSensorDefinition(sensorDefs[i]);
        }

        items[sensorDefs.length] = MenuItemData.createActuator(
                ActuatorId.KATUP_AIR,
                Image.getImage(Icons.KATUP_AIR_ICON_32),
                "KATUP AIR"
        );

        return items;
    }
}