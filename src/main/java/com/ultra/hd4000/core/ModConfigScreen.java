package com.ultra.hd4000.core;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ModConfigScreen {
    public static Screen createConfigScreen(Screen parent) {
        Config cfg = Config.getInstance();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("Ultra HD 4000 Settings").formatted(Formatting.AQUA))
            .setSavingRunnable(Config::save);
        
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        
        ConfigCategory render = builder.getOrCreateCategory(Text.literal("Rendering"));
        
        render.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Software Vertex Processing"),
                cfg.enableSoftwareVertex)
            .setTooltip(Text.literal("Process vertices on CPU (AVX2) instead of GPU vertex shader"))
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableSoftwareVertex = val)
            .build());
        
        render.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("CPU Frustum Culling"),
                cfg.enableCpuCulling)
            .setTooltip(Text.literal("SIMD frustum culling on CPU"))
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableCpuCulling = val)
            .build());
        
        render.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Compressed VBO Format"),
                cfg.enableCompressedVbo)
            .setTooltip(Text.literal("16-bit positions, octahedral normals, packed UV/light"))
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableCompressedVbo = val)
            .build());
        
        render.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Meshoptimizer (JNI)"),
                cfg.enableMeshOptimizer)
            .setTooltip(Text.literal("Optimize vertex cache, overdraw, fetch (requires native library)"))
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableMeshOptimizer = val)
            .build());
        
        render.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Indirect Drawing"),
                cfg.enableIndirectDraw)
            .setTooltip(Text.literal("glMultiDrawArraysIndirect for chunk rendering"))
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableIndirectDraw = val)
            .build());
        
        render.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Dynamic Upscaling"),
                cfg.enableUpscaling)
            .setTooltip(Text.literal("Render at lower internal resolution + upscale"))
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableUpscaling = val)
            .build());
        
        render.addEntry(entryBuilder.startIntSlider(
                Text.literal("Internal Width"), cfg.internalWidth, 640, 1920)
            .setDefaultValue(960)
            .setSaveConsumer(val -> cfg.internalWidth = val)
            .build());
        
        render.addEntry(entryBuilder.startIntSlider(
                Text.literal("Internal Height"), cfg.internalHeight, 360, 1080)
            .setDefaultValue(540)
            .setSaveConsumer(val -> cfg.internalHeight = val)
            .build());
        
        render.addEntry(entryBuilder.startFloatSlider(
                Text.literal("Sharpen Strength"), cfg.sharpenStrength, 0.0f, 1.0f)
            .setDefaultValue(0.3f)
            .setSaveConsumer(val -> cfg.sharpenStrength = val)
            .build());
        
        render.addEntry(entryBuilder.startIntSlider(
                Text.literal("Render Distance"), cfg.renderDistance, 16, 128)
            .setDefaultValue(64)
            .setSaveConsumer(val -> cfg.renderDistance = val)
            .build());
        
        render.addEntry(entryBuilder.startIntSlider(
                Text.literal("Min Render Distance"), cfg.minRenderDistance, 16, 64)
            .setDefaultValue(32)
            .setSaveConsumer(val -> cfg.minRenderDistance = val)
            .build());
        
        render.addEntry(entryBuilder.startIntSlider(
                Text.literal("Max Render Distance"), cfg.maxRenderDistance, 64, 256)
            .setDefaultValue(128)
            .setSaveConsumer(val -> cfg.maxRenderDistance = val)
            .build());
        
        render.addEntry(entryBuilder.startIntSlider(
                Text.literal("Target FPS"), cfg.targetFps, 30, 144)
            .setDefaultValue(60)
            .setSaveConsumer(val -> cfg.targetFps = val)
            .build());
        
        ConfigCategory chunk = builder.getOrCreateCategory(Text.literal("Chunk Storage"));
        
        chunk.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Off-Heap Chunk Storage (mmap)"),
                cfg.enableOffHeapChunks)
            .setTooltip(Text.literal("Store chunk meshes in mmap'd file outside Java heap"))
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableOffHeapChunks = val)
            .build());
        
        chunk.addEntry(entryBuilder.startLongSlider(
                Text.literal("Off-Heap Max Size (MB)"), cfg.offHeapMaxSizeBytes / 1024 / 1024, 256, 8192)
            .setDefaultValue(2048)
            .setSaveConsumer(val -> cfg.offHeapMaxSizeBytes = val * 1024 * 1024)
            .build());
        
        chunk.addEntry(entryBuilder.startIntSlider(
                Text.literal("Activation Range"), cfg.activationRange, 16, 128)
            .setDefaultValue(64)
            .setSaveConsumer(val -> cfg.activationRange = val)
            .build());
        
        chunk.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Lazy Chunk Loading"),
                cfg.lazyChunkLoading)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.lazyChunkLoading = val)
            .build());
        
        chunk.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Compress Chunk Data (zstd)"),
                cfg.compressChunkData)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.compressChunkData = val)
            .build());
        
        chunk.addEntry(entryBuilder.startIntSlider(
                Text.literal("Compression Level"), cfg.compressionLevel, 1, 22)
            .setDefaultValue(1)
            .setSaveConsumer(val -> cfg.compressionLevel = val)
            .build());
        
        ConfigCategory tick = builder.getOrCreateCategory(Text.literal("Tick Scheduler"));
        
        tick.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Priority Work-Stealing Scheduler"),
                cfg.enablePriorityScheduler)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enablePriorityScheduler = val)
            .build());
        
        tick.addEntry(entryBuilder.startIntSlider(
                Text.literal("High Priority Budget (ms)"), cfg.highPriorityBudgetMs, 1, 10)
            .setDefaultValue(3)
            .setSaveConsumer(val -> cfg.highPriorityBudgetMs = val)
            .build());
        
        tick.addEntry(entryBuilder.startIntSlider(
                Text.literal("Normal Priority Budget (ms)"), cfg.normalPriorityBudgetMs, 1, 20)
            .setDefaultValue(8)
            .setSaveConsumer(val -> cfg.normalPriorityBudgetMs = val)
            .build());
        
        tick.addEntry(entryBuilder.startIntSlider(
                Text.literal("Low Priority Budget (ms)"), cfg.lowPriorityBudgetMs, 10, 100)
            .setDefaultValue(50)
            .setSaveConsumer(val -> cfg.lowPriorityBudgetMs = val)
            .build());
        
        tick.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("AI Throttling"),
                cfg.enableAiThrottling)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableAiThrottling = val)
            .build());
        
        tick.addEntry(entryBuilder.startFloatSlider(
                Text.literal("Throttle Threshold TPS"), cfg.aiThrottleThresholdTps, 10.0f, 20.0f)
            .setDefaultValue(19.5f)
            .setSaveConsumer(val -> cfg.aiThrottleThresholdTps = val)
            .build());
        
        tick.addEntry(entryBuilder.startIntSlider(
                Text.literal("Min Activation Range"), cfg.minActivationRange, 8, 32)
            .setDefaultValue(16)
            .setSaveConsumer(val -> cfg.minActivationRange = val)
            .build());
        
        ConfigCategory entity = builder.getOrCreateCategory(Text.literal("Entity Rendering"));
        
        entity.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Entity Instancing"),
                cfg.enableEntityInstancing)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableEntityInstancing = val)
            .build());
        
        entity.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Particle SSBO"),
                cfg.enableParticleSsbo)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableParticleSsbo = val)
            .build());
        
        entity.addEntry(entryBuilder.startIntSlider(
                Text.literal("Max Instances Per Draw"), cfg.maxInstancesPerDraw, 256, 4096)
            .setDefaultValue(1024)
            .setSaveConsumer(val -> cfg.maxInstancesPerDraw = val)
            .build());
        
        entity.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("GPU-Driven Culling"),
                cfg.gpuDrivenCulling)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.gpuDrivenCulling = val)
            .build());
        
        ConfigCategory network = builder.getOrCreateCategory(Text.literal("Network (Singleplayer)"));
        
        network.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Delta Entity Compression"),
                cfg.enableDeltaCompression)
            .setDefaultValue(true)
            .setSaveConsumer(val -> cfg.enableDeltaCompression = val)
            .build());
        
        network.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Predictive Acknowledgment"),
                cfg.enablePredictiveAck)
            .setDefaultValue(false)
            .setSaveConsumer(val -> cfg.enablePredictiveAck = val)
            .build());
        
        ConfigCategory debug = builder.getOrCreateCategory(Text.literal("Debug & Profiling"));
        
        debug.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Enable Profiling"),
                cfg.enableProfiling)
            .setDefaultValue(false)
            .setSaveConsumer(val -> cfg.enableProfiling = val)
            .build());
        
        debug.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Log Frame Times"),
                cfg.logFrameTimes)
            .setDefaultValue(false)
            .setSaveConsumer(val -> cfg.logFrameTimes = val)
            .build());
        
        debug.addEntry(entryBuilder.startIntSlider(
                Text.literal("Benchmark Duration (s)"), cfg.benchmarkDurationSeconds, 10, 300)
            .setDefaultValue(30)
            .setSaveConsumer(val -> cfg.benchmarkDurationSeconds = val)
            .build());
        
        debug.addEntry(entryBuilder.startButton(
                Text.literal("Run 30s Benchmark (F10)"))
            .setTooltip(Text.literal("Press F10 in-game to run benchmark"))
            .setSaveConsumer(b -> UltraHD4000Mod.getInstance().runBenchmark())
            .build());
        
        debug.addEntry(entryBuilder.startButton(
                Text.literal("Reset to Defaults"))
            .setTooltip(Text.literal("Reset all settings to defaults"))
            .setSaveConsumer(b -> {
                Config.resetToDefaults();
                MinecraftClient.getInstance().setScreen(createConfigScreen(parent));
            })
            .build());
        
        return builder.build();
    }
}