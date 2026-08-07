package com.forsetigames.babel;

import java.util.ArrayList;
import java.util.List;

public final class Block {
    public static final int HELD=0, ACTIVE=1, STABLE=2, BROKEN=3, LOST=4;
    public final int id;
    public final MaterialType material;
    public final MaterialSpec spec;
    public float x, y, w, h, angle;
    public float vx, vy, omega;
    public int state = HELD;
    public float sleepTimer;
    public float age;
    public float cure;
    public float construction = .42f;
    public float damage;
    public float fatigue;
    public float stress;
    public float load;
    public float horizontalLoad;
    public float combinedMass;
    public float momentX;
    public float supportLeft, supportRight, supportCenter, supportQuality;
    public float lastImpact;
    public boolean groundConnected;
    public boolean justFailed;
    public boolean countedCollapse;
    public final List<SupportContact> supports = new ArrayList<>();

    public Block(int id, MaterialType material, float x, float y, float w, float h, float angle) {
        this.id=id; this.material=material; this.spec=MaterialSpec.of(material);
        this.x=x; this.y=y; this.w=w; this.h=h; this.angle=angle;
        this.cure = material == MaterialType.CLAY ? .08f : 1f;
    }

    public float area() { return w*h; }
    public float mass() { return Math.max(2f, area()/10000f * spec.density * 10f); }
    public float inertia() { return mass()*(w*w+h*h)/12f; }
    public float ownWeight(float g) { return mass()*g; }
    public float minX() { return x - aabbHalfW(); }
    public float maxX() { return x + aabbHalfW(); }
    public float minY() { return y - aabbHalfH(); }
    public float maxY() { return y + aabbHalfH(); }
    public float aabbHalfW() { float c=Math.abs((float)Math.cos(angle)), s=Math.abs((float)Math.sin(angle)); return (w*c+h*s)*.5f; }
    public float aabbHalfH() { float c=Math.abs((float)Math.cos(angle)), s=Math.abs((float)Math.sin(angle)); return (h*c+w*s)*.5f; }
    public boolean dynamic() { return state==ACTIVE; }
    public boolean stable() { return state==STABLE; }
}
