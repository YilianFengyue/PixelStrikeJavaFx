package org.csu.pixelstrikejavafx.lobby.ui.dialog;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class FullScreenMessageController {

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button confirmButton;

    private Runnable onConfirm;

    @FXML
    public void initialize() {
        confirmButton.setOnAction(event -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
    }

    public void setContent(String title, String message, Runnable onConfirm) {
        this.titleLabel.setText(title);
        this.messageLabel.setText(message);
        this.onConfirm = onConfirm;
    }
}