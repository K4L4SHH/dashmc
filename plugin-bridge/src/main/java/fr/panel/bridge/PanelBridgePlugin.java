package fr.panel.bridge;

import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.Base64;

public class PanelBridgePlugin extends JavaPlugin {

    private ApiServer apiServer;
    private MotdState motdState;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureToken();

        motdState = new MotdState(this);
        getServer().getPluginManager().registerEvents(motdState, this);

        int port = getConfig().getInt("port", 8090);
        String token = getConfig().getString("token");

        apiServer = new ApiServer(this);
        try {
            apiServer.start(port, token);
        } catch (Exception e) {
            getLogger().severe("Impossible de demarrer l'API du panel sur le port " + port + " : " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (apiServer != null) apiServer.stop();
    }

    private void ensureToken() {
        String token = getConfig().getString("token");
        if (token == null || token.isBlank() || token.equals("CHANGE_ME")) {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            getConfig().set("token", token);
            saveConfig();
            getLogger().warning("Nouveau token API genere. Copie-le dans la config du dashboard: " + token);
        }
    }

    public MotdState getMotdState() {
        return motdState;
    }
}
