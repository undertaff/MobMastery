package com.mobmastery;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Gives some hostile mobs a level (1-60) that scales their strength and their drops. */
public class MobLevelManager implements Listener {

    static final class Tier {
        int min;
        int max;
        String color;
        double xpMult;
        List<DropEntry> items = new ArrayList<>();
        List<DropEntry> rare = new ArrayList<>();
    }

    private final MobMastery plugin;
    private final NamespacedKey levelKey;
    private final Random rnd = new Random();
    private final List<Tier> tiers = new ArrayList<>();

    private boolean enabled, showName, onlyPlayerKill;
    private int maxLevel, netherBonus, endBonus;
    private double chance, blocksPerLevel, healthPer, damagePer, xpPer;
    private String nameFormat;
    private final Set<String> disabledWorlds = new HashSet<>();
    private final Set<String> excluded = new HashSet<>();

    public MobLevelManager(MobMastery plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "mob_level");
        reload();
    }

    public NamespacedKey levelKey() { return levelKey; }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        String p = "mob-levels.";
        enabled = c.getBoolean(p + "enabled", true);
        maxLevel = Math.max(1, c.getInt(p + "max-level", 60));
        chance = c.getDouble(p + "leveled-chance", 0.5);
        blocksPerLevel = Math.max(1, c.getDouble(p + "blocks-per-level", 100));
        netherBonus = c.getInt(p + "nether-bonus", 15);
        endBonus = c.getInt(p + "end-bonus", 30);
        healthPer = c.getDouble(p + "health-per-level", 0.10);
        damagePer = c.getDouble(p + "damage-per-level", 0.025);
        xpPer = c.getDouble(p + "xp-per-level", 0.03);
        showName = c.getBoolean(p + "show-name", true);
        onlyPlayerKill = c.getBoolean(p + "only-player-kill", true);
        nameFormat = c.getString(p + "name-format", "&8[{color}Lv.{level}&8] &c{name}");
        disabledWorlds.clear();
        disabledWorlds.addAll(c.getStringList(p + "disabled-worlds"));
        excluded.clear();
        for (String s : c.getStringList(p + "excluded-types")) excluded.add(s.toUpperCase());

        tiers.clear();
        ConfigurationSection ts = c.getConfigurationSection(p + "tiers");
        if (ts != null) {
            for (String k : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(k);
                if (s == null) continue;
                Tier t = new Tier();
                t.min = s.getInt("min-level", 1);
                t.max = s.getInt("max-level", maxLevel);
                t.color = s.getString("color", "&f");
                t.xpMult = s.getDouble("xp-multiplier", 1.0);
                t.items = DropEntry.parseAll(s.getStringList("items"), plugin.getLogger());
                t.rare = DropEntry.parseAll(s.getStringList("rare-items"), plugin.getLogger());
                tiers.add(t);
            }
        }
    }

    public int getLevel(Entity e) {
        Integer v = e.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private Tier tierFor(int level) {
        for (Tier t : tiers) if (level >= t.min && level <= t.max) return t;
        return null;
    }

    private boolean eligible(LivingEntity le) {
        if (le instanceof Boss) return false;
        if (!(le instanceof Monster || le instanceof Slime || le instanceof Phantom)) return false;
        if (excluded.contains(le.getType().name())) return false;
        return le.getCustomName() == null;
    }

    private int computeLevel(LivingEntity le) {
        Location l = le.getLocation();
        World w = l.getWorld();
        Location sp = w.getSpawnLocation();
        double dx = l.getX() - sp.getX();
        double dz = l.getZ() - sp.getZ();
        int lvl = 1 + (int) (Math.sqrt(dx * dx + dz * dz) / blocksPerLevel);
        switch (w.getEnvironment()) {
            case NETHER -> lvl += netherBonus;
            case THE_END -> lvl += endBonus;
            default -> { }
        }
        lvl += rnd.nextInt(7) - 2; // small random spread (-2 .. +4)
        return Math.max(1, Math.min(maxLevel, lvl));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!enabled) return;
        LivingEntity le = e.getEntity();
        if (!eligible(le)) return;
        if (disabledWorlds.contains(le.getWorld().getName())) return;
        if (rnd.nextDouble() >= chance) return;
        if (getLevel(le) > 0) return;

        int level = computeLevel(le);
        le.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);

        Tier t = tierFor(level);
        String color = t == null ? "&f" : t.color;
        String name = nameFormat
                .replace("{color}", color)
                .replace("{level}", String.valueOf(level))
                .replace("{name}", Util.pretty(le.getType().name()));
        le.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
        le.setCustomNameVisible(showName);

        // A custom name can stop mobs from despawning -> keep natural mobs despawnable (less lag)
        switch (e.getSpawnReason()) {
            case NATURAL, SPAWNER, REINFORCEMENTS, RAID, PATROL -> le.setRemoveWhenFarAway(true);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!enabled) return;
        Entity victim = e.getEntity();

        // Leveled mob takes less damage -> acts like it has more health (no attribute tricks)
        if (victim instanceof LivingEntity && !(victim instanceof Player)) {
            int lv = getLevel(victim);
            if (lv > 0) {
                String cause = e.getCause().name();
                if (!(cause.equals("VOID") || cause.equals("SUICIDE") || cause.equals("KILL"))) {
                    e.setDamage(e.getDamage() / (1.0 + lv * healthPer));
                }
            }
        }

        // Leveled mob deals more damage to players
        if (victim instanceof Player && e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) d = shooter;
            int lv = getLevel(d);
            if (lv > 0) e.setDamage(e.getDamage() * (1.0 + lv * damagePer));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!enabled) return;
        LivingEntity le = e.getEntity();
        int lv = getLevel(le);
        if (lv <= 0) return;
        Tier t = tierFor(lv);
        double mult = (1.0 + lv * xpPer) * (t == null ? 1.0 : t.xpMult);
        e.setDroppedExp((int) Math.round(e.getDroppedExp() * mult));

        Player killer = le.getKiller();
        if (onlyPlayerKill && killer == null) return;
        if (t == null) return;

        for (DropEntry d : t.items) {
            ItemStack s = d.roll(rnd);
            if (s != null) e.getDrops().add(s);
        }
        for (DropEntry d : t.rare) {
            ItemStack s = d.roll(rnd);
            if (s == null) continue;
            e.getDrops().add(s);
            if (killer != null) {
                killer.sendMessage(plugin.lang().get(killer, "prefix") + plugin.lang().get(killer, "mob.rare-drop",
                        "item", Util.pretty(s.getType().name()),
                        "level", String.valueOf(lv),
                        "mob", Util.pretty(le.getType().name())));
            }
        }
    }
}
