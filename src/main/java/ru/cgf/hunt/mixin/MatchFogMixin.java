package ru.cgf.hunt.mixin;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.cgf.hunt.HuntClient;

@Mixin(BackgroundRenderer.class)
public abstract class MatchFogMixin {
    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void hunt$applyMatchFog(Camera camera, BackgroundRenderer.FogType fogType,
            float viewDistance, boolean thickFog, float tickDelta,
            CallbackInfoReturnable<Fog> cir) {
        if (HuntClient.isInMatch() && fogType == BackgroundRenderer.FogType.FOG_TERRAIN) {
            // Dense, desaturated grey fog with a short render distance for a horror atmosphere.
            cir.setReturnValue(new Fog(3.0F, 20.0F, FogShape.SPHERE, 0.48F, 0.50F, 0.51F, false));
        }
    }
}
