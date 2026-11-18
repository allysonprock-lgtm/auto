package dev.basefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Settings, stored in config/basefinder/config.json. Edit the file; changes are picked up while the game runs. */
public final class Config {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("basefinder");
    public static final Path FILE = DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;

    /** Block ids that count as "storage". */
    public List<String> storageBlocks = defaultStorage();

    /** Minimum storage blocks inside one cluster to count as a base. */
    public int minStorageBlocks = 8;
    /** Storage blocks must be within this many blocks (box radius) of each other to be one cluster. */
    public int clusterRadius = 10;
    /** A new detection is ignored if one already exists within this many chunks (same server + dimension). */
    public int dedupeChunkRadius = 3;

    /** Heuristic "does this look un-griefed" check. */
    public boolean requireIntactCheck = true;
    /** Share of storage blocks that must have a non-air block directly below them. */
    public double minSupportedRatio = 0.6;
    /** Average number of non-air neighbours (out of 6) per storage block. Exposed/blown-up bases score low. */
    public double minAvgEnclosure = 2.0;

    /** Dimensions to watch. */
    public List<String> dimensions = new ArrayList<>(List.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));

    public boolean saveLocally = true;

    /** Discord webhook URL (leave empty to disable). */
    public String discordWebhookUrl = "";
    public String discordUsername = "Base Finder";
    /** Optional generic HTTP endpoint that receives a JSON POST (leave empty to disable). */
    public String apiEndpointUrl = "";
    /** Optional value sent as the Authorization header to apiEndpointUrl. */
    public String apiAuthHeader = "";
    public boolean includeServerAddress = true;

    /** GLFW key code that opens the settings screen in game (344 = Right Shift, 75 = K, 0 = disabled). */
    public int guiKey = 344;
    /** Minimum seconds between in-game chat/sound alerts (webhooks are never throttled). */
    public int alertCooldownSeconds = 3;

    public boolean alertChat = true;
    public boolean alertSound = true;
    /** WAV file name inside config/basefinder/, or an absolute path. Falls back to a built-in sound if missing. */
    public String customSoundFile = "alert.wav";
    /** 0.0 - 1.0 */
    public float soundVolume = 1.0f;

    /** Set true once to fire a fake detection on world join, to test the webhook and sound. */
    public boolean sendTestOnJoin = false;

    private transient Set<String> storageSet = new HashSet<>();
    private transient Set<String> dimSet = new HashSet<>();
    private transient long loadedStamp;

    private static List<String> defaultStorage() {
        List<String> l = new ArrayList<>(List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:shulker_box"));
        for (String c : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            l.add("minecraft:" + c + "_shulker_box");
        }
        return l;
    }

    public boolean isStorage(String blockId) {
        return storageSet.contains(blockId);
    }

    public boolean watchesDimension(String dim) {
        return dimSet.contains(dim);
    }

    private void rebuild() {
        storageSet = new HashSet<>(storageBlocks);
        dimSet = new HashSet<>(dimensions);
        minStorageBlocks = Math.max(2, minStorageBlocks);
        clusterRadius = Math.max(1, clusterRadius);
        dedupeChunkRadius = Math.max(0, dedupeChunkRadius);
        soundVolume = Math.max(0f, Math.min(1f, soundVolume));
        alertCooldownSeconds = Math.max(0, alertCooldownSeconds);
        if (discordWebhookUrl == null) discordWebhookUrl = "";
    }

    /** Re-validates values changed in code (e.g. by the settings screen) and writes the file. */
    public void applyAndSave() {
        rebuild();
        save();
    }

    public static Config load() {
        Config cfg = null;
        try {
            Files.createDirectories(DIR);
            if (Files.exists(FILE)) {
                cfg = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Config.class);
            }
        } catch (Exception e) {
            BaseFinderClient.LOG.warn("[BaseFinder] Could not read config, using defaults", e);
        }
        if (cfg == null) cfg = new Config();
        cfg.rebuild();
        cfg.save(); // writes any newly added options back to the file
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
            loadedStamp = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException e) {
            BaseFinderClient.LOG.warn("[BaseFinder] Could not save config", e);
        }
    }

    /** True if the file on disk changed since we last loaded/saved it. */
    public boolean changedOnDisk() {
        try {
            return Files.exists(FILE) && Files.getLastModifiedTime(FILE).toMillis() != loadedStamp;
        } catch (IOException e) {
            return false;
        }
    }
}
