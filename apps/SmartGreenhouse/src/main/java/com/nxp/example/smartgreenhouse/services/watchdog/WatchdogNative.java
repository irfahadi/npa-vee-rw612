package com.nxp.example.smartgreenhouse.services.watchdog;

public final class WatchdogNative {

    private WatchdogNative() {
    }

    public static void refresh() {
        refreshNative();
    }

    private static native void refreshNative();
}
