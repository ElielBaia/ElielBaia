package com.forsetigames.babel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StructuralSolver {
    public static final float G = 980f;
    private final List<Block> blocks;
    private final UpgradeProvider upgrades;
    private float globalWind;
    private float lastMaxStress;
    private int failuresThisStep;

    public interface UpgradeProvider {
        float materialStrength(MaterialType type);
        float clayBondMultiplier();
        float crewConstructionRate();
    }

    public StructuralSolver(List<Block> blocks, UpgradeProvider upgrades) {
        this.blocks = blocks;
        this.upgrades = upgrades;
    }

    public void setWind(float wind) { globalWind = wind; }
    public float maxStress() { return lastMaxStress; }
    public int failuresThisStep() { return failuresThisStep; }

    public void step(float dt) {
        failuresThisStep = 0;
        List<Block> stable = new ArrayList<>();
        for (Block b : blocks) {
            b.justFailed = false;
            if (b.state == Block.STABLE) {
                b.age += dt;
                if (b.material == MaterialType.CLAY) {
                    float tau = 7.5f / Math.max(.75f, upgrades.clayBondMultiplier());
                    b.cure += (1f - b.cure) * (1f - (float)Math.exp(-dt / tau));
                    if (b.cure > 1f) b.cure = 1f;
                }
                stable.add(b);
            }
        }
        if (stable.isEmpty()) { lastMaxStress = 0f; return; }

        rebuildSupports(stable);
        solveLoadFlow(stable, dt);
        propagateGroundConnection(stable);
        evaluateFailures(stable, dt);
    }

    private void rebuildSupports(List<Block> stable) {
        for (Block b : stable) {
            b.supports.clear();
            b.supportLeft = Float.POSITIVE_INFINITY;
            b.supportRight = Float.NEGATIVE_INFINITY;
            b.supportQuality = 0f;
            b.groundConnected = false;

            if (b.minY() <= 9f) {
                float ov = Math.max(20f, b.w * .92f);
                b.supports.add(new SupportContact(null, ov, b.x, ov, true));
                b.supportLeft = b.x - ov*.5f;
                b.supportRight = b.x + ov*.5f;
                b.supportCenter = b.x;
                b.supportQuality = Math.min(1f, ov / Math.max(1f,b.w));
            }
        }

        for (Block b : stable) {
            float bottom = b.minY();
            List<SupportContact> candidates = new ArrayList<>();
            for (Block s : stable) {
                if (s == b || s.y >= b.y) continue;
                float top = s.maxY();
                float gap = bottom - top;
                if (gap < -18f || gap > 32f) continue;
                float left = Math.max(b.minX(), s.minX());
                float right = Math.min(b.maxX(), s.maxX());
                float overlap = right-left;
                if (overlap < 12f) continue;

                float align = 1f - Math.min(1f, Math.abs(gap)/34f);
                float tiltPenalty = 1f - Math.min(.72f, Math.abs(b.angle-s.angle)*.65f);
                float eff = overlap * Math.max(.12f, align*tiltPenalty);
                if (eff <= 3f) continue;
                float cx=(left+right)*.5f;
                candidates.add(new SupportContact(s, overlap, cx, eff, false));
            }

            Collections.sort(candidates, (a,c) -> Float.compare(c.weight,a.weight));
            int keep=Math.min(4,candidates.size());
            for (int i=0;i<keep;i++) {
                SupportContact c=candidates.get(i);
                b.supports.add(c);
                b.supportLeft=Math.min(b.supportLeft,c.centerX-c.overlap*.5f);
                b.supportRight=Math.max(b.supportRight,c.centerX+c.overlap*.5f);
            }
            if (!b.supports.isEmpty()) {
                // All actual supports participate in the effective contact polygon, including
                // the foundation. The earlier implementation skipped the ground contact here,
                // unintentionally replacing a valid ~92% foundation contact by zero.
                float sum=0f, center=0f, ov=0f;
                for (SupportContact c : b.supports) {
                    sum += Math.max(1f, c.weight);
                    center += c.centerX * Math.max(1f, c.weight);
                    ov += c.overlap;
                }
                if (sum>0f) b.supportCenter=center/sum;
                else if (b.supportLeft < b.supportRight) b.supportCenter=(b.supportLeft+b.supportRight)*.5f;
                b.supportQuality=Math.min(1f, ov/Math.max(1f,b.w));
            }
        }
    }

    private void solveLoadFlow(List<Block> stable, float dt) {
        for (Block b : stable) {
            float m=b.mass();
            b.combinedMass=m;
            b.load=m*G;
            b.momentX=m*b.x;
            b.horizontalLoad = windForce(b);
            b.stress=0f;
        }

        List<Block> highToLow=new ArrayList<>(stable);
        highToLow.sort((a,b)->Float.compare(b.y,a.y));

        for (Block b : highToLow) {
            if (b.supports.isEmpty()) continue;
            float sum=0f;
            for (SupportContact c:b.supports) sum += Math.max(1f,c.weight);
            if (sum<=0f) continue;

            for (SupportContact c:b.supports) {
                c.share=Math.max(1f,c.weight)/sum;
                c.adhesion=jointAdhesion(b,c);
                if (!c.ground && c.support!=null) {
                    Block s=c.support;
                    float f=c.share;
                    s.load += b.load*f;
                    s.combinedMass += b.combinedMass*f;
                    s.momentX += b.momentX*f;
                    s.horizontalLoad += b.horizontalLoad*f;
                }
            }
        }
    }

    private void propagateGroundConnection(List<Block> stable) {
        List<Block> lowToHigh=new ArrayList<>(stable);
        lowToHigh.sort(Comparator.comparingDouble(a->a.y));
        for (Block b:lowToHigh) {
            boolean connected=false;
            for (SupportContact c:b.supports) {
                if (c.ground) { connected=true; break; }
                if (c.support!=null && c.support.groundConnected) { connected=true; break; }
            }
            b.groundConnected=connected;
        }
    }

    private void evaluateFailures(List<Block> stable, float dt) {
        lastMaxStress=0f;
        List<Block> toFail=new ArrayList<>();
        for (Block b:stable) {
            if (b.supports.isEmpty()) {
                toFail.add(b);
                b.stress=1.2f;
                continue;
            }

            float comX = b.combinedMass>0.0001f ? b.momentX/b.combinedMass : b.x;
            float span = Math.max(18f,b.supportRight-b.supportLeft);
            float half=span*.5f;
            float contactFactor=Math.max(.16f,Math.min(1f,b.supportQuality));
            float constructionFactor=.52f+.48f*clamp01(b.construction);
            float strengthUpgrade=upgrades.materialStrength(b.material);
            float baseCapacity=b.ownWeight(G)*b.spec.compressionFactor*strengthUpgrade;
            float compCapacity=baseCapacity*contactFactor*constructionFactor;
            float compRatio=b.load/Math.max(1f,compCapacity);

            float totalAdhesion=0f;
            float effectiveFriction=b.spec.friction;
            for (SupportContact c:b.supports) {
                totalAdhesion += c.adhesion;
                if (!c.ground && c.support!=null) effectiveFriction=(effectiveFriction+c.support.spec.friction)*.5f;
            }
            float shearCapacity=Math.max(20f,effectiveFriction*b.load*contactFactor+totalAdhesion);
            float shearRatio=Math.abs(b.horizontalLoad)/shearCapacity;

            float bondMoment=totalAdhesion*Math.max(10f,half)*.50f;
            float overturnDemand=b.load*Math.max(0f,Math.abs(comX-b.supportCenter)-half*.38f);
            float resistingMoment=b.load*half*.64f+bondMoment;
            float overturnRatio=overturnDemand/Math.max(20f,resistingMoment);

            float grace=1f + b.spec.flexibility*.14f;
            float ratio=Math.max(compRatio,Math.max(shearRatio,overturnRatio))/grace;
            b.stress=ratio;
            lastMaxStress=Math.max(lastMaxStress,ratio);

            if (ratio>.86f) b.fatigue += Math.max(0f,ratio-.86f)*dt*.010f*(.5f+b.spec.brittleness);
            else b.fatigue=Math.max(0f,b.fatigue-dt*.003f);

            if (ratio>1f) {
                float over=ratio-1f;
                float rate=(.05f + over*over*1.9f)*(0.55f+b.spec.brittleness*.85f);
                b.damage += rate*dt;
            } else if (ratio<.72f) {
                b.damage=Math.max(0f,b.damage-dt*.006f*(1f-b.spec.brittleness*.35f));
            }
            b.damage=Math.min(1.4f,b.damage+b.fatigue*dt*.02f);

            if ((overturnRatio>1.05f || shearRatio>1.18f) && b.damage>.20f) {
                float dir=Math.signum(comX-b.supportCenter);
                if (dir==0f) dir=Math.signum(b.horizontalLoad);
                if (dir==0f) dir=1f;
                b.vx += dir*(35f+55f*Math.min(2f,ratio));
                b.omega += dir*.22f;
                if (ratio>1.28f || b.damage>.55f) toFail.add(b);
            }
            if (b.damage>=1f || ratio>2.25f) toFail.add(b);
        }

        if (!toFail.isEmpty()) {
            Set<Integer> failedIds=new HashSet<>();
            for (Block b:toFail) {
                if (b.state!=Block.STABLE || failedIds.contains(b.id)) continue;
                wakeWithDependents(b, failedIds);
            }
        }
    }

    private void wakeWithDependents(Block root, Set<Integer> failedIds) {
        ArrayDeque<Block> q=new ArrayDeque<>();
        q.add(root);
        int woke=0;
        while(!q.isEmpty() && woke<80) {
            Block b=q.removeFirst();
            if (b.state!=Block.STABLE || failedIds.contains(b.id)) continue;
            failedIds.add(b.id);
            b.state=Block.ACTIVE;
            b.sleepTimer=0f;
            b.justFailed=true;
            b.vy=Math.min(b.vy,-12f);
            if (Math.abs(b.vx)<8f) b.vx=(float)((b.id%2==0?1:-1)*(8+Math.min(70,b.stress*22)));
            failuresThisStep++;
            woke++;

            for (Block upper:blocks) {
                if (upper.state!=Block.STABLE) continue;
                float dependence=0f;
                for (SupportContact c:upper.supports) {
                    if (!c.ground && c.support==b) dependence+=c.share;
                }
                if (dependence>.52f || (dependence>.28f && upper.stress>.92f)) q.add(upper);
            }
        }
    }

    private float jointAdhesion(Block upper, SupportContact c) {
        if (c.ground) return 0f;
        Block lower=c.support;
        if (lower==null) return 0f;
        float clay=0f;
        if (upper.material==MaterialType.CLAY) clay=Math.max(clay,upper.cure);
        if (lower.material==MaterialType.CLAY) clay=Math.max(clay,lower.cure);
        float base=(upper.spec.adhesion+lower.spec.adhesion)*.5f;
        float mortarBoost=clay>0f ? (1.2f+clay*3.8f)*upgrades.clayBondMultiplier() : 1f;
        return c.overlap*base*mortarBoost*58f;
    }

    private float windForce(Block b) {
        float elevation=Math.max(0f,b.y);
        float exposure=1f+(float)Math.sqrt(elevation/900f)*.62f;
        return globalWind*b.h*b.spec.windDrag*.020f*exposure;
    }

    private static float clamp01(float v) { return Math.max(0f,Math.min(1f,v)); }
}
