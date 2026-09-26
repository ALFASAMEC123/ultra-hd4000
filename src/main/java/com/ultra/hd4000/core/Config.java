package com.ultra.hd4000.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config INSTANCE;
    
    public boolean enableSoftwareVertex = true;
    public boolean enableCpuCulling = true;
    public boolean enableCompressedVbo = true;
    public boolean enableMeshOptimizer = true;
    public boolean enableIndirectDraw = true;
    public boolean enableUpscaling = true;
    public int internalWidth = 960;
    public int internalHeight = 540;
    public float sharpenStrength = 0.3f;
    public int renderDistance = 64;
    public int minRenderDistance = 32;
    public int maxRenderDistance = 128;
    public int targetFps = 60;
    
    public boolean enableOffHeapChunks = true;
    public long offHeapMaxSizeBytes = 2L * 1024 * 1024 * 1024;
    public int activationRange = 64;
    public boolean lazyChunkLoading = true;
    public boolean compressChunkData = true;
    public int compressionLevel = 1;
    
    public boolean enablePriorityScheduler = true;
    public int highPriorityBudgetMs = 3;
    public int normalPriorityBudgetMs = 8;
    public int lowPriorityBudgetMs = 50;
    public boolean enableAiThrottling = true;
    public float aiThrottleThresholdTps = 19.5f;
    public int minActivationRange = 16;
    
    public boolean enableEntityInstancing = true;
    public boolean enableParticleSsbo = true;
    public int maxInstancesPerDraw = 1024;
    public boolean gpuDrivenCulling = true;
    
    public boolean enableDeltaCompression = true;
    public boolean enablePredictiveAck = false;
    
    public boolean enableProfiling = false;
    public boolean logFrameTimes = false;
    public int benchmarkDurationSeconds = 30;
    
    public static Config getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }
    
    public static void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ultra-hd4000.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                INSTANCE = GSON.fromJson(reader, Config.class);
                LOGGER.info("Config loaded from {}", configPath);
            } catch (Exception e) {
                LOGGER.error("Failed to load config, using defaults", e);
                INSTANCE = new Config();
            }
        } else {
            INSTANCE = new Config();
            save();
        }
    }
    
    public static void save() {
        if (INSTANCE == null) return;
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ultra-hd4000.json");
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(INSTANCE, writer);
            LOGGER.info("Config saved to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
    
    public static void resetToDefaults() {
        INSTANCE = new Config();
        save();
    }
}