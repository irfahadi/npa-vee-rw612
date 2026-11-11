package com.nxp.example.smartgreenhouse.models.sensor;

public final class SensorData {
    private final int nodeId;
    private final int targetId;
    private float n;
    private float p;
    private float k;
    private float sm;
    private float pH;
    private float st;
    private float ec;
    private float at;
    private float ah;
    private float lux;
    private boolean available;

    public SensorData(int nodeId, int targetId) {
        this.nodeId = nodeId;
        this.targetId = targetId;
        this.available = true;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getTargetId() {
        return targetId;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public float getN() {
        return n;
    }

    public void setN(float n) {
        this.n = n;
    }

    public float getP() {
        return p;
    }

    public void setP(float p) {
        this.p = p;
    }

    public float getK() {
        return k;
    }

    public void setK(float k) {
        this.k = k;
    }

    public float getSm() {
        return sm;
    }

    public void setSm(float sm) {
        this.sm = sm;
    }

    public float getpH() {
        return pH;
    }

    public void setpH(float pH) {
        this.pH = pH;
    }

    public float getSt() {
        return st;
    }

    public void setSt(float st) {
        this.st = st;
    }

    public float getEc() {
        return ec;
    }

    public void setEc(float ec) {
        this.ec = ec;
    }

    public float getAt() {
        return at;
    }

    public void setAt(float at) {
        this.at = at;
    }

    public float getAh() {
        return ah;
    }

    public void setAh(float ah) {
        this.ah = ah;
    }

    public float getLux() {
        return lux;
    }

    public void setLux(float lux) {
        this.lux = lux;
    }
}