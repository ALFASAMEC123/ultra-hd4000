package com.ultra.hd4000;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import com.ultra.hd4000.core.CompatLayer;
import com.ultra.hd4000.render.RenderModule;
import com.ultra.hd4000.chunk.ChunkModule;
import com.ultra.hd4000.tick.TickModule;
import com.ultra.hd4000.entity.EntityModule;
import com.ultra.hd4000.network.NetworkModule;
import com.ultra.hd4000.render.UpscalePipeline;
import com.ultra.hd4000.core.ModConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UltraHD4000ClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-client");
    
    private static KeyBinding benchmarkKey;
    private static KeyBinding toggleModulesKey;
    private static KeyBinding reloadShadersKey;
    
    private static long lastBenchmarkTime = 0;
    private static int frameCount = 0;
    private static long lastFpsTime = System.currentTimeMillis();
    
    public static RenderModule RENDER;
    public static ChunkModule CHUNK;
    public static TickModule TICK;
    public static EntityModule ENTITY;
    public static NetworkModule NETWORK;
    public static CompatLayer COMPAT;

    @Override
    public void onInitializeClient() {
        LOGGER.info("=== Ultra HD 4000 Client Initializing ===");
        
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
        
        UpscalePipeline.init();
        
        ScreenRegistry.register(ModConfigScreen::createConfigScreen, Text.literal("Ultra HD 4000"));
        
        KeyBinding benchmarkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ultra-hd4000.benchmark",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            "category.ultra-hd4000"
        ));
        
        KeyBinding toggleModulesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ultra-hd4000.toggle_modules",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F11,
            "category.ultra-hd4000"
        ));
        
        KeyBinding reloadShadersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ultra-hd4000.reload_shaders",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "category.ultra-hd4000"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (benchmarkKey.wasPressed()) {
                Profiler.startProfiling(Config.getInstance().benchmarkDurationSeconds);
            }
            // Toggle modules with F11
        });
        
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (Config.getInstance().enableUpscaling) {
                UpscalePipeline.render(context);
            }
        });
        
        LOGGER.info("Ultra HD 4000 Client Initialized");
    }
    
    public void runBenchmark() {
        Profiler.startProfiling(Config.getInstance().benchmarkDurationSeconds);
    }
}