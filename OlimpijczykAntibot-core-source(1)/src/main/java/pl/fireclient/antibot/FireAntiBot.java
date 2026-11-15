package pl.fireclient.antibot;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.util.*;

public final class FireAntiBot extends JavaPlugin implements Listener {
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Session> sessions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            beginVerification(player);
        }
        getLogger().info("FireAntiBot zostal wlaczony.");
    }

    @Override
    public void onDisable() {
        for (Session session : sessions.values()) {
            if (session.task != null) session.task.cancel();
            if (session.bar != null) session.bar.removeAll();
        }
        sessions.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> beginVerification(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.verified || event.getTo() == null) return;
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isPending(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (isPending(event.getPlayer())) event.setCancelled(true);
    }

    private boolean isPending(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && !session.verified;
    }

    private void beginVerification(Player player) {
        if (!player.isOnline()) return;
        removeSession(player.getUniqueId());

        String code = generateCode();
        int duration = Math.max(5, getConfig().getInt("verification-time", 60));
        Session session = new Session(code, duration);
        sessions.put(player.getUniqueId(), session);

        session.bar = Bukkit.createBossBar(
                color("&eWeryfikacja: &f" + duration + "s"),
                BarColor.YELLOW,
                BarStyle.SOLID
        );
        session.bar.setProgress(1.0);
        session.bar.addPlayer(player);

        openVerificationGui(player, session);
        session.task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
                removeSession(player.getUniqueId());
                return;
            }
            session.secondsLeft--;
            double progress = Math.max(0.0, (double) session.secondsLeft / session.totalSeconds);
            session.bar.setProgress(progress);
            session.bar.setTitle(color("&eWeryfikacja: &f" + session.secondsLeft + "s"));
            if (session.secondsLeft <= 0) {
                player.kickPlayer(color(getConfig().getString("messages.timeout",
                        "&cNie udalo sie ukonczyc weryfikacji na czas.")));
                removeSession(player.getUniqueId());
            }
        }, 20L, 20L);
    }

    private void openVerificationGui(Player player, Session session) {
        if (!player.isOnline() || session.verified) return;

        ItemStack cancel = item(Material.RED_DYE,
                getConfig().getString("gui.cancel-name", "&cAnuluj weryfikacje"), null);
        ItemStack paper = item(Material.PAPER,
                getConfig().getString("gui.paper-name", "&fWpisz kod z tytulu kowadla"),
                getConfig().getStringList("gui.paper-lore"));
        ItemStack accept = item(Material.LIME_DYE,
                getConfig().getString("gui.accept-name", "&aAkceptuj weryfikacje"), null);

        String title = color(getConfig().getString("gui.title-prefix", "&7KOD: &b") + session.code);
        String placeholder = getConfig().getString("gui.input-placeholder", "Wpisz kod tutaj");

        new AnvilGUI.Builder()
                .plugin(this)
                .title(title)
                .itemLeft(cancel)
                .itemRight(paper)
                .itemOutput(accept)
                .text(placeholder)
                .preventClose()
                .onClick((slot, state) -> {
                    if (slot == AnvilGUI.Slot.INPUT_LEFT) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            if (player.isOnline() && sessions.get(player.getUniqueId()) == session) {
                                player.kickPlayer(color(getConfig().getString("messages.cancelled",
                                        "&cAnulowano weryfikacje.")));
                                removeSession(player.getUniqueId());
                            }
                        });
                        return Collections.emptyList();
                    }

                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    if (state.getText().trim().equals(session.code)) {
                        session.verified = true;
                        if (session.task != null) session.task.cancel();
                        if (session.bar != null) session.bar.removeAll();
                        sessions.remove(player.getUniqueId());
                        return List.of(
                                AnvilGUI.ResponseAction.close(),
                                AnvilGUI.ResponseAction.run(() -> {
                                    player.sendMessage(color(getConfig().getString("messages.success",
                                            "&aWeryfikacja zakonczona pomyslnie!")));
                                })
                        );
                    }

                    player.sendMessage(color(getConfig().getString("messages.wrong-code",
                            "&cNieprawidlowy kod. Sprobuj ponownie.")));
                    return Collections.emptyList();
                })
                .open(player);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore.stream().map(this::color).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private String generateCode() {
        String chars = getConfig().getString("code-characters",
                "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
        int length = Math.max(3, Math.min(12, getConfig().getInt("code-length", 5)));
        if (chars == null || chars.isEmpty()) chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private void removeSession(UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        if (session.bar != null) session.bar.removeAll();
    }

    private static final class Session {
        private final String code;
        private final int totalSeconds;
        private int secondsLeft;
        private boolean verified;
        private BossBar bar;
        private BukkitTask task;

        private Session(String code, int seconds) {
            this.code = code;
            this.totalSeconds = seconds;
            this.secondsLeft = seconds;
        }
    }
}
