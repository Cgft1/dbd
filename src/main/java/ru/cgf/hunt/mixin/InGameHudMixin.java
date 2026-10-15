package ru.cgf.hunt.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.cgf.hunt.HuntClient;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void hunt$hideHealthAndHunger(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HuntClient.isInMatch()) {
            ci.cancel();
        }
    }
}