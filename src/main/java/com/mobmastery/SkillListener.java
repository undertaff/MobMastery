package com.mobmastery;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SkillListener implements Listener {

    private enum Cat { ORE, STONE, LOG, DIRT }

    private final MobMastery plugin;
    private final SkillManager skills;
    private final Random rnd = new Random();
    /** Blocks placed by players (no XP for them, stops place-and-break farming). Bounded size. */
    private final Set<String> placed = new HashSet<>();
    /** true while the plugin itself damages mobs (so the sword/axe bonuses are not applied twice). */
    private boolean internal = false;

    public SkillListener(MobMastery plugin) {
        this.plugin = plugin;
        this.skills = plugin.skills();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.data().unload(e.getPlayer().getUniqueId());
        plugin.hud().remove(e.getPlayer().getUniqueId());
    }

    private boolean roll(String key, double def) {
        return rnd.nextDouble() < plugin.getConfig().getDouble("skills.abilities." + key, def);
    }

    private boolean isHostile(Entity n) {
        return n instanceof Monster || n instanceof Slime || n instanceof Phantom;
    }

    // ---------------------------------------------------------------- right-click abilities

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        // on blocks only while sneaking, so normal block use (path, stripping logs ...) is not affected
        if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        SkillType t = SkillType.fromTool(p.getInventory().getItemInMainHand().getType());
        if (t == null) return;
        useActive(p, t);
    }

    private void useActive(Player p, SkillType t) {
        LangManager L = plugin.lang();
        String code = L.code(p);
        String id = SkillManager.activeId(t);

        if (!skills.has(p, id)) {
            skills.notice(p, L.get(code, "active.locked",
                    "skill", L.get(code, "skill." + t.key()),
                    "level", String.valueOf(SkillManager.unlockLevel(id))));
            return;
        }
        long rem = skills.cooldownRemaining(p, t);
        if (rem > 0) {
            skills.notice(p, L.get(code, "active.cooldown", "s", String.valueOf((rem + 999) / 1000)));
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        String b = "skills.active." + id + ".";
        double cost = cfg.getDouble(b + "mana", 25);
        if (!skills.spendMana(p, cost)) {
            skills.notice(p, L.get(code, "active.no-mana",
                    "mana", String.valueOf((int) skills.mana(p)),
                    "cost", String.valueOf((int) cost)));
            return;
        }
        skills.setCooldown(p, t, (long) (cfg.getDouble(b + "cooldown", 6) * 1000));
        int lvl = skills.level(p, t);

        switch (id) {
            case "slash" -> slash(p, cfg.getDouble(b + "damage", 6) + lvl * 0.15);
            case "slam" -> slam(p, cfg.getDouble(b + "damage", 5) + lvl * 0.12);
            case "frenzy" -> frenzy(p, cfg.getInt(b + "seconds", 12) * 20);
            case "leap" -> leap(p);
            default -> { }
        }
        skills.notice(p, L.get(code, "active.used", "ability", L.get(code, "ability." + id + ".name")));
    }

    private void slash(Player p, double dmg) {
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) return;
        dir.normalize();
        internal = true;
        try {
            for (Entity n : p.getNearbyEntities(4.5, 2.5, 4.5)) {
                if (!isHostile(n)) continue;
                Vector to = n.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (to.lengthSquared() > 0.01 && to.normalize().dot(dir) < 0.3) continue;
                ((LivingEntity) n).damage(dmg, p);
            }
        } finally {
            internal = false;
        }
        Location fx = p.getEyeLocation().add(dir.clone().multiply(1.6));
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, fx, 3, 0.6, 0.2, 0.6, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
    }

    private void slam(Player p, double dmg) {
        internal = true;
        try {
            for (Entity n : p.getNearbyEntities(4.5, 2.0, 4.5)) {
                if (!isHostile(n)) continue;
                LivingEntity le = (LivingEntity) n;
                le.damage(dmg, p);
                Vector away = n.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 0.0001) away.normalize().multiply(0.9);
                away.setY(0.45);
                le.setVelocity(away);
            }
        } finally {
            internal = false;
        }
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 25, 1.5, 0.1, 1.5, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);
    }

    private void frenzy(Player p, int ticks) {
        haste(p, 1, ticks);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0.1);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
    }

    private void leap(Player p) {
        Vector v = p.getLocation().getDirection().multiply(1.1);
        v.setY(Math.max(v.getY(), 0.0) + 0.55);
        p.setVelocity(v);
        p.setFallDistance(0f);
        PotionEffectType slow = PotionEffectType.getByName("SLOW_FALLING");
        if (slow != null) p.addPotionEffect(new PotionEffect(slow, 80, 0, true, false, false));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.4, 0.1, 0.4, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
    }

    // ---------------------------------------------------------------- combat

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (internal) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player || victim instanceof ArmorStand) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;

        SkillType t = SkillType.fromTool(p.getInventory().getItemInMainHand().getType());
        if (t != SkillType.SWORD && t != SkillType.AXE) return;

        FileConfiguration cfg = plugin.getConfig();
        int lvl = skills.level(p, t);
        double mult = 1.0 + lvl * cfg.getDouble("skills.damage-per-level", 0.004);

        if (t == SkillType.SWORD && skills.has(p, "executioner")) {
            if (victim.getHealth() < victim.getMaxHealth() * 0.30) {
                mult *= 1.0 + cfg.getDouble("skills.abilities.executioner-bonus", 0.30);
            }
        }
        e.setDamage(e.getDamage() * mult);
        double finalDmg = e.getDamage();

        if (t == SkillType.SWORD && skills.has(p, "lifesteal") && roll("lifesteal-chance", 0.15)) {
            double heal = cfg.getDouble("skills.abilities.lifesteal-amount", 2.0);
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + heal));
        }

        if (t == SkillType.AXE && skills.has(p, "cleave") && roll("cleave-chance", 0.25)) {
            double part = finalDmg * cfg.getDouble("skills.abilities.cleave-damage", 0.5);
            internal = true;
            try {
                for (Entity n : victim.getNearbyEntities(3, 2, 3)) {
                    if (n instanceof Monster m && n != victim) m.damage(part, p);
                }
            } finally {
                internal = false;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        Player p = v.getKiller();
        if (p == null || p.getGameMode() == GameMode.CREATIVE) return;
        if (v instanceof Player || v instanceof ArmorStand) return;

        SkillType t = SkillType.fromTool(p.getInventory().getItemInMainHand().getType());
        if (t == null) return;

        FileConfiguration cfg = plugin.getConfig();
        int lv = plugin.mobs().getLevel(v);
        double xp;
        if (lv > 0) {
            xp = cfg.getDouble("skills.xp.kill-leveled-base", 6) + lv * cfg.getDouble("skills.xp.kill-per-level", 1.5);
        } else if (v instanceof Monster) {
            xp = cfg.getDouble("skills.xp.kill-normal", 4);
        } else {
            xp = cfg.getDouble("skills.xp.kill-passive", 1);
        }
        skills.addXp(p, t, xp);
    }

    // ---------------------------------------------------------------- blocks

    private Cat category(org.bukkit.Material m) {
        String n = m.name();
        if (n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS")) return Cat.ORE;
        switch (n) {
            case "STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE",
                 "NETHERRACK", "SANDSTONE", "RED_SANDSTONE", "BASALT", "BLACKSTONE", "END_STONE", "TUFF" -> {
                return Cat.STONE;
            }
            case "DIRT", "GRASS_BLOCK", "COARSE_DIRT", "PODZOL", "MYCELIUM", "ROOTED_DIRT", "SAND", "RED_SAND",
                 "GRAVEL", "CLAY", "SOUL_SAND", "SOUL_SOIL", "SNOW_BLOCK", "FARMLAND" -> {
                return Cat.DIRT;
            }
            default -> { }
        }
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return Cat.LOG;
        return null;
    }

    private boolean matches(SkillType t, Cat c) {
        return switch (t) {
            case PICKAXE -> c == Cat.ORE || c == Cat.STONE;
            case AXE -> c == Cat.LOG;
            case SHOVEL -> c == Cat.DIRT;
            default -> false;
        };
    }

    private String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (category(b.getType()) == null) return;
        if (placed.size() >= 50000) placed.clear();
        placed.add(key(b));
    }

    private void haste(Player p, int amp, int ticks) {
        PotionEffectType type = PotionEffectType.getByName("HASTE");
        if (type == null) type = PotionEffectType.getByName("FAST_DIGGING");
        if (type == null) return;
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.getAmplifier() > amp) return;
        p.addPotionEffect(new PotionEffect(type, ticks, amp, true, false, false));
    }

    private int hasteTicks() {
        return plugin.getConfig().getInt("skills.abilities.haste-seconds", 3) * 20;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Cat c = category(b.getType());
        if (c == null) return;
        boolean wasPlaced = placed.remove(key(b));
        if (wasPlaced || p.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        SkillType t = SkillType.fromTool(tool.getType());
        if (t == null || !matches(t, c)) return;

        FileConfiguration cfg = plugin.getConfig();
        String n = b.getType().name();
        double xp = switch (c) {
            case ORE -> (n.contains("DIAMOND") || n.contains("EMERALD") || n.equals("ANCIENT_DEBRIS"))
                    ? cfg.getDouble("skills.xp.ore-rare", 15) : cfg.getDouble("skills.xp.ore", 6);
            case STONE -> cfg.getDouble("skills.xp.stone", 1);
            case LOG -> cfg.getDouble("skills.xp.log", 3);
            case DIRT -> cfg.getDouble("skills.xp.dirt", 1);
        };
        skills.addXp(p, t, xp);

        // ---- passive abilities (checked after XP so a fresh unlock works immediately)
        int extra = 0;
        String proc = null;
        boolean treasure = false;
        switch (t) {
            case PICKAXE -> {
                if (skills.has(p, "haste")) haste(p, skills.level(p, t) >= 60 ? 1 : 0, hasteTicks());
                if (skills.has(p, "jackpot") && roll("jackpot-chance", 0.05)) { extra = 2; proc = "proc.triple"; }
                else if (skills.has(p, "doubledrop") && roll("doubledrop-chance", 0.20)) { extra = 1; proc = "proc.double"; }
            }
            case AXE -> {
                if (skills.has(p, "lumberjack") && roll("lumberjack-chance", 0.20)) { extra = 1; proc = "proc.double"; }
            }
            case SHOVEL -> {
                if (skills.has(p, "digger")) haste(p, skills.level(p, t) >= 60 ? 1 : 0, hasteTicks());
                if (skills.has(p, "shoveldrop") && roll("shovel-doubledrop-chance", 0.15)) { extra = 1; proc = "proc.double"; }
                if (skills.has(p, "treasure") && roll("treasure-chance", 0.03)) treasure = true;
            }
            default -> { }
        }

        if (!e.isDropItems()) return;
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);

        if (extra > 0) {
            Collection<ItemStack> drops = b.getDrops(tool, p);
            for (int i = 0; i < extra; i++) {
                for (ItemStack s : drops) b.getWorld().dropItemNaturally(loc, s.clone());
            }
            skills.notice(p, plugin.lang().get(p, proc));
        }
        if (treasure) {
            List<DropEntry> list = DropEntry.parseAll(cfg.getStringList("skills.treasure-items"), plugin.getLogger());
            if (!list.isEmpty()) {
                ItemStack s = list.get(rnd.nextInt(list.size())).make(rnd);
                b.getWorld().dropItemNaturally(loc, s);
                skills.notice(p, plugin.lang().get(p, "proc.treasure"));
            }
        }
    }
}
