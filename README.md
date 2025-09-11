

```
org.csu.pixelstrikejavafx
├── PixelGameApp.java   // 应用程序入口
├── core                // 核心/通用模块
│   ├── PixelStrikeSceneFactory.java
│   ├── GlobalState.java   // 全局状态
│   └── MatchSuccessEvent.java // 跨模块通信事件
│       
│
├── lobby               // 大厅模块 (所有登录、注册、社交、匹配逻辑)
│   ├── network         // 大厅专用的网络服务
│   │   ├── ApiClient.java          (统一的REST API客户端)
│   │   └── NetworkManager.java     (大厅的WebSocket管理器)
│   ├── ui              // 大厅的所有界面和控制器
│   │   ├── FXMLMainMenu.java
│   │   ├── HistoryController.java
│   │   ├── InviteFriendController.java
│   │   ├── LobbyController.java
│   │   ├── LoginController.java
│   │   ├── RegisterController.java
│   │   ├── UIManager.java
│   │   └── RoomController.java
│   └── events          // 大厅内部事件 (如果需要)
│
└── game                // 游戏核心模块 (进入游戏后的一切)
    ├── services        // 游戏核心服务 (从PixelGameApp解耦的一部分)
    │   ├── NetworkService.java     (游戏场景的WebSocket客户端)
    │   └── PlayerManager.java
    ├── player          // 玩家相关的类
    │   ├── Player.java
    │   ├── PlayerAnimator.java
    │   ├── PlayerHealth.java
    │   ├── PlayerShooting.java
    │   └── RemoteAvatar.java
    ├── world           // 游戏世界构建
    │   ├── MapBuilder.java
    │   └── CameraFollow.java
    ├── ui              // 游戏内UI
    │   ├── MultiplayerDock.java
    │   └── PlayerHUD.java  
    └── core            // 游戏核心定义
        ├── GameConfig.java
        └── GameType.java

resources
└── fxml
    ├── history-view.fxml
    ├── invite-friend-view.fxml
    └── ... (其他大厅相关的FXML文件)
```