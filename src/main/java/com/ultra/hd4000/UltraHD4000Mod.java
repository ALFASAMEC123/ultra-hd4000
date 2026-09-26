package com.ultra.hd4000;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import com.ultra.hd4000.core.CompatLayer;
import com.ultra.hd4000.render.RenderModule;
import com.ultra.hd4000.chunk.ChunkModule;
import com.ultra.hd4000.tick.TickModule;
import com.ultra.hd4000.entity.EntityModule;
import com.ultra.hd4000.network.NetworkModule;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UltraHD4000Mod implements ModInitializer {
    public static final String MOD_ID = "ultra-hd4000";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static RenderModule RENDER;
    public static ChunkModule CHUNK;
    public static TickModule TICK;
    public static EntityModule ENTITY;
    public static NetworkModule NETWORK;
    public static CompatLayer COMPAT;
    
    private static UltraHD4000Mod INSTANCE;
    
    public static UltraHD4000Mod getInstance() {
        return INSTANCE;
    }
    
    public void runBenchmark() {
        Profiler.startProfiling(Config.getInstance().benchmarkDurationSeconds);
    }
    
    @Override
    public void onInitialize() {
        INSTANCE = this;
        long startTime = System.nanoTime();
        LOGGER.info("=== Ultra HD 4000 Initializing ===");
        
        Config.load();
        
        Profiler.init();
        
        COMPAT = new CompatLayer();
        COMPAT.detectAndDisableConflicts();
        
        CHUNK = new ChunkModule();
        CHUNK.init();
        
        RENDER = new RenderModule();
        RENDER.init();
        
        TICK = new TickModule();
        TICK.init();
        
        ENTITY = new EntityModule();
        ENTITY.init();
        
        NETWORK = new NetworkModule();
        NETWORK.init();
        
        long initTime = (System.nanoTime() - startTime) / 1_000_000;
        LOGGER.info("Ultra HD 4000 initialized in {} ms", initTime);
        LOGGER.info("Target: Intel HD 4000 (Ivy Bridge) | Java 21 | Minecraft 1.21.1");
    }
}