package fr.panel.bridge;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * Garde le MOTD courant en mémoire et l'applique à chaque ping du serveur,
 * sans nécessiter de redémarrage quand il est changé depuis le panel.
 */
public class MotdState implements Listener {

    private final PanelBridgePlugin plugin;
    private String line1;
    private String line2;
    private boolean enabled;

    public MotdState(PanelBridgePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        this.line1 = cfg.getString("motd.line1", "&aServeur Minecraft");
        this.line2 = cfg.getString("motd.line2", "&7Propulse par PanelBridge");
        this.enabled = cfg.getBoolean("motd.enabled", true);
    }

    public void set(String line1, String line2, boolean enabled) {
        this.line1 = line1;
        this.line2 = line2;
        this.enabled = enabled;
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("motd.line1", line1);
        cfg.set("motd.line2", line2);
        cfg.set("motd.enabled", enabled);
        plugin.saveConfig();
    }

    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public boolean isEnabled() { return enabled; }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!enabled) return;
        String motd = ChatColor.translateAlternateColorCodes('&', line1 + "\n" + line2);
        event.setMotd(motd);
    }
}
