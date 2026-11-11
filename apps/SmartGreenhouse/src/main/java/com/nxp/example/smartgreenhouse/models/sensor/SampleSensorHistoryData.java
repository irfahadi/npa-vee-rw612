package com.nxp.example.smartgreenhouse.models.sensor;

public final class SampleSensorHistoryData {

    private static final String[] TIME_LABELS = {
            "10:00",
            "10:10",
            "10:20",
            "10:30",
            "10:40",
            "10:50"
    };

    private SampleSensorHistoryData() {
    }

    public static SensorHistoryEntry[] create() {
        SensorHistoryEntry[] result = new SensorHistoryEntry[TIME_LABELS.length * 2];

        int destinationIndex = 0;

        for (int i = 0; i < TIME_LABELS.length; i++) {
            result[destinationIndex++] = new SensorHistoryEntry(TIME_LABELS[i], TIME_LABELS[i], createNode1(i));
            result[destinationIndex++] = new SensorHistoryEntry(TIME_LABELS[i], TIME_LABELS[i], createNode2(i));
        }

        return result;
    }

    private static SensorData createNode1(int index) {
        SensorData data = new SensorData(1, 1);

        data.setN(30 + (index * 2));
        data.setP(18 + index);
        data.setK(165 + (index * 4));
        data.setSm(58 + (index * 2));
        data.setpH(6.2f + (index * 0.05f));
        data.setSt(24 + (index * 0.2f));
        data.setEc(1.2f + (index * 0.1f));
        data.setAt(26 + (index * 0.3f));
        data.setAh(56 + index);
        data.setLux(20000 + (index * 1500));

        return data;
    }

    private static SensorData createNode2(int index) {
        SensorData data = new SensorData(2, 1);

        data.setN(14 + index);
        data.setP(45 + (index * 4));
        data.setK(120 + (index * 5));
        data.setSm(25 + index);
        data.setpH(7.8f + (index * 0.05f));
        data.setSt(20 + (index * 0.3f));
        data.setEc(2.2f + (index * 0.1f));
        data.setAt(21 + (index * 0.4f));
        data.setAh(68 + index);
        data.setLux(45000 + (index * 2000));

        return data;
    }
}