package com.ultra.hd4000;

import com.ultra.hd4000.render.UpscalePipeline;
import com.ultra.hd4000.core.Config;
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

    @Override
    public void onInitializeClient() {
        LOGGER.info("Ultra HD 4000 Client Initializing...");
        
        UpscalePipeline.init();
        
        ScreenRegistry.register(ModConfigScreen::createConfigScreen, Text.literal("Ultra HD 4000"));
        
        benchmarkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ultra-hd4000.benchmark",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            "category.ultra-hd4000"
        ));
        
        toggleModulesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ultra-hd4000.toggle_modules",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F11,
            "category.ultra-hd4000"
        ));
        
        reloadShadersKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ultra-hd4000.reload_shaders",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "category.ultra-hd4000"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeyPresses(client);
            updateFpsCounter();
        });
        
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (Config.getInstance().enableUpscaling) {
                UpscalePipeline.render(context);
            }
        });
        
        LOGGER.info("Ultra HD 4000 Client Initialized");
    }
    
    private void handleKeyPresses(MinecraftClient client) {
        if (benchmarkKey.wasPressed()) {
            UltraHD4000Mod.getInstance().runBenchmark();
        }
        if (toggleModulesKey.wasPressed()) {
            toggleModules();
        }
        if (reloadShadersKey.wasPressed()) {
            UpscalePipeline.reloadShaders();
        }
    }
    
    private void toggleModules() {
        Config cfg = Config.getInstance();
        cfg.enableSoftwareVertex = !cfg.enableSoftwareVertex;
        cfg.enableCpuCulling = !cfg.enableCpuCulling;
        cfg.enableOffHeapChunks = !cfg.enableOffHeapChunks;
        cfg.enablePriorityScheduler = !cfg.enablePriorityScheduler;
        cfg.enableEntityInstancing = !cfg.enableEntityInstancing;
        Config.save();
        LOGGER.info("Modules toggled: vertex={}, culling={}, offheap={}, scheduler={}, instancing={}",
            cfg.enableSoftwareVertex, cfg.enableCpuCulling, cfg.enableOffHeapChunks,
            cfg.enablePriorityScheduler, cfg.enableEntityInstancing);
    }
    
    private void updateFpsCounter() {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            frameCount = 0;
            lastFpsTime = now;
        }
    }
}