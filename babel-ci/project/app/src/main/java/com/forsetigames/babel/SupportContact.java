package com.forsetigames.babel;

public final class SupportContact {
    public final Block support;
    public final float overlap;
    public final float centerX;
    public final float weight;
    public final boolean ground;
    public float share;
    public float adhesion;
    public float shearCapacity;

    public SupportContact(Block support, float overlap, float centerX, float weight, boolean ground) {
        this.support=support; this.overlap=overlap; this.centerX=centerX; this.weight=weight; this.ground=ground;
    }
}
