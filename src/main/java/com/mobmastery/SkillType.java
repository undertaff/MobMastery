package com.mobmastery;

import org.bukkit.Material;

public enum SkillType {
    SWORD("sword"), AXE("axe"), PICKAXE("pickaxe"), SHOVEL("shovel");

    private final String key;

    SkillType(String key) { this.key = key; }

    public String key() { return key; }

    public static SkillType fromTool(Material m) {
        if (m == null) return null;
        String n = m.name();
        if (n.endsWith("_SWORD")) return SWORD;
        if (n.endsWith("_PICKAXE")) return PICKAXE;
        if (n.endsWith("_AXE")) return AXE;
        if (n.endsWith("_SHOVEL")) return SHOVEL;
        return null;
    }

    public static SkillType fromKey(String s) {
        for (SkillType t : values()) if (t.key.equalsIgnoreCase(s)) return t;
        return null;
    }
}
