package com.ultra.hd4000.render;

import com.ultra.hd4000.core.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class UpscalePipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("ultra-hd4000-upscale");
    
    private static Framebuffer sceneFramebuffer;
    private static Framebuffer upscaleFramebuffer;
    private static ShaderProgram upscaleShader;
    private static ShaderProgram sharpenShader;
    
    private static int quadVao = -1;
    private static int quadVbo = -1;
    
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    
    public static void init() {
        createQuad();
        loadShaders();
        LOGGER.info("UpscalePipeline initialized");
    }
    
    private static void createQuad() {
        float[] quadVertices = {
            -1.0f,  1.0f, 0.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f
        };
        
        quadVao = GL30.glGenVertexArrays();
        quadVbo = GL30.glGenBuffers();
        
        GL30.glBindVertexArray(quadVao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, quadVbo);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, quadVertices, GL30.GL_STATIC_DRAW);
        
        GL30.glVertexAttribPointer(0, 2, GL30.GL_FLOAT, false, 4 * 4, 0);
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 4 * 4, 2 * 4);
        GL30.glEnableVertexAttribArray(1);
        
        GL30.glBindVertexArray(0);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
    }
    
    private static void loadShaders() {
        try {
            upscaleShader = new ShaderProgram(MinecraftClient.getInstance().getResourceManager(), 
                "ultra-hd4000:upscale", getDefaultUpscaleShader(), "");
            sharpenShader = new ShaderProgram(MinecraftClient.getInstance().getResourceManager(),
                "ultra-hd4000:sharpen", getDefaultSharpenShader(), "");
            
        } catch (Exception e) {
            LOGGER.error("Failed to load upscale shaders", e);
        }
    }
    
    private static String getDefaultUpscaleShader() {
        return """
            #version 150
            uniform sampler2D uScene;
            uniform vec2 uSceneSize;
            uniform vec2 uOutputSize;
            in vec2 vTex;
            out vec4 outColor;
            
            void main() {
                vec2 scale = uSceneSize / uOutputSize;
                vec2 center = vTex * uOutputSize;
                vec2 samplePos = center * scale;
                vec2 uv = samplePos / uSceneSize;
                outColor = texture(uScene, uv);
            }
            """;
    }
    
    private static String getDefaultSharpenShader() {
        return """
            #version 150
            uniform sampler2D uScene;
            uniform vec2 uInvSize;
            uniform float uStrength;
            in vec2 vTex;
            out vec4 outColor;
            
            void main() {
                vec4 c = texture(uScene, vTex);
                float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
                float lSharp = l + uStrength * (l - texture(uScene, vTex + uInvSize).r);
                outColor = vec4(mix(c.rgb, vec3(lSharp), 0.5), c.a);
            }
            """;
    }
    
    public static void ensureFramebuffers(int width, int height) {
        Config cfg = Config.getInstance();
        int internalW = cfg.internalWidth;
        int internalH = cfg.internalHeight;
        
        if (sceneFramebuffer != null && sceneFramebuffer.getWidth() == internalW && 
            sceneFramebuffer.getHeight() == internalH) {
            return;
        }
        
        if (sceneFramebuffer != null) {
            sceneFramebuffer.delete();
        }
        if (upscaleFramebuffer != null) {
            upscaleFramebuffer.delete();
        }
        
        sceneFramebuffer = new Framebuffer(internalW, internalH, true, 
            MinecraftClient.getInstance().isGlDebugEnabled());
        sceneFramebuffer.setClearColor(0, 0, 0, 0);
        
        upscaleFramebuffer = new Framebuffer(width, height, true,
            MinecraftClient.getInstance().isGlDebugEnabled());
        upscaleFramebuffer.setClearColor(0, 0, 0, 0);
        
        lastWidth = internalW;
        lastHeight = internalH;
        
        LOGGER.info("Framebuffers recreated: scene={}x{}, upscale={}x{}", 
            internalW, internalH, width, height);
    }
    
    public static void beginScene() {
        Config cfg = Config.getInstance();
        if (!cfg.enableUpscaling) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        ensureFramebuffers(client.getWindow().getFramebufferWidth(), 
                          client.getWindow().getFramebufferHeight());
        
        sceneFramebuffer.beginWrite(true);
    }
    
    public static void endScene() {
        Config cfg = Config.getInstance();
        if (!cfg.enableUpscaling) return;
        
        sceneFramebuffer.endWrite();
    }
    
    public static void render(WorldRenderContext context) {
        Config cfg = Config.getInstance();
        if (!cfg.enableUpscaling || sceneFramebuffer == null) return;
        
        @SuppressWarnings("unused")
        var ctx = context;
        
        MinecraftClient client = MinecraftClient.getInstance();
        int fbWidth = client.getWindow().getFramebufferWidth();
        int fbHeight = client.getWindow().getFramebufferHeight();
        
        upscaleFramebuffer.beginWrite(false);
        GL30.glViewport(0, 0, fbWidth, fbHeight);
        GL30.glDisable(GL30.GL_DEPTH_TEST);
        GL30.glDisable(GL30.GL_BLEND);
        
        upscaleShader.use();
        upscaleShader.setUniform("uScene", 0);
        upscaleShader.setUniform("uSceneSize", sceneFramebuffer.getWidth(), sceneFramebuffer.getHeight());
        upscaleShader.setUniform("uOutputSize", fbWidth, fbHeight);
        
        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, sceneFramebuffer.getColorAttachment());
        
        GL30.glBindVertexArray(quadVao);
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, 6);
        
        upscaleFramebuffer.endWrite();
        
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glViewport(0, 0, fbWidth, fbHeight);
        
        sharpenShader.use();
        sharpenShader.setUniform("uScene", 0);
        sharpenShader.setUniform("uInvSize", 1.0f / fbWidth, 1.0f / fbHeight);
        sharpenShader.setUniform("uStrength", cfg.sharpenStrength);
        
        GL30.glActiveTexture(GL30.GL_TEXTURE0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, upscaleFramebuffer.getColorAttachment());
        
        GL30.glBindVertexArray(quadVao);
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, 6);
        
        GL30.glBindVertexArray(0);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, 0);
    }
    
    public static void reloadShaders() {
        loadShaders();
        LOGGER.info("Shaders reloaded");
    }
    
    public static void shutdown() {
        if (sceneFramebuffer != null) sceneFramebuffer.delete();
        if (upscaleFramebuffer != null) upscaleFramebuffer.delete();
        if (quadVao != -1) GL30.glDeleteVertexArrays(quadVao);
        if (quadVbo != -1) GL30.glDeleteBuffers(quadVbo);
    }
}