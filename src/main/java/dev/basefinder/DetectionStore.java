package dev.basefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Saves detections to config/basefinder/detections.json and answers "have we seen this area already?". */
final class DetectionStore {
    static final class Detection {
        String server;
        String dimension;
        int chunkX;
        int chunkZ;
        int storageCount;
        int confidence;
        double supportRatio;
        double avgEnclosure;
        String foundAt;
    }

    private static final Path FILE = Config.DIR.resolve("detections.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Detection> all = new ArrayList<>();

    static DetectionStore load() {
        DetectionStore s = new DetectionStore();
        try {
            if (Files.exists(FILE)) {
                List<Detection> read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                        new TypeToken<List<Detection>>() {}.getType());
                if (read != null) s.all.addAll(read);
            }
        } catch (Exception e) {
            BaseFinderClient.LOG.warn("[BaseFinder] Could not read detections file", e);
        }
        return s;
    }

    synchronized boolean isDuplicate(String server, String dim, int cx, int cz, int radius) {
        for (Detection d : all) {
            if (d.server.equals(server) && d.dimension.equals(dim)
                    && Math.abs(d.chunkX - cx) <= radius && Math.abs(d.chunkZ - cz) <= radius) {
                return true;
            }
        }
        return false;
    }

    synchronized void add(Detection d, boolean persist) {
        all.add(d);
        if (persist) write();
    }

    /** Newest first, at most {@code n} entries. */
    synchronized List<Detection> recent(int n) {
        List<Detection> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < n; i--) out.add(all.get(i));
        return out;
    }

    synchronized int size() {
        return all.size();
    }

    private void write() {
        try {
            Files.createDirectories(Config.DIR);
            Files.writeString(FILE, GSON.toJson(all), StandardCharsets.UTF_8);
        } catch (Exception e) {
            BaseFinderClient.LOG.warn("[BaseFinder] Could not save detections", e);
        }
    }
}
