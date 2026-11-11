package com.nxp.example.smartgreenhouse.views.detail;

import com.nxp.example.smartgreenhouse.style.Fonts;

import ej.microui.display.Font;

final class ActuatorDetailFonts {

    final Font detailTitleFont;
    final Font statusControlFont;
    final Font controlOptionFont;
    final Font statusAlertFont;
    final Font activationTitleFont;
    final Font activationTimeFont;
    final Font activationDateFont;
    final Font conditionFont;
    final Font valveLabelFont;
    final Font valveValueFont;
    final Font pumpInfoFont;
    final Font durationValueFont;
    final Font updateFont;

    ActuatorDetailFonts() {
        this.detailTitleFont = Fonts.jetbrainsMonoBold12px();
        this.statusControlFont = Fonts.jetbrainsMonoBold10px();
        this.controlOptionFont = Fonts.jetbrainsMonoBold10px();
        this.statusAlertFont = Fonts.jetbrainsMonoRegular10px();
        this.activationTitleFont = Fonts.jetbrainsMonoRegular10px();
        this.activationTimeFont = Fonts.jetbrainsMonoBold14px();
        this.activationDateFont = Fonts.jetbrainsMonoRegular8px();
        this.conditionFont = Fonts.jetbrainsMonoBold8px();
        this.valveLabelFont = Fonts.jetbrainsMonoRegular8px();
        this.valveValueFont = Fonts.jetbrainsMonoBold14px();
        this.pumpInfoFont = Fonts.jetbrainsMonoRegular8px();
        this.durationValueFont = Fonts.jetbrainsMonoBold12px();
        this.updateFont = Fonts.jetbrainsMonoBold10px();
    }
}
