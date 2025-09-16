package org.csu.pixelstrikejavafx.game.player;

public class CharacterAnimationSet {
    public final AnimationData idle;
    public final AnimationData walk;
    public final AnimationData run;
    public final AnimationData attackBegin;
    public final AnimationData attackIdle;
    public final AnimationData attackEnd;
    public final AnimationData die;

    public CharacterAnimationSet(AnimationData idle, AnimationData walk,
                                 AnimationData run, AnimationData attackBegin,
                                 AnimationData attackIdle, AnimationData attackEnd,
                                 AnimationData die) {
        this.idle = idle;
        this.walk = walk;
        this.run = run;
        this.attackBegin = attackBegin;
        this.attackIdle = attackIdle;
        this.attackEnd = attackEnd;
        this.die = die;
    }
}