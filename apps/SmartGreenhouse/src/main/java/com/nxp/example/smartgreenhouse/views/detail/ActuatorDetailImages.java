package com.nxp.example.smartgreenhouse.views.detail;

import com.nxp.example.smartgreenhouse.style.Icons;
import com.nxp.example.smartgreenhouse.style.Images;

import ej.microui.display.Image;

final class ActuatorDetailImages {

    final Image headerFrame;
    final Image bodyFrame;
    final Image actuatorFrame;
    final Image actuatorStatusFrame;
    final Image actuatorActivationFrame;
    final Image optimalFrame;
    final Image dotActive;
    final Image dotInactive;

    final Image statusOptimalFrame;
    final Image statusBahayaFrame;

    final Image divideHorizontal;
    final Image divideVertical;

    final Image timerIcon;
    final Image kalenderIcon;
    final Image settingIcon;

    final Image pumpIcon;
    final Image valveIcon;

    ActuatorDetailImages() {
        this.headerFrame = Image.getImage(Images.HEADER_DETAIL_FRAME);
        this.bodyFrame = Image.getImage(Images.ACTUATOR_OUTLINE_FRAME);
        this.actuatorFrame = Image.getImage(Images.ACTUATOR_DETAIL_FRAME);
        this.actuatorStatusFrame = Image.getImage(Images.ACTUATOR_STATUS_FRAME);
        this.actuatorActivationFrame = Image.getImage(Images.ACTUATOR_ACTIVATION_FRAME);
        this.optimalFrame = Image.getImage(Images.OPTIMAL_FRAME);
        this.statusOptimalFrame = Image.getImage(Images.OPTIMAL_ALERT_FRAME_M);
        this.statusBahayaFrame = Image.getImage(Images.BAHAYA_ALERT_FRAME_M);
        this.divideHorizontal = Image.getImage(Images.DIVIDE_HORIZONTAL);
        this.divideVertical = Image.getImage(Images.DIVIDE_VERTICAL);
        this.dotActive = Image.getImage(Images.DOT_ACTIVE);
        this.dotInactive = Image.getImage(Images.DOT_INACTIVE);

        this.timerIcon = Image.getImage(Icons.TIMER_ICON_32);
        this.kalenderIcon = Image.getImage(Icons.KALENDER_ICON_48);
        this.settingIcon = Image.getImage(Icons.SETTING_ICON_48);
        this.pumpIcon = Image.getImage(Icons.POMPA_AIR_ICON_64);
        this.valveIcon = Image.getImage(Icons.KATUP_AIR_ICON_64);
    }
}
