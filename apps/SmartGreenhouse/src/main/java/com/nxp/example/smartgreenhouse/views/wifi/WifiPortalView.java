package com.nxp.example.smartgreenhouse.views.wifi;

import ej.ecom.wifi.AccessPoint;

public final class WifiPortalView {

    private WifiPortalView() {
    }

    public static String buildHomePage() {
        StringBuilder html = new StringBuilder(3072);

        appendHeader(html, "Smart Greenhouse Wi-Fi");

        html.append("<div class='container'>");
        html.append("<h1>Smart Greenhouse</h1>");
        html.append("<p class='description'>Hubungkan perangkat Smart Greenhouse ke jaringan Wi-Fi.</p>");
        html.append("<a class='button' href='/scan'>Scan Wi-Fi</a>");
        html.append("<div class='note'>Tekan Scan Wi-Fi untuk menampilkan jaringan yang tersedia di sekitar perangkat.</div>");
        html.append("</div>");

        appendFooter(html);

        return html.toString();
    }

    public static String buildNetworkListPage(AccessPoint[] accessPoints) {
        StringBuilder html = new StringBuilder(6144);

        appendHeader(html, "Pilih Jaringan Wi-Fi");

        html.append("<div class='container'>");
        html.append("<h1>Pilih Wi-Fi</h1>");
        html.append("<p class='description'>Pilih jaringan yang akan digunakan oleh Smart Greenhouse.</p>");

        int networkCount = countAccessPoints(accessPoints);

        if (networkCount == 0) {
            html.append("<div class='message warning'>Tidak ada jaringan Wi-Fi yang ditemukan.</div>");
            html.append("<a class='button' href='/scan'>Scan Ulang</a>");
        } else {
            for (int index = 0; index < accessPoints.length; index++) {
                AccessPoint accessPoint = accessPoints[index];

                if (accessPoint == null) {
                    continue;
                }

                String ssid = escapeHtml(accessPoint.getSSID());

                html.append("<form method='post' action='/network'>");
                html.append("<input type='hidden' name='ssid' value='");
                html.append(ssid);
                html.append("'>");
                html.append("<button class='network' type='submit'>");
                html.append("<span class='ssid'>");
                html.append(ssid);
                html.append("</span>");
                html.append("<span class='rssi'>Signal: ");
                html.append(accessPoint.getRSSI());
                html.append(" dBm</span>");
                html.append("</button>");
                html.append("</form>");
            }

            html.append("<a class='button secondary' href='/scan'>Scan Ulang</a>");
        }

        html.append("</div>");

        appendFooter(html);

        return html.toString();
    }

    public static String buildPasswordPage(String ssid, float rssi) {
        String safeSsid = escapeHtml(ssid);
        StringBuilder html = new StringBuilder(4096);

        appendHeader(html, "Hubungkan Wi-Fi");

        html.append("<div class='container'>");
        html.append("<h1>Connect to ");
        html.append(safeSsid);
        html.append("</h1>");
        html.append("<p class='description'>Signal: ");
        html.append(rssi);
        html.append(" dBm</p>");
        html.append("<form method='post' action='/connect'>");
        html.append("<input type='hidden' name='ssid' value='");
        html.append(safeSsid);
        html.append("'>");
        html.append("<label class='field-label' for='password'>Password</label>");
        html.append("<input class='password' id='password' name='password' type='password' maxlength='64' placeholder='Masukkan password Wi-Fi'>");
        html.append("<button class='button' type='submit'>Connect</button>");
        html.append("</form>");
        html.append("<a class='back' href='/scan'>Kembali</a>");
        html.append("</div>");

        appendFooter(html);

        return html.toString();
    }

