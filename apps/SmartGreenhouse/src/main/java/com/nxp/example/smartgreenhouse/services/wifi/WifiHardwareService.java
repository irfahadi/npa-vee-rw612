package com.nxp.example.smartgreenhouse.services.wifi;

import ej.ecom.wifi.AccessPoint;
import ej.ecom.wifi.SecurityMode;
import ej.ecom.wifi.SoftAPConfiguration;
import ej.ecom.wifi.WifiCapability;
import ej.ecom.wifi.WifiManager;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WifiHardwareService {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: WIFI HARDWARE SERVICE]");

    private static final String PROVISIONING_SSID = "smartgreenhouse";
    private static final String PROVISIONING_PASSWORD = "smartgreenhouse";
    private static final int MAX_PROVISIONING_NETWORKS = 16;

    private final WifiManager wifiManager;

    public WifiHardwareService() {
        this.wifiManager = WifiManager.getInstance();
    }

    public synchronized WifiCapability getCapability() throws IOException {
        return this.wifiManager.getCapability();
    }

    public String getProvisioningSsid() {
        return PROVISIONING_SSID;
    }

    public String getProvisioningPassword() {
        return PROVISIONING_PASSWORD;
    }

    public synchronized boolean isConnected() throws IOException {
        return this.wifiManager.getJoined() != null;
    }

    public synchronized boolean isConnectedToNetwork(String ssid) throws IOException {
        validateSsid(ssid);

        AccessPoint joinedAccessPoint = this.wifiManager.getJoined();

        if (joinedAccessPoint == null) return false;

        return ssid.equals(joinedAccessPoint.getSSID());
    }

    public synchronized String getConnectedSsid() throws IOException {
        AccessPoint joinedAccessPoint = this.wifiManager.getJoined();

        if (joinedAccessPoint == null) return null;

        return joinedAccessPoint.getSSID();
    }

    public synchronized void disconnectCurrentNetwork() throws IOException {
        AccessPoint joinedAccessPoint = this.wifiManager.getJoined();

        if (joinedAccessPoint == null) {
            LOGGER.log(Level.INFO, "No joined WiFi network to disconnect");
            return;
        }

        LOGGER.log(Level.INFO, "Leaving current WiFi | SSID=" + joinedAccessPoint.getSSID());
        this.wifiManager.leave();
        LOGGER.log(Level.INFO, "Current WiFi left");
    }

    public synchronized boolean connectToNetwork(String ssid, String password) throws IOException {
        validateSsid(ssid);

        String safePassword = password == null ? "" : password;
        validatePassword(safePassword);

        stopProvisioningAccessPoint();

        AccessPoint joinedAccessPoint = this.wifiManager.getJoined();

        if (joinedAccessPoint != null) {
            String joinedSsid = joinedAccessPoint.getSSID();

            if (ssid.equals(joinedSsid)) {
                LOGGER.log(Level.INFO, "Already connected to requested WiFi | SSID=" + ssid);
                return true;
            }

            LOGGER.log(Level.INFO, "Leaving previous WiFi | SSID=" + joinedSsid);
            this.wifiManager.leave();
        }

        LOGGER.log(Level.INFO, "Joining requested WiFi | SSID=" + ssid);
        this.wifiManager.join(ssid, safePassword);

        AccessPoint result = this.wifiManager.getJoined();
        boolean connected = result != null && ssid.equals(result.getSSID());

        LOGGER.log(connected ? Level.INFO : Level.WARNING, "Requested WiFi join completed | SSID=" + ssid + " | connected=" + connected);

        return connected;
    }

    public synchronized AccessPoint[] scanAvailableNetworks() throws IOException {
        stopProvisioningAccessPoint();
        disconnectCurrentNetwork();

        LOGGER.log(Level.INFO, "Available WiFi scan started | active=false");

        AccessPoint[] scannedAccessPoints = this.wifiManager.scan(false);

        LOGGER.log(Level.INFO, "Available WiFi scan completed | count=" + getAccessPointCount(scannedAccessPoints));

        return scannedAccessPoints == null ? new AccessPoint[0] : scannedAccessPoints;
    }

    public synchronized AccessPoint[] scanProvisioningNetworks() throws IOException {
        stopProvisioningAccessPoint();
        disconnectCurrentNetwork();

        LOGGER.log(Level.INFO, "Provisioning WiFi scan started | active=false");

        AccessPoint[] scannedAccessPoints = this.wifiManager.scan(false);
        AccessPoint[] selectedAccessPoints = selectProvisioningNetworks(scannedAccessPoints);

        LOGGER.log(Level.INFO, "Provisioning WiFi scan completed | scanned=" + getAccessPointCount(scannedAccessPoints) + " | selected=" + selectedAccessPoints.length);

        return selectedAccessPoints;
    }

    public synchronized void startProvisioningAccessPoint() throws IOException {
        validatePassword(PROVISIONING_PASSWORD);
        disconnectCurrentNetwork();

        if (this.wifiManager.isSoftAPEnabled()) {
            LOGGER.log(Level.INFO, "Provisioning SoftAP already enabled | SSID=" + PROVISIONING_SSID);
            return;
        }

        SoftAPConfiguration configuration = new SoftAPConfiguration();
        configuration.setName(PROVISIONING_SSID);
        configuration.setSSID(PROVISIONING_SSID);
        configuration.setPassphrase(PROVISIONING_PASSWORD);
        configuration.setSecurityMode(SecurityMode.WPA2);

        LOGGER.log(Level.INFO, "Starting provisioning SoftAP | SSID=" + PROVISIONING_SSID);

        this.wifiManager.enableSoftAP(configuration);

        LOGGER.log(Level.INFO, "Provisioning SoftAP enabled | SSID=" + PROVISIONING_SSID);
    }

    public synchronized void stopProvisioningAccessPoint() throws IOException {
        if (!this.wifiManager.isSoftAPEnabled()) return;

        LOGGER.log(Level.INFO, "Stopping provisioning SoftAP");
        this.wifiManager.disableSoftAP();
        LOGGER.log(Level.INFO, "Provisioning SoftAP stopped");
    }

    public synchronized boolean isProvisioningAccessPointEnabled() throws IOException {
        return this.wifiManager.isSoftAPEnabled();
    }

    private static AccessPoint[] selectProvisioningNetworks(AccessPoint[] scannedAccessPoints) {
        if (scannedAccessPoints == null || scannedAccessPoints.length == 0) return new AccessPoint[0];

        AccessPoint[] selectedAccessPoints = new AccessPoint[MAX_PROVISIONING_NETWORKS];
        int selectedCount = 0;

        for (int index = 0; index < scannedAccessPoints.length; index++) {
            AccessPoint candidate = scannedAccessPoints[index];

            if (!isUsableProvisioningNetwork(candidate)) continue;

            int duplicateIndex = findAccessPointIndexBySsid(selectedAccessPoints, selectedCount, candidate.getSSID());

            if (duplicateIndex >= 0) {
                AccessPoint existing = selectedAccessPoints[duplicateIndex];

                if (candidate.getRSSI() > existing.getRSSI()) selectedAccessPoints[duplicateIndex] = candidate;

                continue;
            }

            if (selectedCount < MAX_PROVISIONING_NETWORKS) {
                selectedAccessPoints[selectedCount] = candidate;
                selectedCount++;
                continue;
            }

            int weakestIndex = findWeakestAccessPointIndex(selectedAccessPoints, selectedCount);

            if (weakestIndex >= 0 && candidate.getRSSI() > selectedAccessPoints[weakestIndex].getRSSI()) selectedAccessPoints[weakestIndex] = candidate;
        }

        sortAccessPointsByRssi(selectedAccessPoints, selectedCount);

        AccessPoint[] result = new AccessPoint[selectedCount];

        for (int index = 0; index < selectedCount; index++) result[index] = selectedAccessPoints[index];

        return result;
    }

    private static boolean isUsableProvisioningNetwork(AccessPoint accessPoint) {
        if (accessPoint == null) return false;

        String ssid = accessPoint.getSSID();

        if (ssid == null || ssid.length() == 0) return false;

        return !PROVISIONING_SSID.equals(ssid);
    }

    private static int findAccessPointIndexBySsid(AccessPoint[] accessPoints, int count, String ssid) {
        for (int index = 0; index < count; index++) {
            AccessPoint accessPoint = accessPoints[index];

            if (accessPoint != null && ssid.equals(accessPoint.getSSID())) return index;
        }

        return -1;
    }

    private static int findWeakestAccessPointIndex(AccessPoint[] accessPoints, int count) {
        if (count <= 0) return -1;

        int weakestIndex = 0;

        for (int index = 1; index < count; index++) {
            if (accessPoints[index].getRSSI() < accessPoints[weakestIndex].getRSSI()) weakestIndex = index;
        }

        return weakestIndex;
    }

    private static void sortAccessPointsByRssi(AccessPoint[] accessPoints, int count) {
        for (int outerIndex = 0; outerIndex < count - 1; outerIndex++) {
            for (int innerIndex = outerIndex + 1; innerIndex < count; innerIndex++) {
                AccessPoint outerAccessPoint = accessPoints[outerIndex];
                AccessPoint innerAccessPoint = accessPoints[innerIndex];

                if (innerAccessPoint.getRSSI() > outerAccessPoint.getRSSI()) {
                    accessPoints[outerIndex] = innerAccessPoint;
                    accessPoints[innerIndex] = outerAccessPoint;
                }
            }
        }
    }

    private static int getAccessPointCount(AccessPoint[] accessPoints) {
        return accessPoints == null ? 0 : accessPoints.length;
    }

    private static void validateSsid(String ssid) {
        if (ssid == null || ssid.length() == 0) throw new IllegalArgumentException("SSID Wi-Fi tidak boleh kosong.");
    }

    private static void validatePassword(String password) {
        int passwordLength = password == null ? 0 : password.length();

        if (passwordLength == 0) return;

        if (passwordLength < 8 || passwordLength > 64) throw new IllegalArgumentException("Password Wi-Fi harus terdiri dari 8 sampai 64 karakter.");
    }
}