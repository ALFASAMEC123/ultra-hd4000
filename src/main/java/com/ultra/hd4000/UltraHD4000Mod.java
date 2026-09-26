package com.ultra.hd4000;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UltraHD4000Mod implements ModInitializer {
    public static final String MOD_ID = "ultra-hd4000";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("=== Ultra HD 4000 Initializing (Common) ===");
        LOGGER.info("Target: Intel HD 4000 (Ivy Bridge) | Java 21 | Minecraft 1.21.1");
    }
}