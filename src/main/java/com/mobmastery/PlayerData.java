package com.mobmastery;

import java.util.Arrays;

public class PlayerData {
    public String lang; // null = server default
    public final int[] level = new int[SkillType.values().length];
    public final long[] xp = new long[SkillType.values().length];
    public boolean dirty = false;

    // runtime only (not saved)
    public double mana = -1; // -1 = fill up on first use
    public final long[] cooldownUntil = new long[SkillType.values().length];

    public PlayerData() {
        Arrays.fill(level, 1);
    }
}
