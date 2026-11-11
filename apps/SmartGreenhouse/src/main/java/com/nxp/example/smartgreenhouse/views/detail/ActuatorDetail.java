package com.nxp.example.smartgreenhouse.views.detail;

import com.nxp.example.smartgreenhouse.models.actuator.ActuatorDisplayItem;
import com.nxp.example.smartgreenhouse.style.ApplicationColors;
import com.nxp.example.smartgreenhouse.style.Icons;
import com.nxp.example.smartgreenhouse.views.HorizontalSwipeListener;
import com.nxp.example.smartgreenhouse.views.actuator.ActuatorToggle;

import com.nxp.example.smartgreenhouse.views.actuator.ActuatorToggleListener;
import ej.microui.display.Display;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Image;
import ej.microui.display.Painter;
import ej.microui.event.Event;
import ej.microui.event.generator.Buttons;
import ej.microui.event.generator.Pointer;
import ej.mwt.Container;
import ej.mwt.Widget;
import ej.mwt.util.Size;
import ej.widget.basic.ImageButton;
import ej.widget.basic.OnClickListener;

public class ActuatorDetail extends Container {

    public interface onBackListener {
        void onBack();
    }
    
    private static final int BACK_ICON_SIZE = 24;

    private static final int PADDING_TOP_HEADER = 26;
    private static final int PADDING_LEFT_ICON = 15;
    private static final int PADDING_TOP_ICON = 3;

    private static final int PADDING_TOP_TITLE = 8;
    private static final int TITLE_OFFSET_Y = 1;

    private static final int PADDING_TOP_TO_HEADER = 5;

    private static final int PADDING_LEFT_TO_BODY_FRAME = 16;
    private static final int PADDING_RIGHT_TO_BODY_FRAME = 16;
    private static final int PADDING_TOP_FRAME_TO_BODY_FRAME = 11;

    private static final int GAP_STATUS_FRAME = 10;
    private static final int STATUS_RIGHT_PADDING = 12;
    private static final int STATUS_LABEL_PADDING_LEFT = 12;

    private static final int TOGGLE_WIDTH = 43;
    private static final int TOGGLE_HEIGHT = 18;

    private static final int CONTROL_RIGHT_PADDING = 12;
    private static final int CONTROL_TEXT_TO_TOGGLE_GAP = 8;

    private static final int MIDDLE_INFO_TOP_GAP = 8;
    private static final int MIDDLE_INFO_TEXT_GAP = 7;

    private static final int PUMP_TIMER_ICON_OFFSET_X = 1;
    private static final int PUMP_TIMER_ICON_OFFSET_Y = 8;
    private static final int PUMP_TEXT_OFFSET_X = 40;

    private static final int VALVE_STATE_OFFSET_X = 4;
    private static final int VALVE_DIVIDER_OFFSET_X = 70;
    private static final int VALVE_FLOW_OFFSET_X = 94;

    private static final int VALVE_LABEL_TO_VALUE_GAP = 1;

    private static final int PADDING_TOP_ACTIVATION_TO_BODY_FRAME = 10;

    private static final int ACTIVATION_ICON_OFFSET_X = 10;
    private static final int ACTIVATION_TEXT_OFFSET_X = 66;

    private static final int CONDITION_TITLE_TO_VALUE_GAP = 4;

    private static final int PADDING_TOP_UPDATE_TO_ACTIVATION = 8;
    private static final int UPDATE_HORIZONTAL_PADDING = 16;

    private static final int DOT_TOP_GAP = 4;
    private static final int DOT_HORIZONTAL_GAP = 3;

    private static final int SWIPE_HORIZONTAL_THRESHOLD = 60;
    private static final int SWIPE_VERTICAL_TOLERANCE = 40;

    private final ActuatorDetailImages images;
    private final ActuatorDetailFonts fonts;

    private final ImageButton backButton;
    private final ActuatorToggle actuatorToggle;

    private onBackListener onBackListener;
    private HorizontalSwipeListener onSwipeListener;

    private boolean swipeTracking;
    private int swipeStartX;
    private int swipeStartY;

    private String detailTitle;
    private ActuatorDisplayItem actuatorItem;

