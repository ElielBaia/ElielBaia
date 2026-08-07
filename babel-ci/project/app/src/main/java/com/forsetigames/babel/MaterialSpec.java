package com.forsetigames.babel;

public final class MaterialSpec {
    public final MaterialType type;
    public final float density;
    public final float compressionFactor;
    public final float friction;
    public final float adhesion;
    public final float restitution;
    public final float flexibility;
    public final float brittleness;
    public final float windDrag;

    public MaterialSpec(MaterialType type, float density, float compressionFactor,
                        float friction, float adhesion, float restitution,
                        float flexibility, float brittleness, float windDrag) {
        this.type = type;
        this.density = density;
        this.compressionFactor = compressionFactor;
        this.friction = friction;
        this.adhesion = adhesion;
        this.restitution = restitution;
        this.flexibility = flexibility;
        this.brittleness = brittleness;
        this.windDrag = windDrag;
    }

    public static MaterialSpec of(MaterialType t) {
        switch (t) {
            case STONE: return new MaterialSpec(t, 2.40f, 6.00f, .68f, .02f, .07f, .07f, .82f, .85f);
            case CLAY:  return new MaterialSpec(t, 1.55f, 2.25f, .88f, 2.60f, .02f, .72f, .22f, .70f);
            case WOOD:  return new MaterialSpec(t, .82f, 4.90f, .76f, .06f, .15f, .68f, .30f, 1.15f);
            case ADOBE: return new MaterialSpec(t, 1.35f, 3.45f, .81f, .16f, .04f, .25f, .48f, .90f);
            default:    return new MaterialSpec(t, 1.82f, 4.35f, .72f, .08f, .05f, .14f, .66f, .95f);
        }
    }
}
