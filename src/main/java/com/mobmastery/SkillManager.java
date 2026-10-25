package com.mobmastery;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/** XP, levels, ranks, ability unlocks, mana and cooldowns for the four tool skills. */
public class SkillManager {

    public record Ability(String id, SkillType type, int level) { }

    public static final List<Ability> ABILITIES = List.of(
            new Ability("slash", SkillType.SWORD, 5),
            new Ability("lifesteal", SkillType.SWORD, 20),
            new Ability("executioner", SkillType.SWORD, 50),
            new Ability("slam", SkillType.AXE, 5),
            new Ability("cleave", SkillType.AXE, 20),
            new Ability("lumberjack", SkillType.AXE, 40),
            new Ability("frenzy", SkillType.PICKAXE, 5),
            new Ability("haste", SkillType.PICKAXE, 20),
            new Ability("doubledrop", SkillType.PICKAXE, 40),
            new Ability("jackpot", SkillType.PICKAXE, 70),
            new Ability("leap", SkillType.SHOVEL, 5),
            new Ability("digger", SkillType.SHOVEL, 20),
            new Ability("treasure", SkillType.SHOVEL, 40),
            new Ability("shoveldrop", SkillType.SHOVEL, 60)
    );

    /** The right-click ability of each tool. */
    public static String activeId(SkillType t) {
        return switch (t) {
            case SWORD -> "slash";
            case AXE -> "slam";
            case PICKAXE -> "frenzy";
            case SHOVEL -> "leap";
        };
    }

    public static int unlockLevel(String id) {
        for (Ability a : ABILITIES) if (a.id().equals(id)) return a.level();
        return 0;
    }

    private final MobMastery plugin;

    public SkillManager(MobMastery plugin) {
        this.plugin = plugin;
    }

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("skills.max-level", 80));
    }

    public long xpNeeded(int level) {
        double base = plugin.getConfig().getDouble("skills.xp-base", 100);
        double growth = plugin.getConfig().getDouble("skills.xp-growth", 12);
        return (long) (base + Math.pow(level, 1.6) * growth);
    }

    public int level(Player p, SkillType t) {
        return plugin.data().get(p.getUniqueId()).level[t.ordinal()];
    }

    public boolean has(Player p, String abilityId) {
        for (Ability a : ABILITIES) {
            if (a.id().equals(abilityId)) return level(p, a.type()) >= a.level();
        }
        return false;
    }

    /** 16 ranks, 5 levels each (1-5, 6-10, ... 76-80). */
    public String rank(String code, int level) {
        List<String> r = plugin.lang().list(code, "ranks");
        if (r.isEmpty()) return "-";
        return r.get(Math.min(r.size() - 1, Math.max(0, (level - 1) / 5)));
    }

    // ------------------------------------------------------------ action bar

    /** Raw action bar message (used by the HUD itself). */
    public void actionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    /** Short notice in the action bar; the bottom HUD line pauses for a moment so it stays readable. */
    public void notice(Player p, String msg) {
        actionBar(p, msg);
        plugin.hud().suppress(p, 1500);
    }

    // ------------------------------------------------------------ mana & cooldowns

    public double maxMana(Player p) {
        double base = plugin.getConfig().getDouble("skills.mana.base", 50);
        double per = plugin.getConfig().getDouble("skills.mana.per-level", 0.5);
        int sum = 0;
        for (SkillType t : SkillType.values()) sum += level(p, t);
        return base + per * sum;
    }

    public double mana(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        double max = maxMana(p);
        if (d.mana < 0) d.mana = max;
        return Math.min(d.mana, max);
    }

    public boolean spendMana(Player p, double cost) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        double m = mana(p);
        if (m < cost) return false;
        d.mana = m - cost;
        return true;
    }

    public void regenMana(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        double regen = plugin.getConfig().getDouble("skills.mana.regen-per-second", 3);
        d.mana = Math.min(maxMana(p), mana(p) + regen);
    }

    public long cooldownRemaining(Player p, SkillType t) {
        long until = plugin.data().get(p.getUniqueId()).cooldownUntil[t.ordinal()];
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void setCooldown(Player p, SkillType t, long ms) {
        plugin.data().get(p.getUniqueId()).cooldownUntil[t.ordinal()] = System.currentTimeMillis() + ms;
    }

    // ------------------------------------------------------------ XP

    public void addXp(Player p, SkillType t, double amount) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        int i = t.ordinal();
        int max = maxLevel();
        if (d.level[i] >= max) return;

        amount *= plugin.getConfig().getDouble("skills.xp-multiplier", 1.0);
        long add = Math.max(1, Math.round(amount));
        d.xp[i] += add;
        d.dirty = true;

        int old = d.level[i];
        while (d.level[i] < max && d.xp[i] >= xpNeeded(d.level[i])) {
            d.xp[i] -= xpNeeded(d.level[i]);
            d.level[i]++;
        }
        if (d.level[i] >= max) d.xp[i] = 0;

        boolean up = d.level[i] > old;
        if (up) levelUp(p, t, old, d.level[i]);
        plugin.hud().showXp(p, t, d.level[i], d.xp[i], xpNeeded(d.level[i]), add, up);
    }

    private void levelUp(Player p, SkillType t, int old, int now) {
        LangManager L = plugin.lang();
        String code = L.code(p);
        String skill = L.get(code, "skill." + t.key());
        String rank = rank(code, now);

        // center-screen congratulation: skill, the level you left and the level you reached
        p.sendTitle(L.get(code, "levelup.title"),
                L.get(code, "levelup.subtitle", "skill", skill, "old", String.valueOf(old),
                        "level", String.valueOf(now), "rank", rank),
                10, 60, 20);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        for (Ability a : ABILITIES) {
            if (a.type() != t || a.level() <= old || a.level() > now) continue;
            String an = L.get(code, "ability." + a.id() + ".name");
            String ad = L.get(code, "ability." + a.id() + ".desc");
            p.sendMessage(L.get(code, "prefix") + L.get(code, "ability.unlocked-chat", "ability", an, "desc", ad));
            // show the second title after the first one has faded
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendTitle(L.get(code, "ability.unlocked-title"),
                        L.get(code, "ability.unlocked-subtitle", "ability", an), 10, 50, 15);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }, 95L);
        }

        if (now >= maxLevel()) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                String oc = L.code(o);
                o.sendMessage(L.get(oc, "prefix") + L.get(oc, "levelup.max-broadcast",
                        "player", p.getName(), "skill", L.get(oc, "skill." + t.key())));
            }
        }
    }
}
