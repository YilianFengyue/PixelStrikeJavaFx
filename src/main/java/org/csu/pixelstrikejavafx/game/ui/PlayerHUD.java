package org.csu.pixelstrikejavafx.game.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXProgressBar;
import javafx.scene.control.ProgressBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.application.Platform;
import javafx.scene.layout.Region;

public class PlayerHUD {

    private static final double CARD_WIDTH  = 360; // ☆ 卡片更大
    private static final double CARD_MARGIN = 0;  // ☆ 距离屏幕边缘（左下）

    private final AnchorPane root = new AnchorPane();

    private final ProgressBar hpBar = new ProgressBar();
    private final Label hpText = new Label("HP 100/100");

    private final MFXButton btnSpawnP2 = new MFXButton("生成 P2");
    private final MFXButton btnKill    = new MFXButton("击杀自己");
    private final MFXButton btnRevive  = new MFXButton("复活");
    private final MFXButton btnP2Shoot = new MFXButton("P2 开火");

    private Runnable onSpawnP2;
    private Runnable onKillSelf;
    private Runnable onRevive;
    private Runnable onP2Shoot;

    // ☆ 构造函数新增 onP2Shoot
    public PlayerHUD(Image avatar,
                     Runnable onSpawnP2,
                     Runnable onKillSelf,
                     Runnable onRevive,
                     Runnable onP2Shoot) {
        this.onSpawnP2 = onSpawnP2;
        this.onKillSelf = onKillSelf;
        this.onRevive  = onRevive;
        this.onP2Shoot = onP2Shoot;

        // 头像
        Circle avatarCircle = new Circle(20);
        if (avatar != null) avatarCircle.setFill(new ImagePattern(avatar));
        else avatarCircle.setFill(Color.web("#E5E7EB"));
        avatarCircle.setStroke(Color.WHITE);
        avatarCircle.setStrokeWidth(2);

        // 名称 + 血条
        Label name = new Label("Player");
        name.setStyle("-fx-font-size: 14px; -fx-text-fill: #111827; -fx-font-weight: bold;");

        hpBar.setProgress(1.0);                       // 0..1
        hpBar.setPrefWidth(CARD_WIDTH - 150);
        hpBar.setMinHeight(6);
        hpBar.setPrefHeight(6);
        hpBar.setMaxHeight(6);
        hpBar.setStyle("-fx-accent: #22c55e; -fx-background-color: transparent; -fx-padding: 0;");

        // 关键：等 skin / scene 就绪后，把内层 .track / .bar 做成“无边框+胶囊”
        styleHpBar(hpBar);

        VBox infoBox = new VBox(4, name, hpBar, hpText);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        HBox topRow = new HBox(10, avatarCircle, infoBox);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // ☆ 统一按钮尺寸，避免“省略号”
        stylePrimary(btnSpawnP2);
        styleOutlined(btnKill);
        styleOutlined(btnRevive);
        styleOutlined(btnP2Shoot);

        btnSpawnP2.setPrefWidth(88);
        btnKill.setPrefWidth(88);
        btnRevive.setPrefWidth(88);
        btnP2Shoot.setPrefWidth(88);
        // … 按钮已创建 & styleOutlined/stylePrimary 之后

// [NEW] 如果回调是 null，则隐藏对应按钮（不占位）
        if (onSpawnP2 == null) { btnSpawnP2.setVisible(false); btnSpawnP2.setManaged(false); }
        if (onKillSelf == null) { btnKill.setVisible(false); btnKill.setManaged(false); }
        if (onRevive  == null) { btnRevive.setVisible(false); btnRevive.setManaged(false); }
        if (onP2Shoot == null) { btnP2Shoot.setVisible(false); btnP2Shoot.setManaged(false); }
        // 按钮行
        HBox btnRow = new HBox(10, btnSpawnP2, btnKill, btnRevive, btnP2Shoot);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        // 卡片
        VBox card = new VBox(12, topRow, btnRow);
        card.setPadding(new Insets(12));
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 14;
            -fx-border-color: #E5E7EB;
            -fx-border-radius: 14;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0.2, 0, 3);
        """);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        root.getChildren().add(card);
        // ☆ 更靠屏幕左下角
        AnchorPane.setLeftAnchor(card, CARD_MARGIN);
        AnchorPane.setBottomAnchor(card, CARD_MARGIN);

        root.setStyle("-fx-font-family: 'Segoe UI','Microsoft YaHei','Roboto';");

        // 绑定动作
        wireButton(btnSpawnP2, onSpawnP2);
        wireButton(btnKill,    onKillSelf);
        wireButton(btnRevive,  onRevive);
        wireButton(btnP2Shoot, onP2Shoot);

        root.setFocusTraversable(false);

    }

    private void stylePrimary(MFXButton b) {
        b.setMinHeight(30);
        b.setStyle("""
            -fx-background-color: #3B82F6;
            -fx-text-fill: white;
            -fx-background-radius: 10;
            -fx-font-size: 12px;
            -fx-padding: 0 14 0 14;
        """);
    }

    private void styleOutlined(MFXButton b) {
        b.setMinHeight(30);
        b.setStyle("""
            -fx-background-color: transparent;
            -fx-border-color: #CBD5E1;
            -fx-text-fill: #374151;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
            -fx-font-size: 12px;
            -fx-padding: 0 14 0 14;
        """);
    }

    public Pane getRoot() { return root; }

    public void updateHP(int hp, int max) {
        double p = max > 0 ? Math.max(0, Math.min(1.0, hp / (double) max)) : 0;
        hpBar.setProgress(p);
        hpText.setText("HP " + hp + "/" + max);
    }
    // 让 ProgressBar 看起来像“无边框的细绿色胶囊条”
    private void styleHpBar(ProgressBar pb) {
        // 任何一次创建/换皮肤/进场，都尝试美化一次
        pb.skinProperty().addListener((obs, o, n) -> Platform.runLater(() -> tweakBar(pb)));
        pb.sceneProperty().addListener((obs, o, n) -> Platform.runLater(() -> tweakBar(pb)));

        // 构造完成后也先尝试一次（有时皮肤已就绪）
        Platform.runLater(() -> tweakBar(pb));
    }

    private void tweakBar(ProgressBar pb) {
        Region track = (Region) pb.lookup(".track");
        Region bar   = (Region) pb.lookup(".bar");
        if (track != null) {
            track.setStyle("-fx-background-color: #e5e7eb; -fx-background-radius: 999; -fx-background-insets: 0;");
        }
        if (bar != null) {
            bar.setStyle("-fx-background-color: #22c55e; -fx-background-radius: 999; -fx-background-insets: 0;");
        }
    }

    private void wireButton(MFXButton b, Runnable action) {
        // 不能获得键盘焦点（Tab/点击都不保留焦点）
        b.setFocusTraversable(false);

        // 若仍被聚焦，屏蔽空格触发按钮
        b.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) e.consume();
        });

        // 点击后执行回调，并把焦点归还到 Scene 根（让后续空格给游戏用）
        b.setOnAction(e -> {
            if (action != null) action.run();
            if (root.getScene() != null && root.getScene().getRoot() != null) {
                root.getScene().getRoot().requestFocus();
            }
        });

    }
}
