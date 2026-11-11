package com.nxp.example.smartgreenhouse;

import com.microej.partial.support.PartialRenderPolicy;
import com.nxp.example.smartgreenhouse.controllers.AppController;
import com.nxp.example.smartgreenhouse.models.sensor.SensorDefinitionProvider;
import ej.annotation.NonNullByDefault;
import ej.microui.MicroUI;
import ej.mwt.Desktop;
import ej.mwt.Widget;
import ej.mwt.render.RenderPolicy;
import ej.mwt.stylesheet.cascading.CascadingStylesheet;
import ej.util.Device;

import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger("[SMART GREENHOUSE]");

    public static void main(String[] args) {
        LOGGER.info("NXP Platform Accelerator VM running on " + Device.getArchitecture());

        MicroUI.start();

        SensorDefinitionProvider.initialize();

        CascadingStylesheet stylesheet = defineStylesheet();

        AppController app = new AppController();
        app.start();

        createDesktop(stylesheet, app.getMainPage());

    }

    private static void createDesktop(CascadingStylesheet stylesheet, Widget rootWidget) {
        Desktop desktop = new Desktop() {
            @Override
            @NonNullByDefault
            protected RenderPolicy createRenderPolicy() {
                return new PartialRenderPolicy(this, true);
            }
        };

        desktop.setStylesheet(stylesheet);
        desktop.setWidget(rootWidget);
        desktop.requestShow();
    }

    private static CascadingStylesheet defineStylesheet() {
        return new CascadingStylesheet();
    }
}