package com.mobmastery;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Gives some hostile mobs a level that scales their strength, armor and drops. */
public class MobLevelManager implements Listener {

    static final class Tier {
        int min;
        int max;
        String color;
        double xpMult;
        List<DropEntry> items = new ArrayList<>();
        List<DropEntry> rare = new ArrayList<>();
    }

    static final class ArmorSet {
        String prefix;
        int min;
        int max;
        double chance;
        double piece;
    }

    private static final String[] DIM_KEYS = {"overworld", "nether", "end"};
    private static final int[] DIM_MIN = {1, 10, 1};
    private static final int[] DIM_MAX = {35, 60, 10};
    private static final double[] DIM_FULL = {3500, 600, 500};

    private final MobMastery plugin;
    private final NamespacedKey levelKey;
    private final Random rnd = new Random();
    private final List<Tier> tiers = new ArrayList<>();
    private final List<ArmorSet> armorSets = new ArrayList<>();
    private final List<DropEntry> oddities = new ArrayList<>();
    private final Map<String, double[]> dims = new HashMap<>(); // {min, max, fullDistance}

    private boolean enabled, showNameAlways, onlyPlayerKill, armorEnabled, oddEnabled;
    private int maxLevel;
    private double chance, healthPer, damagePer, xpPer, armorDrop, oddChance, oddNormalMult;
    private String nameFormat, nameLanguage;
    private final Set<String> disabledWorlds = new HashSet<>();
    private final Set<String> excluded = new HashSet<>();
    private final Set<String> armorMobs = new HashSet<>();

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
        healthPer = c.getDouble(p + "health-per-level", 0.10);
        damagePer = c.getDouble(p + "damage-per-level", 0.025);
        xpPer = c.getDouble(p + "xp-per-level", 0.03);
        showNameAlways = c.getBoolean(p + "show-name-always", false);
        nameLanguage = c.getString(p + "name-language", "server");
        onlyPlayerKill = c.getBoolean(p + "only-player-kill", true);
        nameFormat = c.getString(p + "name-format", "&8[{color}Lv.{level}&8] &c{name}");
        disabledWorlds.clear();
        disabledWorlds.addAll(c.getStringList(p + "disabled-worlds"));
        excluded.clear();
        for (String s : c.getStringList(p + "excluded-types")) excluded.add(s.toUpperCase());

        dims.clear();
        for (int i = 0; i < DIM_KEYS.length; i++) {
            String b = p + "dimensions." + DIM_KEYS[i] + ".";
            dims.put(DIM_KEYS[i], new double[]{
                    c.getInt(b + "min-level", DIM_MIN[i]),
                    c.getInt(b + "max-level", DIM_MAX[i]),
                    Math.max(1.0, c.getDouble(b + "full-level-distance", DIM_FULL[i]))
            });
        }

        // armor
        armorEnabled = c.getBoolean(p + "armor.enabled", true);
        armorDrop = c.getDouble(p + "armor.drop-chance", 0.05);
        armorMobs.clear();
        for (String s : c.getStringList(p + "armor.mobs")) armorMobs.add(s.toUpperCase());
        armorSets.clear();
        ConfigurationSection as = c.getConfigurationSection(p + "armor.sets");
        if (as != null) {
            for (String k : as.getKeys(false)) {
                ConfigurationSection s = as.getConfigurationSection(k);
                if (s == null) continue;
                ArmorSet a = new ArmorSet();
                a.prefix = k.toUpperCase();
                a.min = s.getInt("min-level", 1);
                a.max = s.getInt("max-level", maxLevel);
                a.chance = s.getDouble("chance", 0.01);
                a.piece = s.getDouble("piece-chance", 0.8);
                armorSets.add(a);
            }
        }

        // oddities
        oddEnabled = c.getBoolean(p + "oddities.enabled", true);
        oddChance = c.getDouble(p + "oddities.chance", 0.012);
        oddNormalMult = c.getDouble(p + "oddities.normal-mob-multiplier", 0.5);
        oddities.clear();
        oddities.addAll(DropEntry.parseAll(c.getStringList(p + "oddities.items"), plugin.getLogger()));

        // drop tiers
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

    private boolean isHostile(LivingEntity le) {
        if (le instanceof Boss) return false;
        return le instanceof Monster || le instanceof Slime || le instanceof Phantom;
    }

    private boolean eligible(LivingEntity le) {
        if (!isHostile(le)) return false;
        if (excluded.contains(le.getType().name())) return false;
        return le.getCustomName() == null;
    }

