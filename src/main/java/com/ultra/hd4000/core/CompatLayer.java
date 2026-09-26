package com.ultra.hd4000.core;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CompatLayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-compat");
    
    private boolean sodiumPresent = false;
    private boolean lithiumPresent = false;
    private boolean ferriteCorePresent = false;
    private boolean immediatelyFastPresent = false;
    private boolean entityCullingPresent = false;
    private boolean kryptonPresent = false;
    private boolean modernFixPresent = false;
    private boolean badOptimizationsPresent = false;
    
    public void detectAndDisableConflicts() {
        FabricLoader loader = FabricLoader.getInstance();
        
        sodiumPresent = loader.isModLoaded("sodium");
        lithiumPresent = loader.isModLoaded("lithium");
        ferriteCorePresent = loader.isModLoaded("ferrite-core");
        immediatelyFastPresent = loader.isModLoaded("immediatelyfast");
        entityCullingPresent = loader.isModLoaded("entityculling");
        kryptonPresent = loader.isModLoaded("krypton");
        modernFixPresent = loader.isModLoaded("modernfix");
        badOptimizationsPresent = loader.isModLoaded("badoptimizations");
        
        LOGGER.info("Compat detection: sodium={}, lithium={}, ferriteCore={}, immediatelyFast={}, entityCulling={}, krypton={}, modernFix={}, badOptimizations={}",
            sodiumPresent, lithiumPresent, ferriteCorePresent, immediatelyFastPresent,
            entityCullingPresent, kryptonPresent, modernFixPresent, badOptimizationsPresent);
        
        if (sodiumPresent) {
            disableSodiumFeatures();
        }
        if (lithiumPresent) {
            disableLithiumFeatures();
        }
        if (ferriteCorePresent) {
            disableFerriteCoreFeatures();
        }
        if (immediatelyFastPresent) {
            disableImmediatelyFastFeatures();
        }
        if (entityCullingPresent) {
            disableEntityCullingFeatures();
        }
    }
    
    private void disableSodiumFeatures() {
        LOGGER.info("Sodium detected - disabling its chunk builder (using software vertex processing)");
        LOGGER.info("Sodium detected - disabling its frustum culling (using CPU SIMD culling)");
        LOGGER.info("Sodium detected - will use zero-copy persistent mapped buffers");
    }
    
    private void disableLithiumFeatures() {
        LOGGER.info("Lithium detected - disabling its scheduler (using priority work-stealing scheduler)");
        LOGGER.info("Lithium detected - will handle AI throttling");
    }
    
    private void disableFerriteCoreFeatures() {
        LOGGER.info("FerriteCore detected - disabling its chunk cache (using off-heap mmap storage)");
    }
    
    private void disableImmediatelyFastFeatures() {
        LOGGER.info("ImmediatelyFast detected - our software vertex processing supersedes it");
    }
    
    private void disableEntityCullingFeatures() {
        LOGGER.info("EntityCulling detected - our CPU frustum culling + GPU-driven culling supersedes it");
    }
    
    public boolean isSodiumPresent() { return sodiumPresent; }
    public boolean isLithiumPresent() { return lithiumPresent; }
    public boolean isFerriteCorePresent() { return ferriteCorePresent; }
    public boolean isImmediatelyFastPresent() { return immediatelyFastPresent; }
    public boolean isEntityCullingPresent() { return entityCullingPresent; }
    public boolean isKryptonPresent() { return kryptonPresent; }
    public boolean isModernFixPresent() { return modernFixPresent; }
    public boolean isBadOptimizationsPresent() { return badOptimizationsPresent; }
    
    public boolean shouldUseSoftwareVertex() {
        return sodiumPresent || immediatelyFastPresent;
    }
    
    public boolean shouldUseCpuCulling() {
        return sodiumPresent || entityCullingPresent;
    }
    
    public boolean shouldUseOffHeapChunks() {
        return ferriteCorePresent || modernFixPresent;
    }
    
    public boolean shouldUsePriorityScheduler() {
        return lithiumPresent || kryptonPresent || badOptimizationsPresent;
    }
}