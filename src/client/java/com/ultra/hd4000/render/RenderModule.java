package com.ultra.hd4000.render;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderDispatcher;
import net.minecraft.client.render.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class RenderModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-render");
    
    private static final int MAX_CHUNK_SECTIONS = 16384;
    private static final int VERTEX_STRIDE = 12;
    
    private final ExecutorService vertexExecutor;
    private final ExecutorService cullingExecutor;
    
    private int vertexBuffer = -1;
    private int indexBuffer = -1;
    private int indirectBuffer = -1;
    private long vertexBufferAddress = 0;
    private long indexBufferAddress = 0;
    private long indirectBufferAddress = 0;
    
    private final ByteBuffer indirectCommands;
    
    private final ChunkSectionData[] chunkSections = new ChunkSectionData[MAX_CHUNK_SECTIONS];
    private int activeSections = 0;
    
    private final float[][] frustumPlanes = new float[6][4];
    
    public RenderModule() {
        Config cfg = Config.getInstance();
        int threads = Runtime.getRuntime().availableProcessors();
        
        this.vertexExecutor = new ThreadPoolExecutor(
            threads, threads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "ultra-vertex-" + r.hashCode()); t.setPriority(Thread.NORM_PRIORITY + 1); return t; }
        );
        
        this.cullingExecutor = new ThreadPoolExecutor(
            threads, threads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "ultra-culling-" + r.hashCode()); t.setPriority(Thread.NORM_PRIORITY); return t; }
        );
        
        this.indirectCommands = ByteBuffer.allocateDirect(MAX_CHUNK_SECTIONS * 32);
    }
    
    public void init() {
        Config cfg = Config.getInstance();
        
        if (cfg.enableCompressedVbo) {
            initPersistentBuffers();
        }
        
        LOGGER.info("RenderModule initialized: vertexThreads={}, cullingThreads={}, persistentBuffers={}",
            vertexExecutor.getCorePoolSize(), cullingExecutor.getCorePoolSize(), cfg.enableCompressedVbo);
    }
    
    private void initPersistentBuffers() {
        vertexBuffer = GL45.glCreateBuffers();
        GL45.glNamedBufferStorage(vertexBuffer, MAX_CHUNK_SECTIONS * 65536 * VERTEX_STRIDE, 
            GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT | GL45.GL_CLIENT_STORAGE_BIT);
        
        indexBuffer = GL45.glCreateBuffers();
        GL45.glNamedBufferStorage(indexBuffer, MAX_CHUNK_SECTIONS * 65536 * 2,
            GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT | GL45.GL_CLIENT_STORAGE_BIT);
        
        indirectBuffer = GL45.glCreateBuffers();
        GL45.glNamedBufferStorage(indirectBuffer, MAX_CHUNK_SECTIONS * 32,
            GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT);
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer length = stack.mallocInt(1);
            IntBuffer access = stack.mallocInt(1);
            
            vertexBufferAddress = GL45.glMapNamedBufferRange(vertexBuffer, 0, MAX_CHUNK_SECTIONS * 65536 * VERTEX_STRIDE,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT | GL45.GL_MAP_FLUSH_EXPLICIT_BIT, length, access);
            
            indexBufferAddress = GL45.glMapNamedBufferRange(indexBuffer, 0, MAX_CHUNK_SECTIONS * 65536 * 2,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT | GL45.GL_MAP_FLUSH_EXPLICIT_BIT, length, access);
            
            indirectBufferAddress = GL45.glMapNamedBufferRange(indirectBuffer, 0, MAX_CHUNK_SECTIONS * 32,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_COHERENT_BIT | GL45.GL_MAP_FLUSH_EXPLICIT_BIT, length, access);
        }
        
        LOGGER.info("Persistent buffers mapped: vertex=0x{:X}, index=0x{:X}, indirect=0x{:X}",
            vertexBufferAddress, indexBufferAddress, indirectBufferAddress);
    }
    
    public void buildChunkMeshSoftware(int chunkX, int chunkY, int chunkZ, ChunkSectionData sectionData) {
        if (!Config.getInstance().enableSoftwareVertex) return;
        
        vertexExecutor.submit(() -> {
            Profiler.startTimer("software_vertex_build");
            try {
                SoftwareVertexProcessor.processSection(chunkX, chunkY, chunkZ, sectionData);
                Profiler.incrementCounter("vertex_sections_built");
            } finally {
                Profiler.stopTimer("software_vertex_build");
            }
        });
    }
    
    public void uploadChunkSection(int sectionIndex, ChunkSectionData data) {
        if (!Config.getInstance().enableCompressedVbo) return;
        
        long vertexOffset = (long) sectionIndex * 65536 * VERTEX_STRIDE;
        long indexOffset = (long) sectionIndex * 65536 * 2;
        
        if (data.vertexCount > 0) {
            LOGGER.debug("Uploading chunk section {}: {} vertices, {} indices", sectionIndex, data.vertexCount, data.indexCount);
        }
    }
    
    public void cullAndDraw(float[] viewProjectionMatrix) {
        if (!Config.getInstance().enableCpuCulling) return;
        
        extractFrustumPlanes(viewProjectionMatrix);
        
        int sectionsPerThread = (activeSections + cullingExecutor.getCorePoolSize() - 1) / cullingExecutor.getCorePoolSize();
        CountDownLatch latch = new CountDownLatch(cullingExecutor.getCorePoolSize());
        
        for (int i = 0; i < cullingExecutor.getCorePoolSize(); i++) {
            int start = i * sectionsPerThread;
            int end = Math.min(start + sectionsPerThread, activeSections);
            if (start < end) {
                cullingExecutor.submit(() -> {
                    try {
                        cullRange(start, end);
                    } finally {
                        latch.countDown();
                    }
                });
            } else {
                latch.countDown();
            }
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (Config.getInstance().enableIndirectDraw) {
            drawIndirect();
        }
    }
    
    private void extractFrustumPlanes(float[] vp) {
        frustumPlanes[0][0] = vp[3] + vp[0];  frustumPlanes[0][1] = vp[7] + vp[4];  frustumPlanes[0][2] = vp[11] + vp[8];  frustumPlanes[0][3] = vp[15] + vp[12];
        frustumPlanes[1][0] = vp[3] - vp[0];  frustumPlanes[1][1] = vp[7] - vp[4];  frustumPlanes[1][2] = vp[11] - vp[8];  frustumPlanes[1][3] = vp[15] - vp[12];
        frustumPlanes[2][0] = vp[3] + vp[1];  frustumPlanes[2][1] = vp[7] + vp[5];  frustumPlanes[2][2] = vp[11] + vp[9];  frustumPlanes[2][3] = vp[15] + vp[13];
        frustumPlanes[3][0] = vp[3] - vp[1];  frustumPlanes[3][1] = vp[7] - vp[5];  frustumPlanes[3][2] = vp[11] - vp[9];  frustumPlanes[3][3] = vp[15] - vp[13];
        frustumPlanes[4][0] = vp[3] + vp[2];  frustumPlanes[4][1] = vp[7] + vp[6];  frustumPlanes[4][2] = vp[11] + vp[10]; frustumPlanes[4][3] = vp[15] + vp[14];
        frustumPlanes[5][0] = vp[3] - vp[2];  frustumPlanes[5][1] = vp[7] - vp[6];  frustumPlanes[5][2] = vp[11] - vp[10]; frustumPlanes[5][3] = vp[15] - vp[14];
        
        for (int i = 0; i < 6; i++) {
            float len = (float) Math.sqrt(frustumPlanes[i][0]*frustumPlanes[i][0] + 
                                         frustumPlanes[i][1]*frustumPlanes[i][1] + 
                                         frustumPlanes[i][2]*frustumPlanes[i][2]);
            if (len > 0) {
                frustumPlanes[i][0] /= len;
                frustumPlanes[i][1] /= len;
                frustumPlanes[i][2] /= len;
                frustumPlanes[i][3] /= len;
            }
        }
    }
    
    private void cullRange(int start, int end) {
        for (int i = start; i < end; i++) {
            ChunkSectionData section = chunkSections[i];
            if (section == null || section.vertexCount == 0) continue;
            
            if (!isAabbInFrustum(section.minX, section.minY, section.minZ, 
                                section.maxX, section.maxY, section.maxZ)) {
                section.culled = true;
                continue;
            }
            section.culled = false;
            Profiler.incrementCounter("sections_visible");
        }
    }
    
    private boolean isAabbInFrustum(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            float a = frustumPlanes[i][0];
            float b = frustumPlanes[i][1];
            float c = frustumPlanes[i][2];
            float d = frustumPlanes[i][3];
            
            float x = a > 0 ? maxX : minX;
            float y = b > 0 ? maxY : minY;
            float z = c > 0 ? maxZ : minZ;
            
            if (a * x + b * y + c * z + d < 0) {
                return false;
            }
        }
        return true;
    }
    
    private void drawIndirect() {
        GL45.glBindBuffer(GL45.GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        GL45.glBindBuffer(GL45.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        GL30.glBindVertexArray(0);
        
        GL45.glMultiDrawElementsIndirect(GL31.GL_TRIANGLES, GL31.GL_UNSIGNED_SHORT, 0, activeSections, 32);
        
        Profiler.incrementCounter("indirect_draw_calls");
    }
    
    public Config getConfig() {
        return Config.getInstance();
    }
    
    public void shutdown() {
        vertexExecutor.shutdown();
        cullingExecutor.shutdown();
        
        if (vertexBuffer != -1) GL45.glDeleteBuffers(vertexBuffer);
        if (indexBuffer != -1) GL45.glDeleteBuffers(indexBuffer);
        if (indirectBuffer != -1) GL45.glDeleteBuffers(indirectBuffer);
    }
    
    public static class ChunkSectionData {
        public byte[] vertices;
        public short[] indices;
        public int vertexCount;
        public int indexCount;
        public float minX, minY, minZ, maxX, maxY, maxZ;
        public boolean culled;
        public int sectionIndex;
    }
}