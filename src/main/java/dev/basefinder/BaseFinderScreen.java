package dev.basefinder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Dark, card-style settings + detections screen. Open it in game with the guiKey (Right Shift by default). */
public class BaseFinderScreen extends Screen {
    private static final int ACCENT = 0xFFFF8A3D;
    private static final int PANEL = 0xF0101016;
    private static final int CARD = 0xFF1A1A23;
    private static final int BORDER = 0xFF2B2B38;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9A9AAE;
    private static final int GOOD = 0xFF4ADE80;
    private static final int WARN = 0xFFFBBF24;

    private final Screen parent;

    private boolean enabled;
    private boolean chat;
    private boolean sound;
    private boolean intact;
    private int minStorage;

    private Button enabledBtn;
    private Button chatBtn;
    private Button soundBtn;
    private Button intactBtn;
    private EditBox webhookBox;

    private int px, py, pw, ph;
    private int lx, lw, rx, rw;

    public BaseFinderScreen(Screen parent) {
        super(Component.literal("Base Finder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = BaseFinderClient.config;
        enabled = c.enabled;
        chat = c.alertChat;
        sound = c.alertSound;
        intact = c.requireIntactCheck;
        minStorage = c.minStorageBlocks;

        pw = Math.min(this.width - 16, 480);
        ph = Math.min(this.height - 16, 240);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        lx = px + 14;
        lw = 200;
        rx = lx + lw + 22;
        rw = px + pw - 14 - rx;

        int y = py + 42;
        enabledBtn = Button.builder(Component.empty(), b -> {
            enabled = !enabled;
            refresh();
        }).bounds(lx, y, lw, 20).build();
        addRenderableWidget(enabledBtn);

        y += 24;
        chatBtn = Button.builder(Component.empty(), b -> {
            chat = !chat;
            refresh();
        }).bounds(lx, y, lw, 20).build();
        addRenderableWidget(chatBtn);

        y += 24;
        soundBtn = Button.builder(Component.empty(), b -> {
            sound = !sound;
            refresh();
        }).bounds(lx, y, lw, 20).build();
        addRenderableWidget(soundBtn);

        y += 24;
        intactBtn = Button.builder(Component.empty(), b -> {
            intact = !intact;
            refresh();
        }).bounds(lx, y, lw, 20).build();
        addRenderableWidget(intactBtn);

        y += 24;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> minStorage = Math.max(2, minStorage - 1))
                .bounds(lx + lw - 68, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> minStorage = Math.min(200, minStorage + 1))
                .bounds(lx + lw - 20, y, 20, 20).build());

        y += 36;
        webhookBox = new EditBox(this.font, lx, y, lw, 18, Component.literal("Discord webhook"));
        webhookBox.setMaxLength(400);
        webhookBox.setValue(c.discordWebhookUrl);
        webhookBox.setHint(Component.literal("Paste Discord webhook URL"));
        addRenderableWidget(webhookBox);

        int fy = py + ph - 30;
        addRenderableWidget(Button.builder(Component.literal("Test alert"), b -> {
            commit();
            Alerts.show(this.minecraft, Alerts.testDetection(this.minecraft), true);
        }).bounds(lx, fy, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save & close"), b -> {
            commit();
            onClose();
        }).bounds(lx + 104, fy, 96, 20).build());

        refresh();
    }

    private void refresh() {
        enabledBtn.setMessage(Component.literal("Detection: " + (enabled ? "ON" : "OFF")));
        chatBtn.setMessage(Component.literal("Chat alert: " + (chat ? "ON" : "OFF")));
        soundBtn.setMessage(Component.literal("Sound alert: " + (sound ? "ON" : "OFF")));
        intactBtn.setMessage(Component.literal("Skip griefed-looking: " + (intact ? "ON" : "OFF")));
    }

    private void commit() {
        Config c = BaseFinderClient.config;
        c.enabled = enabled;
        c.alertChat = chat;
        c.alertSound = sound;
        c.requireIntactCheck = intact;
        c.minStorageBlocks = minStorage;
        c.discordWebhookUrl = webhookBox.getValue().trim();
        c.applyAndSave();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Backdrop, panel, header
        g.fill(0, 0, this.width, this.height, 0x99000000);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.outline(px, py, pw, ph, BORDER);
        g.fill(px, py, px + pw, py + 2, ACCENT);

        g.text(this.font, "BASE FINDER", px + 14, py + 12, TEXT, false);
        String sub = BaseFinderClient.store.size() + " saved";
        g.text(this.font, sub, px + pw - 14 - this.font.width(sub), py + 12, MUTED, false);
        g.fill(px + 14, py + 28, px + pw - 14, py + 29, BORDER);

        // Cards
        g.fill(lx - 6, py + 34, lx + lw + 6, py + ph - 38, CARD);
        g.outline(lx - 6, py + 34, lw + 12, ph - 72, BORDER);
        g.fill(rx - 8, py + 34, px + pw - 8, py + ph - 8, CARD);
        g.outline(rx - 8, py + 34, px + pw - 8 - (rx - 8), ph - 42, BORDER);

        // Left card labels
        int stepY = py + 42 + 24 * 4;
        g.text(this.font, "Min storage blocks", lx, stepY + 6, MUTED, false);
        String val = String.valueOf(minStorage);
        g.text(this.font, val, lx + lw - 45 + (24 - this.font.width(val)) / 2, stepY + 6, ACCENT, false);
        g.text(this.font, "Discord webhook", lx, stepY + 36 - 11, MUTED, false);

        // Right card: recent detections
        g.text(this.font, "RECENT DETECTIONS", rx, py + 42, ACCENT, false);
        List<DetectionStore.Detection> recent = BaseFinderClient.store.recent(Math.max(1, (ph - 80) / 26));
        if (recent.isEmpty()) {
            g.text(this.font, "Nothing found yet.", rx, py + 62, MUTED, false);
            g.text(this.font, "Explore to load chunks.", rx, py + 74, MUTED, false);
        }
        int y = py + 58;
        for (DetectionStore.Detection d : recent) {
            int dot = d.confidence >= 75 ? GOOD : WARN;
            g.fill(rx, y + 1, rx + 3, y + 21, dot);
            g.text(this.font, "Chunk " + d.chunkX + ", " + d.chunkZ, rx + 8, y + 1, TEXT, false);
            g.text(this.font, Alerts.prettyDim(d.dimension) + " \u00B7 " + d.storageCount + " storage \u00B7 "
                    + d.confidence + "% \u00B7 " + ago(d.foundAt), rx + 8, y + 12, MUTED, false);
            y += 26;
        }

        // Footer hint
        String hint = "Esc closes without saving";
        g.text(this.font, hint, px + pw - 14 - this.font.width(hint), py + ph - 18, MUTED, false);

        // Buttons and the text box are drawn by the parent implementation.
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private static String ago(String iso) {
        try {
            long s = Duration.between(Instant.parse(iso), Instant.now()).getSeconds();
            if (s < 60) return "just now";
            if (s < 3600) return (s / 60) + "m ago";
            if (s < 86400) return (s / 3600) + "h ago";
            return (s / 86400) + "d ago";
        } catch (Exception e) {
            return "";
        }
    }
}
