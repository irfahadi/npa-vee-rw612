package com.nxp.example.smartgreenhouse.services.wifi;

import com.nxp.example.smartgreenhouse.models.wifi.WifiCredentials;

public interface WifiCredentialStore {

    int getMaxSavedNetworks();

    boolean hasCredentials();

    WifiCredentials load();

    WifiCredentials[] loadAll();

    void save(WifiCredentials credentials);

    void clear();
}