package org.csu.pixelstrikejavafx.lobby.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class MapSelectionController {

    @FXML private ImageView mapImageView;
    @FXML private Text mapNameText;
    @FXML private Text mapDescriptionText;
    @FXML private Button leftButton;
    @FXML private Button rightButton;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private List<Map<String, Object>> maps;
    private int currentIndex = 0;

    private Consumer<Map<String, Object>> onConfirm;
    private Runnable onCancel;

    @FXML
    public void initialize() {
        leftButton.setOnAction(e -> showPreviousMap());
        rightButton.setOnAction(e -> showNextMap());
        confirmButton.setOnAction(e -> {
            if (onConfirm != null && !maps.isEmpty()) {
                onConfirm.accept(maps.get(currentIndex));
            }
        });
        cancelButton.setOnAction(e -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
    }

    public void setOnConfirm(Consumer<Map<String, Object>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public void populateMaps(List<Map<String, Object>> maps) {
        this.maps = maps;
        if (maps != null && !maps.isEmpty()) {
            currentIndex = 0;
            updateDisplay();
        } else {
            // Handle case with no maps
            mapNameText.setText("没有可用的地图");
            leftButton.setDisable(true);
            rightButton.setDisable(true);
            confirmButton.setDisable(true);
        }
    }

    private void showPreviousMap() {
        currentIndex = (currentIndex - 1 + maps.size()) % maps.size();
        updateDisplay();
    }

    private void showNextMap() {
        currentIndex = (currentIndex + 1) % maps.size();
        updateDisplay();
    }

    private void updateDisplay() {
        Map<String, Object> map = maps.get(currentIndex);
        mapNameText.setText((String) map.get("name"));
        mapDescriptionText.setText((String) map.get("description"));

        Object urlObj = map.get("thumbnailUrl");
        Image image;
        if (urlObj != null && !urlObj.toString().isEmpty()) {
            image = new Image(urlObj.toString(), true);
        } else {
            image = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/background.png")));
        }
        mapImageView.setImage(image);
    }
}