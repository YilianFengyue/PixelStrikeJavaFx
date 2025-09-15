// In: org/csu/pixelstrikejavafx/lobby/ui/dialog/DialogManager.java

package org.csu.pixelstrikejavafx.lobby.ui.dialog;

import com.google.gson.JsonObject;
import javafx.animation.*;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.PopOver;
import org.csu.pixelstrikejavafx.lobby.ui.CharacterSelectionController;
import org.csu.pixelstrikejavafx.lobby.ui.HistoryDetailsController;
import org.csu.pixelstrikejavafx.lobby.ui.InviteFriendController;
import org.csu.pixelstrikejavafx.lobby.ui.MapSelectionController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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
        // 【关键修改】不再寻找不一定存在的 "#dialogContainer"
        // 直接对传入的整个节点 (node) 设置初始状态和动画

        // 1. 设置初始状态：透明且稍小
        node.setOpacity(0);
        node.setScaleX(0.8);
        node.setScaleY(0.8);

        // 2. 创建淡入动画 (作用于整个节点)
        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setToValue(1);

        // 3. 创建放大动画 (也作用于整个节点)
        ScaleTransition st = new ScaleTransition(Duration.millis(200), node);
        st.setToX(1);
        st.setToY(1);

        // 4. 并行播放两个动画
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

    public static void showInviteFriendPopOver(Node anchorNode) {
        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/invite-friend-view.fxml"));
            VBox inviteContent = loader.load();
            InviteFriendController controller = loader.getController();

            // 1. 创建 ControlsFX 的 PopOver 实例 (这部分不变)
            PopOver popOver = new PopOver(inviteContent);

            popOver.setDetachable(false);
            popOver.setAutoHide(true);
            popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
            popOver.setArrowSize(0);
            popOver.getStyleClass().add("custom-popover");

            // 将关闭逻辑传递给 InviteFriendController (这部分不变)
            controller.setCloseHandler(popOver::hide);

            // --- ↓↓↓【核心修改】手动计算并设置弹窗位置 ↓↓↓ ---

            // 2. 获取锚点（按钮）在整个屏幕上的边界信息
            Bounds anchorBounds = anchorNode.localToScreen(anchorNode.getBoundsInLocal());

            // 3. 定义您想要的垂直偏移量（向下移动多少像素）
            double verticalOffset = -10.0; // 您可以随意修改这个值

            // 4. 计算弹窗应该出现的目标X, Y坐标
            // X坐标：与按钮的中心对齐
            double targetX = anchorBounds.getMinX() + (anchorBounds.getWidth() / 2);
            // Y坐标：在按钮的底部，再加上我们想要的额外偏移量
            double targetY = anchorBounds.getMaxY() + verticalOffset;

            // 5. 使用 show 方法，在计算好的精确屏幕坐标上显示 PopOver
            //    注意：这里我们用 getScene().getWindow() 作为 owner
            popOver.show(anchorNode.getScene().getWindow(), targetX, targetY);

            // --- ↑↑↑ 修改结束 ↑↑↑ ---

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void showMapSelection(List<Map<String, Object>> maps, Consumer<Map<String, Object>> onConfirm) {
        if (rootPane == null) {
            System.err.println("DialogManager root pane is not set!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/map-selection-view.fxml"));
            Pane dialogPane = loader.load();
            MapSelectionController controller = loader.getController();

            // 配置Controller
            controller.populateMaps(maps);
            controller.setOnConfirm(selectedMap -> {
                animateOut(dialogPane, () -> onConfirm.accept(selectedMap));
            });
            controller.setOnCancel(() -> animateOut(dialogPane, null));

            // 显示对话框
            rootPane.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void showCharacterSelection(String title, List<Map<String, Object>> characters, Consumer<Map<String, Object>> onConfirm) {
        if (rootPane == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/character-selection-view.fxml"));
            Pane dialogPane = loader.load();
            CharacterSelectionController controller = loader.getController();

            controller.setTitle(title);
            controller.populateCharacters(characters);
            controller.setOnConfirm(selectedChar -> animateOut(dialogPane, () -> onConfirm.accept(selectedChar)));
            controller.setOnCancel(() -> animateOut(dialogPane, () -> onConfirm.accept(null))); // 取消时回调 null

            // 显示对话框
            rootPane.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void showHistoryDetails(JsonObject details, long matchId) {
        if (rootPane == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/history-details-view.fxml"));
            Pane dialogPane = loader.load();
            HistoryDetailsController controller = loader.getController();

            controller.populateDetails(details, matchId);
            controller.setOnClose(() -> animateOut(dialogPane, null));

            rootPane.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void showActionableNotification(String message,
                                                  String confirmText, Runnable onConfirm,
                                                  String cancelText, Runnable onCancel) {
        if (rootPane == null) return;

        // 1. 创建UI控件
        HBox notificationPane = new HBox();
        notificationPane.getStyleClass().add("actionable-notification-pane");
        notificationPane.getStylesheets().add(
                Objects.requireNonNull(DialogManager.class.getResource("/assets/css/lobby-style.css")).toExternalForm()
        );


        Label infoLabel = new Label(message);
        Button confirmButton = new Button(confirmText);
        confirmButton.getStyleClass().add("accept-button");
        Button cancelButton = new Button(cancelText);
        cancelButton.getStyleClass().add("reject-button");

        notificationPane.getChildren().addAll(infoLabel, confirmButton, cancelButton);

        // 2. 定义关闭通知栏的逻辑
        Runnable closeAction = () -> {
            // 创建退场动画
            FadeTransition ft = new FadeTransition(Duration.millis(300), notificationPane);
            ft.setToValue(0);
            ft.setOnFinished(e -> rootPane.getChildren().remove(notificationPane));
            ft.play();
        };

        // 3. 为按钮绑定事件
        confirmButton.setOnAction(e -> {
            confirmButton.setDisable(true);
            cancelButton.setDisable(true);
            if (onConfirm != null) {
                onConfirm.run();
            }
            closeAction.run(); // 执行完动作后关闭
        });

        cancelButton.setOnAction(e -> {
            confirmButton.setDisable(true);
            cancelButton.setDisable(true);
            if (onCancel != null) {
                onCancel.run();
            }
            closeAction.run(); // 执行完动作后关闭
        });

        // 4. 将通知栏添加到场景并播放入场动画
        rootPane.getChildren().add(notificationPane);
        StackPane.setAlignment(notificationPane, Pos.BOTTOM_CENTER);
        StackPane.setMargin(notificationPane, new Insets(0, 0, 50, 0));

        notificationPane.setOpacity(0);
        notificationPane.setTranslateY(50); // 从下方滑入
        FadeTransition ft = new FadeTransition(Duration.millis(300), notificationPane);
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), notificationPane);
        ft.setToValue(1);
        tt.setToY(0);
        new ParallelTransition(ft, tt).play();
    }
}