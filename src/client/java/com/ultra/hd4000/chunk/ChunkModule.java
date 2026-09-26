package com.ultra.hd4000.chunk;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import com.ultra.hd4000.render.RenderModule;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.apache.commons.lang3.tuple.Pair;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.MutableDirectBuffer;
import org.lwjgl.system.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ChunkModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-chunk");
    
    private FileChannel mmapChannel;
    private Path mmapFile;
    private long mmapSize;
    private final Map<Long, ChunkMetadata> metadataMap = new ConcurrentHashMap<>();
    
    private Object chronicleMap;
    
    private final Deflater deflater = new Deflater(1, true);
    private final Inflater inflater = new Inflater(true);
    
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(
        r -> { Thread t = new Thread(r, "ultra-chunk-io"); t.setDaemon(true); return t; }
    );
    
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();
    
    public void init() {
        Config cfg = Config.getInstance();
        
        if (cfg.enableOffHeapChunks) {
            initMmapStorage();
        }
        
        LOGGER.info("ChunkModule initialized: offHeap={}, mmapSize={}MB, lazyLoad={}, compression={}",
            cfg.enableOffHeapChunks, cfg.offHeapMaxSizeBytes / 1024 / 1024,
            cfg.lazyChunkLoading, cfg.compressChunkData);
    }
    
    private void initMmapStorage() {
        try {
            mmapFile = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir().resolve("ultra-hd4000-chunks.dat");
            
            Config cfg = Config.getInstance();
            mmapSize = cfg.offHeapMaxSizeBytes;
            
            if (!Files.exists(mmapFile)) {
                Files.createFile(mmapFile);
            }
            
            try (FileChannel fc = FileChannel.open(mmapFile, 
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                long currentSize = fc.size();
                if (currentSize < mmapSize) {
                    fc.truncate(mmapSize);
                }
            }
            
            mmapChannel = FileChannel.open(mmapFile, 
                StandardOpenOption.READ, StandardOpenOption.WRITE);
            
            LOGGER.info("Mmap storage initialized: file={}, size={}MB", mmapFile, mmapSize / 1024 / 1024);
            
        } catch (IOException e) {
            LOGGER.error("Failed to initialize mmap storage, falling back to heap", e);
            Config.getInstance().enableOffHeapChunks = false;
        }
    }
    
    public void saveChunkMesh(ChunkPos pos, RenderModule.ChunkSectionData[] sections) {
        if (!Config.getInstance().enableOffHeapChunks) return;
        
        long key = chunkPosToKey(pos);
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            Profiler.startTimer("chunk_save_total");
            try {
                ByteBuffer buffer = serializeChunkMesh(sections);
                
                if (Config.getInstance().compressChunkData) {
                    buffer = compress(buffer);
                }
                
                writeToMmap(key, buffer);
                
                ChunkMetadata meta = new ChunkMetadata(key, buffer.remaining(), 
                    System.currentTimeMillis(), Config.getInstance().compressChunkData);
                metadataMap.put(key, meta);
                
                Profiler.incrementCounter("chunks_saved");
                Profiler.addCounter("chunk_bytes_written", buffer.remaining());
                
            } catch (Exception e) {
                LOGGER.error("Failed to save chunk {}", pos, e);
            } finally {
                Profiler.stopTimer("chunk_save_total");
            }
        }, ioExecutor);
        
        pendingWrites.put(key, future);
    }
    
    public Optional<RenderModule.ChunkSectionData[]> loadChunkMesh(ChunkPos pos) {
        if (!Config.getInstance().enableOffHeapChunks) return Optional.empty();
        
        long key = chunkPosToKey(pos);
        
        try {
            ChunkMetadata meta = metadataMap.get(key);
            if (meta == null) return Optional.empty();
            
            ByteBuffer buffer = readFromMmap(key, meta.size);
            if (buffer == null) return Optional.empty();
            
            if (meta.compressed) {
                buffer = decompress(buffer);
            }
            
            RenderModule.ChunkSectionData[] sections = deserializeChunkMesh(buffer);
            Profiler.incrementCounter("chunks_loaded");
            Profiler.addCounter("chunk_bytes_read", buffer.remaining());
            
            return Optional.of(sections);
            
        } catch (Exception e) {
            LOGGER.error("Failed to load chunk {}", pos, e);
            return Optional.empty();
        }
    }
    
    private ByteBuffer serializeChunkMesh(RenderModule.ChunkSectionData[] sections) {
        int estimatedSize = 4;
        for (RenderModule.ChunkSectionData s : sections) {
            if (s != null && s.vertexCount > 0) {
                estimatedSize += 4 + 4 + s.vertices.length + s.indices.length * 2 + 6 * 4;
            }
        }
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(estimatedSize).order(ByteOrder.LITTLE_ENDIAN);
        int validSections = 0;
        
        for (RenderModule.ChunkSectionData s : sections) {
            if (s != null && s.vertexCount > 0) validSections++;
        }
        buffer.putInt(validSections);
        
        for (RenderModule.ChunkSectionData s : sections) {
            if (s == null || s.vertexCount == 0) continue;
            
            buffer.putInt(s.sectionIndex);
            buffer.putInt(s.vertexCount);
            buffer.putInt(s.indexCount);
            buffer.put(s.vertices);
            
            for (short idx : s.indices) buffer.putShort(idx);
            
            buffer.putFloat(s.minX).putFloat(s.minY).putFloat(s.minZ);
            buffer.putFloat(s.maxX).putFloat(s.maxY).putFloat(s.maxZ);
        }
        
        buffer.flip();
        return buffer;
    }
    
    private RenderModule.ChunkSectionData[] deserializeChunkMesh(ByteBuffer buffer) {
        int sectionCount = buffer.getInt();
        RenderModule.ChunkSectionData[] sections = new RenderModule.ChunkSectionData[sectionCount];
        
        for (int i = 0; i < sectionCount; i++) {
            RenderModule.ChunkSectionData s = new RenderModule.ChunkSectionData();
            s.sectionIndex = buffer.getInt();
            s.vertexCount = buffer.getInt();
            s.indexCount = buffer.getInt();
            
            s.vertices = new byte[s.vertexCount * 12];
            buffer.get(s.vertices);
            
            s.indices = new short[s.indexCount];
            buffer.asShortBuffer().get(s.indices);
            buffer.position(buffer.position() + s.indexCount * 2);
            
            s.minX = buffer.getFloat(); s.minY = buffer.getFloat(); s.minZ = buffer.getFloat();
            s.maxX = buffer.getFloat(); s.maxY = buffer.getFloat(); s.maxZ = buffer.getFloat();
            
            sections[i] = s;
        }
        
        return sections;
    }
    
    private ByteBuffer compress(ByteBuffer input) {
        byte[] in = new byte[input.remaining()];
        input.get(in);
        input.rewind();
        
        deflater.reset();
        deflater.setInput(in);
        deflater.finish();
        
        byte[] out = new byte[deflater.getBytesWritten() + 100];
        int compressedSize = deflater.deflate(out);
        
        ByteBuffer result = ByteBuffer.allocateDirect(compressedSize).order(ByteOrder.LITTLE_ENDIAN);
        result.put(out, 0, compressedSize);
        result.flip();
        return result;
    }
    
    private ByteBuffer decompress(ByteBuffer input) {
        byte[] in = new byte[input.remaining()];
        input.get(in);
        input.rewind();
        
        inflater.reset();
        inflater.setInput(in);
        
        byte[] out = new byte[8192];
        ByteBuffer result = ByteBuffer.allocateDirect(65536).order(ByteOrder.LITTLE_ENDIAN);
        
        try {
            while (!inflater.finished()) {
                int decompressed = inflater.inflate(out);
                if (decompressed > 0) {
                    if (result.remaining() < decompressed) {
                        ByteBuffer newBuf = ByteBuffer.allocateDirect(result.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
                        result.flip();
                        newBuf.put(result);
                        result = newBuf;
                    }
                    result.put(out, 0, decompressed);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Decompression failed", e);
        }
        
        result.flip();
        return result;
    }
    
    private void writeToMmap(long key, ByteBuffer data) throws IOException {
        long offset = keyToOffset(key);
        int size = data.remaining();
        
        ByteBuffer sizeBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN);
        sizeBuf.putInt(size);
        sizeBuf.flip();
        mmapChannel.write(sizeBuf, offset);
        
        mmapChannel.write(data, offset + 4);
        mmapChannel.force(true);
    }
    
    private ByteBuffer readFromMmap(long key, int expectedSize) throws IOException {
        long offset = keyToOffset(key);
        
        ByteBuffer sizeBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN);
        mmapChannel.read(sizeBuf, offset);
        sizeBuf.flip();
        int size = sizeBuf.getInt();
        
        if (size != expectedSize) {
            LOGGER.warn("Chunk size mismatch: expected {}, got {}", expectedSize, size);
        }
        
        ByteBuffer data = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
        mmapChannel.read(data, offset + 4);
        data.flip();
        return data;
    }
    
    private long chunkPosToKey(ChunkPos pos) {
        return ((long) pos.x << 32) | (pos.z & 0xFFFFFFFFL);
    }
    
    private long keyToOffset(long key) {
        return (key * 4096) % mmapSize;
    }
    
    public void onChunkLoad(ChunkPos pos) {
        if (!Config.getInstance().lazyChunkLoading) return;
        
        Optional<RenderModule.ChunkSectionData[]> cached = loadChunkMesh(pos);
        if (cached.isPresent()) {
            for (int i = 0; i < cached.get().length; i++) {
                RenderModule.ChunkSectionData section = cached.get()[i];
                if (section != null && section.vertexCount > 0) {
                    UltraHD4000Mod.RENDER.uploadChunkSection(section.sectionIndex, section);
                }
            }
        }
    }
    
    public void onChunkUnload(ChunkPos pos) {
    }
    
    public void flushPendingWrites() {
        pendingWrites.values().forEach(CompletableFuture::join);
        pendingWrites.clear();
    }
    
    public Config getConfig() {
        return Config.getInstance();
    }
    
    public void shutdown() {
        flushPendingWrites();
        ioExecutor.shutdown();
        
        try {
            if (mmapChannel != null) mmapChannel.close();
        } catch (IOException e) {
            LOGGER.error("Error closing mmap", e);
        }
    }
    
    private static class ChunkMetadata {
        final long key;
        final int size;
        final long timestamp;
        final boolean compressed;
        
        ChunkMetadata(long key, int size, long timestamp, boolean compressed) {
            this.key = key;
            this.size = size;
            this.timestamp = timestamp;
            this.compressed = compressed;
        }
    }
}