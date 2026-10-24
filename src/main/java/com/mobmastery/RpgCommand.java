package com.mobmastery;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RpgCommand implements CommandExecutor, TabCompleter {

    private final MobMastery plugin;

    public RpgCommand(MobMastery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        LangManager L = plugin.lang();
        if (c.getName().equalsIgnoreCase("skills")) {
            showSkills(s);
            return true;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "lang", "language" -> lang(s, a);
            case "reload" -> {
                if (!admin(s)) return true;
                plugin.reloadAll();
                L.send(s, "reloaded");
            }
            case "cleanup" -> {
                if (!admin(s)) return true;
                int[] r = plugin.antiLag().cleanupNow();
                L.send(s, "antilag.cleanup-done", "entities", String.valueOf(r[0]), "items", String.valueOf(r[1]));
            }
            case "status" -> {
                if (!admin(s)) return true;
                for (String line : plugin.antiLag().statusLines(s)) s.sendMessage(line);
            }
            case "setlevel" -> setLevel(s, a);
            default -> help(s);
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("mobmastery.admin")) return true;
        plugin.lang().send(s, "no-permission");
        return false;
    }

    private void help(CommandSender s) {
        for (String line : plugin.lang().list(plugin.lang().code(s), "help")) s.sendMessage(line);
    }

    private void lang(CommandSender s, String[] a) {
        LangManager L = plugin.lang();
        if (!(s instanceof Player p)) {
            L.send(s, "players-only");
            return;
        }
        if (a.length < 2) {
            L.send(s, "lang.usage", "current", L.code(s));
            return;
        }
        String code = a[1].toLowerCase(Locale.ROOT);
        if (!LangManager.CODES.contains(code)) {
            L.send(s, "lang.invalid");
            return;
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        d.lang = code;
        d.dirty = true;
        p.sendMessage(L.get(code, "prefix") + L.get(code, "lang.changed", "lang", code));
    }

    private void showSkills(CommandSender s) {
        LangManager L = plugin.lang();
        if (!(s instanceof Player p)) {
            L.send(s, "players-only");
            return;
        }
        SkillManager sm = plugin.skills();
        PlayerData d = plugin.data().get(p.getUniqueId());
        String code = L.code(p);
        int max = sm.maxLevel();

        p.sendMessage(L.get(code, "skills.header"));
        for (SkillType t : SkillType.values()) {
            int lvl = d.level[t.ordinal()];
            String need = lvl >= max ? L.get(code, "skills.max-xp") : String.valueOf(sm.xpNeeded(lvl));
            p.sendMessage(L.get(code, "skills.line",
                    "skill", L.get(code, "skill." + t.key()),
                    "level", String.valueOf(lvl),
                    "max", String.valueOf(max),
                    "rank", sm.rank(code, lvl),
                    "xp", String.valueOf(d.xp[t.ordinal()]),
                    "need", need));
        }
        p.sendMessage(L.get(code, "skills.abilities-header"));
        for (SkillManager.Ability ab : SkillManager.ABILITIES) {
            String name = L.get(code, "ability." + ab.id() + ".name");
            if (d.level[ab.type().ordinal()] >= ab.level()) {
                p.sendMessage(L.get(code, "skills.ability-unlocked",
                        "name", name, "desc", L.get(code, "ability." + ab.id() + ".desc")));
            } else {
                p.sendMessage(L.get(code, "skills.ability-locked",
                        "name", name,
                        "skill", L.get(code, "skill." + ab.type().key()),
                        "level", String.valueOf(ab.level())));
            }
        }
    }

    private void setLevel(CommandSender s, String[] a) {
        LangManager L = plugin.lang();
        if (!admin(s)) return;
        if (a.length < 4) {
            L.send(s, "admin.usage-setlevel");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            L.send(s, "admin.player-not-found");
            return;
        }
        SkillType st = SkillType.fromKey(a[2]);
        int lv;
        try {
            lv = Integer.parseInt(a[3]);
        } catch (NumberFormatException ex) {
            L.send(s, "admin.usage-setlevel");
            return;
        }
        if (st == null) {
            L.send(s, "admin.usage-setlevel");
            return;
        }
        lv = Math.max(1, Math.min(lv, plugin.skills().maxLevel()));
        PlayerData d = plugin.data().get(target.getUniqueId());
        d.level[st.ordinal()] = lv;
        d.xp[st.ordinal()] = 0;
        d.dirty = true;
        L.send(s, "admin.setlevel-ok", "player", target.getName(), "skill", st.key(), "level", String.valueOf(lv));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("skills")) return List.of();
        List<String> opts = new ArrayList<>();
        if (a.length == 1) {
            opts.add("help");
            opts.add("lang");
            if (s.hasPermission("mobmastery.admin")) opts.addAll(List.of("reload", "cleanup", "status", "setlevel"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("lang")) {
            opts.addAll(LangManager.CODES);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("setlevel") && s.hasPermission("mobmastery.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) opts.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("setlevel") && s.hasPermission("mobmastery.admin")) {
            for (SkillType t : SkillType.values()) opts.add(t.key());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(last)) out.add(o);
        return out;
    }
}
