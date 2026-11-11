package com.nxp.example.smartgreenhouse.models.sensor;

import com.nxp.example.smartgreenhouse.style.Icons;

import ej.microui.display.Image;

public final class SensorDefinitionProvider {

    private static SensorDefinition[] definitions;

    private SensorDefinitionProvider() {
    }

    public static void initialize() {
        if (definitions != null) {
            return;
        }

        definitions = new SensorDefinition[] {
                new SensorDefinition(
                        SensorId.NITROGEN,
                        Image.getImage(Icons.NITROGEN_ICON_32),
                        "NITROGEN (N)",
                        "ppm",
                        0,
                        true
                ),
                new SensorDefinition(
                        SensorId.FOSFOR,
                        Image.getImage(Icons.FOSFOR_ICON_32),
                        "FOSFOR (P)",
                        "ppm",
                        0,
                        true
                ),
                new SensorDefinition(
                        SensorId.KALIUM,
                        Image.getImage(Icons.KALIUM_ICON_32),
                        "KALIUM (K)",
                        "ppm",
                        0,
                        true
                ),
                new SensorDefinition(
                        SensorId.KELEMBAPAN_TANAH,
                        Image.getImage(Icons.KELEMBAPAN_TANAH_ICON_32),
                        "KELEMBAPAN TANAH",
                        "%",
                        0,
                        true
                ),
                new SensorDefinition(
                        SensorId.PH_TANAH,
                        Image.getImage(Icons.PH_TANAH_ICON_32),
                        "PH TANAH",
                        "",
                        1,
                        true
                ),
                new SensorDefinition(
                        SensorId.SUHU_TANAH,
                        Image.getImage(Icons.SUHU_TANAH_ICON_32),
                        "SUHU TANAH",
                        "°C",
                        1,
                        true
                ),
                new SensorDefinition(
                        SensorId.SALINITAS_TANAH,
                        Image.getImage(Icons.SALINITAS_TANAH_ICON_32),
                        "SALINITAS TANAH",
                        "dS/m",
                        1,
                        false
                ),
                new SensorDefinition(
                        SensorId.SUHU_UDARA,
                        Image.getImage(Icons.SUHU_UDARA_ICON_32),
                        "SUHU UDARA",
                        "°C",
                        1,
                        false
                ),
                new SensorDefinition(
                        SensorId.KELEMBAPAN_UDARA,
                        Image.getImage(Icons.KELEMBAPAN_UDARA_ICON_32),
                        "KELEMBAPAN UDARA",
                        "%",
                        0,
                        false
                ),
                new SensorDefinition(
                        SensorId.INTENSITAS_CAHAYA,
                        Image.getImage(Icons.INTENSITAS_CAHAYA_ICON_32),
                        "INTENSITAS CAHAYA",
                        "lux",
                        0,
                        false
                )
        };
    }

    public static SensorDefinition[] getAll() {
        ensureInitialized();
        return definitions;
    }

    public static SensorDefinition getById(int sensorId) {
        ensureInitialized();

        for (SensorDefinition definition : definitions) {
            if (definition.getSensorId() == sensorId) {
                return definition;
            }
        }

        return null;
    }

    private static void ensureInitialized() {
        if (definitions == null) {
            throw new IllegalStateException("SensorDefinitionProvider belum diinisialisasi. " + "Panggil SensorDefinitionProvider.initialize() " + "setelah MicroUI.start().");
        }
    }
}