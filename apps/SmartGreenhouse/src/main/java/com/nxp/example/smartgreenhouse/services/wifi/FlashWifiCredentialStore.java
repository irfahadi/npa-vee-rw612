package com.nxp.example.smartgreenhouse.services.wifi;

import com.nxp.example.smartgreenhouse.models.wifi.WifiCredentials;

import java.io.UnsupportedEncodingException;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class FlashWifiCredentialStore implements WifiCredentialStore {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: WIFI CREDENTIAL STORE]");

    private static final int MAX_SAVED_NETWORKS = 3;
    private static final int STORAGE_BUFFER_SIZE = 512;

    private static final int HEADER_SIZE = 8;
    private static final int ENTRY_SIZE = 100;

    private static final int VERSION_OFFSET = 4;
    private static final int COUNT_OFFSET = 5;

    private static final int ENTRY_ACTIVE_OFFSET = 0;
    private static final int ENTRY_SSID_LENGTH_OFFSET = 1;
    private static final int ENTRY_PASSWORD_LENGTH_OFFSET = 2;
    private static final int ENTRY_SSID_OFFSET = 4;

    private static final int MAX_SSID_BYTES = 32;
    private static final int MAX_PASSWORD_BYTES = 64;

    private static final int ENTRY_PASSWORD_OFFSET = ENTRY_SSID_OFFSET + MAX_SSID_BYTES;

    private static final byte MAGIC_0 = 'S';
    private static final byte MAGIC_1 = 'G';
    private static final byte MAGIC_2 = 'W';
    private static final byte MAGIC_3 = 'F';

    private static final int STORAGE_VERSION = 1;

    // Optional MQTT broker block, placed after the space reserved for all WiFi entries so the
    // WiFi entry layout (and older firmware reading it) is unaffected.
    // Layout: [marker 'B'][length][address bytes (max 64)]
    private static final int BROKER_BLOCK_OFFSET = HEADER_SIZE + (MAX_SAVED_NETWORKS * ENTRY_SIZE);
    private static final int BROKER_LENGTH_OFFSET = BROKER_BLOCK_OFFSET + 1;
    private static final int BROKER_DATA_OFFSET = BROKER_BLOCK_OFFSET + 2;
    private static final byte BROKER_MARKER = 'B';
    private static final int MAX_BROKER_BYTES = 64;

    // Optional local/edge MQTT broker block (for 2-broker Store-and-Forward mode).
    // Layout: [marker 'E'][length][address bytes (max 64)] starting at offset 374 (374+2+64 = 440 <= 512).
    private static final int EDGE_BROKER_BLOCK_OFFSET = BROKER_DATA_OFFSET + MAX_BROKER_BYTES;
    private static final int EDGE_BROKER_LENGTH_OFFSET = EDGE_BROKER_BLOCK_OFFSET + 1;
    private static final int EDGE_BROKER_DATA_OFFSET = EDGE_BROKER_BLOCK_OFFSET + 2;
    private static final byte EDGE_BROKER_MARKER = 'E';

    @Override
    public int getMaxSavedNetworks() {
        return MAX_SAVED_NETWORKS;
    }

    @Override
    public synchronized boolean hasCredentials() {
        return load() != null;
    }

    @Override
    public synchronized WifiCredentials load() {
        WifiCredentials[] credentials = readAllInternal();

        if (credentials.length == 0) {
            return null;
        }

        return credentials[0];
    }

    @Override
    public synchronized WifiCredentials[] loadAll() {
        return readAllInternal();
    }

    @Override
    public synchronized void save(WifiCredentials credentials) {
        if (credentials == null) {
            throw new NullPointerException("credentials tidak boleh null.");
        }

        validateCredentials(credentials);

        WifiCredentials[] current = readAllInternal();
        WifiCredentials[] next = new WifiCredentials[MAX_SAVED_NETWORKS];

        int count = 0;
        next[count++] = credentials;

        for (int i = 0; i < current.length && count < MAX_SAVED_NETWORKS; i++) {
            if (!current[i].getSsid().equals(credentials.getSsid())) {
                next[count++] = current[i];
            }
        }

        WifiCredentials[] stored = new WifiCredentials[count];
        System.arraycopy(next, 0, stored, 0, count);

        persist(stored, readBrokerInternal(), readEdgeBrokerInternal());

        LOGGER.log(Level.INFO, "WiFi credentials saved | SSID=" + credentials.getSsid() + " | stored=" + count);
    }

    @Override
    public synchronized void clear() {
        persist(new WifiCredentials[0], readBrokerInternal(), readEdgeBrokerInternal());
        LOGGER.log(Level.INFO, "Stored WiFi credentials cleared");
    }

    @Override
    public synchronized String loadBrokerAddress() {
        return readBrokerInternal();
    }

    @Override
    public synchronized void saveBrokerAddress(String brokerAddress) {
        String address = brokerAddress == null ? "" : brokerAddress.trim();

        if (encodeUtf8(address).length > MAX_BROKER_BYTES) {
            throw new IllegalArgumentException("Alamat broker maksimal 64 byte.");
        }

        persist(readAllInternal(), address.length() == 0 ? null : address, readEdgeBrokerInternal());

        LOGGER.log(Level.INFO, "MQTT broker address saved | broker=" + (address.length() == 0 ? "(default)" : address));
    }

    @Override
    public synchronized String loadEdgeBrokerAddress() {
        return readEdgeBrokerInternal();
    }

    @Override
    public synchronized void saveEdgeBrokerAddress(String edgeBrokerAddress) {
        String address = edgeBrokerAddress == null ? "" : edgeBrokerAddress.trim();

        if (encodeUtf8(address).length > MAX_BROKER_BYTES) {
            throw new IllegalArgumentException("Alamat edge broker maksimal 64 byte.");
        }

        persist(readAllInternal(), readBrokerInternal(), address.length() == 0 ? null : address);

        LOGGER.log(Level.INFO, "Edge MQTT broker address saved | edgeBroker=" + (address.length() == 0 ? "(none)" : address));
    }

    private String readBrokerInternal() {
        return readBlockInternal(BROKER_BLOCK_OFFSET, BROKER_LENGTH_OFFSET, BROKER_DATA_OFFSET, BROKER_MARKER);
    }

    private String readEdgeBrokerInternal() {
        return readBlockInternal(EDGE_BROKER_BLOCK_OFFSET, EDGE_BROKER_LENGTH_OFFSET, EDGE_BROKER_DATA_OFFSET, EDGE_BROKER_MARKER);
    }

    private String readBlockInternal(int blockOffset, int lengthOffset, int dataOffset, byte expectedMarker) {
        byte[] storage = new byte[STORAGE_BUFFER_SIZE];
        int storedLength = WifiCredentialNative.readNative(storage);

        if (storedLength < dataOffset || !isHeaderValid(storage)) {
            return null;
        }

        if (storage[blockOffset] != expectedMarker) {
            return null;
        }

        int length = unsigned(storage[lengthOffset]);

        if (length <= 0 || length > MAX_BROKER_BYTES || dataOffset + length > storedLength) {
            return null;
        }

        byte[] addressBytes = new byte[length];
        System.arraycopy(storage, dataOffset, addressBytes, 0, length);

        try {
            return new String(addressBytes, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 tidak tersedia: " + exception);
        }
    }

    private WifiCredentials[] readAllInternal() {
        byte[] storage = new byte[STORAGE_BUFFER_SIZE];
        int storedLength = WifiCredentialNative.readNative(storage);

        if (storedLength < HEADER_SIZE) {
            return new WifiCredentials[0];
        }

        if (!isHeaderValid(storage)) {
            LOGGER.log(Level.WARNING, "Stored WiFi credential header is invalid");
            return new WifiCredentials[0];
        }

        int count = unsigned(storage[COUNT_OFFSET]);

        if (count < 0 || count > MAX_SAVED_NETWORKS) {
            LOGGER.log(Level.WARNING, "Stored WiFi credential count is invalid");
            return new WifiCredentials[0];
        }

        WifiCredentials[] temporary = new WifiCredentials[count];
        int loadedCount = 0;

        for (int i = 0; i < count; i++) {
            int baseOffset = HEADER_SIZE + (i * ENTRY_SIZE);

            if (baseOffset + ENTRY_SIZE > storedLength) {
                LOGGER.log(Level.WARNING, "Stored WiFi credential data is incomplete");
                return new WifiCredentials[0];
            }

            if (unsigned(storage[baseOffset + ENTRY_ACTIVE_OFFSET]) != 1) {
                continue;
            }

            int ssidLength = unsigned(storage[baseOffset + ENTRY_SSID_LENGTH_OFFSET]);
            int passwordLength = unsigned(storage[baseOffset + ENTRY_PASSWORD_LENGTH_OFFSET]);

            if (ssidLength <= 0 || ssidLength > MAX_SSID_BYTES) {
                LOGGER.log(Level.WARNING, "Stored WiFi SSID length is invalid");
                return new WifiCredentials[0];
            }

            if (passwordLength < 0 || passwordLength > MAX_PASSWORD_BYTES) {
                LOGGER.log(Level.WARNING, "Stored WiFi password length is invalid");
                return new WifiCredentials[0];
            }

            byte[] ssidBytes = new byte[ssidLength];
            byte[] passwordBytes = new byte[passwordLength];

            System.arraycopy(storage, baseOffset + ENTRY_SSID_OFFSET, ssidBytes, 0, ssidLength);

            if (passwordLength > 0) {
                System.arraycopy(storage, baseOffset + ENTRY_PASSWORD_OFFSET, passwordBytes, 0, passwordLength);
            }

            try {
                String ssid = new String(ssidBytes, "UTF-8");
                String password = new String(passwordBytes, "UTF-8");
                temporary[loadedCount++] = new WifiCredentials(ssid, password);
            } catch (UnsupportedEncodingException exception) {
                throw new IllegalStateException("UTF-8 tidak tersedia: " + exception);
            }
        }

        WifiCredentials[] result = new WifiCredentials[loadedCount];

        if (loadedCount > 0) {
            System.arraycopy(temporary, 0, result, 0, loadedCount);
        }

        return result;
    }

    private void persist(WifiCredentials[] credentials, String brokerAddress, String edgeBrokerAddress) {
        byte[] storage = new byte[STORAGE_BUFFER_SIZE];

        storage[0] = MAGIC_0;
        storage[1] = MAGIC_1;
        storage[2] = MAGIC_2;
        storage[3] = MAGIC_3;
        storage[VERSION_OFFSET] = (byte) STORAGE_VERSION;
        storage[COUNT_OFFSET] = (byte) credentials.length;

        for (int i = 0; i < credentials.length; i++) {
            writeEntry(storage, i, credentials[i]);
        }

        int length = HEADER_SIZE + (credentials.length * ENTRY_SIZE);

        if (brokerAddress != null && brokerAddress.length() > 0) {
            byte[] brokerBytes = encodeUtf8(brokerAddress);

            storage[BROKER_BLOCK_OFFSET] = BROKER_MARKER;
            storage[BROKER_LENGTH_OFFSET] = (byte) brokerBytes.length;
            System.arraycopy(brokerBytes, 0, storage, BROKER_DATA_OFFSET, brokerBytes.length);
            length = BROKER_DATA_OFFSET + brokerBytes.length;
        }

        if (edgeBrokerAddress != null && edgeBrokerAddress.length() > 0) {
            byte[] edgeBytes = encodeUtf8(edgeBrokerAddress);

            storage[EDGE_BROKER_BLOCK_OFFSET] = EDGE_BROKER_MARKER;
            storage[EDGE_BROKER_LENGTH_OFFSET] = (byte) edgeBytes.length;
            System.arraycopy(edgeBytes, 0, storage, EDGE_BROKER_DATA_OFFSET, edgeBytes.length);
            length = EDGE_BROKER_DATA_OFFSET + edgeBytes.length;
        }

        if (!WifiCredentialNative.writeNative(storage, length)) {
            throw new IllegalStateException("Gagal menyimpan credential Wi-Fi ke flash.");
        }
    }

    private void writeEntry(byte[] storage, int index, WifiCredentials credentials) {
        byte[] ssidBytes = encodeUtf8(credentials.getSsid());
        byte[] passwordBytes = encodeUtf8(credentials.getPassword());

        int baseOffset = HEADER_SIZE + (index * ENTRY_SIZE);

        storage[baseOffset + ENTRY_ACTIVE_OFFSET] = 1;
        storage[baseOffset + ENTRY_SSID_LENGTH_OFFSET] = (byte) ssidBytes.length;
        storage[baseOffset + ENTRY_PASSWORD_LENGTH_OFFSET] = (byte) passwordBytes.length;

        System.arraycopy(ssidBytes, 0, storage, baseOffset + ENTRY_SSID_OFFSET, ssidBytes.length);

        if (passwordBytes.length > 0) {
            System.arraycopy(passwordBytes, 0, storage, baseOffset + ENTRY_PASSWORD_OFFSET, passwordBytes.length);
        }
    }

    private void validateCredentials(WifiCredentials credentials) {
        byte[] ssidBytes = encodeUtf8(credentials.getSsid());
        byte[] passwordBytes = encodeUtf8(credentials.getPassword());

        if (ssidBytes.length == 0 || ssidBytes.length > MAX_SSID_BYTES) {
            throw new IllegalArgumentException("SSID Wi-Fi harus berukuran 1 sampai 32 byte.");
        }

        if (passwordBytes.length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password Wi-Fi maksimal 64 byte.");
        }
    }

    private byte[] encodeUtf8(String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 tidak tersedia: " + exception);
        }
    }

    private boolean isHeaderValid(byte[] storage) {
        return storage[0] == MAGIC_0
                && storage[1] == MAGIC_1
                && storage[2] == MAGIC_2
                && storage[3] == MAGIC_3
                && unsigned(storage[VERSION_OFFSET]) == STORAGE_VERSION;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }
}