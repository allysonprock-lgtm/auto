package dev.basefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches chunks as the client loads them, looks at their block entities (chests, barrels, shulkers...),
 * groups them into clusters and reports clusters that look like an intact player base.
 */
final class Scanner {
    private record Pending(String dim, int cx, int cz, long due) {}

    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final Deque<Pending> pending = new ArrayDeque<>();
    /** dimension -> chunkKey -> storage block positions in that chunk */
    private final Map<String, Map<Long, List<BlockPos>>> storage = new HashMap<>();

    private long tick;
    private boolean inWorld;
    private boolean keyWasDown;

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    static String dimKey(ClientLevel level) {
        // ResourceKey#toString looks like "ResourceKey[minecraft:dimension / minecraft:overworld]"
        String s = level.dimension().toString();
        int i = s.lastIndexOf(" / ");
        int j = s.lastIndexOf(']');
        return (i >= 0 && j > i) ? s.substring(i + 3, j) : s;
    }

    static String serverKey(Minecraft mc) {
        return mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
    }

    void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        Config cfg = BaseFinderClient.config;
        if (cfg == null || !cfg.enabled) return;
        String dim = dimKey(level);
        if (!cfg.watchesDimension(dim)) return;
        int cx = chunk.getPos().getMinBlockX() >> 4;
        int cz = chunk.getPos().getMinBlockZ() >> 4;
        // Scan twice: shortly after load, and again once neighbouring chunks have probably arrived.
        pending.add(new Pending(dim, cx, cz, tick + 10));
        pending.add(new Pending(dim, cx, cz, tick + 100));
    }

    void onChunkUnload(ClientLevel level, LevelChunk chunk) {
        Map<Long, List<BlockPos>> map = storage.get(dimKey(level));
        if (map == null) return;
        map.remove(key(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4));
    }

    void tick(Minecraft mc) {
        tick++;
        Config cfg = BaseFinderClient.config;

        if (mc.level == null) {
            if (inWorld) {
                inWorld = false;
                pending.clear();
                storage.clear();
            }
            return;
        }
        if (!inWorld) {
            inWorld = true;
            if (cfg.sendTestOnJoin) Alerts.show(mc, Alerts.testDetection(mc), true);
        }

        if (tick % 100 == 0 && cfg.changedOnDisk()) {
            BaseFinderClient.config = Config.load();
            cfg = BaseFinderClient.config;
            BaseFinderClient.LOG.info("[BaseFinder] Config reloaded");
        }

        // Open the settings screen with the configured key (works only while no other screen is open).
        long window = GLFW.glfwGetCurrentContext();
        boolean down = window != 0L && cfg.guiKey > 0 && GLFW.glfwGetKey(window, cfg.guiKey) == GLFW.GLFW_PRESS;
        if (down && !keyWasDown && mc.screen == null) mc.setScreen(new BaseFinderScreen(null));
        keyWasDown = down;

        if (!cfg.enabled) {
            pending.clear();
            return;
        }

        String currentDim = dimKey(mc.level);
        int budget = 4;
        while (budget > 0 && !pending.isEmpty() && pending.peek().due() <= tick) {
            Pending p = pending.poll();
            if (!p.dim().equals(currentDim)) continue;
            budget--;
            try {
                scan(mc, mc.level, p.dim(), p.cx(), p.cz());
            } catch (Exception e) {
                BaseFinderClient.LOG.warn("[BaseFinder] Scan failed for chunk {}, {}", p.cx(), p.cz(), e);
            }
        }
    }

    private void scan(Minecraft mc, ClientLevel level, String dim, int cx, int cz) {
        Config cfg = BaseFinderClient.config;
        LevelChunk chunk = level.getChunk(cx, cz);

        List<BlockPos> found = new ArrayList<>();
        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            String id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
            if (cfg.isStorage(id)) found.add(be.getBlockPos());
        }

        Map<Long, List<BlockPos>> map = storage.computeIfAbsent(dim, k -> new HashMap<>());
        if (map.size() > 30000) map.clear(); // keep memory bounded on very long sessions
        if (found.isEmpty()) {
            map.remove(key(cx, cz));
            return;
        }
        map.put(key(cx, cz), found);
        evaluate(mc, level, dim, cx, cz, map, found);
    }

    private void evaluate(Minecraft mc, ClientLevel level, String dim, int cx, int cz,
                          Map<Long, List<BlockPos>> map, List<BlockPos> center) {
        Config cfg = BaseFinderClient.config;

        // Gather this chunk and its 8 neighbours so clusters crossing chunk borders are seen whole.
        List<BlockPos> pts = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<BlockPos> l = map.get(key(cx + dx, cz + dz));
                if (l != null) pts.addAll(l);
            }
        }
        if (pts.size() < cfg.minStorageBlocks) return;
        if (pts.size() > 3000) pts = pts.subList(0, 3000);

        // Densest cluster anchored on a storage block in this chunk.
        int r = cfg.clusterRadius;
        List<BlockPos> best = List.of();
        for (BlockPos a : center) {
            List<BlockPos> members = new ArrayList<>();
            for (BlockPos p : pts) {
                if (Math.abs(p.getX() - a.getX()) <= r && Math.abs(p.getY() - a.getY()) <= r
                        && Math.abs(p.getZ() - a.getZ()) <= r) {
                    members.add(p);
                }
            }
            if (members.size() > best.size()) best = members;
        }
        if (best.size() < cfg.minStorageBlocks) return;

        // Report the chunk containing the cluster's centre.
        long sx = 0, sz = 0;
        for (BlockPos p : best) {
            sx += p.getX();
            sz += p.getZ();
        }
        int bx = (int) Math.floorDiv(sx, (long) best.size());
        int bz = (int) Math.floorDiv(sz, (long) best.size());
        int baseCx = bx >> 4;
        int baseCz = bz >> 4;

        // "Does it look griefed?" heuristic: storage should sit on floors and be enclosed by other blocks.
        int supported = 0;
        int enclosure = 0;
        for (BlockPos p : best) {
            if (!level.getBlockState(p.below()).isAir()) supported++;
            for (int[] o : NEIGHBOURS) {
                if (!level.getBlockState(p.offset(o[0], o[1], o[2])).isAir()) enclosure++;
            }
        }
        double supportRatio = supported / (double) best.size();
        double avgEnclosure = enclosure / (double) best.size();
        if (cfg.requireIntactCheck && (supportRatio < cfg.minSupportedRatio || avgEnclosure < cfg.minAvgEnclosure)) {
            return;
        }

        String server = serverKey(mc);
        if (BaseFinderClient.store.isDuplicate(server, dim, baseCx, baseCz, cfg.dedupeChunkRadius)) return;

        DetectionStore.Detection d = new DetectionStore.Detection();
        d.server = server;
        d.dimension = dim;
        d.chunkX = baseCx;
        d.chunkZ = baseCz;
        d.storageCount = best.size();
        d.supportRatio = Math.round(supportRatio * 100.0) / 100.0;
        d.avgEnclosure = Math.round(avgEnclosure * 10.0) / 10.0;
        double conf = 0.45 * Math.min(1.0, supportRatio) + 0.30 * Math.min(1.0, avgEnclosure / 4.0)
                + 0.25 * Math.min(1.0, best.size() / 20.0);
        d.confidence = (int) Math.round(conf * 100.0);
        d.foundAt = Instant.now().toString();

        BaseFinderClient.store.add(d, cfg.saveLocally);
        BaseFinderClient.LOG.info("[BaseFinder] Possible base at chunk {}, {} in {} ({} storage blocks)",
                baseCx, baseCz, dim, best.size());
        Alerts.show(mc, d, false);
    }
}
