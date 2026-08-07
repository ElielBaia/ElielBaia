package com.forsetigames.babel;

import android.content.Context;
import android.content.SharedPreferences;

public final class SaveData implements StructuralSolver.UpgradeProvider {
    private final SharedPreferences p;
    public int seals, bestHeight, totalRuns;
    public int stone, clay, wood, adobe, brick, crew;

    public SaveData(Context c){
        p=c.getSharedPreferences("babel_save_v3",Context.MODE_PRIVATE);
        load();
    }
    public void load(){
        seals=p.getInt("seals",0); bestHeight=p.getInt("bestHeight",0); totalRuns=p.getInt("runs",0);
        stone=p.getInt("stone",0); clay=p.getInt("clay",0); wood=p.getInt("wood",0);
        adobe=p.getInt("adobe",0); brick=p.getInt("brick",0); crew=p.getInt("crew",0);
    }
    public void save(){
        p.edit().putInt("seals",seals).putInt("bestHeight",bestHeight).putInt("runs",totalRuns)
         .putInt("stone",stone).putInt("clay",clay).putInt("wood",wood).putInt("adobe",adobe)
         .putInt("brick",brick).putInt("crew",crew).apply();
    }
    public int level(MaterialType t){
        switch(t){case STONE:return stone;case CLAY:return clay;case WOOD:return wood;case ADOBE:return adobe;default:return brick;}
    }
    public int upgradeCost(MaterialType t){return upgradeCost(level(t));}
    public int crewCost(){return upgradeCost(crew)+40;}
    private int upgradeCost(int level){return (int)(110*Math.pow(level+1,1.55));}
    public boolean buy(MaterialType t){
        int lvl=level(t); if(lvl>=10)return false; int cost=upgradeCost(lvl);if(seals<cost)return false;seals-=cost;
        switch(t){case STONE:stone++;break;case CLAY:clay++;break;case WOOD:wood++;break;case ADOBE:adobe++;break;case BRICK:brick++;break;}
        save();return true;
    }
    public boolean buyCrew(){int cost=crewCost();if(crew>=10||seals<cost)return false;seals-=cost;crew++;save();return true;}
    public void addReward(int reward,int height){seals+=Math.max(0,reward);totalRuns++;bestHeight=Math.max(bestHeight,height);save();}

    @Override public float materialStrength(MaterialType type){return 1f+level(type)*.045f;}
    @Override public float clayBondMultiplier(){return 1f+clay*.055f;}
    @Override public float crewConstructionRate(){return 1f+crew*.085f;}
}
