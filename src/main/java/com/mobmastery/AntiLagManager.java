package com.mobmastery;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight anti-lag: everything runs on slow timers (no per-tick work).
 * - trims overcrowded chunks, clears old ground items with a warning,
 * - pauses natural spawns in crowded chunks or when TPS drops ("lag mode"),
 * - reacts to high heap usage. It never calls System.gc().
 */
public class AntiLagManager implements Listener {

    private enum Group { MONSTER, ANIMAL, OTHER }

    private final MobMastery plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    private boolean enabled, limitSpawns;
    private int maxMonsters, maxAnimals, maxOthers, hardCap, minItemAge, itemIntervalSec, countdown;
    private double lagTps, recoverTps, memThreshold;
    private boolean announceItems = true;
    private final Set<Material> keep = new HashSet<>();
    private final Set<String> disabledWorlds = new HashSet<>();
    private final List<Integer> warnTimes = new ArrayList<>();

    private volatile boolean lagMode = false;
    private double tps = 20.0;
    private long lastNano = 0, lastEmergency = 0;
    private int lowCount = 0, memHits = 0;

    public AntiLagManager(MobMastery plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        FileConfiguration c = plugin.getConfig();
        String p = "anti-lag.";
        enabled = c.getBoolean(p + "enabled", true);
        if (!enabled) return;

        maxMonsters = Math.max(1, c.getInt(p + "entity-limits.max-monsters-per-chunk", 16));
        maxAnimals = Math.max(1, c.getInt(p + "entity-limits.max-animals-per-chunk", 20));
        maxOthers = Math.max(1, c.getInt(p + "entity-limits.max-other-per-chunk", 10));
        limitSpawns = c.getBoolean(p + "spawn-limiter.enabled", true);
        hardCap = Math.max(10, c.getInt(p + "spawn-limiter.chunk-entity-hard-cap", 70));
        minItemAge = Math.max(0, c.getInt(p + "item-clear.min-item-age-seconds", 20)) * 20;
        lagTps = c.getDouble(p + "tps-watch.lag-tps", 16.0);
        recoverTps = c.getDouble(p + "tps-watch.recover-tps", 18.5);
        memThreshold = c.getDouble(p + "memory-watch.threshold", 0.92);

        keep.clear();
        for (String s : c.getStringList(p + "item-clear.keep-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) keep.add(m);
        }
        disabledWorlds.clear();
        disabledWorlds.addAll(c.getStringList(p + "disabled-worlds"));
        warnTimes.clear();
        warnTimes.addAll(c.getIntegerList(p + "item-clear.warn-seconds"));

        lastNano = 0;
        lowCount = 0;
        memHits = 0;
        lagMode = false;
        tps = 20.0;

        BukkitScheduler s = Bukkit.getScheduler();
        tasks.add(s.runTaskTimer(plugin, this::checkTps, 100L, 100L));

        if (c.getBoolean(p + "entity-limits.enabled", true)) {
            long every = 20L * Math.max(5, c.getInt(p + "entity-limits.check-interval-seconds", 30));
            tasks.add(s.runTaskTimer(plugin, () -> trimEntities(1.0), every, every));
        }
        if (c.getBoolean(p + "item-clear.enabled", true)) {
            itemIntervalSec = Math.max(30, c.getInt(p + "item-clear.interval-minutes", 5) * 60);
            countdown = itemIntervalSec;
            tasks.add(s.runTaskTimer(plugin, this::itemTick, 20L, 20L));
        }
        if (c.getBoolean(p + "memory-watch.enabled", true)) {
            tasks.add(s.runTaskTimer(plugin, this::checkMemory, 20L * 60, 20L * 60));
        }
    }

    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
    }

    // ------------------------------------------------------------ helpers

