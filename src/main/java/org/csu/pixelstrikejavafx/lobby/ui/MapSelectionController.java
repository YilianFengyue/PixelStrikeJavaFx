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
        String mapName = (String) map.get("name");
        mapNameText.setText(mapName);
        mapDescriptionText.setText((String) map.get("description"));


        String imagePath;
        switch (mapName) {
            case "沙漠小镇":
                imagePath = "/assets/textures/Desert_background_4.png";
                break;
            case "雪地哨站":
                imagePath = "/assets/textures/Snow_biome_background_9.png";
                break;
            case "丛林遗迹":
                imagePath = "/assets/textures/Jungle_background_2.png";
                break;
            case "神圣之地":
                imagePath = "/assets/textures/Hallow_background_1.png";
                break;
            case "森林湖畔":
                imagePath = "/assets/textures/Forest_background_9.png";
                break;
            default:
                // 如果没有匹配的地图，则使用一张默认的背景图
                imagePath = "/assets/textures/background.png";
                break;
        }

        Image image;
        try {
            // 尝试从我们指定的本地路径加载图片
            image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imagePath)));
        } catch (Exception e) {
            System.err.println("加载地图预览图失败: " + imagePath);
            // 如果加载失败，加载一张最终的备用图片
            image = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/assets/textures/background.png")));
        }

        mapImageView.setImage(image);
    }
}