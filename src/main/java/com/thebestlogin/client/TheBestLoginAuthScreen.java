package com.thebestlogin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import com.thebestlogin.auth.AuthMessageType;
import com.thebestlogin.auth.AuthScreenMode;
import com.thebestlogin.network.TheBestLoginNetwork;
import com.thebestlogin.network.packet.ClosePromptC2SPacket;
import com.thebestlogin.network.packet.ChangePasswordRequestC2SPacket;
import com.thebestlogin.network.packet.LoginRequestC2SPacket;
import com.thebestlogin.network.packet.RegisterRequestC2SPacket;
import com.thebestlogin.util.HashingUtil;

import java.util.Objects;

public final class TheBestLoginAuthScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 420;
    private static final int MAX_PANEL_WIDTH = 560;
    private static final int PANEL_PADDING = 20;
    private static final int LABEL_WIDTH = 168;
    private static final int LABEL_TO_FIELD_GAP = 10;
    private static final int TOGGLE_WIDTH = 86;
    private static final int FIELD_TO_TOGGLE_GAP = 8;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_ROW_HEIGHT = 32;
    private static final int MESSAGE_HEIGHT = 34;
    private static final int MAX_PASSWORD_LENGTH = 40;
    private static final String PASSWORD_MASK_SYMBOL = "●";

    private PasswordField firstPassword;
    private PasswordField secondPassword;
    private PasswordField thirdPassword;
    private Button firstToggleButton;
    private Button secondToggleButton;
    private Button thirdToggleButton;
    private Button submitButton;
    private String lastChallenge = "";
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean submitting;
    private Component localStatusMessage = Component.empty();
    private AuthMessageType localStatusType = AuthMessageType.NONE;

    public TheBestLoginAuthScreen() {
        super(Component.translatable("thebestlogin.gui.brand"));
    }

    @Override
    protected void init() {
        AuthPromptData prompt = ClientAuthorizationState.getPromptData();
        if (prompt == null) {
            return;
        }

        panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, this.width - 32));
        panelHeight = 184 + visibleFieldCount(prompt.mode()) * FIELD_ROW_HEIGHT;
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(10, (this.height - panelHeight) / 2);
        clearWidgets();

        int labelX = panelX + PANEL_PADDING;
        int fieldX = labelX + LABEL_WIDTH + LABEL_TO_FIELD_GAP;
        int fieldWidth = panelWidth - PANEL_PADDING * 2 - LABEL_WIDTH - LABEL_TO_FIELD_GAP - FIELD_TO_TOGGLE_GAP - TOGGLE_WIDTH;
        int toggleX = fieldX + fieldWidth + FIELD_TO_TOGGLE_GAP;
        int rowY = panelY + 108;

        firstPassword = addRenderableWidget(createPasswordField(fieldX, rowY, fieldWidth));
        firstToggleButton = addRenderableWidget(createToggleButton(toggleX, rowY, firstPassword));

        secondPassword = addRenderableWidget(createPasswordField(fieldX, rowY + FIELD_ROW_HEIGHT, fieldWidth));
        secondToggleButton = addRenderableWidget(createToggleButton(toggleX, rowY + FIELD_ROW_HEIGHT, secondPassword));

        thirdPassword = addRenderableWidget(createPasswordField(fieldX, rowY + FIELD_ROW_HEIGHT * 2, fieldWidth));
        thirdToggleButton = addRenderableWidget(createToggleButton(toggleX, rowY + FIELD_ROW_HEIGHT * 2, thirdPassword));

        int messageY = rowY + visibleFieldCount(prompt.mode()) * FIELD_ROW_HEIGHT + 6;
        int buttonY = messageY + MESSAGE_HEIGHT + 10;
        submitButton = addRenderableWidget(Button.builder(actionTitle(prompt.mode()), button -> submit())
                .bounds(labelX, buttonY, panelWidth - PANEL_PADDING * 2, 22)
                .build());

        configureVisibility(prompt.mode());
        setInitialFocus(firstPassword);
        if (firstPassword != null) {
            firstPassword.setFocused(true);
        }
        lastChallenge = prompt.challenge();
        submitting = false;
        clearLocalStatus();
    }

    @Override
    public void tick() {
        super.tick();
        if (firstPassword != null) {
            firstPassword.tick();
        }
        if (secondPassword != null) {
            secondPassword.tick();
        }
        if (thirdPassword != null) {
            thirdPassword.tick();
        }

        AuthPromptData prompt = ClientAuthorizationState.getPromptData();
        if (prompt == null || ClientAuthorizationState.isAuthenticated()) {
            return;
        }
        if (!Objects.equals(lastChallenge, prompt.challenge())) {
            init();
            return;
        }
        if (submitButton != null) {
            submitButton.active = !submitting;
        }
        updateToggleLabels();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            AuthPromptData prompt = ClientAuthorizationState.getPromptData();
            if (prompt != null && prompt.mode() == AuthScreenMode.CHANGE_PASSWORD && ClientAuthorizationState.isAuthenticated()) {
                ClientAuthorizationState.clearPrompt();
                TheBestLoginNetwork.sendToServer(new ClosePromptC2SPacket());
                this.minecraft.setScreen(null);
            } else {
                this.minecraft.setScreen(new TheBestLoginExitScreen());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
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
        AuthPromptData prompt = ClientAuthorizationState.getPromptData();
        if (prompt == null) {
            return;
        }

        renderBackdrop(guiGraphics);
        renderPanel(guiGraphics, prompt);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderForeground(guiGraphics, prompt);
    }

    private PasswordField createPasswordField(int x, int y, int width) {
        PasswordField editBox = new PasswordField(this.font, x, y, width, FIELD_HEIGHT, Component.empty());
        editBox.setBordered(true);
        editBox.setMaxLength(MAX_PASSWORD_LENGTH);
        editBox.setTextColor(0xF2F7FA);
        editBox.setTextColorUneditable(0x90A8B8);
        return editBox;
    }

    private Button createToggleButton(int x, int y, PasswordField field) {
        return Button.builder(toggleLabel(field), button -> {
                    int cursorPosition = field.getCursorPosition();
                    field.setPasswordVisible(!field.isPasswordVisible());
                    field.setFocused(true);
                    field.setCursorPosition(Math.min(cursorPosition, field.getValue().length()));
                    setFocused(field);
                    button.setMessage(toggleLabel(field));
                })
                .bounds(x, y, TOGGLE_WIDTH, FIELD_HEIGHT)
                .build();
    }

    private void configureVisibility(AuthScreenMode mode) {
        boolean showSecondField = mode == AuthScreenMode.REGISTER || mode == AuthScreenMode.CHANGE_PASSWORD;
        boolean showThirdField = mode == AuthScreenMode.CHANGE_PASSWORD;

        setFieldVisibility(firstPassword, firstToggleButton, true);
        setFieldVisibility(secondPassword, secondToggleButton, showSecondField);
        setFieldVisibility(thirdPassword, thirdToggleButton, showThirdField);
        resetPasswordVisibility();
    }

    private void setFieldVisibility(PasswordField field, Button toggleButton, boolean visible) {
        if (field != null) {
            field.visible = visible;
        }
        if (toggleButton != null) {
            toggleButton.visible = visible;
            toggleButton.active = visible;
        }
    }

    private void updateToggleLabels() {
        updateToggleLabel(firstPassword, firstToggleButton);
        updateToggleLabel(secondPassword, secondToggleButton);
        updateToggleLabel(thirdPassword, thirdToggleButton);
    }

    private void updateToggleLabel(PasswordField field, Button button) {
        if (field != null && button != null) {
            button.setMessage(toggleLabel(field));
        }
    }

    private void resetPasswordVisibility() {
        if (firstPassword != null) {
            firstPassword.setPasswordVisible(false);
        }
        if (secondPassword != null) {
            secondPassword.setPasswordVisible(false);
        }
        if (thirdPassword != null) {
            thirdPassword.setPasswordVisible(false);
        }
        updateToggleLabels();
    }

    private void submit() {
        AuthPromptData prompt = ClientAuthorizationState.getPromptData();
        if (prompt == null || submitting) {
            return;
        }

        switch (prompt.mode()) {
            case LOGIN -> submitLogin(prompt);
            case REGISTER -> submitRegistration(prompt);
            case CHANGE_PASSWORD -> submitPasswordChange(prompt);
        }
    }

    private void submitLogin(AuthPromptData prompt) {
        String password = firstPassword.getValue();
        if (password.isBlank()) {
            setLocalStatus(Component.translatable("thebestlogin.gui.status.enter_password"), AuthMessageType.ERROR);
            return;
        }
        String passwordHash = HashingUtil.derivePasswordHash(prompt.currentHashVersion(), prompt.nickname(), prompt.currentSalt(), password);
        String storedHash = HashingUtil.deriveStoredClientHash(prompt.serverId(), passwordHash);
        String proof = HashingUtil.deriveProof(prompt.serverId(), prompt.nickname(), storedHash, prompt.challenge());
        TheBestLoginNetwork.sendToServer(new LoginRequestC2SPacket(prompt.challenge(), proof, false));
        clearSensitiveFields();
        submitting = true;
        setLocalStatus(Component.translatable("thebestlogin.gui.status.verifying"), AuthMessageType.INFO);
    }

    private void submitRegistration(AuthPromptData prompt) {
        String password = firstPassword.getValue();
        String confirmation = secondPassword.getValue();
        if (password.isBlank()) {
            setLocalStatus(Component.translatable("thebestlogin.gui.status.password_empty"), AuthMessageType.ERROR);
            return;
        }
        if (!password.equals(confirmation)) {
            setLocalStatus(Component.translatable("thebestlogin.gui.status.passwords_mismatch"), AuthMessageType.ERROR);
            return;
        }
        String passwordHash = HashingUtil.derivePasswordHash(prompt.targetHashVersion(), prompt.nickname(), prompt.targetSalt(), password);
        String encryptedPayload = HashingUtil.encryptForServer(prompt.publicKey(), passwordHash);
        TheBestLoginNetwork.sendToServer(new RegisterRequestC2SPacket(prompt.challenge(), encryptedPayload));
        clearSensitiveFields();
        submitting = true;
        setLocalStatus(Component.translatable("thebestlogin.gui.status.creating_account"), AuthMessageType.INFO);
    }

    private void submitPasswordChange(AuthPromptData prompt) {
        String oldPassword = firstPassword.getValue();
        String newPassword = secondPassword.getValue();
        String confirmation = thirdPassword.getValue();
        if (oldPassword.isBlank()) {
            setLocalStatus(Component.translatable("thebestlogin.gui.status.enter_current_password"), AuthMessageType.ERROR);
            return;
        }
        if (newPassword.isBlank()) {
            setLocalStatus(Component.translatable("thebestlogin.gui.status.new_password_empty"), AuthMessageType.ERROR);
            return;
        }
        if (!newPassword.equals(confirmation)) {
            setLocalStatus(Component.translatable("thebestlogin.gui.status.new_password_mismatch"), AuthMessageType.ERROR);
            return;
        }

        String oldHash = HashingUtil.derivePasswordHash(prompt.currentHashVersion(), prompt.nickname(), prompt.currentSalt(), oldPassword);
        String oldStoredHash = HashingUtil.deriveStoredClientHash(prompt.serverId(), oldHash);
        String oldProof = HashingUtil.deriveProof(prompt.serverId(), prompt.nickname(), oldStoredHash, prompt.challenge());
        String newHash = HashingUtil.derivePasswordHash(prompt.targetHashVersion(), prompt.nickname(), prompt.targetSalt(), newPassword);
        String encryptedPayload = HashingUtil.encryptForServer(prompt.publicKey(), newHash);
        TheBestLoginNetwork.sendToServer(new ChangePasswordRequestC2SPacket(prompt.challenge(), oldProof, encryptedPayload));
        clearSensitiveFields();
        submitting = true;
        setLocalStatus(Component.translatable("thebestlogin.gui.status.updating_password"), AuthMessageType.INFO);
    }

    private void clearSensitiveFields() {
        if (firstPassword != null) {
            firstPassword.setValue("");
        }
        if (secondPassword != null) {
            secondPassword.setValue("");
        }
        if (thirdPassword != null) {
            thirdPassword.setValue("");
        }
        resetPasswordVisibility();
    }

    private void renderBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF08131B, 0xFF153244);
        guiGraphics.fillGradient(0, this.height / 3, this.width, this.height, 0x4412A0C5, 0x32060D12);
        guiGraphics.fill(this.width - 220, -20, this.width + 24, 184, 0x1628CAFF);
        guiGraphics.fill(-20, this.height - 190, 230, this.height + 20, 0x1427F59B);
    }

    private void renderPanel(GuiGraphics guiGraphics, AuthPromptData prompt) {
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE6121A24);
        guiGraphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + 6, 0xFF41D0FF, 0xFF2DD4A6);
        guiGraphics.renderOutline(panelX, panelY, panelWidth, panelHeight, 0x885DE1FF);
        guiGraphics.fill(panelX + PANEL_PADDING, panelY + 58, panelX + panelWidth - PANEL_PADDING, panelY + 60, 0x3346C9FF);

        if (hasVisibleMessage(prompt)) {
            int messageY = panelY + 114 + visibleFieldCount(prompt.mode()) * FIELD_ROW_HEIGHT;
            int color = switch (visibleMessageType(prompt)) {
                case ERROR -> 0xAA8C1D1D;
                case SUCCESS -> 0xAA1D7A42;
                case INFO -> 0xAA174B73;
                case NONE -> 0;
            };
            guiGraphics.fill(panelX + PANEL_PADDING, messageY, panelX + panelWidth - PANEL_PADDING, messageY + MESSAGE_HEIGHT, color);
        }
    }

    private void renderForeground(GuiGraphics guiGraphics, AuthPromptData prompt) {
        guiGraphics.drawString(this.font, Component.translatable("thebestlogin.gui.brand"), panelX + PANEL_PADDING, panelY + 18, 0x63E2FF, false);
        guiGraphics.drawString(this.font, modeTitle(prompt.mode()), panelX + PANEL_PADDING, panelY + 34, 0xFFFFFF, false);
        guiGraphics.drawWordWrap(this.font, modeSubtitle(prompt.mode()), panelX + PANEL_PADDING, panelY + 66, panelWidth - PANEL_PADDING * 2, 0xB7CBD7);

        if (prompt.mode() != AuthScreenMode.CHANGE_PASSWORD) {
            Component timer = Component.translatable("thebestlogin.gui.timer", prompt.secondsRemaining());
            guiGraphics.drawString(this.font, timer, panelX + panelWidth - PANEL_PADDING - this.font.width(timer), panelY + 18, 0x9ED7E8, false);
        }

        int labelX = panelX + PANEL_PADDING;
        int rowY = panelY + 111;
        drawFieldRowLabel(guiGraphics, labelForFirstField(prompt.mode()), labelX, rowY);
        drawFieldRowLabel(guiGraphics, labelForSecondField(prompt.mode()), labelX, rowY + FIELD_ROW_HEIGHT, secondPassword != null && secondPassword.visible);
        drawFieldRowLabel(guiGraphics, labelForThirdField(prompt.mode()), labelX, rowY + FIELD_ROW_HEIGHT * 2, thirdPassword != null && thirdPassword.visible);

        if (hasVisibleMessage(prompt)) {
            int messageY = panelY + 114 + visibleFieldCount(prompt.mode()) * FIELD_ROW_HEIGHT;
            int color = switch (visibleMessageType(prompt)) {
                case ERROR -> 0xFFF5B6B6;
                case SUCCESS -> 0xFFC8F7D3;
                case INFO -> 0xFFD2EEFF;
                case NONE -> 0xFFFFFFFF;
            };
            guiGraphics.drawWordWrap(this.font, visibleMessage(prompt), panelX + PANEL_PADDING + 6, messageY + 6, panelWidth - PANEL_PADDING * 2 - 12, color);
        }

    }

    private void drawFieldRowLabel(GuiGraphics guiGraphics, Component label, int x, int y) {
        drawFieldRowLabel(guiGraphics, label, x, y, true);
    }

    private void drawFieldRowLabel(GuiGraphics guiGraphics, Component label, int x, int y, boolean visible) {
        if (!visible) {
            return;
        }
        guiGraphics.drawString(this.font, label, x, y + 6, 0xD6E7F0, false);
    }

    private boolean hasVisibleMessage(AuthPromptData prompt) {
        Component message = visibleMessage(prompt);
        return message != null && !message.getString().isBlank() && visibleMessageType(prompt) != AuthMessageType.NONE;
    }

    private AuthMessageType visibleMessageType(AuthPromptData prompt) {
        if (prompt.statusType() != AuthMessageType.NONE && prompt.statusMessage() != null && !prompt.statusMessage().isBlank()) {
            return prompt.statusType();
        }
        return localStatusType;
    }

    private Component visibleMessage(AuthPromptData prompt) {
        if (prompt.statusType() != AuthMessageType.NONE && prompt.statusMessage() != null && !prompt.statusMessage().isBlank()) {
            return Component.literal(prompt.statusMessage());
        }
        return localStatusMessage;
    }

    private void setLocalStatus(Component message, AuthMessageType type) {
        localStatusMessage = message == null ? Component.empty() : message;
        localStatusType = type == null ? AuthMessageType.NONE : type;
    }

    private void clearLocalStatus() {
        localStatusMessage = Component.empty();
        localStatusType = AuthMessageType.NONE;
    }

    private static int visibleFieldCount(AuthScreenMode mode) {
        return switch (mode) {
            case LOGIN -> 1;
            case REGISTER -> 2;
            case CHANGE_PASSWORD -> 3;
        };
    }

    private static Component modeTitle(AuthScreenMode mode) {
        return switch (mode) {
            case LOGIN -> Component.translatable("thebestlogin.gui.mode.login");
            case REGISTER -> Component.translatable("thebestlogin.gui.mode.register");
            case CHANGE_PASSWORD -> Component.translatable("thebestlogin.gui.mode.change_password");
        };
    }

    private static Component modeSubtitle(AuthScreenMode mode) {
        return switch (mode) {
            case LOGIN -> Component.translatable("thebestlogin.gui.subtitle.login");
            case REGISTER -> Component.translatable("thebestlogin.gui.subtitle.register");
            case CHANGE_PASSWORD -> Component.translatable("thebestlogin.gui.subtitle.change_password");
        };
    }

    private static Component actionTitle(AuthScreenMode mode) {
        return switch (mode) {
            case LOGIN -> Component.translatable("thebestlogin.gui.button.login");
            case REGISTER -> Component.translatable("thebestlogin.gui.button.register");
            case CHANGE_PASSWORD -> Component.translatable("thebestlogin.gui.button.change_password");
        };
    }

    private static Component labelForFirstField(AuthScreenMode mode) {
        return switch (mode) {
            case LOGIN -> Component.translatable("thebestlogin.gui.label.password");
            case REGISTER -> Component.translatable("thebestlogin.gui.label.password");
            case CHANGE_PASSWORD -> Component.translatable("thebestlogin.gui.label.current_password");
        };
    }

    private static Component labelForSecondField(AuthScreenMode mode) {
        return switch (mode) {
            case LOGIN -> Component.empty();
            case REGISTER -> Component.translatable("thebestlogin.gui.label.password_repeat");
            case CHANGE_PASSWORD -> Component.translatable("thebestlogin.gui.label.new_password");
        };
    }

    private static Component labelForThirdField(AuthScreenMode mode) {
        return switch (mode) {
            case LOGIN, REGISTER -> Component.empty();
            case CHANGE_PASSWORD -> Component.translatable("thebestlogin.gui.label.new_password_repeat");
        };
    }

    private static Component toggleLabel(PasswordField field) {
        return Component.translatable(field != null && field.isPasswordVisible()
                ? "thebestlogin.gui.button.hide"
                : "thebestlogin.gui.button.show");
    }

    private static String mask(int length) {
        if (length <= 0) {
            return "";
        }
        return PASSWORD_MASK_SYMBOL.repeat(length);
    }

    private static final class PasswordField extends EditBox {
        private boolean passwordVisible;

        private PasswordField(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            updateFormatter();
        }

        public boolean isPasswordVisible() {
            return passwordVisible;
        }

        public void setPasswordVisible(boolean passwordVisible) {
            this.passwordVisible = passwordVisible;
            updateFormatter();
        }

        private void updateFormatter() {
            setFormatter((value, cursorPosition) -> passwordVisible
                    ? FormattedCharSequence.forward(value, Style.EMPTY)
                    : FormattedCharSequence.forward(mask(value.length()), Style.EMPTY.withBold(true)));
        }
    }
}
