package org.csu.pixelstrikejavafx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HelloController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
    @FXML
    protected void onMaterialButtonClick() {
        welcomeText.setText("Hello, MaterialFX!");
    }
}