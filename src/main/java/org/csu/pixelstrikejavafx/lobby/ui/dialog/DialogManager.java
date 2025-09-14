// In: org/csu/pixelstrikejavafx/lobby/ui/dialog/DialogManager.java

package org.csu.pixelstrikejavafx.lobby.ui.dialog;

import javafx.animation.*;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public class DialogManager {

    private static StackPane rootPane;

    public static void setRoot(StackPane root) {
        rootPane = root;
    }

    // Show simple message box
    public static void showMessage(String title, String message) {
        loadAndShowDialog(controller -> {
            Runnable finalConfirm = () -> animateOut(controller.getDialogContainer().getParent(), null);
            controller.setupAsMessage(title, message, finalConfirm);
        });
    }

    // Show confirmation box (OK / Cancel)
    public static void showConfirmation(String title, String message, Runnable onConfirm) {
        loadAndShowDialog(controller -> {
            Runnable finalConfirm = () -> animateOut(controller.getDialogContainer().getParent(), onConfirm);
            Runnable finalCancel = () -> animateOut(controller.getDialogContainer().getParent(), null);
            controller.setupAsConfirmation(title, message, finalConfirm, finalCancel);
        });
    }

    // Show input box
    public static void showInput(String title, String message, Consumer<String> onConfirm) {
        loadAndShowDialog(controller -> {
            Consumer<String> finalConfirm = (result) -> animateOut(controller.getDialogContainer().getParent(), () -> onConfirm.accept(result));
            Runnable finalCancel = () -> animateOut(controller.getDialogContainer().getParent(), null);
            controller.setupAsInput(title, message, finalConfirm, finalCancel);
        });
    }

    // (This cancellable version is now redundant but kept for compatibility)
    public static void showCancellableInput(String title, String message, Consumer<String> onConfirm, Runnable onCancel) {
        loadAndShowDialog(controller -> {
            Consumer<String> finalConfirm = (result) -> animateOut(controller.getDialogContainer().getParent(), () -> onConfirm.accept(result));
            Runnable finalCancel = () -> animateOut(controller.getDialogContainer().getParent(), onCancel);
            controller.setupAsInput(title, message, finalConfirm, finalCancel);
        });
    }

    private static void loadAndShowDialog(Consumer<CustomDialogController> setupAction) {
        if (rootPane == null) {
            System.err.println("DialogManager root pane is not set!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/custom-dialog-pane.fxml"));
            StackPane dialogPane = loader.load();
            CustomDialogController controller = loader.getController();
            dialogPane.getStylesheets().add(
                    Objects.requireNonNull(DialogManager.class.getResource("/assets/css/lobby-style.css")).toExternalForm()
            );

            // The provided lambda (setupAction) configures the controller
            // with our new animation-wrapped callbacks.
            setupAction.accept(controller);

            rootPane.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void showNotification(String message) {
        if (rootPane == null) {
            System.err.println("DialogManager 错误: rootPane 未设置!");
            return;
        }

        // 1. 创建标签并加载样式 (这部分不变)
        Label notificationLabel = new Label(message);
        notificationLabel.getStyleClass().add("game-notification");
        notificationLabel.getStylesheets().add(
                Objects.requireNonNull(DialogManager.class.getResource("/assets/css/lobby-style.css")).toExternalForm()
        );

        // 将通知添加到场景中
        rootPane.getChildren().add(notificationLabel);

        // --- ↓↓↓ 调整位置的核心代码在这里 ↓↓↓ ---

        // 【位置控制 1: 对齐锚点】
        //  决定通知的基础位置。可选值有：
        //  Pos.TOP_CENTER, Pos.TOP_RIGHT, Pos.TOP_LEFT,
        //  Pos.BOTTOM_CENTER, Pos.BOTTOM_RIGHT, Pos.BOTTOM_LEFT, Pos.CENTER
        StackPane.setAlignment(notificationLabel, Pos.TOP_CENTER);

        // 【位置控制 2: 动画初始位置 (让它先藏在屏幕外)】
        //  因为是顶部中央，所以我们让它在Y轴负方向（屏幕上方）作为起点
        notificationLabel.setTranslateY(-100); // 初始Y坐标在屏幕顶部之外
        notificationLabel.setTranslateX(400);     // X坐标不动
        notificationLabel.setOpacity(1);        // 确保它是不透明的

        // 【位置控制 3: 入场动画 (从屏幕外移动到屏幕内)】
        //  我们让它沿着Y轴移动到最终位置
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(400), notificationLabel);
        slideIn.setToY(95); // 最终Y坐标在距离顶部边缘95像素的位置

        // --- ↑↑↑ 核心代码结束 ↑↑↑ ---

        // 停留和退场动画 (这部分通常无需修改)
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), notificationLabel);
        fadeOut.setToValue(0);

        // 组合并播放动画
        SequentialTransition sequence = new SequentialTransition(slideIn, delay, fadeOut);
        sequence.setOnFinished(event -> rootPane.getChildren().remove(notificationLabel));
        sequence.play();
    }

    // --- Animation Helper Methods ---

    private static void animateIn(Node node) {
        Node dialogContainer = node.lookup("#dialogContainer");
        node.setOpacity(0);
        dialogContainer.setScaleX(0.8);
        dialogContainer.setScaleY(0.8);

        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setToValue(1);

        ScaleTransition st = new ScaleTransition(Duration.millis(200), dialogContainer);
        st.setToX(1);
        st.setToY(1);

        ParallelTransition pt = new ParallelTransition(ft, st);
        pt.play();
    }

    private static void animateOut(Node node, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setToValue(0);
        ft.setOnFinished(event -> {
            rootPane.getChildren().remove(node);
            if (onFinished != null) {
                onFinished.run();
            }
        });
        ft.play();
    }
}