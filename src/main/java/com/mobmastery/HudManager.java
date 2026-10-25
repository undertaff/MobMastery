package com.mobmastery;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * On-screen display:
 *  - top-center boss bar with the XP progress (appears when you gain XP, hides after a few seconds)
 *  - bottom-center line above the hotbar (health, mana, skill level, ability status) while holding a tool
 * It also regenerates mana once per second.
 */
public class HudManager {

    private final MobMastery plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, BukkitTask> hideTasks = new HashMap<>();
    private final Map<UUID, Long> suppressUntil = new HashMap<>();
    private BukkitTask task;

    public HudManager(MobMastery plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (BukkitTask t : hideTasks.values()) t.cancel();
        hideTasks.clear();
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }

    public void remove(UUID id) {
        BukkitTask t = hideTasks.remove(id);
        if (t != null) t.cancel();
        BossBar b = bars.remove(id);
        if (b != null) b.removeAll();
        suppressUntil.remove(id);
    }

    /** Pauses the bottom HUD line for this player so a short notice stays readable. */
    public void suppress(Player p, long ms) {
        suppressUntil.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    private void tick() {
        FileConfiguration c = plugin.getConfig();
        boolean line = c.getBoolean("hud.enabled", true) && c.getBoolean("hud.actionbar", true);
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.skills().regenMana(p);
            if (!line) continue;
            Long sup = suppressUntil.get(p.getUniqueId());
            if (sup != null && now < sup) continue;
            SkillType t = SkillType.fromTool(p.getInventory().getItemInMainHand().getType());
            if (t == null) continue;
            plugin.skills().actionBar(p, buildLine(p, t));
        }
    }

    @SuppressWarnings("deprecation")
    private String buildLine(Player p, SkillType t) {
        LangManager L = plugin.lang();
        SkillManager sm = plugin.skills();
        String code = L.code(p);
        String id = SkillManager.activeId(t);
        String ability;
        if (!sm.has(p, id)) {
            ability = L.get(code, "hud.locked", "level", String.valueOf(SkillManager.unlockLevel(id)));
        } else {
            long rem = sm.cooldownRemaining(p, t);
            ability = rem > 0
                    ? L.get(code, "hud.cooldown", "s", String.valueOf((rem + 999) / 1000))
                    : L.get(code, "hud.ready");
        }
        return L.get(code, "hud.format",
                "hp", String.valueOf((int) Math.ceil(p.getHealth())),
                "maxhp", String.valueOf((int) Math.ceil(p.getMaxHealth())),
                "mana", String.valueOf((int) sm.mana(p)),
                "maxmana", String.valueOf((int) sm.maxMana(p)),
                "skill", L.get(code, "skill." + t.key()),
                "level", String.valueOf(sm.level(p, t)),
                "ability", ability);
    }

    private BarColor color(SkillType t) {
        return switch (t) {
            case SWORD -> BarColor.RED;
            case AXE -> BarColor.YELLOW;
            case PICKAXE -> BarColor.BLUE;
            case SHOVEL -> BarColor.GREEN;
        };
    }

    /** Shows or refreshes the top XP bar. */
    public void showXp(Player p, SkillType t, int level, long cur, long need, long gained, boolean levelUp) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("hud.enabled", true) || !c.getBoolean("hud.xp-bar", true)) return;

        LangManager L = plugin.lang();
        String code = L.code(p);
        String skill = L.get(code, "skill." + t.key());
        boolean max = level >= plugin.skills().maxLevel();

        String title;
        double progress;
        if (levelUp) {
            title = L.get(code, "hud.levelup-bar", "skill", skill, "level", String.valueOf(level));
            progress = 1.0;
        } else if (max) {
            title = L.get(code, "hud.max-bar", "skill", skill, "level", String.valueOf(level));
            progress = 1.0;
        } else {
            title = L.get(code, "hud.xp-bar", "skill", skill, "level", String.valueOf(level),
                    "gained", String.valueOf(gained), "cur", String.valueOf(cur), "need", String.valueOf(need));
            progress = Math.max(0.0, Math.min(1.0, cur / (double) Math.max(1L, need)));
        }

        UUID id = p.getUniqueId();
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = Bukkit.createBossBar(title, color(t), BarStyle.SEGMENTED_10);
            bar.addPlayer(p);
            bars.put(id, bar);
        }
        bar.setTitle(title);
        bar.setColor(color(t));
        bar.setProgress(progress);
        bar.setVisible(true);

        BukkitTask old = hideTasks.remove(id);
        if (old != null) old.cancel();
        long seconds = Math.max(1, c.getInt("hud.xp-bar-seconds", 4));
        long ticks = 20L * seconds * (levelUp ? 2 : 1);
        final BossBar shown = bar;
        hideTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            shown.setVisible(false);
            hideTasks.remove(id);
        }, ticks));
    }
}