    public ActuatorDetail() {
        this.images = new ActuatorDetailImages();
        this.fonts = new ActuatorDetailFonts();

        this.detailTitle = "";

        this.actuatorItem = null;
        this.onSwipeListener = null;
        this.swipeTracking = false;
        this.swipeStartX = 0;
        this.swipeStartY = 0;

        this.actuatorToggle = new ActuatorToggle();

        this.backButton = new ImageButton(Icons.BACK_ICON_24);
        this.backButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick() {
                        if (ActuatorDetail.this.onBackListener != null) {
                            ActuatorDetail.this.onBackListener.onBack();
                        }
                    }
                }
        );

        addChild(this.actuatorToggle);
        addChild(this.backButton);
    }

    public void setOnBackListener(onBackListener listener) {
        this.onBackListener = listener;
    }

    public void setOnToggleRequestedListener(ActuatorToggleListener listener) {
        this.actuatorToggle.setOnToggleRequestedListener(listener);
    }

    public void setOnSwipeListener(HorizontalSwipeListener listener) {
        this.onSwipeListener = listener;
    }

    public void setActuatorItem(ActuatorDisplayItem actuatorItem) {
        this.actuatorItem = actuatorItem;
        this.actuatorToggle.setChecked(actuatorItem != null && actuatorItem.isDataAvailable() && actuatorItem.isActive());

        requestLayOut();
        requestRender();
    }

    public void setDetailTitle(String detailTitle) {
        this.detailTitle = detailTitle == null ? "" : detailTitle;
        requestRender();
    }

    private boolean isPumpMode() {
        return this.actuatorItem != null && this.actuatorItem.isPump();
    }

    private int getBodyFrameX(int contentWidth) {
        return (contentWidth - this.images.bodyFrame.getWidth()) / 2;
    }

    private int getBodyFrameY() {
        return PADDING_TOP_HEADER + this.images.headerFrame.getHeight() + PADDING_TOP_TO_HEADER;
    }

    private int getActuatorFrameX(int contentWidth) {
        return getBodyFrameX(contentWidth) + PADDING_LEFT_TO_BODY_FRAME;
    }

    private int getActuatorFrameY() {
        return getBodyFrameY() + PADDING_TOP_FRAME_TO_BODY_FRAME;
    }

    private int getStatusFrameX(int contentWidth) {
        return getBodyFrameX(contentWidth) + this.images.bodyFrame.getWidth() - PADDING_RIGHT_TO_BODY_FRAME - this.images.actuatorStatusFrame.getWidth();
    }

    private int getStatusFrameY() {
        return getActuatorFrameY();
    }

    private int getControlFrameY() {
        return getStatusFrameY() + this.images.actuatorStatusFrame.getHeight() + GAP_STATUS_FRAME;
    }

    private int getMiddleInfoY() {
        return getControlFrameY() + this.images.actuatorStatusFrame.getHeight() + MIDDLE_INFO_TOP_GAP;
    }

    private int getActivationFrameY() {
        return getBodyFrameY() + this.images.bodyFrame.getHeight() + PADDING_TOP_ACTIVATION_TO_BODY_FRAME;
    }

    private int getUpdateFrameY() {
        return getActivationFrameY() + this.images.actuatorActivationFrame.getHeight() + PADDING_TOP_UPDATE_TO_ACTIVATION;
    }

    private String getControlLeftText() {
        return isPumpMode() ? "MATI" : "OFF";
    }

    private String getControlRightText() {
        return isPumpMode() ? "NYALA" : "ON";
    }

    private int getToggleX(int contentWidth) {
        int statusFrameX = getStatusFrameX(contentWidth);
        int rightTextWidth = this.fonts.controlOptionFont.stringWidth(getControlRightText());
        int rightTextX = statusFrameX + this.images.actuatorStatusFrame.getWidth() - CONTROL_RIGHT_PADDING - rightTextWidth;

        return rightTextX - CONTROL_TEXT_TO_TOGGLE_GAP - TOGGLE_WIDTH;
    }

    private int getToggleY() {
        return getControlFrameY() + (this.images.actuatorStatusFrame.getHeight() - TOGGLE_HEIGHT) / 2;
    }

    private boolean isInsideWidget(Widget widget, int pointerX, int pointerY) {
        return pointerX >= widget.getX() && pointerX < widget.getX() + widget.getWidth() && pointerY >= widget.getY() && pointerY < widget.getY() + widget.getHeight();
    }

    @Override
    public boolean handleEvent(int event) {
        if (this.actuatorItem == null || !this.actuatorItem.isValve()) {
            return super.handleEvent(event);
        }

        if (Event.getType(event) != Pointer.EVENT_TYPE) {
            return super.handleEvent(event);
        }

        Pointer pointer = (Pointer) Event.getGenerator(event);
        int action = Buttons.getAction(event);

        int pointerX = pointer.getX();
        int pointerY = pointer.getY();

        if (action == Buttons.PRESSED) {
            if (isInsideWidget(this.backButton, pointerX, pointerY) || isInsideWidget(this.actuatorToggle, pointerX, pointerY)) {
                this.swipeTracking = false;
                return super.handleEvent(event);
            }

            this.swipeTracking = true;
            this.swipeStartX = pointerX;
            this.swipeStartY = pointerY;

            return true;
        }

        if (action == Pointer.DRAGGED) {
            return this.swipeTracking;
        }

        if (action == Buttons.RELEASED) {
            if (!this.swipeTracking) {
                return super.handleEvent(event);
            }

            this.swipeTracking = false;

            int deltaX = pointerX - this.swipeStartX;
            int deltaY = pointerY - this.swipeStartY;

            int horizontalDistance = Math.abs(deltaX);
            int verticalDistance = Math.abs(deltaY);

            boolean validHorizontalSwipe = horizontalDistance >= SWIPE_HORIZONTAL_THRESHOLD && verticalDistance <= SWIPE_VERTICAL_TOLERANCE && horizontalDistance > verticalDistance;

            if (!validHorizontalSwipe) {
                return true;
            }

            if (this.onSwipeListener == null) {
                return true;
            }

            if (deltaX < 0) {
                this.onSwipeListener.onSwipeLeft();
            } else {
                this.onSwipeListener.onSwipeRight();
            }

            return true;
        }

        return super.handleEvent(event);
    }

    @Override
    protected void layOutChildren(int contentWidth, int contentHeight) {
        layOutChild(this.backButton, PADDING_LEFT_ICON, PADDING_TOP_ICON, BACK_ICON_SIZE, BACK_ICON_SIZE);
        layOutChild(this.actuatorToggle, getToggleX(contentWidth), getToggleY(), TOGGLE_WIDTH, TOGGLE_HEIGHT);
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        int displayWidth = Display.getDisplay().getWidth();
        int displayHeight = Display.getDisplay().getHeight();

        computeChildOptimalSize(this.backButton, BACK_ICON_SIZE, BACK_ICON_SIZE);
        computeChildOptimalSize(this.actuatorToggle, TOGGLE_WIDTH, TOGGLE_HEIGHT);

        size.setSize(displayWidth, displayHeight);
    }

    @Override
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        g.setColor(ApplicationColors.BACKGROUND);
        Painter.fillRectangle(g, 0, 0, contentWidth, contentHeight);


        Painter.drawImage(g, this.images.headerFrame, 0, PADDING_TOP_HEADER);

        int bodyFrameX = getBodyFrameX(contentWidth);
        int bodyFrameY = getBodyFrameY();
        Painter.drawImage(g, this.images.bodyFrame, bodyFrameX, bodyFrameY);

        int actuatorFrameX = getActuatorFrameX(contentWidth);
        int actuatorFrameY = getActuatorFrameY();
        Painter.drawImage(g, this.images.actuatorFrame, actuatorFrameX, actuatorFrameY);

        drawActuatorIcon(g, actuatorFrameX, actuatorFrameY);

        int statusFrameX = getStatusFrameX(contentWidth);
        int statusFrameY = getStatusFrameY();
        Painter.drawImage(g, this.images.actuatorStatusFrame, statusFrameX, statusFrameY);

        int controlFrameY = getControlFrameY();
        Painter.drawImage(g, this.images.actuatorStatusFrame, statusFrameX, controlFrameY);

        drawDetailTitle(g, contentWidth);
        drawStatusSection(g, statusFrameX, statusFrameY);
        drawControlSection(g, contentWidth, statusFrameX, controlFrameY);
        drawMiddleInformation(g, statusFrameX, getMiddleInfoY());
        drawActivationCards(g, contentWidth);
        drawLastUpdateFrame(g, contentWidth);
        drawTrayIndicators(g, contentWidth);

        super.renderContent(g, contentWidth, contentHeight);
    }

    private void drawDetailTitle(GraphicsContext g, int contentWidth) {
        if (this.detailTitle.isEmpty()) {
            return;
        }

        int titleWidth = this.fonts.detailTitleFont.stringWidth(this.detailTitle);
        int titleX = (contentWidth - titleWidth) / 2;
        int titleY = PADDING_TOP_TITLE - TITLE_OFFSET_Y;

        g.setColor(ApplicationColors.PRIMARY_COLOR);
        Painter.drawString(g, this.detailTitle, this.fonts.detailTitleFont, titleX, titleY);
    }

    private void drawActuatorIcon(GraphicsContext g, int frameX, int frameY) {
        Image actuatorIcon = isPumpMode() ? this.images.pumpIcon : this.images.valveIcon;
        int iconX = frameX + (this.images.actuatorFrame.getWidth() - actuatorIcon.getWidth()) / 2;
        int iconY = frameY + (this.images.actuatorFrame.getHeight() - actuatorIcon.getHeight()) / 2;
        Painter.drawImage(g, actuatorIcon, iconX, iconY);
    }

    private void drawStatusSection(GraphicsContext g, int frameX, int frameY) {
        int labelY = frameY + (this.images.actuatorStatusFrame.getHeight() - this.fonts.statusControlFont.getHeight()) / 2;
        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, "STATUS", this.fonts.statusControlFont, frameX + STATUS_LABEL_PADDING_LEFT, labelY);

        boolean dataAvailable = this.actuatorItem != null && this.actuatorItem.isDataAvailable();
        boolean activeStatus = dataAvailable && this.actuatorItem.isActive();

        String statusText;

        if (!dataAvailable) {
            statusText = "-";
        } else {
            statusText = activeStatus ? "NYALA" : "MATI";
        }

        Image statusFrame = activeStatus ? this.images.statusOptimalFrame : this.images.statusBahayaFrame;
        int statusTextColor;

        if (!dataAvailable) {
            statusTextColor = ApplicationColors.SECONDARY_COLOR;
        } else {
            statusTextColor = activeStatus ? ApplicationColors.GREEN : ApplicationColors.RED;
        }

        int statusFrameX = frameX + this.images.actuatorStatusFrame.getWidth() - STATUS_RIGHT_PADDING - statusFrame.getWidth();
        int statusFrameY = frameY + (this.images.actuatorStatusFrame.getHeight() - statusFrame.getHeight()) / 2;

        Painter.drawImage(g, statusFrame, statusFrameX, statusFrameY);
        int statusTextWidth = this.fonts.statusAlertFont.stringWidth(statusText);
        int statusTextX = statusFrameX + (statusFrame.getWidth() - statusTextWidth) / 2 + 1;
        int statusTextY = statusFrameY + (statusFrame.getHeight() - this.fonts.statusAlertFont.getHeight()) / 2;

        g.setColor(statusTextColor);
        Painter.drawString(g, statusText, this.fonts.statusAlertFont, statusTextX, statusTextY);
    }

    private void drawControlSection(GraphicsContext g, int contentWidth, int frameX, int frameY) {
        int textY = frameY + (this.images.actuatorStatusFrame.getHeight() - this.fonts.controlOptionFont.getHeight()) / 2;

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, "KONTROL", this.fonts.statusControlFont, frameX + STATUS_LABEL_PADDING_LEFT, textY);

        String leftText = getControlLeftText();
        int toggleX = getToggleX(contentWidth);

        int leftTextWidth = this.fonts.controlOptionFont.stringWidth(leftText);
        int leftTextX = toggleX - CONTROL_TEXT_TO_TOGGLE_GAP - leftTextWidth;
        Painter.drawString(g, leftText, this.fonts.controlOptionFont, leftTextX, textY);

        Painter.drawString(g, getControlRightText(), this.fonts.controlOptionFont, toggleX + TOGGLE_WIDTH + CONTROL_TEXT_TO_TOGGLE_GAP, textY);
    }

    private void drawMiddleInformation(GraphicsContext g, int frameX, int frameY) {
        Painter.drawImage(g, this.images.divideHorizontal, frameX, frameY);

        if (isPumpMode()) {
            drawPumpInformation(g, frameX, frameY);
        } else {
            drawValveInformation(g, frameX, frameY);
        }
    }

    private void drawPumpInformation(GraphicsContext g, int frameX, int frameY) {
        int iconX = frameX + PUMP_TIMER_ICON_OFFSET_X;
        int iconY = frameY + PUMP_TIMER_ICON_OFFSET_Y;
        Painter.drawImage(g, this.images.timerIcon, iconX, iconY);

        int textX = frameX + PUMP_TEXT_OFFSET_X;
        int titleY = frameY + 7;
        g.setColor(ApplicationColors.PRIMARY_COLOR);
        Painter.drawString(g, "LAMA POMPA AKTIF", this.fonts.pumpInfoFont, textX, titleY);

        int durationY = titleY + this.fonts.pumpInfoFont.getHeight() + MIDDLE_INFO_TEXT_GAP;
        g.setColor(ApplicationColors.SECONDARY_COLOR);

        Painter.drawString(g, "DURASI", this.fonts.pumpInfoFont, textX, durationY);

        String durationText = this.actuatorItem == null ? "-" : this.actuatorItem.getDurationText();
        Painter.drawString(g, durationText, this.fonts.durationValueFont, textX + 52, durationY - 2);
    }

    private void drawValveInformation(GraphicsContext g, int frameX, int frameY) {
        String labelText = "ALIRAN AIR";
        String valueText = "SELALU AKTIF";

        int frameWidth = this.images.actuatorStatusFrame.getWidth();

        int labelY = frameY + 7;

        int valueY = labelY + this.fonts.valveLabelFont.getHeight() + VALVE_LABEL_TO_VALUE_GAP;

        int labelWidth = this.fonts.valveLabelFont.stringWidth(labelText);

        int labelX = frameX + (frameWidth - labelWidth) / 2;

        int valueWidth =
                this.fonts.valveValueFont.stringWidth(valueText);
        int valueX = frameX + (frameWidth - valueWidth) / 2;

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, labelText, this.fonts.valveLabelFont, labelX, labelY);

        g.setColor(ApplicationColors.GREEN);
        Painter.drawString(g, valueText, this.fonts.valveValueFont, valueX, valueY);
    }

    private void drawActivationCards(GraphicsContext g, int contentWidth) {
        int bodyFrameX = getBodyFrameX(contentWidth);
        int activationFrameY = getActivationFrameY();

        int rightFrameX = bodyFrameX + this.images.bodyFrame.getWidth() - this.images.actuatorActivationFrame.getWidth();
        Painter.drawImage(g, this.images.actuatorActivationFrame, bodyFrameX, activationFrameY);

        Painter.drawImage(g, this.images.actuatorActivationFrame, rightFrameX, activationFrameY);

        drawLeftActivationCard(g, bodyFrameX, activationFrameY
        );

        drawRightActivationCard(g, rightFrameX, activationFrameY);
    }

    private void drawLeftActivationCard(GraphicsContext g, int frameX, int frameY) {

        int iconX = frameX + ACTIVATION_ICON_OFFSET_X;
        int iconY = frameY + (this.images.actuatorActivationFrame.getHeight() - this.images.kalenderIcon.getHeight()) / 2;

        Painter.drawImage(g, this.images.kalenderIcon, iconX, iconY);

        int groupHeight = this.fonts.activationTitleFont.getHeight() + this.fonts.activationTimeFont.getHeight() + this.fonts.activationDateFont.getHeight();
        int groupY = iconY + (this.images.kalenderIcon.getHeight() - groupHeight) / 2;

        int textX = frameX + ACTIVATION_TEXT_OFFSET_X;

        int timeY = groupY + this.fonts.activationTitleFont.getHeight();

        int dateY = timeY + this.fonts.activationTimeFont.getHeight();

        String cardTitle = isPumpMode() ? "AKTIF TERAKHIR" : "TERAKHIR DIBUKA";

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, cardTitle, this.fonts.activationTitleFont, textX, groupY);

        String activationTime = this.actuatorItem == null ? "-" : this.actuatorItem.getLastActivatedTimeText();
        Painter.drawString(g, activationTime, this.fonts.activationTimeFont, textX, timeY);
        String activationDate = this.actuatorItem == null ? "-" : this.actuatorItem.getLastActivatedDateText();
        Painter.drawString(g, activationDate, this.fonts.activationDateFont, textX, dateY);
    }

    private void drawRightActivationCard(GraphicsContext g, int frameX, int frameY) {
        int iconX = frameX + ACTIVATION_ICON_OFFSET_X;
        int iconY = frameY + (this.images.actuatorActivationFrame.getHeight() - this.images.settingIcon.getHeight()) / 2;

        Painter.drawImage(g, this.images.settingIcon, iconX, iconY);

        int groupHeight = this.fonts.activationTitleFont.getHeight() + CONDITION_TITLE_TO_VALUE_GAP + this.fonts.conditionFont.getHeight();
        int groupY = iconY + (this.images.settingIcon.getHeight() - groupHeight) / 2;

        int textX = frameX + ACTIVATION_TEXT_OFFSET_X;

        int conditionY = groupY + this.fonts.activationTitleFont.getHeight() + CONDITION_TITLE_TO_VALUE_GAP;

        g.setColor(ApplicationColors.SECONDARY_COLOR);

        Painter.drawString(g, "KONDISI AKTIF", this.fonts.activationTitleFont, textX, groupY);

        String conditionText = isPumpMode() ? "Kelembapan Tanah < 40%" : "Terbuka saat pompa menyala";

        Painter.drawString(g, conditionText, this.fonts.conditionFont, textX, conditionY);
    }

    private void drawLastUpdateFrame(GraphicsContext g, int contentWidth) {
        int frameX = getBodyFrameX(contentWidth);
        int frameY = getUpdateFrameY();
        Painter.drawImage(g, this.images.optimalFrame, frameX, frameY);

        int textY = frameY + (this.images.optimalFrame.getHeight() - this.fonts.updateFont.getHeight()) / 2;

        g.setColor(ApplicationColors.SECONDARY_COLOR);
        Painter.drawString(g, "TERAKHIR DIPERBARUI", this.fonts.updateFont, frameX + UPDATE_HORIZONTAL_PADDING, textY);

        String updateText = "-";

        if (this.actuatorItem != null && this.actuatorItem.isDataAvailable()) {
            updateText = this.actuatorItem.getLastUpdatedTimeText();
        }

        int timeWidth = this.fonts.updateFont.stringWidth(updateText);
        int timeX = frameX + this.images.optimalFrame.getWidth() - UPDATE_HORIZONTAL_PADDING - timeWidth;

        Painter.drawString(g, updateText, this.fonts.updateFont, timeX, textY);
    }

    private void drawTrayIndicators(GraphicsContext g, int contentWidth) {
        if (this.actuatorItem == null || !this.actuatorItem.isValve()) {
            return;
        }

        int trayCount = this.actuatorItem.getTrayCount();
        int activeTrayIndex = this.actuatorItem.getTrayIndex();

        if (activeTrayIndex < 0 || activeTrayIndex >= trayCount) {
            return;
        }

        int groupWidth = 0;
        for (int i = 0; i < trayCount; i++) {
            Image dotImage = i == activeTrayIndex ? this.images.dotActive : this.images.dotInactive;
            groupWidth += dotImage.getWidth();

            if (i < trayCount - 1) {
                groupWidth += DOT_HORIZONTAL_GAP;
            }
        }

        int frameX = getBodyFrameX(contentWidth);
        int dotX = frameX + (this.images.optimalFrame.getWidth() - groupWidth) / 2;
        int dotY = getUpdateFrameY() + this.images.optimalFrame.getHeight() + DOT_TOP_GAP;

        for (int i = 0; i < trayCount; i++) {
            Image dotImage = i == activeTrayIndex ? this.images.dotActive : this.images.dotInactive;
            Painter.drawImage(g, dotImage, dotX, dotY);

            dotX += dotImage.getWidth() + DOT_HORIZONTAL_GAP;
        }
    }

    @Override
    protected void onHidden() {
        this.swipeTracking = false;
    }
}