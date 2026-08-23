package io.github.theruviparambil.craftmetry;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
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

public final class CraftmetryClient implements ClientModInitializer {
    private static final Identifier CPS_HUD = Identifier.fromNamespaceAndPath("craftmetry", "cps");
    private static final Identifier CONTROLS = Identifier.fromNamespaceAndPath("craftmetry", "controls");
    private static final ClickWindow LEFT_CLICKS = new ClickWindow();
    private static final ClickWindow RIGHT_CLICKS = new ClickWindow();
    private static KeyMapping toggleHud;
    private static boolean hudVisible = true;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(CONTROLS);
        toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.craftmetry.toggle_hud",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_H,
                category));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CPS_HUD, CraftmetryClient::renderCps);
        ClientTickEvents.END_CLIENT_TICK.register(CraftmetryClient::handleClientTick);
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

        if (toggleHud != null && toggleHud.matches(key) && !toggleHud.isDown()) {
            hudVisible = !hudVisible;
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
        if (!hudVisible || !isGameplayInputContext(client)) {
            return;
        }

        long now = System.nanoTime();
        String text = "L " + LEFT_CLICKS.count(now) + "  R " + RIGHT_CLICKS.count(now);
        graphics.fill(6, 6, 94, 26, 0xCC191A18);
        graphics.fill(6, 6, 8, 26, 0xFFB6F23A);
        graphics.text(client.font, text, 13, 12, 0xFFF0F2ED, true);
    }

    private static void handleClientTick(Minecraft client) {
        while (toggleHud.consumeClick()) {
            // KeyMapping.set handles the rising edge; this keeps the click queue bounded.
        }

        if (!isGameplayInputContext(client)) {
            resetClicks();
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
