package com.nxp.example.smartgreenhouse.views.detail;

import com.nxp.example.smartgreenhouse.style.Fonts;

import ej.microui.display.Font;

final class SensorDetailFonts {

    final Font detailTitleFont;

    final Font summaryTitleFont;
    final Font summaryValueFont;
    final Font summaryValueSmallFont;
    final Font summaryUnitFont;
    final Font summaryUnitSmallFont;

    final Font updateTitleFont;
    final Font updateValueFont;
    final Font updateValueSmallFont;

    final Font optimalTitleFont;
    final Font optimalValueFont;
    final Font optimalUnitFont;

    SensorDetailFonts() {
        this.detailTitleFont = Fonts.jetbrainsMonoBold12px();

        this.summaryTitleFont = Fonts.jetbrainsMonoBold12px();
        this.summaryValueFont = Fonts.jetbrainsMonoBold12px();
        this.summaryValueSmallFont = Fonts.jetbrainsMonoBold10px();
        this.summaryUnitFont = Fonts.jetbrainsMonoBold10px();
        this.summaryUnitSmallFont = Fonts.jetbrainsMonoBold8px();

        this.updateTitleFont = Fonts.jetbrainsMonoBold12px();
        this.updateValueFont = Fonts.jetbrainsMonoBold16px();
        this.updateValueSmallFont = Fonts.jetbrainsMonoBold14px();

        this.optimalTitleFont = Fonts.jetbrainsMonoBold12px();
        this.optimalValueFont = Fonts.jetbrainsMonoBold10px();
        this.optimalUnitFont = Fonts.jetbrainsMonoBold8px();
    }
}
