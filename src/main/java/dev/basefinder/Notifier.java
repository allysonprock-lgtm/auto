package dev.basefinder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends detections to a Discord webhook and/or a generic JSON API, off the game thread, with retries. */
final class Notifier {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Gson GSON = new Gson();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "basefinder-http");
        t.setDaemon(true);
        return t;
    });

    private Notifier() {}

    static void send(Config cfg, DetectionStore.Detection d, boolean test) {
        String discord = cfg.discordWebhookUrl == null ? "" : cfg.discordWebhookUrl.trim();
        String api = cfg.apiEndpointUrl == null ? "" : cfg.apiEndpointUrl.trim();

        if (!discord.isEmpty()) {
            String body = GSON.toJson(discordBody(cfg, d, test));
            EXEC.execute(() -> post(discord, body, null));
        }
        if (!api.isEmpty()) {
            String body = GSON.toJson(apiBody(cfg, d, test));
            String auth = cfg.apiAuthHeader;
            EXEC.execute(() -> post(api, body, auth));
        }
    }

    private static JsonObject discordBody(Config cfg, DetectionStore.Detection d, boolean test) {
        JsonObject root = new JsonObject();
        root.addProperty("username", cfg.discordUsername);

        JsonObject embed = new JsonObject();
        embed.addProperty("title", test ? "Base Finder test message" : "Possible base found");
        embed.addProperty("description", "Chunk **" + d.chunkX + ", " + d.chunkZ + "**");
        embed.addProperty("color", test ? 0x3498DB : (d.confidence >= 75 ? 0x2ECC71 : 0xE67E22));
        embed.addProperty("timestamp", Instant.now().toString());

        JsonArray fields = new JsonArray();
        fields.add(field("Dimension", d.dimension, true));
        fields.add(field("Chunk", d.chunkX + ", " + d.chunkZ, true));
        fields.add(field("Chunk centre (blocks)", (d.chunkX * 16 + 8) + ", " + (d.chunkZ * 16 + 8), true));
        fields.add(field("Storage blocks", String.valueOf(d.storageCount), true));
        fields.add(field("Confidence", d.confidence + "%", true));
        fields.add(field("Details", String.format("support %.0f%%, enclosure %.1f/6",
                d.supportRatio * 100, d.avgEnclosure), true));
        if (cfg.includeServerAddress) fields.add(field("Server", d.server, true));
        embed.add("fields", fields);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);
        return root;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private static JsonObject apiBody(Config cfg, DetectionStore.Detection d, boolean test) {
        JsonObject o = new JsonObject();
        o.addProperty("event", test ? "test" : "base_detected");
        if (cfg.includeServerAddress) o.addProperty("server", d.server);
        o.addProperty("dimension", d.dimension);
        o.addProperty("chunkX", d.chunkX);
        o.addProperty("chunkZ", d.chunkZ);
        o.addProperty("blockX", d.chunkX * 16 + 8);
        o.addProperty("blockZ", d.chunkZ * 16 + 8);
        o.addProperty("storageCount", d.storageCount);
        o.addProperty("confidence", d.confidence);
        o.addProperty("supportRatio", d.supportRatio);
        o.addProperty("avgEnclosure", d.avgEnclosure);
        o.addProperty("foundAt", d.foundAt);
        return o;
    }

    private static void post(String url, String body, String authHeader) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                if (authHeader != null && !authHeader.isBlank()) b.header("Authorization", authHeader);

                HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code >= 200 && code < 300) return;
                if (code == 429 || code >= 500) {
                    Thread.sleep(2000L * (attempt + 1));
                    continue;
                }
                BaseFinderClient.LOG.warn("[BaseFinder] Webhook/API returned HTTP {}: {}", code, res.body());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                BaseFinderClient.LOG.warn("[BaseFinder] Webhook/API request failed (attempt {}): {}", attempt + 1, e.toString());
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
