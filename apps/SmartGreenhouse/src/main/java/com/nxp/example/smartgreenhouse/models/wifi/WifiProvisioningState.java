package com.nxp.example.smartgreenhouse.models.wifi;

public final class WifiProvisioningState {

    public static final int IDLE = 0;

    public static final int AUTO_CONNECTING = 1;

    public static final int HOTSPOT_STARTING = 2;

    public static final int HOTSPOT_READY = 3;

    public static final int NETWORK_SCANNING = 4;

    public static final int CONNECTING = 5;

    public static final int CONNECTED = 6;

    public static final int FAILED = 7;

    private WifiProvisioningState() {}
}