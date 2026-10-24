package com.mobmastery;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player data: one small YAML file per player, kept in memory only while online. */
public class DataManager {
    private final MobMastery plugin;
    private final File dir;
    private final Map<UUID, PlayerData> map = new HashMap<>();

    public DataManager(MobMastery plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "data");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public PlayerData get(UUID id) {
        PlayerData d = map.get(id);
        if (d == null) {
            d = load(id);
            map.put(id, d);
        }
        return d;
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData();
        File f = new File(dir, id + ".yml");
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            d.lang = y.getString("lang");
            for (SkillType t : SkillType.values()) {
                d.level[t.ordinal()] = Math.max(1, y.getInt("skills." + t.key() + ".level", 1));
                d.xp[t.ordinal()] = y.getLong("skills." + t.key() + ".xp", 0L);
            }
        }
        return d;
    }

    private YamlConfiguration snapshot(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        if (d.lang != null) y.set("lang", d.lang);
        for (SkillType t : SkillType.values()) {
            y.set("skills." + t.key() + ".level", d.level[t.ordinal()]);
            y.set("skills." + t.key() + ".xp", d.xp[t.ordinal()]);
        }
        return y;
    }

    private void write(Map<File, YamlConfiguration> jobs) {
        for (Map.Entry<File, YamlConfiguration> e : jobs.entrySet()) {
            try {
                e.getValue().save(e.getKey());
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save " + e.getKey().getName() + ": " + ex.getMessage());
            }
        }
    }

    private Map<File, YamlConfiguration> collectDirty() {
        Map<File, YamlConfiguration> jobs = new HashMap<>();
        for (Map.Entry<UUID, PlayerData> en : map.entrySet()) {
            PlayerData d = en.getValue();
            if (!d.dirty) continue;
            d.dirty = false;
            jobs.put(new File(dir, en.getKey() + ".yml"), snapshot(d));
        }
        return jobs;
    }

    public void saveAllAsync() {
        Map<File, YamlConfiguration> jobs = collectDirty();
        if (jobs.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(jobs));
    }

    public void saveAll() {
        write(collectDirty());
    }

    public void unload(UUID id) {
        PlayerData d = map.remove(id);
        if (d == null || !d.dirty) return;
        Map<File, YamlConfiguration> jobs = new HashMap<>();
        jobs.put(new File(dir, id + ".yml"), snapshot(d));
        write(jobs);
    }
}
