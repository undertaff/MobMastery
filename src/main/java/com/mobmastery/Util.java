package com.mobmastery;

public final class Util {
    private Util() {}

    /** ZOMBIE_VILLAGER -> Zombie Villager */
    public static String pretty(String s) {
        StringBuilder b = new StringBuilder();
        for (String p : s.toLowerCase().split("_")) {
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }
}
