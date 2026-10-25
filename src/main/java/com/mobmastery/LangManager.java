package com.mobmastery;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LangManager {
    public static final List<String> CODES = List.of("en", "tr");

    private final MobMastery plugin;
    private final Map<String, YamlConfiguration> langs = new HashMap<>();

    public LangManager(MobMastery plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        langs.clear();
        for (String c : CODES) {
            File f = new File(plugin.getDataFolder(), "lang/" + c + ".yml");
            if (!f.exists()) plugin.saveResource("lang/" + c + ".yml", false);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            InputStream in = plugin.getResource("lang/" + c + ".yml");
            if (in != null) {
                // missing keys fall back to the built-in text
                y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
            langs.put(c, y);
        }
    }

    public String defaultCode() {
        String c = plugin.getConfig().getString("language", "en");
        return langs.containsKey(c) ? c : "en";
    }

    public String code(CommandSender s) {
        if (s instanceof Player p) {
            String l = plugin.data().get(p.getUniqueId()).lang;
            if (l != null && langs.containsKey(l)) return l;
        }
        return defaultCode();
    }

    private String raw(String code, String key) {
        YamlConfiguration y = langs.getOrDefault(code, langs.get("en"));
        String v = y.getString(key);
        if (v == null) v = langs.get("en").getString(key);
        return v == null ? key : v;
    }

    public String get(String code, String key, String... kv) {
        String v = raw(code, key);
        for (int i = 0; i + 1 < kv.length; i += 2) v = v.replace("{" + kv[i] + "}", kv[i + 1]);
        return ChatColor.translateAlternateColorCodes('&', v);
    }

    public String get(CommandSender s, String key, String... kv) {
        return get(code(s), key, kv);
    }

    public List<String> list(String code, String key) {
        YamlConfiguration y = langs.getOrDefault(code, langs.get("en"));
        List<String> l = y.getStringList(key);
        if (l.isEmpty()) l = langs.get("en").getStringList(key);
        List<String> out = new ArrayList<>();
        for (String s : l) out.add(ChatColor.translateAlternateColorCodes('&', s));
        return out;
    }

    /** Translated mob name (falls back to English, then to a prettified type name). */
    public String mobName(String code, String type) {
        String key = "mobs." + type;
        YamlConfiguration y = langs.getOrDefault(code, langs.get("en"));
        String v = y.getString(key);
        if (v == null) v = langs.get("en").getString(key);
        return v == null ? Util.pretty(type) : v;
    }

    public void send(CommandSender s, String key, String... kv) {
        s.sendMessage(get(s, "prefix") + get(s, key, kv));
    }
}
