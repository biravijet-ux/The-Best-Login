package com.thebestlogin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class TheBestLoginExitScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 136;

    public TheBestLoginExitScreen() {
        super(Component.translatable("thebestlogin.gui.brand"));
    }

    @Override
    protected void init() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int buttonWidth = 116;

        addRenderableWidget(Button.builder(Component.translatable("thebestlogin.exit.back"), button -> this.minecraft.setScreen(new TheBestLoginAuthScreen()))
                .bounds(panelX + 20, panelY + PANEL_HEIGHT - 34, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("thebestlogin.exit.disconnect"), button -> disconnectToTitle())
                .bounds(panelX + PANEL_WIDTH - 20 - buttonWidth, panelY + PANEL_HEIGHT - 34, buttonWidth, 20)
                .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(new TheBestLoginAuthScreen());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(guiGraphics);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE6121A24);
        guiGraphics.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + 6, 0xFF41D0FF, 0xFF2DD4A6);
        guiGraphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0x885DE1FF);

        guiGraphics.drawCenteredString(this.font, Component.translatable("thebestlogin.exit.title"), panelX + PANEL_WIDTH / 2, panelY + 18, 0xFFFFFF);
        guiGraphics.drawWordWrap(this.font, Component.translatable("thebestlogin.exit.body"), panelX + 20, panelY + 42, PANEL_WIDTH - 40, 0xB7CBD7);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF08131B, 0xFF153244);
        guiGraphics.fillGradient(0, this.height / 3, this.width, this.height, 0x4412A0C5, 0x32060D12);
    }

    private void disconnectToTitle() {
        if (this.minecraft == null) {
            return;
        }
        if (this.minecraft.level != null) {
            this.minecraft.level.disconnect();
        }
        this.minecraft.clearLevel(new TitleScreen());
    }
}
