package dev.basefinder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.Instant;

/** Everything that happens when a base is found: in-game message, sound, webhook. */
final class Alerts {
    private static long lastLocalAlert;

    private Alerts() {}

    static DetectionStore.Detection testDetection(Minecraft mc) {
        DetectionStore.Detection t = new DetectionStore.Detection();
        t.server = Scanner.serverKey(mc);
        t.dimension = mc.level != null ? Scanner.dimKey(mc.level) : "minecraft:overworld";
        t.chunkX = 0;
        t.chunkZ = 0;
        t.storageCount = 12;
        t.confidence = 88;
        t.supportRatio = 1.0;
        t.avgEnclosure = 3.0;
        t.foundAt = Instant.now().toString();
        return t;
    }

    static String prettyDim(String dim) {
        String s = dim.replace("minecraft:", "").replace('_', ' ');
        if (s.startsWith("the ")) s = s.substring(4);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static void show(Minecraft mc, DetectionStore.Detection d, boolean test) {
        Config cfg = BaseFinderClient.config;

        long now = System.currentTimeMillis();
        boolean local = test || now - lastLocalAlert >= cfg.alertCooldownSeconds * 1000L;
        if (local) {
            lastLocalAlert = now;

            if (cfg.alertChat && mc.player != null) {
                Component head = Component.literal("\u258C ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(test ? "TEST ALERT" : "BASE FOUND")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Component.literal("  " + prettyDim(d.dimension)).withStyle(ChatFormatting.GRAY));
                Component detail = Component.literal("\u258C ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Chunk ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(d.chunkX + ", " + d.chunkZ)
                                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                        .append(Component.literal("   " + d.storageCount + " storage \u00B7 " + d.confidence
                                + "% confidence").withStyle(ChatFormatting.DARK_GRAY));
                Component bar = Component.literal((test ? "Test alert" : "Base found") + ": chunk "
                        + d.chunkX + ", " + d.chunkZ).withStyle(ChatFormatting.GOLD);

                mc.player.displayClientMessage(head, false);
                mc.player.displayClientMessage(detail, false);
                mc.player.displayClientMessage(bar, true); // action bar
            }
            if (cfg.alertSound) SoundPlayer.play(mc, cfg);
        }

        Notifier.send(cfg, d, test);
    }
}
