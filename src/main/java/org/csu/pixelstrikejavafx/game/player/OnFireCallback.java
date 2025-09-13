package org.csu.pixelstrikejavafx.game.player;

/**
 * 一个简单的回调接口，当武器成功开火时被调用。
 */
public interface OnFireCallback {
    void onSuccessfulShot();
}