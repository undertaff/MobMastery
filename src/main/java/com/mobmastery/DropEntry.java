package com.mobmastery;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/** One line of the config drop format: MATERIAL:min:max:chance */
public final class DropEntry {
    public final Material material;
    private final int min;
    private final int max;
    private final double chance;

    private DropEntry(Material material, int min, int max, double chance) {
        this.material = material;
        this.min = min;
        this.max = max;
        this.chance = chance;
    }

    public static DropEntry parse(String s) {
        try {
            String[] p = s.split(":");
            Material m = Material.matchMaterial(p[0].trim());
            if (m == null || !m.isItem()) return null;
            int min = Integer.parseInt(p[1].trim());
            int max = Integer.parseInt(p[2].trim());
            double ch = Double.parseDouble(p[3].trim());
            if (max < min) max = min;
            return new DropEntry(m, Math.max(1, min), Math.max(1, max), ch);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<DropEntry> parseAll(List<String> lines, Logger log) {
        List<DropEntry> out = new ArrayList<>();
        for (String s : lines) {
            DropEntry d = parse(s);
            if (d == null) log.warning("Invalid item entry in config.yml (skipped): " + s);
            else out.add(d);
        }
        return out;
    }

    /** The chance (or weight) value from the config line. */
    public double chanceValue() {
        return chance;
    }

    /** Rolls the chance. Returns null when nothing drops. */
    public ItemStack roll(Random r) {
        if (r.nextDouble() >= chance) return null;
        return make(r);
    }

    /** Ignores the chance value. */
    public ItemStack make(Random r) {
        int amt = min + (max > min ? r.nextInt(max - min + 1) : 0);
        return new ItemStack(material, amt);
    }
}
