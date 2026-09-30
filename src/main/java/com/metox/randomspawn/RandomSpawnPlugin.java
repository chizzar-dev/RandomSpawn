package com.metox.randomspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Ilk giris ve yeniden dogusta rastgele guvenli konum, ayrica /rtp komutu.
 *
 * Guvenli nokta aramasi tek tick'te degil, tick'lere bolunerek yapilir;
 * boylece chunk yukleme sunucuyu kilitlemez.
 */
public class RandomSpawnPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Random random = new Random();

    /** Isinlanmayi bekleyen oyuncular (warmup). */
    private final Map<UUID, Warmup> warmups = new HashMap<UUID, Warmup>();
    /** Son /rtp kullanim zamanlari. */
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();

    private static class Warmup {
        Location from;
        int ticksLeft;
        BukkitRunnable task;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        for (String c : new String[]{"randomspawn", "rtp"}) {
            if (getCommand(c) != null) {
                getCommand(c).setExecutor(this);
                getCommand(c).setTabCompleter(this);
            }
        }
        getLogger().info("RandomSpawn aktif - algilanan surum 1." + Compat.MINOR
                + (Compat.PATCH > 0 ? "." + Compat.PATCH : ""));
    }

    @Override
    public void onDisable() {
        for (Warmup w : new ArrayList<Warmup>(warmups.values())) {
            if (w.task != null) {
                try {
                    w.task.cancel();
                } catch (Throwable ignored) {
                }
            }
        }
        warmups.clear();
    }

    // ------------------------------------------------------------------
    // Giris / yeniden dogus
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("first-join", true)) return;
        final Player p = e.getPlayer();
        if (p.hasPlayedBefore()) return;

        final World world = resolveWorld(null);
        if (world == null) return;

        searchAsyncish(world, new Callback() {
            @Override
            public void done(Location loc) {
                if (loc != null && p.isOnline()) {
                    p.teleport(loc);
                    msg(p, "teleported");
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!getConfig().getBoolean("respawn", true)) return;
        final Player p = e.getPlayer();

        // Yatak / anchor spawn'i varsa ve zorlama kapaliysa dokunma
        if (!getConfig().getBoolean("override-bed", false)) {
            try {
                if (e.isBedSpawn()) return;
            } catch (Throwable ignored) {
            }
            try {
                if (p.getBedSpawnLocation() != null) return;
            } catch (Throwable ignored) {
            }
        }

        World world = resolveWorld(null);
        if (world == null) return;

        // Respawn olayinda konum hemen gerekli; hizli bir deneme yap,
        // bulunamazsa oyuncu normal spawn'a doger ve arkadan isinlanir.
        Location quick = trySome(world, Math.min(6, getConfig().getInt("max-attempts", 40)));
        if (quick != null) {
            e.setRespawnLocation(quick);
            return;
        }
        final Player fp = p;
        searchAsyncish(world, new Callback() {
            @Override
            public void done(Location loc) {
                if (loc != null && fp.isOnline()) fp.teleport(loc);
            }
        });
    }

    // ------------------------------------------------------------------
    // /rtp
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ENGLISH);

        if (cmd.equals("randomspawn")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("randomspawn.reload")) {
                    send(sender, "no-permission");
                    return true;
                }
                reloadConfig();
                send(sender, "reloaded");
                return true;
            }
            sender.sendMessage(Compat.color("&7/" + label + " reload"));
            return true;
        }

        // /rtp [oyuncu]
        if (args.length >= 1) {
            if (!sender.hasPermission("randomspawn.rtp.others")) {
                send(sender, "no-permission");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                send(sender, "player-not-found");
                return true;
            }
            startTeleport(target, true);
            if (!sender.equals(target)) {
                sender.sendMessage(Compat.color(getConfig()
                        .getString("messages.sent-other", "&aOyuncu isinlandi.")
                        .replace("%player%", target.getName())));
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            send(sender, "players-only");
            return true;
        }
        Player p = (Player) sender;
        if (!getConfig().getBoolean("rtp.enabled", true)) {
            send(p, "rtp-disabled");
            return true;
        }
        if (!p.hasPermission("randomspawn.rtp")) {
            send(p, "no-permission");
            return true;
        }
        if (warmups.containsKey(p.getUniqueId())) {
            send(p, "already-warming");
            return true;
        }

        long left = cooldownLeft(p);
        if (left > 0) {
            p.sendMessage(Compat.color(getConfig()
                    .getString("messages.cooldown", "&cBeklemen gerek: &e%time%s")
                    .replace("%time%", String.valueOf(left))));
            return true;
        }
        startTeleport(p, false);
        return true;
    }

    private long cooldownLeft(Player p) {
        if (p.hasPermission("randomspawn.rtp.bypass")) return 0;
        int cd = getConfig().getInt("rtp.cooldown", 60);
        if (cd <= 0) return 0;
        Long last = cooldowns.get(p.getUniqueId());
        if (last == null) return 0;
        long passed = (System.currentTimeMillis() - last) / 1000L;
        return passed >= cd ? 0 : cd - passed;
    }

    /** Bekleme suresini baslatir, bitince isinlanir. */
    private void startTeleport(final Player p, boolean instant) {
        int warmup = instant || p.hasPermission("randomspawn.rtp.bypass")
                ? 0 : getConfig().getInt("rtp.warmup", 3);

        if (warmup <= 0) {
            doTeleport(p);
            return;
        }

        final Warmup w = new Warmup();
        w.from = p.getLocation().clone();
        w.ticksLeft = warmup * 20;
        w.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    warmups.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                if (w.ticksLeft <= 0) {
                    warmups.remove(p.getUniqueId());
                    cancel();
                    doTeleport(p);
                    return;
                }
                if (w.ticksLeft % 20 == 0) {
                    int sec = w.ticksLeft / 20;
                    Compat.actionBar(p, getConfig()
                            .getString("messages.countdown", "&eIsinlaniyorsun... &f%time%")
                            .replace("%time%", String.valueOf(sec)));
                    Compat.sound(p, "click", 0.6f, 1.4f);
                }
                w.ticksLeft -= 2;
            }
        };
        warmups.put(p.getUniqueId(), w);
        w.task.runTaskTimer(this, 0L, 2L);
        send(p, "searching");
    }

    private void doTeleport(final Player p) {
        World world = resolveWorld(p.getWorld());
        if (world == null) {
            send(p, "world-missing");
            return;
        }
        searchAsyncish(world, new Callback() {
            @Override
            public void done(Location loc) {
                if (!p.isOnline()) return;
                if (loc == null) {
                    send(p, "failed");
                    return;
                }
                p.teleport(loc);
                cooldowns.put(p.getUniqueId(), System.currentTimeMillis());
                msg(p, "teleported");
                Compat.sound(p, "teleport", 0.8f, 1.0f);
            }
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!getConfig().getBoolean("rtp.cancel-on-move", true)) return;
        Warmup w = warmups.get(e.getPlayer().getUniqueId());
        if (w == null || e.getTo() == null) return;
        if (e.getTo().getBlockX() != w.from.getBlockX()
                || e.getTo().getBlockY() != w.from.getBlockY()
                || e.getTo().getBlockZ() != w.from.getBlockZ()) {
            warmups.remove(e.getPlayer().getUniqueId());
            try {
                w.task.cancel();
            } catch (Throwable ignored) {
            }
            send(e.getPlayer(), "cancelled-move");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Warmup w = warmups.remove(e.getPlayer().getUniqueId());
        if (w != null && w.task != null) {
            try {
                w.task.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Guvenli konum arama
    // ------------------------------------------------------------------

    private interface Callback {
        void done(Location loc);
    }

    /**
     * Denemeleri tick'lere boler; her tick'te birkac nokta dener.
     * Chunk yukleme maliyeti tek kareye yigilmaz.
     */
    private void searchAsyncish(final World world, final Callback cb) {
        final int maxAttempts = Math.max(1, getConfig().getInt("max-attempts", 40));
        final int perTick = Math.max(1, getConfig().getInt("attempts-per-tick", 3));

        new BukkitRunnable() {
            int tried = 0;

            @Override
            public void run() {
                for (int i = 0; i < perTick && tried < maxAttempts; i++) {
                    tried++;
                    Location loc = attempt(world);
                    if (loc != null) {
                        cancel();
                        cb.done(loc);
                        return;
                    }
                }
                if (tried >= maxAttempts) {
                    cancel();
                    cb.done(null);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    /** Senkron, sinirli sayida deneme (respawn gibi hemen sonuc gereken yerler icin). */
    private Location trySome(World world, int attempts) {
        for (int i = 0; i < attempts; i++) {
            Location loc = attempt(world);
            if (loc != null) return loc;
        }
        return null;
    }

    private Location attempt(World world) {
        int cx = getConfig().getInt("center-x", 0);
        int cz = getConfig().getInt("center-z", 0);
        int radius = Math.max(1, getConfig().getInt("radius", 1000));
        int minRadius = Math.max(0, Math.min(getConfig().getInt("min-radius", 0), radius - 1));

        int dx = nextOffset(minRadius, radius);
        int dz = nextOffset(minRadius, radius);
        int x = cx + dx;
        int z = cz + dz;

        try {
            // Chunk'i yukle ki blok okumalari dogru olsun
            world.getChunkAt(x >> 4, z >> 4).load(true);
        } catch (Throwable ignored) {
        }

        Integer y = findGround(world, x, z);
        if (y == null) return null;

        Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
        loc.setYaw(random.nextFloat() * 360f - 180f);
        return loc;
    }

    private int nextOffset(int min, int max) {
        int span = max - min;
        int v = min + random.nextInt(Math.max(1, span + 1));
        return random.nextBoolean() ? v : -v;
    }

    /**
     * Ustune guvenle basilabilecek blogun Y'sini bulur.
     * Nether gibi tavani olan dunyalarda asagi dogru tarar.
     */
    private Integer findGround(World world, int x, int z) {
        boolean roofed = isRoofed(world);
        int startY;
        if (roofed) {
            startY = Math.min(getConfig().getInt("nether-scan-from", 100), world.getMaxHeight() - 3);
        } else {
            try {
                startY = world.getHighestBlockYAt(x, z);
            } catch (Throwable t) {
                return null;
            }
            if (startY <= Compat.minHeight(world) + 1) return null;
            Block ground = world.getBlockAt(x, startY, z);
            return isSafeStandOn(world, ground) ? Integer.valueOf(startY) : null;
        }

        int minY = Math.max(Compat.minHeight(world) + 1, getConfig().getInt("nether-scan-to", 5));
        for (int y = startY; y >= minY; y--) {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType().name().equals("AIR")) continue;
            return isSafeStandOn(world, b) ? Integer.valueOf(y) : null;
        }
        return null;
    }

    private boolean isRoofed(World world) {
        try {
            return world.getEnvironment() == World.Environment.NETHER;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ustunde durulabilir mi: zemin kati, ustundeki iki blok bos ve tehlike yok. */
    private boolean isSafeStandOn(World world, Block ground) {
        if (ground == null) return false;
        String name = ground.getType().name();
        if (name.equals("AIR")) return false;
        if (isUnsafe(name)) return false;
        if (getConfig().getBoolean("avoid-water", true) && isLiquidName(name)) return false;

        for (int i = 1; i <= 2; i++) {
            Block above = world.getBlockAt(ground.getX(), ground.getY() + i, ground.getZ());
            String an = above.getType().name();
            if (!an.equals("AIR") && !an.equals("CAVE_AIR") && !an.equals("VOID_AIR")) return false;
        }
        return true;
    }

    private boolean isUnsafe(String name) {
        for (String bad : getConfig().getStringList("unsafe-blocks")) {
            if (bad != null && name.equalsIgnoreCase(bad)) return true;
        }
        // Isimde gecenler (STATIONARY_LAVA, MAGMA_BLOCK gibi varyantlari yakalar)
        return name.contains("LAVA") || name.contains("FIRE") || name.contains("CACTUS")
                || name.contains("MAGMA") || name.contains("CAMPFIRE") || name.contains("PORTAL")
                || name.contains("BERRY_BUSH") || name.contains("POWDER_SNOW");
    }

    private boolean isLiquidName(String name) {
        return name.contains("WATER") || name.contains("LAVA");
    }

    private World resolveWorld(World fallback) {
        String name = getConfig().getString("world", "");
        if (name != null && !name.isEmpty()) {
            World w = Bukkit.getWorld(name);
            if (w != null) return w;
            getLogger().warning("config.yml icindeki world='" + name + "' bulunamadi.");
        }
        if (getConfig().getBoolean("rtp.use-current-world", false) && fallback != null) return fallback;
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    // ------------------------------------------------------------------
    // Mesajlar
    // ------------------------------------------------------------------

    private void send(CommandSender s, String key) {
        String m = getConfig().getString("messages." + key, "");
        if (m != null && !m.isEmpty()) s.sendMessage(Compat.color(m));
    }

    private void msg(Player p, String key) {
        send(p, key);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (command.getName().equalsIgnoreCase("randomspawn")) {
            if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ENGLISH))) out.add("reload");
            return out;
        }
        if (args.length == 1 && sender.hasPermission("randomspawn.rtp.others")) {
            String pref = args[0].toLowerCase(Locale.ENGLISH);
            for (Player pl : Compat.online()) {
                if (pl.getName().toLowerCase(Locale.ENGLISH).startsWith(pref)) out.add(pl.getName());
            }
            return out;
        }
        return Collections.emptyList();
    }
}
