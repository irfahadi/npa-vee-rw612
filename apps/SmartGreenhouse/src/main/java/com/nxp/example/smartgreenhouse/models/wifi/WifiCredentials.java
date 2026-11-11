package com.nxp.example.smartgreenhouse.models.wifi;

public final class WifiCredentials {

    private final String ssid;
    private final String password;

    public WifiCredentials(
            String ssid,
            String password) {

        if (ssid == null || ssid.length() == 0) {
            throw new IllegalArgumentException("SSID tidak boleh kosong.");
        }

        if (password == null) {
            password = "";
        }

        this.ssid = ssid;
        this.password = password;
    }

    public String getSsid() {
        return this.ssid;
    }

    public String getPassword() {
        return this.password;
    }
}