package org.csu.pixelstrikejavafx.game.player;

import javafx.util.Duration;

public class AnimationData {
    public final String imageFile;
    public final int frameCount;
    public final Duration duration;
    public final int startFrame;
    public final int endFrame;

    public AnimationData(String imageFile, int frameCount, double durationSeconds, int startFrame, int endFrame) {
        this.imageFile = imageFile;
        this.frameCount = frameCount;
        this.duration = Duration.seconds(durationSeconds);
        this.startFrame = startFrame;
        this.endFrame = endFrame;
    }
}