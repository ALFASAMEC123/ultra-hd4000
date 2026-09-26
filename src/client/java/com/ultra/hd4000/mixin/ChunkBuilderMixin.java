package com.ultra.hd4000.mixin;

import com.ultra.hd4000.UltraHD4000Mod;
import com.ultra.hd4000.render.RenderModule;
import com.ultra.hd4000.render.SoftwareVertexProcessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderDispatcher;
import net.minecraft.client.render.chunk.RenderChunk;
import net.minecraft.client.render.chunk.ChunkRenderDispatcher.RenderChunkBuilder;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChunkBuilder.class)
public class ChunkBuilderMixin {
    
    @Inject(method = "buildChunkMesh", at = @At("HEAD"), cancellable = true)
    private void onBuildChunkMesh(BlockRenderView world, RenderChunk renderChunk, 
                                   ChunkRenderDispatcher.RenderChunkBuilder builder, 
                                   CallbackInfoReturnable<List<BakedQuad>> cir) {
        
        if (!UltraHD4000ClientMod.RENDER.getConfig().enableSoftwareVertex) return;
        
        int chunkX = ChunkSectionPos.getSectionCoord(renderChunk.getOrigin().getX());
        int chunkY = ChunkSectionPos.getSectionCoord(renderChunk.getOrigin().getY());
        int chunkZ = ChunkSectionPos.getSectionCoord(renderChunk.getOrigin().getZ());
        
        RenderModule.ChunkSectionData sectionData = new RenderModule.ChunkSectionData();
        
        SoftwareVertexProcessor.processSection(chunkX, chunkY, chunkZ, sectionData);
        
        int sectionIndex = (chunkY & 0xF) * 256 + (chunkZ & 0xF) * 16 + (chunkX & 0xF);
        UltraHD4000ClientMod.RENDER.uploadChunkSection(sectionIndex, sectionData);
        
        cir.setReturnValue(List.of());
    }
}