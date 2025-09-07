package org.csu.pixelstrikejavafx.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXProgressBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.control.Label;

/** 左下角 HUD：头像 + 血条 + 三个测试按钮 */
public class PlayerHUD {

    private final StackPane root = new StackPane();
    private final MFXProgressBar hpBar = new MFXProgressBar();
    private final Label hpText = new Label("HP 100/100");
    private final MFXButton btnSpawnP2 = new MFXButton("生成P2");
    private final MFXButton btnKill    = new MFXButton("击杀自己");
    private final MFXButton btnRevive  = new MFXButton("复活");

    private Runnable onSpawnP2;
    private Runnable onKillSelf;
    private Runnable onRevive;

    public PlayerHUD(Image avatar, Runnable onSpawnP2, Runnable onKillSelf, Runnable onRevive) {
        this.onSpawnP2 = onSpawnP2;
        this.onKillSelf = onKillSelf;
        this.onRevive = onRevive;

        // 头像
        Circle avatarCircle = new Circle(28);
        if (avatar != null) avatarCircle.setFill(new ImagePattern(avatar));

        // 名称 + 血条
        Label name = new Label("Player");
        name.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");

        hpBar.setProgress(1.0);        // 0~1
        hpBar.setPrefWidth(180);
        hpBar.setMinWidth(180);

        VBox infoBox = new VBox(4, name, hpBar, hpText);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        HBox topRow = new HBox(12, avatarCircle, infoBox);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // 按钮
        btnSpawnP2.setOnAction(e -> { if (onSpawnP2 != null) onSpawnP2.run(); });
        btnKill.setOnAction(e -> { if (onKillSelf != null) onKillSelf.run(); });
        btnRevive.setOnAction(e -> { if (onRevive != null) onRevive.run(); });

        HBox btnRow = new HBox(8, btnSpawnP2, btnKill, btnRevive);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, topRow, btnRow);
        card.setPadding(new Insets(10));
        card.setMaxWidth(300);
        card.setStyle("""
            -fx-background-color: rgba(30,30,34,0.85);
            -fx-background-radius: 12;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 14, 0.2, 0, 4);
        """);

        root.getChildren().add(card);
        StackPane.setAlignment(card, Pos.BOTTOM_LEFT);
        StackPane.setMargin(card, new Insets(0, 0, 16, 16));

        root.setStyle("-fx-font-family: 'Segoe UI','Microsoft YaHei','Roboto';");
        hpText.setStyle("-fx-font-size: 12px; -fx-text-fill: #d0d0d0;");
    }

    public Pane getRoot() { return root; }

    /** 刷新血条（0~max） */
    public void updateHP(int hp, int max) {
        double p = max > 0 ? Math.max(0, Math.min(1.0, hp / (double) max)) : 0;
        hpBar.setProgress(p);
        hpText.setText("HP " + hp + "/" + max);
    }
}
