package com.ultra.hd4000.entity;

import com.ultra.hd4000.core.Config;
import com.ultra.hd4000.core.Profiler;
import com.ultra.hd4000.render.RenderModule;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EntityModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-entity");
    
    private final Map<ModelKey, InstanceBatch> instanceBatches = new ConcurrentHashMap<>();
    private int instanceBuffer = -1;
    private long instanceBufferAddress = 0;
    private int maxInstances = 1024;
    
    private int particleSsbo = -1;
    private int maxParticles = 10000;
    private final List<ParticleData> particles = Collections.synchronizedList(new ArrayList<>());
    
    private int cullingComputeShader = -1;
    private int cullingIndirectBuffer = -1;
    
    public void init() {
        Config cfg = Config.getInstance();
        maxInstances = cfg.maxInstancesPerDraw;
        
        if (cfg.enableEntityInstancing) {
            initInstanceBuffer();
        }
        
        if (cfg.enableParticleSsbo) {
            initParticleSsbo();
        }
        
        if (cfg.gpuDrivenCulling) {
            initGpuCulling();
        }
        
        LOGGER.info("EntityModule initialized: instancing={}, particles={}, gpuCulling={}",
            cfg.enableEntityInstancing, cfg.enableParticleSsbo, cfg.gpuDrivenCulling);
    }
    
    private void initInstanceBuffer() {
        int bufferSize = maxInstances * 80;
        
        instanceBuffer = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, instanceBuffer);
        GL30.glBufferData(GL30.GL_SHADER_STORAGE_BUFFER, bufferSize, GL30.GL_DYNAMIC_DRAW);
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            instanceBufferAddress = GL43.glGetBufferPointer(GL30.GL_SHADER_STORAGE_BUFFER);
        }
        
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    private void initParticleSsbo() {
        int particleSize = 4*4 + 4*4 + 4*4 + 4 + 4 + 4;
        int bufferSize = maxParticles * particleSize;
        
        particleSsbo = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, particleSsbo);
        GL30.glBufferData(GL30.GL_SHADER_STORAGE_BUFFER, bufferSize, GL30.GL_DYNAMIC_DRAW);
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    private void initGpuCulling() {
        cullingIndirectBuffer = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_DRAW_INDIRECT_BUFFER, cullingIndirectBuffer);
        GL30.glBufferData(GL30.GL_DRAW_INDIRECT_BUFFER, 1024 * 20, GL30.GL_DYNAMIC_DRAW);
        GL30.glBindBuffer(GL30.GL_DRAW_INDIRECT_BUFFER, 0);
    }
    
    public void registerEntity(Entity entity, EntityRenderer<?, ?> renderer) {
        if (!Config.getInstance().enableEntityInstancing) return;
        if (!(renderer instanceof LivingEntityRenderer<?, ?>)) return;
        
        ModelKey key = createModelKey(entity, renderer);
        
        instanceBatches.computeIfAbsent(key, k -> new InstanceBatch(k)).addEntity(entity);
    }
    
    public void unregisterEntity(Entity entity, EntityRenderer<?, ?> renderer) {
        if (!Config.getInstance().enableEntityInstancing) return;
        
        ModelKey key = createModelKey(entity, renderer);
        InstanceBatch batch = instanceBatches.get(key);
        if (batch != null) {
            batch.removeEntity(entity);
            if (batch.isEmpty()) {
                instanceBatches.remove(key);
            }
        }
    }
    
    public void updateInstances() {
        if (!Config.getInstance().enableEntityInstancing) return;
        
        Profiler.startTimer("update_instances");
        
        int instanceIndex = 0;
        ByteBuffer buffer = getInstanceBufferView();
        
        for (InstanceBatch batch : instanceBatches.values()) {
            if (batch.entities.isEmpty()) continue;
            
            batch.firstInstance = instanceIndex;
            batch.instanceCount = 0;
            
            for (Entity entity : batch.entities) {
                if (instanceIndex >= maxInstances) break;
                
                writeInstanceData(buffer, instanceIndex, entity);
                instanceIndex++;
                batch.instanceCount++;
            }
        }
        
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, instanceBuffer);
        GL30.glBufferSubData(GL30.GL_SHADER_STORAGE_BUFFER, 0, buffer);
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, 0);
        
        Profiler.stopTimer("update_instances");
        Profiler.incrementCounter("instances_updated", instanceIndex);
    }
    
    private ByteBuffer getInstanceBufferView() {
        return ByteBuffer.allocateDirect(maxInstances * 80).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    }
    
    private void writeInstanceData(ByteBuffer buffer, int index, Entity entity) {
        int offset = index * 80;
        
        Matrix4f model = new Matrix4f()
            .translate(entity.getX(), entity.getY(), entity.getZ())
            .rotateY((float) Math.toRadians(entity.getYaw()))
            .rotateX((float) Math.toRadians(entity.getPitch()))
            .scale(entity.getScale());
        
        for (int i = 0; i < 16; i++) {
            buffer.putFloat(offset + i * 4, model.get(i / 4, i % 4));
        }
        
        buffer.putFloat(offset + 64, 1.0f);
        buffer.putFloat(offset + 68, 1.0f);
        buffer.putFloat(offset + 72, 1.0f);
        buffer.putFloat(offset + 76, 1.0f);
    }
    
    public void renderInstanced(VertexConsumerProvider provider) {
        if (!Config.getInstance().enableEntityInstancing) return;
        
        Profiler.startTimer("render_instanced");
        
        for (InstanceBatch batch : instanceBatches.values()) {
            if (batch.instanceCount == 0) continue;
            
            GL30.glBindBufferBase(GL30.GL_SHADER_STORAGE_BUFFER, 0, instanceBuffer);
            
            GL30.glDrawArraysInstanced(GL30.GL_TRIANGLES, 0, batch.vertexCount, batch.instanceCount);
            
            Profiler.incrementCounter("instanced_draw_calls");
        }
        
        GL30.glBindBufferBase(GL30.GL_SHADER_STORAGE_BUFFER, 0, 0);
        Profiler.stopTimer("render_instanced");
    }
    
    public void addParticle(ParticleData particle) {
        if (!Config.getInstance().enableParticleSsbo) return;
        
        if (particles.size() < maxParticles) {
            particles.add(particle);
        }
    }
    
    public void updateParticles(float deltaTime) {
        if (!Config.getInstance().enableParticleSsbo) return;
        
        Profiler.startTimer("update_particles");
        
        Iterator<ParticleData> it = particles.iterator();
        while (it.hasNext()) {
            ParticleData p = it.next();
            p.life -= deltaTime;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            
            p.pos.add(p.vel.x * deltaTime, p.vel.y * deltaTime, p.vel.z * deltaTime);
            p.vel.y -= 9.81f * deltaTime;
        }
        
        if (!particles.isEmpty()) {
            uploadParticles();
        }
        
        Profiler.stopTimer("update_particles");
    }
    
    private void uploadParticles() {
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, particleSsbo);
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(particles.size() * 64).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (ParticleData p : particles) {
            buffer.putFloat(p.pos.x).putFloat(p.pos.y).putFloat(p.pos.z).putFloat(p.size);
            buffer.putFloat(p.vel.x).putFloat(p.vel.y).putFloat(p.vel.z).putFloat(0);
            buffer.putFloat(p.color.x).putFloat(p.color.y).putFloat(p.color.z).putFloat(p.color.w);
            buffer.putFloat(p.life).putFloat(0).putFloat(0).putFloat(0);
        }
        buffer.flip();
        
        GL30.glBufferSubData(GL30.GL_SHADER_STORAGE_BUFFER, 0, buffer);
        GL30.glBindBuffer(GL30.GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    public void renderParticles() {
        if (!Config.getInstance().enableParticleSsbo || particles.isEmpty()) return;
        
        Profiler.startTimer("render_particles");
        
        GL30.glBindBufferBase(GL30.GL_SHADER_STORAGE_BUFFER, 1, particleSsbo);
        
        GL30.glDrawArrays(GL30.GL_POINTS, 0, particles.size());
        
        GL30.glBindBufferBase(GL30.GL_SHADER_STORAGE_BUFFER, 1, 0);
        Profiler.stopTimer("render_particles");
    }
    
    private ModelKey createModelKey(Entity entity, EntityRenderer<?, ?> renderer) {
        if (renderer instanceof LivingEntityRenderer<?, ?> ler) {
            EntityModel<?> model = ler.getModel();
            Identifier texture = ler.getTexture((net.minecraft.entity.LivingEntity) entity);
            return new ModelKey(model.getClass().getSimpleName(), texture.toString());
        }
        return new ModelKey("default", "default");
    }
    
    public Config getConfig() {
        return Config.getInstance();
    }
    
    public void shutdown() {
        if (instanceBuffer != -1) GL30.glDeleteBuffers(instanceBuffer);
        if (particleSsbo != -1) GL30.glDeleteBuffers(particleSsbo);
        if (cullingIndirectBuffer != -1) GL30.glDeleteBuffers(cullingIndirectBuffer);
    }
    
    static class ModelKey {
        final String modelName;
        final String texture;
        
        ModelKey(String modelName, String texture) {
            this.modelName = modelName;
            this.texture = texture;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ModelKey)) return false;
            ModelKey that = (ModelKey) o;
            return modelName.equals(that.modelName) && texture.equals(that.texture);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(modelName, texture);
        }
    }
    
    static class InstanceBatch {
        final ModelKey key;
        final Set<Entity> entities = Collections.newSetFromMap(new ConcurrentHashMap<>());
        int firstInstance = 0;
        int instanceCount = 0;
        int vertexCount = 0;
        BakedModel model;
        
        InstanceBatch(ModelKey key) {
            this.key = key;
        }
        
        void addEntity(Entity entity) {
            entities.add(entity);
        }
        
        void removeEntity(Entity entity) {
            entities.remove(entity);
        }
        
        boolean isEmpty() {
            return entities.isEmpty();
        }
    }
    
    static class ParticleData {
        Vector3f pos = new Vector3f();
        Vector3f vel = new Vector3f();
        Vector4f color = new Vector4f(1, 1, 1, 1);
        float life = 1.0f;
        float size = 0.1f;
        int type = 0;
    }
}