package com.ultra.hd4000.tick;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkCache;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class TickModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-tick");
    
    private final ForkJoinPool workStealingPool;
    
    private final PriorityBlockingQueue<TickTask> highQueue = new PriorityBlockingQueue<>();
    private final PriorityBlockingQueue<TickTask> normalQueue = new PriorityBlockingQueue<>();
    private final PriorityBlockingQueue<TickTask> lowQueue = new PriorityBlockingQueue<>();
    
    private final Map<String, Queue<Runnable>> taskQueues = new ConcurrentHashMap<>();
    
    private final AtomicLong lastTickTime = new AtomicLong(System.nanoTime());
    private volatile double currentTps = 20.0;
    private volatile int activationRange = 64;
    
    private final Map<UUID, Long> entityLastAiTick = new ConcurrentHashMap<>();
    
    public TickModule() {
        Config cfg = Config.getInstance();
        int parallelism = Runtime.getRuntime().availableProcessors();
        
        this.workStealingPool = new ForkJoinPool(parallelism, 
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            (t, e) -> LOGGER.error("Tick thread error", e),
            true);
    }
    
    public void init() {
        Config cfg = Config.getInstance();
        activationRange = cfg.activationRange;
        
        Thread schedulerThread = new Thread(this::schedulerLoop, "ultra-tick-scheduler");
        schedulerThread.setPriority(Thread.NORM_PRIORITY + 2);
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        
        LOGGER.info("TickModule initialized: priorityScheduler={}, parallelism={}, activationRange={}",
            cfg.enablePriorityScheduler, workStealingPool.getParallelism(), activationRange);
    }
    
    public void onServerTickStart(MinecraftServer server) {
        long now = System.nanoTime();
        long delta = now - lastTickTime.getAndSet(now);
        currentTps = 1_000_000_000.0 / delta;
        
        if (Config.getInstance().enableAiThrottling && currentTps < Config.getInstance().aiThrottleThresholdTps) {
            float factor = (float) (currentTps / 20.0);
            activationRange = Math.max(Config.getInstance().minActivationRange, 
                (int) (Config.getInstance().activationRange * factor));
        } else {
            activationRange = Config.getInstance().activationRange;
        }
        
        submitHighPriority(() -> tickPlayers(server));
        submitHighPriority(() -> tickRedstone(server));
        submitHighPriority(() -> tickBlockEntities(server));
        
        submitNormal(() -> tickEntities(server));
        submitNormal(() -> tickFluids(server));
        
        submitLow(() -> tickDecorative(server));
    }
    
    private void schedulerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            long tickStart = System.nanoTime();
            Config cfg = Config.getInstance();
            
            long highBudget = cfg.highPriorityBudgetMs * 1_000_000L;
            processQueue(highQueue, highBudget);
            
            long normalBudget = cfg.normalPriorityBudgetMs * 1_000_000L;
            processQueue(normalQueue, normalBudget);
            
            long elapsed = System.nanoTime() - tickStart;
            long lowBudget = Math.max(0, cfg.lowPriorityBudgetMs * 1_000_000L - elapsed);
            processQueue(lowQueue, lowBudget);
            
            long remaining = 50_000_000L - (System.nanoTime() - tickStart);
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining / 1_000_000, (int) (remaining % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    private void processQueue(PriorityBlockingQueue<TickTask> queue, long budgetNs) {
        long start = System.nanoTime();
        while (System.nanoTime() - start < budgetNs) {
            TickTask task = queue.poll();
            if (task == null) break;
            
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("Tick task failed", e);
            }
        }
    }
    
    public void submitHighPriority(Runnable task) {
        highQueue.offer(new TickTask(task, System.nanoTime()));
    }
    
    public void submitNormal(Runnable task) {
        normalQueue.offer(new TickTask(task, System.nanoTime()));
    }
    
    public void submitLow(Runnable task) {
        lowQueue.offer(new TickTask(task, System.nanoTime()));
    }
    
    private void tickPlayers(MinecraftServer server) {
        Profiler.startTimer("tick_players");
        for (ServerWorld world : server.getWorlds()) {
            for (var player : world.getPlayers()) {
                player.tick();
            }
        }
        Profiler.stopTimer("tick_players");
    }
    
    private void tickRedstone(MinecraftServer server) {
        Profiler.startTimer("tick_redstone");
        Profiler.stopTimer("tick_redstone");
    }
    
    private void tickBlockEntities(MinecraftServer server) {
        Profiler.startTimer("tick_block_entities");
        Profiler.stopTimer("tick_block_entities");
    }
    
    private void tickEntities(MinecraftServer server) {
        Profiler.startTimer("tick_entities");
        
        for (ServerWorld world : server.getWorlds()) {
            for (var entity : world.getEntities()) {
                if (shouldSkipAi(entity)) continue;
                
                workStealingPool.execute(() -> {
                    try {
                        entity.tick();
                    } catch (Exception e) {
                        LOGGER.error("Entity tick failed", e);
                    }
                });
            }
        }
        Profiler.stopTimer("tick_entities");
    }
    
    private boolean shouldSkipAi(LivingEntity entity) {
        if (!Config.getInstance().enableAiThrottling) return false;
        if (!(entity instanceof LivingEntity)) return false;
        
        UUID uuid = entity.getUuid();
        long now = System.currentTimeMillis();
        Long lastTick = entityLastAiTick.get(uuid);
        
        double minDistSq = Double.MAX_VALUE;
        for (var player : entity.getWorld().getPlayers()) {
            double d = player.squaredDistanceTo(entity);
            if (d < minDistSq) minDistSq = d;
        }
        
        int range = activationRange;
        int rangeSq = range * range;
        
        if (minDistSq > rangeSq) {
            if (lastTick != null && now - lastTick < 2000) {
                return true;
            }
        } else if (minDistSq > rangeSq / 4) {
            if (lastTick != null && now - lastTick < 500) {
                return true;
            }
        }
        
        entityLastAiTick.put(uuid, now);
        return false;
    }
    
    private void tickFluids(MinecraftServer server) {
        Profiler.startTimer("tick_fluids");
        Profiler.stopTimer("tick_fluids");
    }
    
    private void tickDecorative(MinecraftServer server) {
        Profiler.startTimer("tick_decorative");
        Profiler.stopTimer("tick_decorative");
    }
    
    public int getActivationRange() {
        return activationRange;
    }
    
    public double getCurrentTps() {
        return currentTps;
    }
    
    public Config getConfig() {
        return Config.getInstance();
    }
    
    public void shutdown() {
        workStealingPool.shutdown();
        try {
            if (!workStealingPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workStealingPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workStealingPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private static class TickTask implements Comparable<TickTask>, Runnable {
        final Runnable task;
        final long submitTime;
        
        TickTask(Runnable task, long submitTime) {
            this.task = task;
            this.submitTime = submitTime;
        }
        
        @Override
        public void run() {
            task.run();
        }
        
        @Override
        public int compareTo(TickTask other) {
            return Long.compare(submitTime, other.submitTime);
        }
    }
}