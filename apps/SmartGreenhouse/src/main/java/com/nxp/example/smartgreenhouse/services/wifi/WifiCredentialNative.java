package com.nxp.example.smartgreenhouse.services.wifi;

public final class WifiCredentialNative {

    private WifiCredentialNative() {
    }

    public static native int readNative(byte[] destination);

    public static native boolean writeNative(byte[] source, int length);
}