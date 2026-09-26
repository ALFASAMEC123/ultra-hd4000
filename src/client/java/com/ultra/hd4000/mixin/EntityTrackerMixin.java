package com.ultra.hd4000.mixin;

import com.ultra.hd4000.UltraHD4000Mod;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.network.EntityTracker;
import net.minecraft.server.network.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkLoadingManager.EntityTracker.class)
public class EntityTrackerMixin {
    
    @Inject(method = "addEntity", at = @At("RETURN"))
    private void onEntityAdded(Entity entity, CallbackInfo ci) {
        if (!UltraHD4000Mod.ENTITY.getConfig().enableEntityInstancing) return;
    }
    
    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void onEntityRemoved(Entity entity, CallbackInfo ci) {
        if (!UltraHD4000Mod.ENTITY.getConfig().enableEntityInstancing) return;
    }
}