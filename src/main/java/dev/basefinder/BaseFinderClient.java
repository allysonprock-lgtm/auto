package dev.basefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "basefinder";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static volatile Config config;
    public static DetectionStore store;
    private static Scanner scanner;

    @Override
    public void onInitializeClient() {
        config = Config.load();
        store = DetectionStore.load();
        scanner = new Scanner();

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> scanner.onChunkLoad(level, chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> scanner.onChunkUnload(level, chunk));
        ClientTickEvents.END_CLIENT_TICK.register(scanner::tick);

        LOG.info("[BaseFinder] Loaded. Config: {}", Config.FILE);
    }
}
