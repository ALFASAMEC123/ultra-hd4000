package com.ultra.hd4000.network;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-network");
    
    private final Map<UUID, EntityDeltaState> entityStates = new ConcurrentHashMap<>();
    private final Map<Long, ChunkDeltaState> chunkStates = new ConcurrentHashMap<>();
    
    public void init() {
        Config cfg = Config.getInstance();
        LOGGER.info("NetworkModule initialized: deltaCompression={}, predictiveAck={}",
            cfg.enableDeltaCompression, cfg.enablePredictiveAck);
    }
    
    public Packet<?> compressPacket(ServerPlayNetworkHandler handler, Packet<?> packet) {
        if (!Config.getInstance().enableDeltaCompression) return packet;
        
        if (packet instanceof EntityPositionS2CPacket posPacket) {
            return compressEntityPosition(posPacket);
        } else if (packet instanceof ChunkDataS2CPacket chunkPacket) {
            return compressChunkData(chunkPacket);
        }
        
        return packet;
    }
    
    private EntityPositionS2CPacket compressEntityPosition(EntityPositionS2CPacket original) {
        // Would need Mixin accessor to get entity ID from packet
        UUID entityId = UUID.randomUUID(); // Placeholder
        
        EntityDeltaState state = entityStates.computeIfAbsent(entityId, k -> new EntityDeltaState());
        
        // These would use Mixin accessors
        double x = 0, y = 0, z = 0;
        byte yaw = 0, pitch = 0;
        boolean onGround = false;
        
        int deltaX = (int) ((x - state.lastX) * 4096.0);
        int deltaY = (int) ((y - state.lastY) * 4096.0);
        int deltaZ = (int) ((z - state.lastZ) * 4096.0);
        
        boolean useDelta = Math.abs(deltaX) <= Short.MAX_VALUE && 
                          Math.abs(deltaY) <= Short.MAX_VALUE && 
                          Math.abs(deltaZ) <= Short.MAX_VALUE;
        
        if (useDelta && state.hasPrevious) {
            state.lastX = x; state.lastY = y; state.lastZ = z;
            state.lastYaw = yaw; state.lastPitch = pitch; state.lastOnGround = onGround;
            
            return new CompressedEntityPositionPacket(
                original.getEntityId(), 
                (short) deltaX, (short) deltaY, (short) deltaZ,
                yaw, pitch, onGround
            );
        } else {
            state.lastX = x; state.lastY = y; state.lastZ = z;
            state.lastYaw = yaw; state.lastPitch = pitch; state.lastOnGround = onGround;
            state.hasPrevious = true;
            return original;
        }
    }
    
    private ChunkDataS2CPacket compressChunkData(ChunkDataS2CPacket original) {
        return original;
    }
    
    public static class CompressedEntityPositionPacket extends EntityPositionS2CPacket {
        public CompressedEntityPositionPacket(int entityId, short dx, short dy, short dz, 
                                              byte yaw, byte pitch, boolean onGround) {
            super(entityId, dx, dy, dz, yaw, pitch, onGround);
        }
        
        @Override
        public void write(PacketByteBuf buf) {
            buf.writeVarInt(getEntityId());
            buf.writeShort(getX());
            buf.writeShort(getY());
            buf.writeShort(getZ());
            buf.writeByte(getYaw());
            buf.writeByte(getPitch());
            buf.writeBoolean(isOnGround());
        }
    }
    
    static class EntityDeltaState {
        double lastX, lastY, lastZ;
        byte lastYaw, lastPitch;
        boolean lastOnGround;
        boolean hasPrevious = false;
    }
    
    static class ChunkDeltaState {
        byte[] lastData;
        long lastUpdate = 0;
    }
    
    public Config getConfig() {
        return Config.getInstance();
    }
    
    public void shutdown() {
        entityStates.clear();
        chunkStates.clear();
    }
}