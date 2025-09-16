package org.csu.pixelstrikejavafx.lobby.ui.dialog;

import com.almasb.fxgl.dsl.FXGL;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
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

    private static StackPane mainMenuRootPane;

    public static void setRoot(StackPane root) {
        mainMenuRootPane = root;
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
        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) {
            System.err.println("DialogManager Error: Could not find an active root pane!");
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

            activeRoot.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void showNotification(String message) {
        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) {
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
        activeRoot.getChildren().add(notificationLabel);

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
        sequence.setOnFinished(event -> activeRoot.getChildren().remove(notificationLabel));
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
        Pane activeRoot = getActiveRoot();
        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setToValue(0);
        ft.setOnFinished(event -> {
            activeRoot.getChildren().remove(node);
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
        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) {
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
            activeRoot.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void showCharacterSelection(String title, List<Map<String, Object>> characters, Consumer<Map<String, Object>> onConfirm) {
        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/character-selection-view.fxml"));
            Pane dialogPane = loader.load();
            CharacterSelectionController controller = loader.getController();

            controller.setTitle(title);
            controller.populateCharacters(characters);
            controller.setOnConfirm(selectedChar -> animateOut(dialogPane, () -> onConfirm.accept(selectedChar)));
            controller.setOnCancel(() -> animateOut(dialogPane, () -> onConfirm.accept(null))); // 取消时回调 null

            // 显示对话框
          activeRoot.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void showHistoryDetails(JsonObject details, long matchId) {
        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/history-details-view.fxml"));
            Pane dialogPane = loader.load();
            HistoryDetailsController controller = loader.getController();

            controller.populateDetails(details, matchId);
            controller.setOnClose(() -> animateOut(dialogPane, null));

            activeRoot.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void showMessage(String title, String message, Runnable onConfirm) {
        loadAndShowDialog(controller -> {
            Runnable finalConfirm = () -> animateOut(controller.getDialogContainer().getParent(), onConfirm);
            controller.setupAsMessage(title, message, finalConfirm);
        });
    }
    private static Pane getActiveRoot() {
        var gameScene = FXGL.getGameScene();
        // 如果游戏场景是激活状态，就在游戏场景的UI层上绘制
        if (gameScene != null && gameScene.getRoot().getScene() != null) {
            return (Pane) gameScene.getRoot();
        }
        return mainMenuRootPane;
    }
    public static void showFullScreenMessage(String title, String message, Runnable onConfirm) {
        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) {
            System.err.println("DialogManager Error: Could not find an active root pane!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(DialogManager.class.getResource("/fxml/full-screen-message-view.fxml"));
            Pane dialogPane = loader.load(); // dialogPane 是我们 FXML 的根节点 (StackPane)
            FullScreenMessageController controller = loader.getController();

            // 将关闭逻辑和内容设置传递给新Controller
            controller.setContent(title, message, () -> animateOut(dialogPane, onConfirm));

            // 【关键修复】在将弹窗添加到场景前，将它的尺寸与场景的尺寸绑定！
            dialogPane.prefWidthProperty().bind(activeRoot.widthProperty());
            dialogPane.prefHeightProperty().bind(activeRoot.heightProperty());

            activeRoot.getChildren().add(dialogPane);
            animateIn(dialogPane);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void showActionableNotification(String message,
                                                  String confirmText, Runnable onConfirm,
                                                  String cancelText, Runnable onCancel) {

        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) return;

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
            ft.setOnFinished(e -> activeRoot.getChildren().remove(notificationPane));
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
        activeRoot.getChildren().add(notificationPane);
        StackPane.setAlignment(notificationPane, Pos.CENTER); // 从 BOTTOM_CENTER 改为 CENTER
        StackPane.setMargin(notificationPane, new Insets(250, 0, 250, 0));

        notificationPane.setOpacity(0);
        notificationPane.setTranslateY(50); // 从下方滑入
        FadeTransition ft = new FadeTransition(Duration.millis(300), notificationPane);
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), notificationPane);
        ft.setToValue(1);
        tt.setToY(0);
        new ParallelTransition(ft, tt).play();
    }

    public static void showInGameNotification(String message) {
        // 调用新方法，并传入一个默认的颜色
        showInGameNotification(message, "rgba(0, 0, 0, 0.5)");
    }

    /**
     * @param message 要显示的消息
     * @param backgroundColor CSS格式的背景颜色字符串 (例如 "#27ae60", "rgba(40, 50, 60, 0.8)")
     */
    public static void showInGameNotification(String message, String backgroundColor) {
        Pane activeRoot = getActiveRoot();
        if (activeRoot == null) {
            System.err.println("DialogManager Error: In-game notification failed, root pane is not set!");
            return;
        }

        Text notificationText = new Text(message);
        notificationText.setFont(Font.font("Press Start 2P", 18));
        notificationText.setFill(Color.WHITE); // 文字颜色固定为白色
        notificationText.setStroke(Color.BLACK);
        notificationText.setStrokeWidth(1);

        StackPane notificationPane = new StackPane(notificationText);

        // --- ★ 核心修改：使用传入的颜色参数 ---
        notificationPane.setStyle(
                "-fx-background-color: " + backgroundColor + ";" +
                        "-fx-background-radius: 8;"
        );

        notificationPane.setPadding(new Insets(10, 20, 10, 20));

        activeRoot.getChildren().add(notificationPane);
        StackPane.setAlignment(notificationPane, Pos.TOP_CENTER);

        // (动画部分的代码保持不变)
        notificationPane.setTranslateY(-100);
        notificationPane.setOpacity(0);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(400), notificationPane);
        slideIn.setToY(120);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), notificationPane);
        fadeIn.setToValue(1.0);
        ParallelTransition entrance = new ParallelTransition(slideIn, fadeIn);
        PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), notificationPane);
        fadeOut.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(entrance, delay, fadeOut);
        sequence.setOnFinished(event -> activeRoot.getChildren().remove(notificationPane));
        sequence.play();
    }
}