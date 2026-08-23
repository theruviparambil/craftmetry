package io.github.theruviparambil.craftmetry.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.theruviparambil.craftmetry.CraftmetryClient;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "set", at = @At("HEAD"))
    private static void craftmetry$observeClick(InputConstants.Key key, boolean down, CallbackInfo callbackInfo) {
        CraftmetryClient.observeKeyMapping(key, down);
    }
}
