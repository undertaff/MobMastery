package com.mobmastery;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MobMastery extends JavaPlugin {

    private DataManager data;
    private LangManager lang;
    private SkillManager skills;
    private MobLevelManager mobs;
    private AntiLagManager antiLag;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataManager(this);
        lang = new LangManager(this);
        skills = new SkillManager(this);
        mobs = new MobLevelManager(this);
        antiLag = new AntiLagManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(mobs, this);
        pm.registerEvents(new SkillListener(this), this);
        pm.registerEvents(antiLag, this);

        RpgCommand cmd = new RpgCommand(this);
        for (String name : new String[]{"rpg", "skills"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }

        antiLag.start();
        // Autosave every 5 minutes (written on a background thread)
        getServer().getScheduler().runTaskTimer(this, () -> data.saveAllAsync(), 20L * 60 * 5, 20L * 60 * 5);
        getLogger().info("MobMastery enabled.");
    }

    @Override
    public void onDisable() {
        if (antiLag != null) antiLag.stop();
        if (data != null) data.saveAll();
    }

    public void reloadAll() {
        reloadConfig();
        lang.load();
        mobs.reload();
        antiLag.start();
    }

    public DataManager data() { return data; }
    public LangManager lang() { return lang; }
    public SkillManager skills() { return skills; }
    public MobLevelManager mobs() { return mobs; }
    public AntiLagManager antiLag() { return antiLag; }
}
