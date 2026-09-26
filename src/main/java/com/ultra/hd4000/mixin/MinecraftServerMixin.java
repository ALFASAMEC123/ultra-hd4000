package com.ultra.hd4000.mixin;

import com.ultra.hd4000.UltraHD4000Mod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        if (!UltraHD4000Mod.TICK.getConfig().enablePriorityScheduler) return;
        
        MinecraftServer server = (MinecraftServer) (Object) this;
        UltraHD4000Mod.TICK.onServerTickStart(server);
    }
}