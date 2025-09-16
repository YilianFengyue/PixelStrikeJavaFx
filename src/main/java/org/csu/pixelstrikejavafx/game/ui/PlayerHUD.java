// main/java/org/csu/pixelstrikejavafx/game/ui/PlayerHUD.java
package org.csu.pixelstrikejavafx.game.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;

public class PlayerHUD {

    // --- ★ 核心修正 1：增加边距 ---
    private static final double CARD_MARGIN = 150;

    private final AnchorPane root = new AnchorPane();
    private final ProgressBar hpBar = new ProgressBar();
    private final Label hpText = new Label("HP 100/100");
    private final Label nameLabel = new Label("Player"); // 将name提升为成员变量

    public PlayerHUD(Image avatar, String nickname) {
        // 头像
        Circle avatarCircle = new Circle(32); // 头像稍微大一点
        if (avatar != null) avatarCircle.setFill(new ImagePattern(avatar));
        else avatarCircle.setFill(Color.web("#E5E7EB"));
        avatarCircle.setStroke(Color.WHITE);
        avatarCircle.setStrokeWidth(2);

        // 名称 + 血条
        nameLabel.setText(nickname);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: white; -fx-font-weight: bold;");
        hpText.setStyle("-fx-font-size: 12px; -fx-text-fill: #bdc3c7;");

        hpBar.setProgress(1.0);
        hpBar.setPrefWidth(250); // 调整宽度
        hpBar.setMinHeight(8);
        hpBar.setPrefHeight(8);
        hpBar.setMaxHeight(8);
        styleHpBar(hpBar);

        VBox infoBox = new VBox(5, nameLabel, hpBar, hpText);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        HBox cardContent = new HBox(15, avatarCircle, infoBox);
        cardContent.setAlignment(Pos.CENTER_LEFT);
        cardContent.setPadding(new Insets(12));
        // --- ★ 核心修正 2：更新为暗色透明风格 ---
        cardContent.setStyle("""
            -fx-background-color: rgba(0, 0, 0, 0.4);
            -fx-background-radius: 8;
            -fx-border-color: rgba(255, 255, 255, 0.1);
            -fx-border-width: 1;
            -fx-border-radius: 8;
        """);

        root.getChildren().add(cardContent);
        AnchorPane.setLeftAnchor(cardContent, CARD_MARGIN);
        AnchorPane.setBottomAnchor(cardContent, CARD_MARGIN);

        root.setStyle("-fx-font-family: 'Segoe UI','Microsoft YaHei','Roboto';");
    }

    // --- 省略了 stylePrimary, styleOutlined, 以及所有与按钮相关的代码 ---

    public Pane getRoot() { return root; }

    public void updateHP(int hp, int max) {
        double p = max > 0 ? Math.max(0, Math.min(1.0, hp / (double) max)) : 0;
        hpBar.setProgress(p);
        hpText.setText("HP " + hp + "/" + max);
    }

    // tweakBar 和 styleHpBar 方法保持不变
    private void styleHpBar(ProgressBar pb) {
        pb.skinProperty().addListener((obs, o, n) -> Platform.runLater(() -> tweakBar(pb)));
        pb.sceneProperty().addListener((obs, o, n) -> Platform.runLater(() -> tweakBar(pb)));
        Platform.runLater(() -> tweakBar(pb));
    }

    private void tweakBar(ProgressBar pb) {
        Region track = (Region) pb.lookup(".track");
        Region bar   = (Region) pb.lookup(".bar");
        if (track != null) {
            track.setStyle("-fx-background-color: #4b5563; -fx-background-radius: 999; -fx-background-insets: 0;");
        }
        if (bar != null) {
            bar.setStyle("-fx-background-color: #22c55e; -fx-background-radius: 999; -fx-background-insets: 0;");
        }
    }
}