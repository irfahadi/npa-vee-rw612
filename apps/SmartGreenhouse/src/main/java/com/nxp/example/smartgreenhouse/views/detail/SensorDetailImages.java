package com.nxp.example.smartgreenhouse.views.detail;

import com.nxp.example.smartgreenhouse.style.Images;

import ej.microui.display.Image;

final class SensorDetailImages {

    final Image headerFrame;
    final Image historyCardFrame;
    final Image historyUpdateCardFrame;
    final Image optimalFrame;
    final Image dotActive;
    final Image dotInactive;

    SensorDetailImages() {
        this.headerFrame = Image.getImage(Images.HEADER_DETAIL_FRAME);
        this.historyCardFrame = Image.getImage(Images.HISTORY_CARD_FRAME);
        this.historyUpdateCardFrame = Image.getImage(Images.HISTORY_UPDATE_CARD_FRAME);
        this.optimalFrame = Image.getImage(Images.OPTIMAL_FRAME);
        this.dotActive = Image.getImage(Images.DOT_ACTIVE);
        this.dotInactive = Image.getImage(Images.DOT_INACTIVE);
    }
}