    private int computeLevel(LivingEntity le) {
        Location l = le.getLocation();
        World w = l.getWorld();
        String key;
        double cx = 0, cz = 0;
        switch (w.getEnvironment()) {
            case NETHER -> key = "nether";
            case THE_END -> key = "end";
            default -> {
                key = "overworld";
                Location sp = w.getSpawnLocation();
                cx = sp.getX();
                cz = sp.getZ();
            }
        }
        double[] d = dims.get(key);
        int min = (int) d[0];
        int max = (int) d[1];
        double dx = l.getX() - cx;
        double dz = l.getZ() - cz;
        double t = Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / d[2]);
        int lvl = min + (int) Math.round(t * (max - min)) + (rnd.nextInt(5) - 2);
        lvl = Math.max(min, Math.min(max, lvl));
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
        LangManager L = plugin.lang();
        String code = nameLanguage.equalsIgnoreCase("server") ? L.defaultCode() : nameLanguage.toLowerCase();
        String name = nameFormat
                .replace("{color}", color)
                .replace("{level}", String.valueOf(level))
                .replace("{name}", L.mobName(code, le.getType().name()));
        le.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
        // false = the tag only shows when you look straight at the mob (not through walls)
        le.setCustomNameVisible(showNameAlways);

        // A custom name can stop mobs from despawning -> keep natural mobs despawnable (less lag)
        switch (e.getSpawnReason()) {
            case NATURAL, SPAWNER, REINFORCEMENTS, RAID, PATROL -> le.setRemoveWhenFarAway(true);
            default -> { }
        }

        applyArmor(le, level);
    }

    private void applyArmor(LivingEntity le, int level) {
        if (!armorEnabled) return;
        if (!armorMobs.contains(le.getType().name())) return;
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        if (eq.getHelmet() != null || eq.getChestplate() != null
                || eq.getLeggings() != null || eq.getBoots() != null) return;

        for (ArmorSet s : armorSets) {
            if (level < s.min || level > s.max) continue;
            if (rnd.nextDouble() >= s.chance) continue;
            equip(eq, s);
            return;
        }
    }

    private void equip(EntityEquipment eq, ArmorSet s) {
        Material helmet = Material.matchMaterial(s.prefix + "_HELMET");
        Material chest = Material.matchMaterial(s.prefix + "_CHESTPLATE");
        Material legs = Material.matchMaterial(s.prefix + "_LEGGINGS");
        Material boots = Material.matchMaterial(s.prefix + "_BOOTS");
        boolean any = false;
        if (helmet != null && rnd.nextDouble() < s.piece) { eq.setHelmet(new ItemStack(helmet)); any = true; }
        if (chest != null && rnd.nextDouble() < s.piece) { eq.setChestplate(new ItemStack(chest)); any = true; }
        if (legs != null && rnd.nextDouble() < s.piece) { eq.setLeggings(new ItemStack(legs)); any = true; }
        if (boots != null && rnd.nextDouble() < s.piece) { eq.setBoots(new ItemStack(boots)); any = true; }
        if (!any && chest != null) eq.setChestplate(new ItemStack(chest));
        float drop = (float) Math.max(0.0, Math.min(1.0, armorDrop));
        eq.setHelmetDropChance(drop);
        eq.setChestplateDropChance(drop);
        eq.setLeggingsDropChance(drop);
        eq.setBootsDropChance(drop);
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
        Player killer = le.getKiller();
        int lv = getLevel(le);

        if (lv <= 0) {
            // normal hostile mob: only a (smaller) chance for a strange item
            if (killer != null && oddEnabled && isHostile(le)) rollOddity(e, killer, 0, oddNormalMult);
            return;
        }

        Tier t = tierFor(lv);
        double mult = (1.0 + lv * xpPer) * (t == null ? 1.0 : t.xpMult);
        e.setDroppedExp((int) Math.round(e.getDroppedExp() * mult));

        if (onlyPlayerKill && killer == null) return;

        if (t != null) {
            for (DropEntry d : t.items) {
                ItemStack s = d.roll(rnd);
                if (s != null) e.getDrops().add(s);
            }
            for (DropEntry d : t.rare) {
                ItemStack s = d.roll(rnd);
                if (s == null) continue;
                e.getDrops().add(s);
                if (killer != null) {
                    LangManager L = plugin.lang();
                    killer.sendMessage(L.get(killer, "prefix") + L.get(killer, "mob.rare-drop",
                            "item", Util.pretty(s.getType().name()),
                            "level", String.valueOf(lv),
                            "mob", L.mobName(L.code(killer), le.getType().name())));
                }
            }
        }
        if (oddEnabled && killer != null) rollOddity(e, killer, lv, 1.0);
    }

    /** One very rare strange item (goat horn, echo shard ...). */
    private void rollOddity(EntityDeathEvent e, Player killer, int level, double mult) {
        if (oddities.isEmpty()) return;
        double ch = oddChance * mult * (1.0 + level * 0.01);
        if (rnd.nextDouble() >= ch) return;
        double total = 0;
        for (DropEntry d : oddities) total += d.chanceValue();
        if (total <= 0) return;
        double r = rnd.nextDouble() * total;
        DropEntry pick = oddities.get(oddities.size() - 1);
        for (DropEntry d : oddities) {
            r -= d.chanceValue();
            if (r <= 0) {
                pick = d;
                break;
            }
        }
        ItemStack s = pick.make(rnd);
        e.getDrops().add(s);
        LangManager L = plugin.lang();
        killer.sendMessage(L.get(killer, "prefix") + L.get(killer, "mob.oddity-drop",
                "item", Util.pretty(s.getType().name())));
    }
}
