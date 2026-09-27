package com.ultra.hd4000.mixin;

import com.ultra.hd4000.UltraHD4000Mod;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    
    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void onRenderChunkLayer(int layer, CallbackInfo ci) {
        if (!UltraHD4000ClientMod.RENDER.getConfig().enableCpuCulling) return;
        
        ci.cancel();
    }
    
    @Inject(method = "updateFrustum", at = @At("RETURN"))
    private void onUpdateFrustum(Camera camera, CallbackInfo ci) {
        if (!UltraHD4000ClientMod.RENDER.getConfig().enableCpuCulling) return;
        
        Matrix4f viewProj = new Matrix4f(camera.getProjectionMatrix())
            .mul(camera.getViewMatrix());
        
        float[] matrixArray = new float[16];
        viewProj.get(matrixArray);
        
        UltraHD4000ClientMod.RENDER.cullAndDraw(matrixArray);
    }
}