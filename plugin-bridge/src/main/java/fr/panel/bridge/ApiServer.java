package fr.panel.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class ApiServer {

    private final PanelBridgePlugin plugin;
    private HttpServer server;
    private String token;

    public ApiServer(PanelBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void start(int port, String token) throws IOException {
        this.token = token;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/players", this::handlePlayers);
        server.createContext("/api/command", this::handleCommand);
        server.createContext("/api/motd", this::handleMotd);
        server.createContext("/api/plugins", this::handlePlugins);
        server.setExecutor(null);
        server.start();
        plugin.getLogger().info("API du panel demarree sur le port " + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ---------- Handlers ----------

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

        runSyncAndReply(ex, () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("online", Bukkit.getOnlinePlayers().size());
            out.put("max", Bukkit.getMaxPlayers());
            double[] tps = Bukkit.getTPS();
            out.put("tps1m", round(tps[0]));
            out.put("tps5m", round(tps[1]));
            out.put("tps15m", round(tps[2]));
            Runtime rt = Runtime.getRuntime();
            out.put("memUsedMB", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
            out.put("memMaxMB", rt.maxMemory() / 1024 / 1024);
            out.put("version", Bukkit.getVersion());
            out.put("whitelistEnabled", Bukkit.hasWhitelist());
            return out;
        });
    }

    private void handlePlayers(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!"GET".equals(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

        runSyncAndReply(ex, () -> {
            List<Object> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("name", p.getName());
                pm.put("uuid", p.getUniqueId().toString());
                pm.put("ping", p.getPing());
                pm.put("gamemode", p.getGameMode().name());
                pm.put("world", p.getWorld().getName());
                pm.put("health", p.getHealth());
                pm.put("foodLevel", p.getFoodLevel());
                pm.put("op", p.isOp());
                list.add(pm);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("players", list);
            return out;
        });
    }

    private void handleCommand(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

        String body = readBody(ex);
        Map<String, String> data = Json.parseFlat(body);
        String cmd = data.get("cmd");
        if (cmd == null || cmd.isBlank()) {
            reply(ex, 400, Json.write(Map.of("success", false, "error", "Champ 'cmd' manquant")));
            return;
        }
        final String finalCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;

        runSyncAndReply(ex, () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            try {
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                out.put("success", ok);
            } catch (Exception e) {
                out.put("success", false);
                out.put("error", e.getMessage());
            }
            return out;
        });
    }

    private void handleMotd(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;

        if ("GET".equals(ex.getRequestMethod())) {
            runSyncAndReply(ex, () -> {
                MotdState m = plugin.getMotdState();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("line1", m.getLine1());
                out.put("line2", m.getLine2());
                out.put("enabled", m.isEnabled());
                return out;
            });
            return;
        }

        if ("POST".equals(ex.getRequestMethod())) {
            String body = readBody(ex);
            Map<String, String> data = Json.parseFlat(body);
            String line1 = data.getOrDefault("line1", "");
            String line2 = data.getOrDefault("line2", "");
            boolean enabled = Boolean.parseBoolean(data.getOrDefault("enabled", "true"));

            runSyncAndReply(ex, () -> {
                plugin.getMotdState().set(line1, line2, enabled);
                return Map.of("success", true);
            });
            return;
        }

        methodNotAllowed(ex);
    }

    private void handlePlugins(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        String path = ex.getRequestURI().getPath(); // /api/plugins ou /api/plugins/{name}/toggle

        String[] parts = path.split("/");
        // parts: ["", "api", "plugins", (name), (toggle)]
        if (parts.length == 3 && "GET".equals(ex.getRequestMethod())) {
            runSyncAndReply(ex, () -> {
                List<Object> list = new ArrayList<>();
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("name", p.getName());
                    pm.put("version", p.getDescription().getVersion());
                    pm.put("enabled", p.isEnabled());
                    list.add(pm);
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("plugins", list);
                return out;
            });
            return;
        }

        if (parts.length == 5 && "toggle".equals(parts[4]) && "POST".equals(ex.getRequestMethod())) {
            String name = parts[3];
            String body = readBody(ex);
            Map<String, String> data = Json.parseFlat(body);
            boolean enable = Boolean.parseBoolean(data.getOrDefault("enable", "true"));

            runSyncAndReply(ex, () -> {
                Plugin target = Bukkit.getPluginManager().getPlugin(name);
                Map<String, Object> out = new LinkedHashMap<>();
                if (target == null) {
                    out.put("success", false);
                    out.put("error", "Plugin introuvable: " + name);
                    return out;
                }
                if (enable) Bukkit.getPluginManager().enablePlugin(target);
                else Bukkit.getPluginManager().disablePlugin(target);
                out.put("success", true);
                out.put("enabled", target.isEnabled());
                return out;
            });
            return;
        }

        notFound(ex);
    }

    // ---------- Utilitaires ----------

    /** Exécute une tâche sur le thread principal du serveur (obligatoire pour l'API Bukkit) et répond en JSON. */
    private void runSyncAndReply(HttpExchange ex, Callable<Map<String, Object>> task) throws IOException {
        FutureTask<Map<String, Object>> future = new FutureTask<>(task);
        Bukkit.getScheduler().runTask(plugin, future);
        try {
            Map<String, Object> result = future.get();
            reply(ex, 200, Json.write(result));
        } catch (InterruptedException | ExecutionException e) {
            reply(ex, 500, Json.write(Map.of("success", false, "error", String.valueOf(e.getMessage()))));
        }
    }

    private boolean checkAuth(HttpExchange ex) throws IOException {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String expected = "Bearer " + token;
        if (header == null || !header.equals(expected)) {
            reply(ex, 401, Json.write(Map.of("success", false, "error", "Non autorise")));
            return false;
        }
        return true;
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void methodNotAllowed(HttpExchange ex) throws IOException {
        reply(ex, 405, Json.write(Map.of("success", false, "error", "Methode non autorisee")));
    }

    private void notFound(HttpExchange ex) throws IOException {
        reply(ex, 404, Json.write(Map.of("success", false, "error", "Route inconnue")));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
