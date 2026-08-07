package com.forsetigames.babel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class DifficultyDirector {
    private final Random rng;
    private final List<MaterialType> bag=new ArrayList<>();
    private int sinceClay=0;
    private float phase=0f;

    public DifficultyDirector(long seed){rng=new Random(seed);refill(0f);}

    public MaterialType next(float height){
        if(bag.isEmpty())refill(height);
        MaterialType t=bag.remove(bag.size()-1);
        if(sinceClay>=7 && t!=MaterialType.CLAY){
            int idx=bag.indexOf(MaterialType.CLAY);
            if(idx>=0){MaterialType swap=bag.get(idx);bag.set(idx,t);t=swap;}
            else t=MaterialType.CLAY;
        }
        if(t==MaterialType.CLAY)sinceClay=0;else sinceClay++;
        return t;
    }

    private void refill(float height){
        bag.clear();
        float d=difficulty(height);
        int stone=d<.45f?4:3;
        int clay=d<.4f?2:1;
        int wood=2,adobe=2,brick=d>.18f?2:1;
        for(int i=0;i<stone;i++)bag.add(MaterialType.STONE);
        for(int i=0;i<clay;i++)bag.add(MaterialType.CLAY);
        for(int i=0;i<wood;i++)bag.add(MaterialType.WOOD);
        for(int i=0;i<adobe;i++)bag.add(MaterialType.ADOBE);
        for(int i=0;i<brick;i++)bag.add(MaterialType.BRICK);
        Collections.shuffle(bag,rng);
    }

    public float difficulty(float height){
        float h=Math.max(0,height)/100f;
        return Math.min(1f,(float)(1.0-Math.exp(-h/24.0)));
    }
    public float wind(float height,float time){
        float d=difficulty(height);
        if(d<.12f)return 0f;
        phase+=.0025f;
        float base=24f+170f*d*d;
        float slow=(float)Math.sin(time*.27+phase)*base;
        float gust=(float)Math.sin(time*.93+1.7)*base*.31f+(float)Math.sin(time*2.11)*base*.12f;
        return slow+gust;
    }
    public float craneSway(float height,float time){
        float d=difficulty(height);
        return (float)Math.sin(time*(.72f+d*.52f))*18f*d;
    }
    public float shapeIrregularity(float height){return .02f+difficulty(height)*.10f;}
    public Random rng(){return rng;}
}
