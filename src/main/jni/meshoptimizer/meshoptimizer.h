#ifndef MESHOPTIMIZER_H
#define MESHOPTIMIZER_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Vertex cache optimization
size_t meshopt_optimizeVertexCache(
    unsigned int* destination,
    const unsigned int* indices,
    size_t index_count,
    size_t vertex_count);

// Overdraw optimization
size_t meshopt_optimizeOverdraw(
    unsigned int* destination,
    const unsigned int* indices,
    size_t index_count,
    const float* vertex_positions,
    size_t vertex_count,
    size_t vertex_positions_stride,
    float threshold);

// Vertex fetch optimization
void meshopt_optimizeVertexFetch(
    void* destination,
    const unsigned int* indices,
    size_t index_count,
    const void* vertices,
    size_t vertex_count,
    size_t vertex_size);

// Vertex cache statistics
typedef struct {
    float atvr; // average transform to vertex ratio
    float acmr; // average cache miss ratio
} meshopt_cache_statistics_t;

meshopt_cache_statistics_t meshopt_analyzeVertexCache(
    const unsigned int* indices,
    size_t index_count,
    size_t vertex_count);

// Quantization
size_t meshopt_quantizeFloat(
    uint8_t* destination,
    const float* source,
    size_t count,
    float range_min,
    float range_max,
    size_t bits);

// Bounds computation
void meshopt_computeBounds(
    float* center,
    float* radius,
    const float* vertices,
    size_t vertex_count,
    size_t vertex_stride);

#ifdef __cplusplus
}
#endif

#endif // MESHOPTIMIZER_H