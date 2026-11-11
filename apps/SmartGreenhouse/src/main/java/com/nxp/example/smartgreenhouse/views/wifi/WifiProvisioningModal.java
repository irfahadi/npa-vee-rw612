package com.nxp.example.smartgreenhouse.views.wifi;

import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Fonts;
import com.nxp.example.smartgreenhouse.style.Icons;
import com.nxp.example.smartgreenhouse.style.Images;

import ej.microui.display.Display;
import ej.microui.display.Font;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.mwt.Container;
import ej.mwt.util.Size;
import ej.widget.basic.ImageButton;
import ej.widget.basic.OnClickListener;

public final class WifiProvisioningModal extends Container {

    public interface OnBackListener {
        void onBack();
    }

    private static final int MODE_STARTING = 0;
    private static final int MODE_READY = 1;
    private static final int MODE_CONNECTING = 2;
    private static final int MODE_FAILED = 3;

    private static final int BACK_ICON_SIZE = 24;
    private static final int PADDING_LEFT_ICON = 15;
    private static final int PADDING_TOP_ICON = 3;

    private static final int PADDING_TOP_HEADER = 26;
    private static final int PADDING_TOP_TITLE = 8;
    private static final int TITLE_OFFSET_Y = 1;

    private final Image headerFrame;
    private final Image qrCode;
    private final ImageButton backButton;

    private final Font titleFont;
    private final Font textFont;

    private int mode;

    private String provisioningSsid;
    private String provisioningPassword;
    private String portalUrl;
    private String connectingSsid;

    private int networkCount;

    private OnBackListener backListener;

