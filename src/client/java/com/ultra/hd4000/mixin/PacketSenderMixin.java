package com.ultra.hd4000.mixin;

import com.ultra.hd4000.UltraHD4000Mod;
import com.ultra.hd4000.network.NetworkModule;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class PacketSenderMixin {
    
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!UltraHD4000ClientMod.NETWORK.getConfig().enableDeltaCompression) return;
        
        Packet<?> compressed = UltraHD4000ClientMod.NETWORK.compressPacket(
            (ServerPlayNetworkHandler) (Object) this, packet);
        
        if (compressed != packet) {
            ci.cancel();
        }
    }
}