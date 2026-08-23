package io.github.theruviparambil.craftmetry;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class CraftmetryClient implements ClientModInitializer {
    private static final Identifier CPS_HUD = Identifier.fromNamespaceAndPath("craftmetry", "cps_spike");
    private static final ClickWindow LEFT_CLICKS = new ClickWindow();
    private static final ClickWindow RIGHT_CLICKS = new ClickWindow();

    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CPS_HUD, CraftmetryClient::renderCps);
    }

    public static void observeKeyMapping(InputConstants.Key key, boolean down) {
        if (!down) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null
                || client.gui.screen() != null || client.gui.overlay() != null) {
            return;
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
        if (client.player == null || client.level == null) {
            return;
        }

        long now = System.nanoTime();
        String text = "L " + LEFT_CLICKS.count(now) + "  R " + RIGHT_CLICKS.count(now);
        graphics.fill(6, 6, 94, 26, 0xCC191A18);
        graphics.fill(6, 6, 8, 26, 0xFFB6F23A);
        graphics.text(client.font, text, 13, 12, 0xFFF0F2ED, true);
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

        private void prune(long now) {
            long cutoff = now - WINDOW_NANOS;
            while (size > 0 && clicks[first] <= cutoff) {
                first = (first + 1) % clicks.length;
                size--;
            }
        }
    }
}
