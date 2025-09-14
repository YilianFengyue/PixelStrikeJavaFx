package org.csu.pixelstrikejavafx.lobby.ui.dialog;

import javafx.fxml.FXML;
import javafx.scene.Node; // Import Node
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class CustomDialogController {

    @FXML private VBox dialogContainer;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private TextField inputField;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private Runnable onCancel;
    private Consumer<String> onConfirmInput;
    private Runnable onConfirmAction;

    // 1. CHANGE THIS METHOD TO PUBLIC
    @FXML
    public void initialize() { // <-- Was private, now is public
        confirmButton.setOnAction(event -> {
            if (onConfirmInput != null) {
                onConfirmInput.accept(inputField.getText());
            }
            if (onConfirmAction != null) {
                onConfirmAction.run();
            }
        });

        cancelButton.setOnAction(event -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
    }

    // 2. ADD THIS NEW GETTER METHOD
    public Node getDialogContainer() {
        return dialogContainer;
    }

    // --- The rest of the file remains exactly the same ---

    // Configured for a simple message box (only one confirm button)
    public void setupAsMessage(String title, String message, Runnable onConfirm) {
        titleLabel.setText(title);
        messageLabel.setText(message);
        this.onConfirmAction = onConfirm;

        setControlVisible(titleLabel, true);
        setControlVisible(inputField, false);
        setControlVisible(cancelButton, false);
    }

    // 配置为确认框 (确认和取消两个按钮)
    public void setupAsConfirmation(String title, String message, Runnable onConfirm, Runnable onCancel) {
        setupAsMessage(title, message, onConfirm);
        this.onCancel = onCancel;
        setControlVisible(cancelButton, true);
    }

    // 配置为输入框
    public void setupAsInput(String title, String message, Consumer<String> onConfirm, Runnable onCancel) {
        setupAsConfirmation(title, message, null, onCancel);
        this.onConfirmInput = onConfirm;
        setControlVisible(inputField, true);
    }

    private void setControlVisible(javafx.scene.Node control, boolean isVisible) {
        control.setVisible(isVisible);
        control.setManaged(isVisible);
    }
}