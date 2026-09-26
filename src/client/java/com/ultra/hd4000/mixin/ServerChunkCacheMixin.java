package com.ultra.hd4000.mixin;

import com.ultra.hd4000.UltraHD4000Mod;
import com.ultra.hd4000.chunk.ChunkModule;
import net.minecraft.server.world.ServerChunkCache;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
    
    @Inject(method = "loadChunk", at = @At("RETURN"))
    private void onChunkLoad(ChunkPos pos, CallbackInfo ci) {
        if (!UltraHD4000ClientMod.CHUNK.getConfig().enableOffHeapChunks) return;
        
        UltraHD4000ClientMod.CHUNK.onChunkLoad(pos);
    }
    
    @Inject(method = "unloadChunk", at = @At("HEAD"))
    private void onChunkUnload(ChunkPos pos, CallbackInfo ci) {
        if (!UltraHD4000ClientMod.CHUNK.getConfig().enableOffHeapChunks) return;
        
        UltraHD4000ClientMod.CHUNK.onChunkUnload(pos);
    }
}