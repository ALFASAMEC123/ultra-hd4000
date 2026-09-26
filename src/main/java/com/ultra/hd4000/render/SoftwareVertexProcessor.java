package com.ultra.hd4000.render;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SoftwareVertexProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-vertex");
    
    private static final int VERTEX_SIZE = 12;
    private static final int MAX_VERTICES_PER_SECTION = 65536;
    
    private static final ThreadLocal<ByteBuffer> vertexBufferTL = ThreadLocal.withInitial(() -> 
        java.nio.ByteBuffer.allocateDirect(MAX_VERTICES_PER_SECTION * VERTEX_SIZE));
    private static final ThreadLocal<ShortBuffer> indexBufferTL = ThreadLocal.withInitial(() -> 
        java.nio.ShortBuffer.allocate(MAX_VERTICES_PER_SECTION));
    
    public static void processSection(int chunkX, int chunkY, int chunkZ, RenderModule.ChunkSectionData output) {
        if (!Config.getInstance().enableSoftwareVertex) return;
        
        Profiler.startTimer("process_section_total");
        try {
            ByteBuffer vb = vertexBufferTL.get();
            ShortBuffer ib = indexBufferTL.get();
            vb.clear();
            ib.clear();
            
            int vertexCount = 0;
            int indexCount = 0;
            
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            
            output.vertices = new byte[vb.position()];
            vb.flip();
            vb.get(output.vertices);
            output.vertexCount = vertexCount;
            
            output.indices = new short[ib.position()];
            ib.flip();
            ib.get(output.indices);
            output.indexCount = indexCount;
            
            output.minX = minX; output.minY = minY; output.minZ = minZ;
            output.maxX = maxX; output.maxY = maxY; output.maxZ = maxZ;
            output.sectionIndex = (chunkY & 0xF) * 256 + (chunkZ & 0xF) * 16 + (chunkX & 0xF);
            
            Profiler.incrementCounter("vertices_generated", vertexCount);
            Profiler.incrementCounter("indices_generated", indexCount);
            
        } finally {
            Profiler.stopTimer("process_section_total");
        }
    }
    
    public static void transformVerticesSimd(ByteBuffer vertices, int count, Matrix4f transform) {
    }
    
    public static short encodeNormalOctahedral(float nx, float ny, float nz) {
        float invL1 = 1.0f / (Math.abs(nx) + Math.abs(ny) + Math.abs(nz));
        float x = nx * invL1;
        float y = ny * invL1;
        
        if (nz < 0) {
            float tx = (1.0f - Math.abs(y)) * (x >= 0 ? 1 : -1);
            float ty = (1.0f - Math.abs(x)) * (y >= 0 ? 1 : -1);
            x = tx; y = ty;
        }
        
        short sx = (short) ((x * 0.5f + 0.5f) * 255.0f);
        short sy = (short) ((y * 0.5f + 0.5f) * 255.0f);
        return (short) ((sx << 8) | (sy & 0xFF));
    }
    
    public static Vector3f decodeNormalOctahedral(short encoded) {
        float x = ((encoded >> 8) & 0xFF) / 255.0f * 2.0f - 1.0f;
        float y = (encoded & 0xFF) / 255.0f * 2.0f - 1.0f;
        float z = 1.0f - Math.abs(x) - Math.abs(y);
        if (z < 0) {
            float tx = (1.0f - Math.abs(y)) * (x >= 0 ? 1 : -1);
            float ty = (1.0f - Math.abs(x)) * (y >= 0 ? 1 : -1);
            x = tx; y = ty;
            z = 1.0f - Math.abs(x) - Math.abs(y);
        }
        float len = (float) Math.sqrt(x*x + y*y + z*z);
        return new Vector3f(x/len, y/len, z/len);
    }
    
    public static void packVertex(ByteBuffer buffer, float x, float y, float z, 
                                   short normal, int u, int v, int light) {
        short px = (short) (x * 1024.0f);
        short py = (short) (y * 1024.0f);
        short pz = (short) (z * 1024.0f);
        
        buffer.putShort(px);
        buffer.putShort(py);
        buffer.putShort(pz);
        buffer.putShort(normal);
        buffer.putShort((short) u);
        buffer.putShort((short) v);
        buffer.putShort((short) light);
    }
    
    public static void addQuadIndices(ShortBuffer indices, int baseVertex) {
        indices.put((short) baseVertex);
        indices.put((short) (baseVertex + 1));
        indices.put((short) (baseVertex + 2));
        indices.put((short) (baseVertex + 2));
        indices.put((short) (baseVertex + 3));
        indices.put((short) baseVertex);
    }
    
    public static int mortonOrder(int x, int y, int z) {
        int morton = 0;
        for (int i = 0; i < 10; i++) {
            morton |= ((x >> i) & 1) << (3*i);
            morton |= ((y >> i) & 1) << (3*i + 1);
            morton |= ((z >> i) & 1) << (3*i + 2);
        }
        return morton;
    }
}