    public static String buildConnectingPage(String ssid) {
        String safeSsid = escapeHtml(ssid);
        StringBuilder html = new StringBuilder(3072);

        appendHeader(html, "Connecting");

        html.append("<div class='container center'>");
        html.append("<h1>Wi-Fi</h1>");
        html.append("<div class='wifi-icon'>&#128246;</div>");
        html.append("<h2>");
        html.append(safeSsid);
        html.append("</h2>");
        html.append("<div class='message success'>Connecting...</div>");
        html.append("<p class='description'>Hotspot Smart Greenhouse akan ditutup dan perangkat akan mencoba terhubung ke jaringan yang dipilih.</p>");
        html.append("</div>");

        appendFooter(html);

        return html.toString();
    }

    public static String buildErrorPage(String title, String message) {
        StringBuilder html = new StringBuilder(3072);

        appendHeader(html, title);

        html.append("<div class='container'>");
        html.append("<h1>");
        html.append(escapeHtml(title));
        html.append("</h1>");
        html.append("<div class='message warning'>");
        html.append(escapeHtml(message));
        html.append("</div>");
        html.append("<a class='button' href='/scan'>Pilih Jaringan</a>");
        html.append("</div>");

        appendFooter(html);

        return html.toString();
    }

    private static void appendHeader(StringBuilder html, String title) {
        html.append("<!DOCTYPE html>");
        html.append("<html lang='id'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>");
        html.append(escapeHtml(title));
        html.append("</title>");
        html.append("<style>");
        html.append("body{margin:0;padding:20px;font-family:Arial,sans-serif;background:#f3f6f4;color:#1f2933;}");
        html.append(".container{max-width:520px;margin:0 auto;background:#fff;padding:24px;border-radius:16px;box-shadow:0 4px 16px rgba(0,0,0,.10);}");
        html.append(".center{text-align:center;}");
        html.append("h1{margin:0 0 10px;font-size:24px;color:#166534;}");
        html.append("h2{margin:10px 0 16px;font-size:20px;}");
        html.append(".description{color:#52606d;line-height:1.5;margin:0 0 20px;}");
        html.append(".button{display:block;width:100%;box-sizing:border-box;margin-top:16px;padding:14px;border:0;border-radius:10px;background:#166534;color:#fff;text-align:center;text-decoration:none;font-size:16px;font-weight:bold;cursor:pointer;}");
        html.append(".secondary{background:#52606d;}");
        html.append(".network{display:block;width:100%;box-sizing:border-box;margin-bottom:10px;padding:14px;border:1px solid #d9e2dc;border-radius:10px;background:#f9fbfa;text-align:left;cursor:pointer;}");
        html.append(".ssid{display:block;font-size:16px;font-weight:bold;color:#1f2933;word-break:break-word;}");
        html.append(".rssi{display:block;margin-top:5px;font-size:13px;color:#66788a;}");
        html.append(".field-label{display:block;margin-bottom:8px;font-weight:bold;}");
        html.append(".password{width:100%;box-sizing:border-box;padding:13px;border:1px solid #b8c4bc;border-radius:9px;font-size:16px;}");
        html.append(".message{padding:16px;border-radius:10px;line-height:1.5;}");
        html.append(".success{background:#e8f5e9;color:#166534;}");
        html.append(".warning{background:#fff4e5;color:#92400e;}");
        html.append(".note{margin-top:20px;padding:12px;border-radius:8px;background:#e8f5e9;color:#245b2a;font-size:13px;line-height:1.5;}");
        html.append(".back{display:inline-block;margin-top:20px;color:#166534;text-decoration:none;font-weight:bold;}");
        html.append(".wifi-icon{font-size:52px;margin:20px 0 10px;}");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
    }

    private static void appendFooter(StringBuilder html) {
        html.append("</body>");
        html.append("</html>");
    }

    private static int countAccessPoints(AccessPoint[] accessPoints) {
        if (accessPoints == null) {
            return 0;
        }

        int count = 0;

        for (int index = 0; index < accessPoints.length; index++) {
            if (accessPoints[index] != null) {
                count++;
            }
        }

        return count;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(value.length() + 16);

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            switch (character) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(character);
                    break;
            }
        }

        return escaped.toString();
    }
}