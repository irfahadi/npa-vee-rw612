package com.nxp.example.smartgreenhouse.views.circularprogress;

import com.nxp.example.smartgreenhouse.style.ApplicationColors;

import ej.bon.Util;
import ej.drawing.ShapePainter;
import ej.microui.display.GraphicsContext;
import ej.microui.display.Painter;
import ej.mwt.Widget;
import ej.mwt.animation.Animation;
import ej.mwt.util.Size;

public final class CircularProgress extends Widget implements Animation {

    private static final int START_ANGLE = 90;
    private static final int FULL_ANGLE = 360;

    private static final int ARC_ANGLE = -105;

    private static final int DIAMETER = 32;
    private static final int THICKNESS = 3;
    private static final int FADE = 1;

    private static final long ROTATION_DURATION = 900L;

    private boolean running;
    private boolean animationStarted;

    private long startTime;

    private int currentStartAngle;

    public CircularProgress() {
        this.running = false;
        this.animationStarted = false;

        this.startTime = 0L;
        this.currentStartAngle = START_ANGLE;
    }

    public void start() {
        if (this.running) {
            return;
        }

        this.running = true;
        this.startTime = Util.platformTimeMillis();

        startAnimationIfPossible();
        requestRender();
    }

    public void stop() {
        this.running = false;

        if (this.animationStarted && isShown()) {
            getDesktop().getAnimator().stopAnimation(this);
        }

        this.animationStarted = false;
        requestRender();
    }

    @Override
    protected void onShown() {
        startAnimationIfPossible();
    }

    @Override
    protected void onHidden() {
        if (this.animationStarted) {
            getDesktop().getAnimator().stopAnimation(this);
            this.animationStarted = false;
        }
    }

    private void startAnimationIfPossible() {
        if (!this.running) {
            return;
        }

        if (!isShown()) {
            return;
        }

        if (this.animationStarted) {
            return;
        }

        this.startTime = Util.platformTimeMillis();

        getDesktop().getAnimator().startAnimation(this);
        this.animationStarted = true;
    }

    @Override
    public boolean tick(long platformTimeMillis) {
        if (!this.running) {
            this.animationStarted = false;
            return false;
        }

        long elapsedTime = platformTimeMillis - this.startTime;

        int rotation = (int) ((elapsedTime % ROTATION_DURATION) * FULL_ANGLE / ROTATION_DURATION);

        this.currentStartAngle = START_ANGLE - rotation;

        requestRender();

        return true;
    }

    @Override
    protected void computeContentOptimalSize(Size size) {
        int widgetSize = DIAMETER + (FADE * 2);
        size.setSize(widgetSize, widgetSize);
    }

    @Override
    protected void renderContent(GraphicsContext g, int contentWidth, int contentHeight) {
        int progressDiameter = DIAMETER - (THICKNESS << 2);
        int centerX = contentWidth / 2;
        int centerY = contentHeight / 2;
        g.setColor(ApplicationColors.BACKGROUND);
        int left = centerX - (DIAMETER >> 1);
        int top = centerY - (DIAMETER >> 1);

        ShapePainter.drawThickFadedCircle(g, left, top, DIAMETER, 0, FADE);
        Painter.fillCircle(g, left, top, DIAMETER);

        g.setColor(ApplicationColors.PRIMARY_COLOR);
        left = centerX - (progressDiameter >> 1);
        top = centerY - (progressDiameter >> 1);
        ShapePainter.drawThickFadedCircleArc(g, left, top, progressDiameter, this.currentStartAngle, ARC_ANGLE, THICKNESS, FADE, ShapePainter.Cap.ROUNDED, ShapePainter.Cap.ROUNDED);
    }
}