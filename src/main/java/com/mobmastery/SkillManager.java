package com.mobmastery;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/** XP, levels, ranks and ability unlocks for the four tool skills. */
public class SkillManager {

    public record Ability(String id, SkillType type, int level) { }

    public static final List<Ability> ABILITIES = List.of(
            new Ability("lifesteal", SkillType.SWORD, 20),
            new Ability("executioner", SkillType.SWORD, 50),
            new Ability("cleave", SkillType.AXE, 20),
            new Ability("lumberjack", SkillType.AXE, 40),
            new Ability("haste", SkillType.PICKAXE, 20),
            new Ability("doubledrop", SkillType.PICKAXE, 40),
            new Ability("jackpot", SkillType.PICKAXE, 70),
            new Ability("digger", SkillType.SHOVEL, 20),
            new Ability("treasure", SkillType.SHOVEL, 40),
            new Ability("shoveldrop", SkillType.SHOVEL, 60)
    );

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

    public void actionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

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

        LangManager L = plugin.lang();
        if (d.level[i] > old) {
            levelUp(p, t, old, d.level[i]);
        } else if (plugin.getConfig().getBoolean("skills.actionbar", true)) {
            String code = L.code(p);
            actionBar(p, L.get(code, "xp.actionbar",
                    "skill", L.get(code, "skill." + t.key()),
                    "xp", String.valueOf(add),
                    "cur", String.valueOf(d.xp[i]),
                    "need", String.valueOf(xpNeeded(d.level[i]))));
        }
    }

    private void levelUp(Player p, SkillType t, int old, int now) {
        LangManager L = plugin.lang();
        String code = L.code(p);
        String skill = L.get(code, "skill." + t.key());
        String rank = rank(code, now);

        p.sendTitle(L.get(code, "levelup.title"),
                L.get(code, "levelup.subtitle", "skill", skill, "level", String.valueOf(now), "rank", rank),
                10, 50, 15);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.sendMessage(L.get(code, "prefix") + L.get(code, "levelup.chat",
                "skill", skill, "level", String.valueOf(now), "rank", rank));

        for (Ability a : ABILITIES) {
            if (a.type() != t || a.level() <= old || a.level() > now) continue;
            String an = L.get(code, "ability." + a.id() + ".name");
            String ad = L.get(code, "ability." + a.id() + ".desc");
            p.sendMessage(L.get(code, "prefix") + L.get(code, "ability.unlocked-chat", "ability", an, "desc", ad));
            // show the second title a moment later so it does not overwrite the level-up title
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendTitle(L.get(code, "ability.unlocked-title"),
                        L.get(code, "ability.unlocked-subtitle", "ability", an), 10, 50, 15);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }, 60L);
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
