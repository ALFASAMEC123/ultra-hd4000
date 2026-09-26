package com.ultra.hd4000.mixin;

import com.ultra.hd4000.UltraHD4000Mod;
import com.ultra.hd4000.render.RenderModule;
import net.minecraft.client.render.chunk.ChunkRenderDispatcher;
import net.minecraft.client.render.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkRenderDispatcher.class)
public class ChunkRenderDispatcherMixin {
    
    @Inject(method = "rebuildChunk", at = @At("HEAD"), cancellable = true)
    private void onRebuildChunk(RenderChunk renderChunk, CallbackInfo ci) {
        if (!UltraHD4000ClientMod.RENDER.getConfig().enableCompressedVbo) return;
        
        ci.cancel();
    }
    
    @Inject(method = "drawChunk", at = @At("HEAD"), cancellable = true)
    private void onDrawChunk(RenderChunk renderChunk, CallbackInfo ci) {
        if (!UltraHD4000ClientMod.RENDER.getConfig().enableIndirectDraw) return;
        
        ci.cancel();
    }
}