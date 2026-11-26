package com.nxp.example.smartgreenhouse.services.mqtt;

import com.nxp.example.smartgreenhouse.services.wifi.FlashWifiCredentialStore;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MQTT broker address configuration.
 *
 * The address is entered on the WiFi provisioning portal (phone browser) and stored in flash
 * together with the WiFi credentials as "host:port". When nothing valid is stored, the
 * built-in default broker is used.
 */
public final class BrokerConfig {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE: BROKER CONFIG]");

    /** Built-in broker used when no broker address has been provisioned. */
    public static final String DEFAULT_BROKER_ADDRESS = "168.110.214.70:1883";

    public static final int DEFAULT_PORT = 1883;
    public static final int MAX_ADDRESS_LENGTH = 64;

    private static final String URI_SCHEME = "tcp://";

    private BrokerConfig() {
    }

    /** Returns the broker URI to connect to, e.g. "tcp://192.168.1.23:1883". */
    public static String loadBrokerUri() {
        String stored = null;

        try {
            stored = normalize(new FlashWifiCredentialStore().loadBrokerAddress());
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Unable to read stored broker address | error=" + exception);
        }

        if (stored == null) {
            LOGGER.log(Level.INFO, "Using default MQTT broker | address=" + DEFAULT_BROKER_ADDRESS);
            return URI_SCHEME + DEFAULT_BROKER_ADDRESS;
        }

        LOGGER.log(Level.INFO, "Using provisioned MQTT broker | address=" + stored);
        return URI_SCHEME + stored;
    }

    /**
     * Returns the optional local/edge broker URI (e.g. "tcp://192.168.1.50:1883"),
     * or null when single-broker mode is used (empty or identical to the primary broker).
     */
    public static String loadEdgeBrokerUri(String primaryBrokerUri) {
        String stored = null;

        try {
            stored = normalize(new FlashWifiCredentialStore().loadEdgeBrokerAddress());
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Unable to read stored edge broker address | error=" + exception);
        }

        if (stored == null) {
            return null;
        }

        String uri = URI_SCHEME + stored;

        if (uri.equalsIgnoreCase(primaryBrokerUri)) {
            return null;
        }

        LOGGER.log(Level.INFO, "Using provisioned edge MQTT broker | address=" + stored);
        return uri;
    }

    /**
     * Normalizes user input to "host:port".
     * Accepts "192.168.1.23", "192.168.1.23:1884", "tcp://192.168.1.23:1883" or a hostname.
     *
     * @return the normalized address, or null when the input is empty or invalid.
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }

        String value = input.trim();

        if (value.length() > URI_SCHEME.length()
                && value.substring(0, URI_SCHEME.length()).equalsIgnoreCase(URI_SCHEME)) {
            value = value.substring(URI_SCHEME.length());
        }

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.length() == 0) {
            return null;
        }

        String host = value;
        int port = DEFAULT_PORT;
        int colon = value.lastIndexOf(':');

        if (colon >= 0) {
            host = value.substring(0, colon);
            port = parsePort(value.substring(colon + 1));

            if (port < 0) {
                return null;
            }
        }

        if (!isValidHost(host)) {
            return null;
        }

        String normalized = host + ":" + port;

        return normalized.length() <= MAX_ADDRESS_LENGTH ? normalized : null;
    }

    private static int parsePort(String text) {
        if (text.length() == 0 || text.length() > 5) {
            return -1;
        }

        int port = 0;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (character < '0' || character > '9') {
                return -1;
            }

            port = port * 10 + (character - '0');
        }

        return (port >= 1 && port <= 65535) ? port : -1;
    }

    private static boolean isValidHost(String host) {
        if (host.length() == 0) {
            return false;
        }

        char first = host.charAt(0);
        char last = host.charAt(host.length() - 1);

        if (first == '.' || first == '-' || last == '.' || last == '-') {
            return false;
        }

        for (int index = 0; index < host.length(); index++) {
            char character = host.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '.'
                    || character == '-';

            if (!allowed) {
                return false;
            }
        }

        return true;
    }
}
