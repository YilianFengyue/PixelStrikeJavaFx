// 文件路径: org.csu.pixelstrikejavafx.lobby.ui.CharacterSelectionController.java
package org.csu.pixelstrikejavafx.lobby.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CharacterSelectionController {

    @FXML private Label titleLabel;
    @FXML private ListView<Map<String, Object>> characterListView;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private Consumer<Map<String, Object>> onConfirm;
    private Runnable onCancel;

    @FXML
    public void initialize() {
        setupListViewCellFactory();

        confirmButton.setOnAction(e -> {
            Map<String, Object> selectedCharacter = characterListView.getSelectionModel().getSelectedItem();
            if (onConfirm != null && selectedCharacter != null) {
                onConfirm.accept(selectedCharacter);
            }
        });

        cancelButton.setOnAction(e -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setOnConfirm(Consumer<Map<String, Object>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public void populateCharacters(List<Map<String, Object>> characters) {
        characterListView.getItems().setAll(characters);
        // 默认选中第一个
        if (!characters.isEmpty()) {
            characterListView.getSelectionModel().selectFirst();
        }
    }

    private void setupListViewCellFactory() {
        characterListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // 自定义单元格显示，可以做得更丰富
                    setText(String.format("%s (生命: %s, 速度: %s)",
                            item.get("name"), item.get("health"), item.get("speed")));
                    // 你也可以在这里添加图片等
                }
            }
        });
    }
}