package com.nxp.example.smartgreenhouse.services.wifi;

import com.nxp.example.smartgreenhouse.models.wifi.WifiCredentials;

public interface WifiCredentialStore {

    int getMaxSavedNetworks();

    boolean hasCredentials();

    WifiCredentials load();

    WifiCredentials[] loadAll();

    void save(WifiCredentials credentials);

    void clear();

    /** Returns the stored MQTT broker address ("host:port"), or null when none is stored. */
    String loadBrokerAddress();

    /** Stores the MQTT broker address ("host:port"); null or empty removes it. */
    void saveBrokerAddress(String brokerAddress);
}