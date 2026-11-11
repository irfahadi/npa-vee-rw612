package com.nxp.example.smartgreenhouse.models.sensor;

public class SampleSensorData {
    private SampleSensorData(){}

    public static SensorData[] createSampleSensorData(){
        return new SensorData[] {
                sampleNode1(),
                sampleNode2(),
        };
    }

    private static SensorData sampleNode1() {
        SensorData sample = new SensorData(1, 1);
        sample.setN(35);
        sample.setP(20);
        sample.setK(180);
        sample.setSm(65);
        sample.setSt(25);
        sample.setpH(6.5f);
        sample.setEc(1.5f);
        sample.setAt(27);
        sample.setAh(60);
        sample.setLux(25000);
        return sample;
    }

    private static SensorData sampleNode2() {
        SensorData sample = new SensorData(2, 1);
        sample.setN(15);
        sample.setP(100);
        sample.setK(130);
        sample.setSm(25);
        sample.setSt(20);
        sample.setpH(8.5f);
        sample.setEc(2.5f);
        sample.setAt(20);
        sample.setAh(69);
        sample.setLux(55000);
        return sample;
    }
}
