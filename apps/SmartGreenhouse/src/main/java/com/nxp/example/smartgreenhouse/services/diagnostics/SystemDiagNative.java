package com.nxp.example.smartgreenhouse.services.diagnostics;

/**
 * Native bridge to read FreeRTOS heap and CPU load diagnostics from the RW612 BSP.
 */
public final class SystemDiagNative {

    private SystemDiagNative() {
    }

    public static int getFreeNativeHeapBytes() {
        return getFreeNativeHeapNative();
    }

    public static int getMinFreeNativeHeapBytes() {
        return getMinFreeNativeHeapNative();
    }

    public static int getCpuLoadPercent() {
        return getCpuLoadPercentNative();
    }

    private static native int getFreeNativeHeapNative();

    private static native int getMinFreeNativeHeapNative();

    private static native int getCpuLoadPercentNative();
}
