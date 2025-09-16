// src/main/java/org/csu/pixelstrikejavafx/game/core/MusicManager.java
package org.csu.pixelstrikejavafx.game.core;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.audio.Music;

public class MusicManager {

    private static MusicManager instance;
    private Music currentMusic;

    private MusicManager() {
        // 私有构造函数
    }

    public static synchronized MusicManager getInstance() {
        if (instance == null) {
            instance = new MusicManager();
        }
        return instance;
    }

    /**
     * 播放主菜单/大厅的背景音乐。
     */
    public void playMenuMusic() {
        stopMusic();
        try {
            // 【最终解决方案】直接调用 setGlobalMusicVolume 方法
            FXGL.getSettings().setGlobalMusicVolume(0.03);

            currentMusic = FXGL.getAssetLoader().loadMusic("menu_music.mp3");
            FXGL.getAudioPlayer().loopMusic(currentMusic);
        } catch (Exception e) {
            System.err.println("无法加载主菜单音乐 (menu_music.mp3): " + e.getMessage());
        }
    }

    /**
     * 播放游戏内的战斗背景音乐。
     */
    public void playInGameMusic() {
        stopMusic();
        try {
            // 【最终解决方案】同样，在这里也直接调用 setter
            FXGL.getSettings().setGlobalMusicVolume(0.03);

            currentMusic = FXGL.getAssetLoader().loadMusic("ingame_music.mp3");
            FXGL.getAudioPlayer().loopMusic(currentMusic);
        } catch (Exception e) {
            System.err.println("无法加载游戏内音乐 (ingame_music.mp3): " + e.getMessage());
        }
    }

    /**
     * 停止当前正在播放的音乐。
     */
    public void stopMusic() {
        if (currentMusic != null) {
            FXGL.getAudioPlayer().stopMusic(currentMusic);
            currentMusic = null;
        }
    }
}