    public WifiProvisioningModal() {
        this.headerFrame = Image.getImage(Images.HEADER_DETAIL_FRAME);
        this.qrCode = Image.getImage(Images.QR);

        this.titleFont = Fonts.jetbrainsMonoBold12px();
        this.textFont = Fonts.jetbrainsMonoBold10px();

        this.mode = MODE_STARTING;

        this.provisioningSsid = "smartgreenhouse";
        this.provisioningPassword = "smartgreenhouse";
        this.portalUrl = "http://192.168.1.1/";
        this.connectingSsid = "";

        this.networkCount = 0;
        this.backListener = null;

        this.backButton = new ImageButton(Icons.BACK_ICON_24);
        this.backButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick() {
                        if (WifiProvisioningModal.this.backListener != null && WifiProvisioningModal.this.shouldShowBackButton()) {
                            WifiProvisioningModal.this.backListener.onBack();
                        }
                    }
                }
        );

        addChild(this.backButton);
        updateBackButtonState();

        setEnabled(false);
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    public void showStarting() {
        this.mode = MODE_STARTING;
        this.connectingSsid = "";
        updateBackButtonState();
        requestLayOut();
        requestRender();
    }

    public void showReady(String ssid, String password, String portalUrl, int networkCount) {
        this.mode = MODE_READY;
        this.provisioningSsid = ssid == null ? "smartgreenhouse" : ssid;
        this.provisioningPassword = password == null ? "smartgreenhouse" : password;
        this.portalUrl = portalUrl == null ? "http://192.168.4.1/" : portalUrl;
        this.networkCount = networkCount;
        updateBackButtonState();
        requestLayOut();
        requestRender();
    }

    public void showConnecting(String ssid) {
        this.mode = MODE_CONNECTING;
        this.connectingSsid = ssid == null ? "" : ssid;
        updateBackButtonState();
        requestLayOut();
        requestRender();
    }

    public void showFailed() {
        this.mode = MODE_FAILED;
        updateBackButtonState();
        requestLayOut();
        requestRender();
    }

    @Override
    protected void layOutChildren(int contentWidth, int contentHeight) {
        if (shouldShowBackButton()) {
            layOutChild(this.backButton, PADDING_LEFT_ICON, PADDING_TOP_ICON, BACK_ICON_SIZE, BACK_ICON_SIZE);
        } else {
            layOutChild(this.backButton, -BACK_ICON_SIZE, -BACK_ICON_SIZE, BACK_ICON_SIZE, BACK_ICON_SIZE);
        }
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        int displayWidth = Display.getDisplay().getWidth();
        int displayHeight = Display.getDisplay().getHeight();

        computeChildOptimalSize(this.backButton, BACK_ICON_SIZE, BACK_ICON_SIZE);

        size.setSize(displayWidth, displayHeight);
    }

    @Override
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);

        Painter.drawImage(g, this.headerFrame, 0, PADDING_TOP_HEADER);

        drawHeaderTitle(g, contentWidth);

        if (this.mode == MODE_STARTING) {
            renderStarting(g, contentWidth);
        } else if (this.mode == MODE_READY) {
            renderReady(g, contentWidth);
        } else if (this.mode == MODE_CONNECTING) {
            renderConnecting(g, contentWidth);
        } else {
            renderFailed(g, contentWidth);
        }

        super.renderContent(g, contentWidth, contentHeight);
    }

    private void drawHeaderTitle(GraphicsContext g, int contentWidth) {
        String title = "Wi-Fi";
        int titleWidth = this.titleFont.stringWidth(title);
        int titleX = (contentWidth - titleWidth) / 2;
        int titleY = PADDING_TOP_TITLE - TITLE_OFFSET_Y;

        g.setColor(ApplicationColors.PRIMARY_COLOR);
        Painter.drawString(g, title, this.titleFont, titleX, titleY);
    }

    private void renderStarting(GraphicsContext g, int contentWidth) {
        drawCenteredText(g, "Menyiapkan pengaturan Wi-Fi...", contentWidth, 112, ApplicationColors.SECONDARY_COLOR);
        drawCenteredText(g, "Memindai jaringan yang tersedia.", contentWidth, 136, ApplicationColors.SECONDARY_COLOR);
    }

    private void renderReady(GraphicsContext g, int contentWidth) {
        int qrX = 28;
        int qrY = 64;

        Painter.drawImage(g, this.qrCode, qrX, qrY);

        int textX = 178;
        int currentY = 54;
        int lineGap = 18;

        g.setColor(ApplicationColors.SECONDARY_COLOR);

        Painter.drawString(g, "1. Scan QR atau hubungkan manual:", this.textFont, textX, currentY);

        currentY += lineGap;
        Painter.drawString(g, "SSID:", this.textFont, textX, currentY);

        currentY += lineGap;
        Painter.drawString(g, this.provisioningSsid, this.textFont, textX, currentY);

        currentY += lineGap;
        Painter.drawString(g, "Password:", this.textFont, textX, currentY);

        currentY += lineGap;
        Painter.drawString(g, this.provisioningPassword, this.textFont, textX, currentY);

        currentY += lineGap + 4;
        Painter.drawString(g, "2. Buka:", this.textFont, textX, currentY);

        currentY += lineGap;
        Painter.drawString(g, this.portalUrl, this.textFont, textX, currentY);

        currentY += lineGap + 4;
        Painter.drawString(g, "Jaringan ditemukan: " + this.networkCount, this.textFont, textX, currentY);

        drawCenteredText(g, "Gunakan HP untuk melanjutkan pengaturan Wi-Fi.", contentWidth, 238, ApplicationColors.SECONDARY_COLOR);
    }

    private void renderConnecting(GraphicsContext g, int contentWidth) {
        drawCenteredText(g, this.connectingSsid, contentWidth, 110, ApplicationColors.PRIMARY_COLOR);
        drawCenteredText(g, "Connecting...", contentWidth, 138, ApplicationColors.SECONDARY_COLOR);
        drawCenteredText(g, "Mohon tunggu.", contentWidth, 164, ApplicationColors.SECONDARY_COLOR);
    }

    private void renderFailed(GraphicsContext g, int contentWidth) {
        drawCenteredText(g, "Koneksi Wi-Fi gagal.", contentWidth, 110, ApplicationColors.THIRD_COLOR);
        drawCenteredText(g, "Tekan Back untuk kembali.", contentWidth, 140, ApplicationColors.SECONDARY_COLOR);
    }

    private void drawCenteredText(GraphicsContext g, String text, int contentWidth, int y, int color) {
        int textWidth = this.textFont.stringWidth(text);
        int x = (contentWidth - textWidth) / 2;

        g.setColor(color);
        Painter.drawString(g, text, this.textFont, x, y);
    }

    private boolean shouldShowBackButton() {
        return this.mode != MODE_CONNECTING;
    }

    private void updateBackButtonState() {
        this.backButton.setEnabled(shouldShowBackButton());
    }
}