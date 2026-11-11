package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorHistorySummaryCalculate {

    private SensorHistorySummaryCalculate() {
    }

    public static SensorHistorySummary calculate(SensorHistoryEntry[] entries, SensorDefinition definition) {
        if (entries == null || entries.length == 0 || definition == null) {
            return SensorHistorySummary.empty();
        }

        boolean found = false;

        float minimum = 0;
        float maximum = 0;
        float total = 0;

        int validCount = 0;
        String lastUpdate = "";

        for (SensorHistoryEntry entry : entries) {
            if (entry == null || entry.getSensorData() == null) {
                continue;
            }

            SensorDisplayItem displayItem = SensorDisplayBuilder.build(definition, entry.getSensorData());

            if (displayItem == null) {
                continue;
            }

            float value = displayItem.getValue();

            if (!found) {
                minimum = value;
                maximum = value;
                found = true;
            } else {
                if (value < minimum) {
                    minimum = value;
                }

                if (value > maximum) {
                    maximum = value;
                }
            }

            total += value;
            validCount++;

            if (entry.getShortTime() != null && !entry.getShortTime().isEmpty()) {
                lastUpdate = entry.getShortTime();
            }
        }

        if (!found || validCount == 0) {
            return SensorHistorySummary.empty();
        }

        return new SensorHistorySummary(true, minimum, maximum, total / validCount, lastUpdate);
    }
}