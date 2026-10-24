package com.mobmastery;

import java.util.Arrays;

public class PlayerData {
    public String lang; // null = server default
    public final int[] level = new int[SkillType.values().length];
    public final long[] xp = new long[SkillType.values().length];
    public boolean dirty = false;

    public PlayerData() {
        Arrays.fill(level, 1);
    }
}
