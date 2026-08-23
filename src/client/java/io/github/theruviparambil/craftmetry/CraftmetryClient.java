package io.github.theruviparambil.craftmetry;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

public final class CraftmetryClient implements ClientModInitializer {
    private static final int MARGIN = 6;
    private static final int PANEL_WIDTH = 88;
    private static final int PANEL_HEIGHT = 20;
    private static final Identifier CPS_HUD = Identifier.fromNamespaceAndPath("craftmetry", "cps");
    private static final Identifier CONTROLS = Identifier.fromNamespaceAndPath("craftmetry", "controls");
    private static final ClickWindow LEFT_CLICKS = new ClickWindow();
    private static final ClickWindow RIGHT_CLICKS = new ClickWindow();
    private static HudPreferences preferences = HudPreferences.defaults();
    private static HudPreferencesStore preferencesStore;
    private static KeyMapping toggleHud;
    private static KeyMapping cycleCorner;
    private static KeyMapping cycleScale;

    @Override
    public void onInitializeClient() {
        preferencesStore = HudPreferencesStore.createDefault();
        preferences = preferencesStore.load();

        KeyMapping.Category category = KeyMapping.Category.register(CONTROLS);
        toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.craftmetry.toggle_hud",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                category));
        cycleCorner = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.craftmetry.cycle_corner",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_M,
                category));
        cycleScale = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.craftmetry.cycle_scale",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_R,
                category));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CPS_HUD, CraftmetryClient::renderCps);
        ClientTickEvents.END_CLIENT_TICK.register(CraftmetryClient::handleClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> preferencesStore.flush());
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> resetClicks());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> resetClicks());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> resetClicks());
    }

    public static void observeKeyMapping(InputConstants.Key key, boolean down) {
        if (!down) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (!isGameplayInputContext(client)) {
            return;
        }

        HudPreferences updated = preferences;
        if (isRisingEdge(toggleHud, key)) {
            updated = updated.toggleVisibility();
        }
        if (isRisingEdge(cycleCorner, key)) {
            updated = updated.cycleCorner();
        }
        if (isRisingEdge(cycleScale, key)) {
            updated = updated.cycleScale();
        }
        if (updated != preferences) {
            preferences = updated;
            preferencesStore.save(updated);
        }

        long now = System.nanoTime();
        if (client.options.keyAttack.matches(key) && !client.options.keyAttack.isDown()) {
            LEFT_CLICKS.add(now);
        }
        if (client.options.keyUse.matches(key) && !client.options.keyUse.isDown()) {
            RIGHT_CLICKS.add(now);
        }
    }

    private static void renderCps(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        HudPreferences current = preferences;
        if (!current.visible() || !isGameplayInputContext(client)) {
            return;
        }

        long now = System.nanoTime();
        String text = "L " + LEFT_CLICKS.count(now) + "  R " + RIGHT_CLICKS.count(now);
        float scale = current.scale().factor();
        int scaledWidth = (int) Math.ceil(PANEL_WIDTH * (double) scale);
        int scaledHeight = (int) Math.ceil(PANEL_HEIGHT * (double) scale);
        int maxX = Math.max(0, graphics.guiWidth() - scaledWidth);
        int maxY = Math.max(0, graphics.guiHeight() - scaledHeight);
        int x = current.corner().isRight() ? Math.max(0, maxX - MARGIN) : Math.min(MARGIN, maxX);
        int y = current.corner().isBottom() ? Math.max(0, maxY - MARGIN) : Math.min(MARGIN, maxY);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scale, scale);
            graphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 0xCC191A18);
            graphics.fill(0, 0, 2, PANEL_HEIGHT, 0xFFB6F23A);
            graphics.text(client.font, text, 7, 6, 0xFFF0F2ED, true);
        } finally {
            pose.popMatrix();
        }
    }

    private static void handleClientTick(Minecraft client) {
        drainClicks(toggleHud);
        drainClicks(cycleCorner);
        drainClicks(cycleScale);

        if (!isGameplayInputContext(client)) {
            resetClicks();
        }
    }

    private static boolean isRisingEdge(KeyMapping mapping, InputConstants.Key key) {
        return mapping != null && mapping.matches(key) && !mapping.isDown();
    }

    private static void drainClicks(KeyMapping mapping) {
        if (mapping == null) {
            return;
        }
        while (mapping.consumeClick()) {
            // KeyMapping.set handles the rising edge; this keeps the click queue bounded.
        }
    }

    private static boolean isGameplayInputContext(Minecraft client) {
        return client.player != null && client.level != null && client.isWindowActive()
                && client.gui.screen() == null && client.gui.overlay() == null;
    }

    private static void resetClicks() {
        LEFT_CLICKS.clear();
        RIGHT_CLICKS.clear();
    }

    private static final class ClickWindow {
        private static final long WINDOW_NANOS = 1_000_000_000L;
        private static final int CAPACITY = 256;

        private final long[] clicks = new long[CAPACITY];
        private int first;
        private int size;

        void add(long now) {
            prune(now);
            if (size == clicks.length) {
                first = (first + 1) % clicks.length;
                size--;
            }
            clicks[(first + size) % clicks.length] = now;
            size++;
        }

        int count(long now) {
            prune(now);
            return size;
        }

        void clear() {
            first = 0;
            size = 0;
        }

        private void prune(long now) {
            long cutoff = now - WINDOW_NANOS;
            while (size > 0 && clicks[first] <= cutoff) {
                first = (first + 1) % clicks.length;
                size--;
            }
        }
    }
}
