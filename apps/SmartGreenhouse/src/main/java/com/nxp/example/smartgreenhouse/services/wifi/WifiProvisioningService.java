package com.nxp.example.smartgreenhouse.services.wifi;

import com.nxp.example.smartgreenhouse.models.wifi.WifiCredentials;
import com.nxp.example.smartgreenhouse.models.wifi.WifiProvisioningState;
import com.nxp.example.smartgreenhouse.views.wifi.WifiPortalView;

import ej.ecom.wifi.AccessPoint;
import ej.ecom.wifi.WifiCapability;
import ej.bon.Util;

import ej.hoka.http.HttpRequest;
import ej.hoka.http.HttpResponse;
import ej.hoka.http.HttpServer;
import ej.hoka.http.body.ParameterParser;
import ej.hoka.http.requesthandler.RequestHandler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WifiProvisioningService {

    public interface Listener {

        void onStateChanged(int state);

        void onWifiConnectionStatusChanged(boolean connected);

        void onProvisioningReady(String ssid, String password, String portalUrl, int networkCount);

        void onConnecting(String ssid);

        void onConnected(String ssid);

        void onFailed(String message);
    }

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: WIFI PROVISIONING SERVICE]");

    private static final String PROVISIONING_PORTAL_URL = "http://192.168.1.1/";
    private static final long PROVISIONING_TIMEOUT_MS = 300000L;
    private static final long PROVISIONING_CREDENTIAL_POLL_INTERVAL_MS = 250L;
    private static final long PROVISIONING_HTTP_RESPONSE_GRACE_MS = 1500L;
    private static final long PROVISIONING_CLIENT_RESTART_DELAY_MS = 1500L;

    private final WifiHardwareService wifiHardwareService;
    private final WifiCredentialStore credentialStore;
    private final Listener listener;
    private final Object stateLock;

    private volatile int state;
    private volatile boolean autoConnectRunning;
    private volatile boolean provisioningRunning;
    private volatile boolean restoreRunning;
    private volatile boolean provisioningCredentialsSubmitted;
    private volatile boolean provisioningCancelRequested;

    private volatile String submittedProvisioningSsid;
    private volatile String submittedProvisioningPassword;

    private WifiCredentials connectedCredentials;
    private WifiCredentials previousConnectedCredentials;

    private HttpServer provisioningHttpServer;

    public WifiProvisioningService(Listener listener) {
        this(new WifiHardwareService(), new FlashWifiCredentialStore(), listener);
    }

    public WifiProvisioningService(WifiHardwareService wifiHardwareService, WifiCredentialStore credentialStore, Listener listener) {
        if (wifiHardwareService == null) throw new NullPointerException("wifiHardwareService tidak boleh null.");
        if (credentialStore == null) throw new NullPointerException("credentialStore tidak boleh null.");
        if (listener == null) throw new NullPointerException("listener tidak boleh null.");

        this.wifiHardwareService = wifiHardwareService;
        this.credentialStore = credentialStore;
        this.listener = listener;
        this.stateLock = new Object();

        this.state = WifiProvisioningState.IDLE;
        this.autoConnectRunning = false;
        this.provisioningRunning = false;
        this.restoreRunning = false;
        this.provisioningCredentialsSubmitted = false;
        this.provisioningCancelRequested = false;

        this.submittedProvisioningSsid = null;
        this.submittedProvisioningPassword = null;

        this.connectedCredentials = null;
        this.previousConnectedCredentials = null;

        this.provisioningHttpServer = null;
    }

    public int getState() {
        return this.state;
    }

    public boolean isBusy() {
        return this.autoConnectRunning || this.provisioningRunning || this.restoreRunning;
    }

    public boolean isProvisioningRunning() {
        return this.provisioningRunning;
    }

    public boolean isAutoConnectRunning() {
        return this.autoConnectRunning;
    }

    public WifiCredentialStore getCredentialStore() {
        return this.credentialStore;
    }

    public boolean tryAutoConnect() {
        synchronized (this.stateLock) {
            if (this.autoConnectRunning || this.provisioningRunning || this.restoreRunning) {
                LOGGER.log(Level.WARNING, "Automatic WiFi connection ignored | service is busy");
                return false;
            }

            this.autoConnectRunning = true;
        }

        setState(WifiProvisioningState.AUTO_CONNECTING);

        Thread worker = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        WifiProvisioningService.this.runAutoConnect();
                    }
                },
                "wifi-auto-connect"
        );

        try {
            worker.start();
            return true;
        } catch (Error error) {
            synchronized (this.stateLock) {
                this.autoConnectRunning = false;
                this.connectedCredentials = null;
            }

            setState(WifiProvisioningState.IDLE);
            notifyConnectionStatus(false);
            notifyFailed("Tidak dapat menjalankan proses koneksi Wi-Fi otomatis.");

            LOGGER.log(Level.SEVERE, "Unable to start automatic WiFi connection worker | error=" + error);

            return false;
        }
    }

    public boolean startProvisioning() {
        synchronized (this.stateLock) {
            if (this.provisioningRunning || this.autoConnectRunning || this.restoreRunning) {
                LOGGER.log(Level.WARNING, "WiFi provisioning ignored | service is busy");
                return false;
            }

            this.provisioningRunning = true;
            this.provisioningCancelRequested = false;

            this.previousConnectedCredentials = this.connectedCredentials;
            this.connectedCredentials = null;
        }

        clearSubmittedProvisioningCredentials();

        setState(WifiProvisioningState.HOTSPOT_STARTING);
        notifyConnectionStatus(false);

        Thread worker = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        WifiProvisioningService.this.runProvisioning();
                    }
                },
                "wifi-provisioning"
        );

        try {
            worker.start();
            return true;
        } catch (Error error) {
            WifiCredentials previousCredentials;

            synchronized (this.stateLock) {
                this.provisioningRunning = false;
                this.provisioningCancelRequested = false;

                previousCredentials = this.previousConnectedCredentials;
                this.connectedCredentials = previousCredentials;
                this.previousConnectedCredentials = null;
            }

            if (previousCredentials != null) {
                setState(WifiProvisioningState.CONNECTED);
                notifyConnectionStatus(true);
                notifyConnected(previousCredentials.getSsid());
            } else {
                setState(WifiProvisioningState.IDLE);
                notifyConnectionStatus(false);
                notifyFailed("Tidak dapat menjalankan proses provisioning Wi-Fi.");
            }

            LOGGER.log(Level.SEVERE, "Unable to start WiFi provisioning worker | error=" + error);

            return false;
        }
    }

    public boolean cancelProvisioning() {
        boolean restoreAfterFailure = false;

        synchronized (this.stateLock) {
            if (this.state == WifiProvisioningState.CONNECTING || this.provisioningCredentialsSubmitted) {
                LOGGER.log(Level.WARNING, "WiFi provisioning cancellation ignored | connection is already in progress");
                return false;
            }

            if (this.provisioningRunning) {
                this.provisioningCancelRequested = true;
                LOGGER.log(Level.INFO, "WiFi provisioning cancellation requested");
                return true;
            }

            if (this.state == WifiProvisioningState.FAILED && !this.autoConnectRunning && !this.restoreRunning) {
                this.restoreRunning = true;
                restoreAfterFailure = true;
            } else {
                return false;
            }
        }

        if (restoreAfterFailure) return startPreviousConnectionRestoreWorker();

        return false;
    }

    private void runAutoConnect() {
        WifiCredentials[] storedCredentials = null;
        WifiCredentials connectedCredential = null;
        boolean connected = false;
        String failureMessage = null;

        try {
            storedCredentials = this.credentialStore.loadAll();

            if (storedCredentials == null || storedCredentials.length == 0) {
                LOGGER.log(Level.INFO, "No stored WiFi credentials found");
                finishAutoConnectWithoutCredential();
                return;
            }

            LOGGER.log(Level.INFO, "Stored WiFi credentials found | count=" + storedCredentials.length);

            WifiCapability capability = this.wifiHardwareService.getCapability();
            LOGGER.log(Level.INFO, "WiFi capability: " + capability);

            AccessPoint[] availableNetworks = this.wifiHardwareService.scanAvailableNetworks();

            LOGGER.log(Level.INFO, "Automatic WiFi availability scan completed | networkCount=" + countAccessPoints(availableNetworks));

            for (int index = 0; index < storedCredentials.length; index++) {
                WifiCredentials credentials = storedCredentials[index];

                if (credentials == null) continue;

                String ssid = credentials.getSsid();

                if (findAccessPointBySsid(availableNetworks, ssid) == null) {
                    LOGGER.log(Level.INFO, "Stored WiFi is not currently available | SSID=" + ssid + " | skipped=true");
                    continue;
                }

                LOGGER.log(Level.INFO, "Stored WiFi is available | SSID=" + ssid + " | connecting=true");

                connected = this.wifiHardwareService.connectToNetwork(ssid, credentials.getPassword());

                if (connected) {
                    connectedCredential = credentials;
                    break;
                }

                LOGGER.log(Level.WARNING, "Stored WiFi connection failed | SSID=" + ssid);
            }

            if (!connected) failureMessage = "Tidak ada Wi-Fi tersimpan yang tersedia atau dapat terhubung.";
        } catch (Exception exception) {
            failureMessage = "Koneksi otomatis Wi-Fi gagal.";
            LOGGER.log(Level.WARNING, "Automatic WiFi connection failed | error=" + exception);
        }

        synchronized (this.stateLock) {
            this.autoConnectRunning = false;
            this.connectedCredentials = connected ? connectedCredential : null;
        }

        if (connected && connectedCredential != null) {
            setState(WifiProvisioningState.CONNECTED);
            notifyConnectionStatus(true);
            notifyConnected(connectedCredential.getSsid());

            LOGGER.log(Level.INFO, "Automatic WiFi connection successful | SSID=" + connectedCredential.getSsid());
        } else {
            setState(WifiProvisioningState.FAILED);
            notifyConnectionStatus(false);

            if (failureMessage != null) notifyFailed(failureMessage);
        }
    }

    private void finishAutoConnectWithoutCredential() {
        synchronized (this.stateLock) {
            this.autoConnectRunning = false;
            this.connectedCredentials = null;
        }

        setState(WifiProvisioningState.IDLE);
        notifyConnectionStatus(false);
    }

    private void runProvisioning() {
        AccessPoint[] accessPoints = null;
        String targetSsid = null;
        String targetPassword = null;
        String failureMessage = null;
        boolean credentialsSubmitted = false;

        try {
            setState(WifiProvisioningState.NETWORK_SCANNING);

            accessPoints = this.wifiHardwareService.scanProvisioningNetworks();

            LOGGER.log(Level.INFO, "Provisioning WiFi scan completed | networkCount=" + countAccessPoints(accessPoints));

            if (!this.provisioningCancelRequested) this.wifiHardwareService.startProvisioningAccessPoint();

            if (!this.provisioningCancelRequested) {
                this.provisioningHttpServer = createProvisioningHttpServer(accessPoints);

                LOGGER.log(Level.INFO, "Starting HOKA provisioning server | port=80");

                this.provisioningHttpServer.start();

                LOGGER.log(Level.INFO, "HOKA provisioning server started | port=80");

                setState(WifiProvisioningState.HOTSPOT_READY);

                notifyProvisioningReady(
                        this.wifiHardwareService.getProvisioningSsid(),
                        this.wifiHardwareService.getProvisioningPassword(),
                        PROVISIONING_PORTAL_URL,
                        countAccessPoints(accessPoints)
                );

                LOGGER.log(
                        Level.INFO,
                        "Provisioning portal ready | SSID=" + this.wifiHardwareService.getProvisioningSsid()
                                + " | portal=" + PROVISIONING_PORTAL_URL
                                + " | networkCount=" + countAccessPoints(accessPoints)
                );

                credentialsSubmitted = waitForProvisioningCredentials(PROVISIONING_TIMEOUT_MS);
            }

            if (!this.provisioningCancelRequested) {
                if (!credentialsSubmitted) {
                    failureMessage = "Waktu provisioning Wi-Fi telah habis.";
                } else {
                    targetSsid = this.submittedProvisioningSsid;
                    targetPassword = this.submittedProvisioningPassword;

                    setState(WifiProvisioningState.CONNECTING);
                    notifyConnecting(targetSsid);

                    LOGGER.log(
                            Level.INFO,
                            "Provisioning credentials submitted | SSID=" + targetSsid
                                    + " | waitingForHttpResponseMs=" + PROVISIONING_HTTP_RESPONSE_GRACE_MS
                    );

                    Thread.sleep(PROVISIONING_HTTP_RESPONSE_GRACE_MS);
                }
            }
        } catch (InterruptedException exception) {
            failureMessage = "Proses provisioning Wi-Fi terhenti.";
            LOGGER.log(Level.WARNING, "WiFi provisioning interrupted | error=" + exception);
        } catch (Exception exception) {
            failureMessage = "Terjadi kesalahan saat menjalankan provisioning Wi-Fi.";
            LOGGER.log(Level.WARNING, "WiFi provisioning failed | error=" + exception);
        } finally {
            stopProvisioningHttpServer();

            try {
                this.wifiHardwareService.stopProvisioningAccessPoint();
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "Unable to stop provisioning SoftAP | error=" + exception);
            }
        }

        if (this.provisioningCancelRequested) {
            finishProvisioningCancellation();
            return;
        }

        if (!credentialsSubmitted || targetSsid == null || targetSsid.length() == 0) {
            finishProvisioningFailure(failureMessage == null ? "Provisioning Wi-Fi tidak selesai." : failureMessage);
            clearSubmittedProvisioningCredentials();
            return;
        }

        try {
            Thread.sleep(PROVISIONING_CLIENT_RESTART_DELAY_MS);
        } catch (InterruptedException exception) {
            LOGGER.log(Level.WARNING, "Provisioning client restart delay interrupted | error=" + exception);
        }

        boolean connected = false;

        try {
            LOGGER.log(Level.INFO, "Connecting to provisioned WiFi | SSID=" + targetSsid);

            connected = this.wifiHardwareService.connectToNetwork(targetSsid, targetPassword);

            if (!connected) failureMessage = "Tidak dapat terhubung ke Wi-Fi " + targetSsid + ".";
        } catch (Exception exception) {
            failureMessage = "Koneksi ke Wi-Fi " + targetSsid + " gagal.";
            LOGGER.log(Level.WARNING, "Provisioned WiFi connection failed | SSID=" + targetSsid + " | error=" + exception);
        }

        WifiCredentials persistedCredentials = null;

        if (connected) {
            try {
                persistedCredentials = new WifiCredentials(targetSsid, targetPassword);
                this.credentialStore.save(persistedCredentials);

                LOGGER.log(Level.INFO, "Provisioned WiFi credentials persisted | SSID=" + targetSsid);
            } catch (RuntimeException exception) {
                connected = false;
                failureMessage = "Wi-Fi berhasil terhubung tetapi credential tidak dapat disimpan.";

                LOGGER.log(Level.SEVERE, "Unable to persist provisioned WiFi credentials | SSID=" + targetSsid + " | error=" + exception);

                try {
                    this.wifiHardwareService.disconnectCurrentNetwork();
                } catch (Exception disconnectException) {
                    LOGGER.log(Level.WARNING, "Unable to disconnect WiFi after credential storage failure | error=" + disconnectException);
                }
            }
        }

        synchronized (this.stateLock) {
            this.provisioningRunning = false;
            this.provisioningCancelRequested = false;

            if (connected && persistedCredentials != null) {
                this.connectedCredentials = persistedCredentials;
                this.previousConnectedCredentials = null;
            } else {
                this.connectedCredentials = null;
            }
        }

        clearSubmittedProvisioningCredentials();

        if (connected) {
            setState(WifiProvisioningState.CONNECTED);
            notifyConnectionStatus(true);
            notifyConnected(targetSsid);

            LOGGER.log(Level.INFO, "WiFi provisioning successful | SSID=" + targetSsid);
        } else {
            setState(WifiProvisioningState.FAILED);
            notifyConnectionStatus(false);
            notifyFailed(failureMessage == null ? "Koneksi Wi-Fi gagal." : failureMessage);

            LOGGER.log(Level.WARNING, "WiFi provisioning failed | SSID=" + targetSsid + " | reason=" + failureMessage);
        }
    }

    private void finishProvisioningCancellation() {
        synchronized (this.stateLock) {
            this.provisioningRunning = false;
            this.provisioningCancelRequested = false;
            this.restoreRunning = true;
        }

        clearSubmittedProvisioningCredentials();

        LOGGER.log(Level.INFO, "WiFi provisioning cancelled by user");

        restorePreviousConnectionAfterBack();
    }

    private boolean startPreviousConnectionRestoreWorker() {
        Thread worker = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        WifiProvisioningService.this.restorePreviousConnectionAfterBack();
                    }
                },
                "wifi-restore-previous"
        );

        try {
            worker.start();
            return true;
        } catch (Error error) {
            synchronized (this.stateLock) {
                this.restoreRunning = false;
            }

            LOGGER.log(Level.SEVERE, "Unable to start previous WiFi restore worker | error=" + error);

            return false;
        }
    }

    private void restorePreviousConnectionAfterBack() {
        WifiCredentials previousCredentials;

        synchronized (this.stateLock) {
            previousCredentials = this.previousConnectedCredentials;
            this.previousConnectedCredentials = null;
        }

        if (previousCredentials == null) {
            synchronized (this.stateLock) {
                this.restoreRunning = false;
                this.connectedCredentials = null;
            }

            setState(WifiProvisioningState.IDLE);
            notifyConnectionStatus(false);

            LOGGER.log(Level.INFO, "WiFi provisioning Back completed | no previous connected network");

            return;
        }

        setState(WifiProvisioningState.AUTO_CONNECTING);

        boolean restored = false;

        try {
            LOGGER.log(Level.INFO, "Restoring previous WiFi | SSID=" + previousCredentials.getSsid());

            restored = this.wifiHardwareService.connectToNetwork(previousCredentials.getSsid(), previousCredentials.getPassword());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Unable to restore previous WiFi | SSID=" + previousCredentials.getSsid() + " | error=" + exception);
        }

        synchronized (this.stateLock) {
            this.restoreRunning = false;
            this.connectedCredentials = restored ? previousCredentials : null;
        }

        if (restored) {
            setState(WifiProvisioningState.CONNECTED);
            notifyConnectionStatus(true);
            notifyConnected(previousCredentials.getSsid());

            LOGGER.log(Level.INFO, "Previous WiFi restored | SSID=" + previousCredentials.getSsid());
        } else {
            setState(WifiProvisioningState.FAILED);
            notifyConnectionStatus(false);
            notifyFailed("Tidak dapat terhubung kembali ke Wi-Fi sebelumnya.");

            LOGGER.log(Level.WARNING, "Previous WiFi restore failed | SSID=" + previousCredentials.getSsid());
        }
    }

    private HttpServer createProvisioningHttpServer(final AccessPoint[] accessPoints) {
        HttpServer server = HttpServer.builder()
                .port(80)
                .simultaneousConnections(1)
                .workerCount(1)
                .connectionTimeout(5000)
                .build();

        server.get(
                "/",
                new RequestHandler() {
                    @Override
                    public void process(HttpRequest request, HttpResponse response) {
                        setProvisioningHtmlResponse(response, WifiPortalView.buildHomePage());
                    }
                }
        );

        server.get(
                "/scan",
                new RequestHandler() {
                    @Override
                    public void process(HttpRequest request, HttpResponse response) {
                        setProvisioningHtmlResponse(response, WifiPortalView.buildNetworkListPage(accessPoints));
                    }
                }
        );

        server.post(
                "/network",
                new RequestHandler() {
                    @Override
                    public void process(HttpRequest request, HttpResponse response) {
                        try {
                            Map<String, String> parameters = request.parseBody(new ParameterParser());
                            String ssid = parameters.get("ssid");

                            if (ssid == null || ssid.length() == 0) {
                                setProvisioningHtmlResponse(
                                        response,
                                        WifiPortalView.buildErrorPage("Jaringan tidak valid", "Pilih salah satu jaringan Wi-Fi.")
                                );
                                return;
                            }

                            AccessPoint accessPoint = findAccessPointBySsid(accessPoints, ssid);

                            if (accessPoint == null) {
                                setProvisioningHtmlResponse(
                                        response,
                                        WifiPortalView.buildErrorPage("Jaringan tidak valid", "Jaringan yang dipilih tidak ditemukan.")
                                );
                                return;
                            }

                            setProvisioningHtmlResponse(
                                    response,
                                    WifiPortalView.buildPasswordPage(accessPoint.getSSID(), accessPoint.getRSSI())
                            );
                        } catch (IOException exception) {
                            LOGGER.log(Level.WARNING, "Unable to process provisioning network selection | error=" + exception);

                            setProvisioningHtmlResponse(
                                    response,
                                    WifiPortalView.buildErrorPage("Terjadi kesalahan", "Perangkat tidak dapat membaca jaringan yang dipilih.")
                            );
                        }
                    }
                }
        );

        server.post(
                "/connect",
                new RequestHandler() {
                    @Override
                    public void process(HttpRequest request, HttpResponse response) {
                        try {
                            if (WifiProvisioningService.this.provisioningCancelRequested) {
                                setProvisioningHtmlResponse(
                                        response,
                                        WifiPortalView.buildErrorPage("Provisioning dibatalkan", "Silakan mulai kembali dari perangkat.")
                                );
                                return;
                            }

                            Map<String, String> parameters = request.parseBody(new ParameterParser());

                            String ssid = parameters.get("ssid");
                            String password = parameters.get("password");

                            if (ssid == null || ssid.length() == 0) {
                                setProvisioningHtmlResponse(
                                        response,
                                        WifiPortalView.buildErrorPage("Jaringan tidak valid", "SSID Wi-Fi tidak boleh kosong.")
                                );
                                return;
                            }

                            if (findAccessPointBySsid(accessPoints, ssid) == null) {
                                setProvisioningHtmlResponse(
                                        response,
                                        WifiPortalView.buildErrorPage("Jaringan tidak valid", "Jaringan yang dipilih tidak terdapat pada hasil scan.")
                                );
                                return;
                            }

                            if (password == null) password = "";

                            if (!isPasswordValid(password)) {
                                setProvisioningHtmlResponse(
                                        response,
                                        WifiPortalView.buildErrorPage("Password tidak valid", "Password harus kosong untuk jaringan terbuka atau terdiri dari 8 sampai 64 karakter.")
                                );
                                return;
                            }

                            if (WifiProvisioningService.this.provisioningCredentialsSubmitted) {
                                setProvisioningHtmlResponse(response, WifiPortalView.buildConnectingPage(ssid));
                                return;
                            }

                            setProvisioningHtmlResponse(response, WifiPortalView.buildConnectingPage(ssid));

                            WifiProvisioningService.this.submittedProvisioningSsid = ssid;
                            WifiProvisioningService.this.submittedProvisioningPassword = password;
                            WifiProvisioningService.this.provisioningCredentialsSubmitted = true;

                            LOGGER.log(Level.INFO, "Provisioning credentials queued | SSID=" + ssid + " | passwordLength=" + password.length());
                        } catch (IOException exception) {
                            LOGGER.log(Level.WARNING, "Unable to process provisioning credentials | error=" + exception);

                            setProvisioningHtmlResponse(
                                    response,
                                    WifiPortalView.buildErrorPage("Terjadi kesalahan", "Perangkat tidak dapat membaca credential Wi-Fi.")
                            );
                        }
                    }
                }
        );

        return server;
    }

    private boolean waitForProvisioningCredentials(long timeoutMilliseconds) throws InterruptedException {
        long deadline = Util.platformTimeMillis() + timeoutMilliseconds;

        while (!this.provisioningCredentialsSubmitted && !this.provisioningCancelRequested) {
            long remaining = deadline - Util.platformTimeMillis();

            if (remaining <= 0L) return false;

            long sleepDuration = remaining < PROVISIONING_CREDENTIAL_POLL_INTERVAL_MS ? remaining : PROVISIONING_CREDENTIAL_POLL_INTERVAL_MS;

            Thread.sleep(sleepDuration);
        }

        return this.provisioningCredentialsSubmitted && !this.provisioningCancelRequested;
    }

    private void stopProvisioningHttpServer() {
        HttpServer server = this.provisioningHttpServer;

        if (server == null) return;

        try {
            LOGGER.log(Level.INFO, "Stopping HOKA provisioning server");
            server.stop();
            LOGGER.log(Level.INFO, "HOKA provisioning server stopped");
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Unable to stop HOKA provisioning server | error=" + exception);
        } finally {
            this.provisioningHttpServer = null;
        }
    }

    private void finishProvisioningFailure(String message) {
        synchronized (this.stateLock) {
            this.provisioningRunning = false;
            this.provisioningCancelRequested = false;
            this.connectedCredentials = null;
        }

        setState(WifiProvisioningState.FAILED);
        notifyConnectionStatus(false);
        notifyFailed(message);

        LOGGER.log(Level.WARNING, "WiFi provisioning finished with failure | reason=" + message);
    }

    private void clearSubmittedProvisioningCredentials() {
        this.provisioningCredentialsSubmitted = false;
        this.submittedProvisioningSsid = null;
        this.submittedProvisioningPassword = null;
    }

    private void setProvisioningHtmlResponse(HttpResponse response, String page) {
        response.addHeader("content-type", "text/html; charset=UTF-8");
        response.addHeader("cache-control", "no-store");

        try {
            response.setData(page, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            LOGGER.log(Level.WARNING, "UTF-8 encoding unavailable | using default encoding | error=" + exception);
            response.setData(page);
        }
    }

    private static AccessPoint findAccessPointBySsid(AccessPoint[] accessPoints, String ssid) {
        if (accessPoints == null || ssid == null) return null;

        for (int index = 0; index < accessPoints.length; index++) {
            AccessPoint accessPoint = accessPoints[index];

            if (accessPoint != null && ssid.equals(accessPoint.getSSID())) return accessPoint;
        }

        return null;
    }

    private static int countAccessPoints(AccessPoint[] accessPoints) {
        if (accessPoints == null) return 0;

        int count = 0;

        for (int index = 0; index < accessPoints.length; index++) {
            if (accessPoints[index] != null) count++;
        }

        return count;
    }

    private static boolean isPasswordValid(String password) {
        if (password == null || password.length() == 0) return true;

        return password.length() >= 8 && password.length() <= 64;
    }

    private void notifyConnectionStatus(boolean connected) {
        try {
            this.listener.onWifiConnectionStatusChanged(connected);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi connection listener failed | error=" + exception);
        }
    }

    private void notifyProvisioningReady(String ssid, String password, String portalUrl, int networkCount) {
        try {
            this.listener.onProvisioningReady(ssid, password, portalUrl, networkCount);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi provisioning ready listener failed | error=" + exception);
        }
    }

    private void notifyConnecting(String ssid) {
        try {
            this.listener.onConnecting(ssid);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi connecting listener failed | error=" + exception);
        }
    }

    private void notifyConnected(String ssid) {
        try {
            this.listener.onConnected(ssid);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi connected listener failed | error=" + exception);
        }
    }

    private void notifyFailed(String message) {
        try {
            this.listener.onFailed(message);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi failure listener failed | error=" + exception);
        }
    }

    private void setState(int newState) {
        this.state = newState;

        try {
            this.listener.onStateChanged(newState);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "WiFi state listener failed | error=" + exception);
        }
    }
}