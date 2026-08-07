package com.forsetigames.babel;

public enum MaterialType {
    STONE("PEDRA"), CLAY("ARGILA"), WOOD("MADEIRA"), ADOBE("ADOBE"), BRICK("TIJOLO");
    public final String label;
    MaterialType(String label) { this.label = label; }
}
