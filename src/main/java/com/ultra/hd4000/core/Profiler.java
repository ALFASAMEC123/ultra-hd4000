package com.ultra.hd4000.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class Profiler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-profiler");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    
    private static final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private static final Map<String, Long> timers = new ConcurrentHashMap<>();
    private static final List<FrameSample> frameSamples = Collections.synchronizedList(new ArrayList<>());
    
    private static boolean profiling = false;
    private static long profilingStartTime = 0;
    private static int sampleCount = 0;
    private static Thread benchmarkThread = null;
    
    public static void init() {
        LOGGER.info("Profiler initialized");
    }
    
    public static void startProfiling(int durationSeconds) {
        if (profiling) return;
        profiling = true;
        profilingStartTime = System.nanoTime();
        sampleCount = 0;
        frameSamples.clear();
        counters.clear();
        timers.clear();
        
        LOGGER.info("Profiling started for {} seconds", durationSeconds);
        
        benchmarkThread = new Thread(() -> {
            try {
                Thread.sleep(durationSeconds * 1000L);
                stopProfilingAndSave();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ultra-hd4000-benchmark");
        benchmarkThread.start();
    }
    
    public static void stopProfilingAndSave() {
        if (!profiling) return;
        profiling = false;
        long durationNs = System.nanoTime() - profilingStartTime;
        double durationSec = durationNs / 1_000_000_000.0;
        
        BenchmarkResult result = generateResult(durationSec);
        saveResult(result);
        
        LOGGER.info("Benchmark completed: avgFps={}, p1Fps={}, frameTime_p50={}ms, frameTime_p99={}ms",
            result.avgFps, result.p1Fps, result.frameTimeMs.p50, result.frameTimeMs.p99);
    }
    
    public static void recordFrameTime(double frameTimeMs) {
        if (profiling) {
            frameSamples.add(new FrameSample(System.nanoTime(), frameTimeMs));
            sampleCount++;
        }
    }
    
    public static void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }
    
    public static void addCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(value);
    }
    
    public static void startTimer(String name) {
        timers.put(name, System.nanoTime());
    }
    
    public static long stopTimer(String name) {
        Long start = timers.remove(name);
        if (start != null) {
            long elapsed = System.nanoTime() - start;
            addCounter(name + "_ns", elapsed);
            return elapsed;
        }
        return -1;
    }
    
    private static BenchmarkResult generateResult(double durationSec) {
        List<Double> frameTimes = frameSamples.stream()
            .map(s -> s.frameTimeMs)
            .sorted()
            .toList();
        
        double avgFps = frameTimes.isEmpty() ? 0 : 1000.0 / frameTimes.stream().mapToDouble(d -> d).average().orElse(16.67);
        double p1Fps = frameTimes.isEmpty() ? 0 : 1000.0 / percentile(frameTimes, 0.99);
        
        MemoryUsage heap = MEMORY_BEAN.getHeapMemoryUsage();
        MemoryUsage nonHeap = MEMORY_BEAN.getNonHeapMemoryUsage();
        
        Map<String, Long> counterMap = new HashMap<>();
        counters.forEach((k, v) -> counterMap.put(k, v.sum()));
        
        return new BenchmarkResult(
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            durationSec,
            avgFps,
            p1Fps,
            new FrameTimePercentiles(
                percentile(frameTimes, 0.50),
                percentile(frameTimes, 0.90),
                percentile(frameTimes, 0.95),
                percentile(frameTimes, 0.99),
                percentile(frameTimes, 0.999)
            ),
            heap.getUsed() / 1024 / 1024,
            heap.getMax() / 1024 / 1024,
            nonHeap.getUsed() / 1024 / 1024,
            counterMap
        );
    }
    
    private static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
    
    private static void saveResult(BenchmarkResult result) {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("logs");
        try {
            Files.createDirectories(logDir);
            Path file = logDir.resolve("ultra-hd4000-benchmark-" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(result, writer);
            }
            LOGGER.info("Benchmark saved to {}", file);
        } catch (IOException e) {
            LOGGER.error("Failed to save benchmark", e);
        }
    }
    
    public static boolean isProfiling() {
        return profiling;
    }
    
    private static class FrameSample {
        final long timestampNs;
        final double frameTimeMs;
        FrameSample(long timestampNs, double frameTimeMs) {
            this.timestampNs = timestampNs;
            this.frameTimeMs = frameTimeMs;
        }
    }
    
    public static class BenchmarkResult {
        public final String timestamp;
        public final double durationSeconds;
        public final double avgFps;
        public final double p1Fps;
        public final FrameTimePercentiles frameTimeMs;
        public final long heapUsedMb;
        public final long heapMaxMb;
        public final long nonHeapUsedMb;
        public final Map<String, Long> counters;
        
        public BenchmarkResult(String timestamp, double durationSeconds, double avgFps, double p1Fps,
                              FrameTimePercentiles frameTimeMs, long heapUsedMb, long heapMaxMb,
                              long nonHeapUsedMb, Map<String, Long> counters) {
            this.timestamp = timestamp;
            this.durationSeconds = durationSeconds;
            this.avgFps = avgFps;
            this.p1Fps = p1Fps;
            this.frameTimeMs = frameTimeMs;
            this.heapUsedMb = heapUsedMb;
            this.heapMaxMb = heapMaxMb;
            this.nonHeapUsedMb = nonHeapUsedMb;
            this.counters = counters;
        }
    }
    
    public static class FrameTimePercentiles {
        public final double p50, p90, p95, p99, p999;
        public FrameTimePercentiles(double p50, double p90, double p95, double p99, double p999) {
            this.p50 = p50; this.p90 = p90; this.p95 = p95; this.p99 = p99; this.p999 = p999;
        }
    }
}