    private void broadcast(String key, String... kv) {
        LangManager L = plugin.lang();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.sendMessage(L.get(pl, "prefix") + L.get(pl, key, kv));
        }
    }

    private void notifyAdmins(String key, String... kv) {
        LangManager L = plugin.lang();
        Bukkit.getConsoleSender().sendMessage(L.get(L.defaultCode(), "prefix") + L.get(L.defaultCode(), key, kv));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.hasPermission("mobmastery.admin")) {
                pl.sendMessage(L.get(pl, "prefix") + L.get(pl, key, kv));
            }
        }
    }

    private Group classify(LivingEntity le) {
        if (le instanceof Boss) return null;
        if (le instanceof Monster || le instanceof Slime || le instanceof Phantom) return Group.MONSTER;
        if (le instanceof Animals) return Group.ANIMAL;
        if (le instanceof WaterMob || le instanceof Ambient) return Group.OTHER;
        return null; // villagers, golems, armor stands, players ... are never touched
    }

    private boolean removable(LivingEntity le) {
        if (le instanceof Tameable t && t.isTamed()) return false;
        if (le.isLeashed() || le.isInsideVehicle() || !le.getPassengers().isEmpty()) return false;
        if (le.getCustomName() != null) {
            // keep name-tagged mobs, but our own leveled mobs may be removed
            boolean ours = le.getPersistentDataContainer().has(plugin.mobs().levelKey(), PersistentDataType.INTEGER);
            if (!ours) return false;
        }
        return true;
    }

    private int limitFor(Group g) {
        return switch (g) {
            case MONSTER -> maxMonsters;
            case ANIMAL -> maxAnimals;
            case OTHER -> maxOthers;
        };
    }

    // ------------------------------------------------------------ cleanup

    /** Removes the oldest mobs above the per-chunk limits. factor 0.5 = half the normal limits. */
    public int trimEntities(double factor) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            if (disabledWorlds.contains(w.getName())) continue;
            Map<Group, Map<Long, List<LivingEntity>>> map = new EnumMap<>(Group.class);
            for (LivingEntity le : w.getLivingEntities()) {
                Group g = classify(le);
                if (g == null || !removable(le)) continue;
                Location l = le.getLocation();
                long key = (((long) (l.getBlockX() >> 4)) << 32) | ((l.getBlockZ() >> 4) & 0xFFFFFFFFL);
                map.computeIfAbsent(g, k -> new HashMap<>()).computeIfAbsent(key, k -> new ArrayList<>()).add(le);
            }
            for (Map.Entry<Group, Map<Long, List<LivingEntity>>> ge : map.entrySet()) {
                int limit = Math.max(1, (int) Math.round(limitFor(ge.getKey()) * factor));
                for (List<LivingEntity> list : ge.getValue().values()) {
                    if (list.size() <= limit) continue;
                    list.sort((a, b) -> Integer.compare(b.getTicksLived(), a.getTicksLived())); // oldest first
                    for (int i = 0; i < list.size() - limit; i++) {
                        list.get(i).remove();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    public int clearItems(int minAgeTicks) {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            if (disabledWorlds.contains(w.getName())) continue;
            for (Item it : w.getEntitiesByClass(Item.class)) {
                if (it.getTicksLived() < minAgeTicks) continue;
                ItemStack st = it.getItemStack();
                if (keep.contains(st.getType())) continue;
                if (st.hasItemMeta() && st.getItemMeta().hasDisplayName()) continue;
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Used by /rpg cleanup: returns {mobsRemoved, itemsRemoved}. */
    public int[] cleanupNow() {
        return new int[]{trimEntities(1.0), clearItems(0)};
    }

    private void emergency() {
        lastEmergency = System.currentTimeMillis();
        int e = trimEntities(0.5);
        int i = clearItems(300);
        notifyAdmins("antilag.cleanup-done", "entities", String.valueOf(e), "items", String.valueOf(i));
    }

    // ------------------------------------------------------------ timers

    private void itemTick() {
        countdown--;
        if (countdown <= 0) {
            int n = clearItems(minItemAge);
            countdown = itemIntervalSec;
            if (announceItems && n > 0) broadcast("antilag.clear-done", "items", String.valueOf(n));
            return;
        }
        if (warnTimes.contains(countdown)) broadcast("antilag.clear-warning", "seconds", String.valueOf(countdown));
    }

    /** Runs every 100 ticks (should take exactly 5 s). A slower run means a lower TPS. */
    private void checkTps() {
        long now = System.nanoTime();
        if (lastNano != 0) {
            double sec = (now - lastNano) / 1.0E9;
            double cur = Math.min(20.0, 100.0 / Math.max(0.1, sec));
            tps = tps * 0.5 + cur * 0.5;
        }
        lastNano = now;

        if (tps < lagTps) {
            lowCount++;
            if (lowCount >= 2 && !lagMode) {
                lagMode = true;
                notifyAdmins("antilag.lag-on", "tps", String.format("%.1f", tps));
                if (System.currentTimeMillis() - lastEmergency > 60000) emergency();
            }
        } else {
            lowCount = 0;
            if (lagMode && tps >= recoverTps) {
                lagMode = false;
                notifyAdmins("antilag.lag-off", "tps", String.format("%.1f", tps));
            }
        }
    }

    private void checkMemory() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long used = rt.totalMemory() - rt.freeMemory();
        if (used / (double) max >= memThreshold) memHits++;
        else memHits = 0;
        // two high readings in a row (garbage that is not collected yet can look like "high")
        if (memHits >= 2 && System.currentTimeMillis() - lastEmergency > 60000) {
            memHits = 0;
            notifyAdmins("antilag.memory-high", "used", String.valueOf(used / 1048576), "max", String.valueOf(max / 1048576));
            emergency();
        }
    }

    // ------------------------------------------------------------ spawn limiter

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!enabled || !limitSpawns) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (lagMode) {
            e.setCancelled(true);
            return;
        }
        Chunk ch = e.getLocation().getChunk();
        if (ch.getEntities().length > hardCap) e.setCancelled(true);
    }

    // ------------------------------------------------------------ status

    public List<String> statusLines(CommandSender s) {
        LangManager L = plugin.lang();
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        long max = rt.maxMemory() / 1048576;
        List<String> out = new ArrayList<>();
        for (String line : L.list(L.code(s), "antilag.status")) {
            out.add(line.replace("{tps}", String.format("%.1f", tps))
                    .replace("{mode}", lagMode ? "ON" : "OFF")
                    .replace("{used}", String.valueOf(used))
                    .replace("{max}", String.valueOf(max)));
        }
        return out;
    }
}
