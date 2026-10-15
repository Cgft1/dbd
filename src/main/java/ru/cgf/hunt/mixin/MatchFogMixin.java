package ru.cgf.hunt.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.cgf.hunt.HuntClient;

@Mixin(BackgroundRenderer.class)
public abstract class MatchFogMixin {
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void hunt$applyMatchFog(Camera camera, BackgroundRenderer.FogType fogType,
            float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (HuntClient.isInMatch() && fogType == BackgroundRenderer.FogType.FOG_TERRAIN) {
            // Apply real render fog instead of decorative white particles.
            RenderSystem.setShaderFogColor(0.48F, 0.50F, 0.51F);
            RenderSystem.setShaderFogStart(3.0F);
            RenderSystem.setShaderFogEnd(20.0F);
        }
    }
}
