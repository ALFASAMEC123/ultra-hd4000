// Minimal meshoptimizer implementation for JNI
#include "meshoptimizer.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

size_t meshopt_optimizeVertexCache(
    unsigned int* destination,
    const unsigned int* indices,
    size_t index_count,
    size_t vertex_count) {
    
    // Tom Forsyth's vertex cache optimization algorithm
    const size_t CACHE_SIZE = 16;
    const size_t MAX_CACHE_ENTRIES = 32;
    
    std::vector<int> cache(vertex_count, -1);
    std::vector<float> score(vertex_count, 0.0f);
    std::vector<bool> used(index_count, false);
    
    size_t dst = 0;
    std::vector<unsigned int> cache_fifo;
    
    while (dst < index_count) {
        float best_score = -1.0f;
        size_t best_index = 0;
        
        // Find best triangle
        for (size_t i = 0; i < index_count; i += 3) {
            if (used[i / 3]) continue;
            
            float tri_score = 0.0f;
            for (int v = 0; v < 3; v++) {
                unsigned int idx = indices[i + v];
                int pos = cache[idx];
                if (pos >= 0) {
                    tri_score += std::pow(0.75f, pos);
                } else {
                    tri_score += 1.0f;
                }
            }
            
            if (tri_score > best_score) {
                best_score = tri_score;
                best_index = i / 3;
            }
        }
        
        // Emit best triangle
        used[best_index] = true;
        for (int v = 0; v < 3; v++) {
            unsigned int idx = indices[best_index * 3 + v];
            destination[dst++] = idx;
            
            // Update cache
            if (cache[idx] < 0) {
                cache_fifo.push_back(idx);
                cache[idx] = (int)cache_fifo.size() - 1;
            } else {
                // Move to front
                int pos = cache[idx];
                for (size_t i = pos; i > 0; i--) {
                    cache[cache_fifo[i]] = (int)i - 1;
                    cache_fifo[i] = cache_fifo[i - 1];
                }
                cache_fifo[0] = idx;
                cache[idx] = 0;
            }
            
            // Evict if cache full
            if (cache_fifo.size() > CACHE_SIZE) {
                unsigned int evicted = cache_fifo.back();
                cache[evicted] = -1;
                cache_fifo.pop_back();
            }
        }
    }
    
    return dst;
}

size_t meshopt_optimizeOverdraw(
    unsigned int* destination,
    const unsigned int* indices,
    size_t index_count,
    const float* vertex_positions,
    size_t vertex_count,
    size_t vertex_positions_stride,
    float threshold) {
    
    // Simplified: just copy for now
    std::copy(indices, indices + index_count, destination);
    return index_count;
}

void meshopt_optimizeVertexFetch(
    void* destination,
    const unsigned int* indices,
    size_t index_count,
    const void* vertices,
    size_t vertex_count,
    size_t vertex_size) {
    
    std::vector<int> remap(vertex_count, -1);
    size_t new_vertex_count = 0;
    
    for (size_t i = 0; i < index_count; i++) {
        unsigned int idx = indices[i];
        if (remap[idx] == -1) {
            remap[idx] = (int)new_vertex_count++;
        }
    }
    
    // Copy vertices in new order
    const uint8_t* src = static_cast<const uint8_t*>(vertices);
    uint8_t* dst = static_cast<uint8_t*>(destination);
    
    for (size_t i = 0; i < vertex_count; i++) {
        if (remap[i] >= 0) {
            std::copy(src + i * vertex_size, src + (i + 1) * vertex_size, 
                     dst + remap[i] * vertex_size);
        }
    }
}

struct meshopt_cache_statistics_t {
    float atvr;
    float acmr;
};

meshopt_cache_statistics_t meshopt_analyzeVertexCache(
    const unsigned int* indices,
    size_t index_count,
    size_t vertex_count) {
    
    meshopt_cache_statistics_t stats = {0};
    const size_t CACHE_SIZE = 16;
    
    std::vector<int> cache(vertex_count, -1);
    size_t cache_misses = 0;
    size_t transforms = 0;
    
    for (size_t i = 0; i < index_count; i++) {
        unsigned int idx = indices[i];
        if (cache[idx] < 0) {
            cache_misses++;
            transforms++;
            cache[idx] = (int)transforms;
        } else {
            // Check if still in cache (simplified)
        }
    }
    
    stats.acmr = (float)cache_misses / (index_count / 3.0f);
    stats.atvr = (float)transforms / vertex_count;
    
    return stats;
}

size_t meshopt_quantizeFloat(
    uint8_t* destination,
    const float* source,
    size_t count,
    float range_min,
    float range_max,
    size_t bits) {
    
    size_t bytes_per_value = (bits + 7) / 8;
    float scale = (1 << bits) - 1;
    float range = range_max - range_min;
    
    if (destination == nullptr) {
        return count * bytes_per_value;
    }
    
    for (size_t i = 0; i < count; i++) {
        float normalized = (source[i] - range_min) / range;
        normalized = std::max(0.0f, std::min(1.0f, normalized));
        uint64_t quantized = (uint64_t)(normalized * scale + 0.5f);
        
        for (size_t b = 0; b < bytes_per_value; b++) {
            destination[i * bytes_per_value + b] = (quantized >> (b * 8)) & 0xFF;
        }
    }
    
    return count * bytes_per_value;
}

void meshopt_computeBounds(
    float* center,
    float* radius,
    const float* vertices,
    size_t vertex_count,
    size_t vertex_stride) {
    
    float min_x = vertices[0], max_x = vertices[0];
    float min_y = vertices[1], max_y = vertices[1];
    float min_z = vertices[2], max_z = vertices[2];
    
    for (size_t i = 1; i < vertex_count; i++) {
        float x = vertices[i * vertex_stride / sizeof(float)];
        float y = vertices[i * vertex_stride / sizeof(float) + 1];
        float z = vertices[i * vertex_stride / sizeof(float) + 2];
        
        min_x = std::min(min_x, x);
        max_x = std::max(max_x, x);
        min_y = std::min(min_y, y);
        max_y = std::max(max_y, y);
        min_z = std::min(min_z, z);
        max_z = std::max(max_z, z);
    }
    
    center[0] = (min_x + max_x) * 0.5f;
    center[1] = (min_y + max_y) * 0.5f;
    center[2] = (min_z + max_z) * 0.5f;
    
    float dx = max_x - min_x;
    float dy = max_y - min_y;
    float dz = max_z - min_z;
    *radius = std::sqrt(dx*dx + dy*dy + dz*dz) * 0.5f;
}