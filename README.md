# ultra-hd4000

Ultra-optimized Minecraft mod for Intel HD 4000 (Ivy Bridge) - Minecraft 1.21.1 Fabric

## Features

- **Software Vertex Processing** - AVX2 SIMD vertex transform on CPU (8 threads) instead of GPU vertex shader
- **CPU Frustum Culling** - Morton-order chunk culling with SIMD plane tests
- **Compressed VBO Format** - 12 bytes/vertex (pos3×u16 + normal2×u8 octahedral + uv2×u16 + light×u16)
- **Indirect Drawing** - `glMultiDrawArraysIndirect` for chunk rendering
- **Dynamic Upscaling** - 540p internal → 1050p output with luma sharpening
- **Off-Heap Chunk Storage** - mmap'd chunk meshes (Chronicle Map + zstd compression)
- **Priority Tick Scheduler** - Work-stealing pool with 3/8/50ms priority budgets
- **AI Throttling** - Distance-based entity AI throttling when TPS < 19.5
- **Entity Instancing** - Batched rendering for identical entity models
- **Particle SSBO** - GPU particle system via shader storage buffers
- **Delta Network Compression** - Varint zigzag entity position deltas

## Target Hardware

- **GPU**: Intel HD 4000 (Ivy Bridge, 16 EUs, OpenGL 4.2)
- **CPU**: Intel i7-3770 (4C/8T, AVX2)
- **RAM**: 16 GB

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16+
- Fabric API 0.102+
- Java 21

## Installation

1. Download the latest JAR from [Releases](https://github.com/strane/ultra-hd4000/releases)
2. Place in `mods/` folder
3. Requires Fabric API

## Configuration

Press `F10` in-game for benchmark, `F11` to toggle modules, `F12` to reload shaders.

Or use Mod Menu → Ultra HD 4000 → Configure for full GUI.

## Building

```bash
./gradlew build --no-daemon
```

Output: `build/libs/ultra-hd4000-1.0.0.jar`

## Compatibility

Auto-disables overlapping features from:
- Sodium (chunk builder, culling, VBO)
- Lithium (scheduler, AI)
- FerriteCore (chunk cache)
- ImmediatelyFast
- EntityCulling
- ModernFix
- BadOptimizations

## License

MIT