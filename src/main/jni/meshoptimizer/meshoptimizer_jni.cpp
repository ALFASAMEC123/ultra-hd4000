#include <jni.h>
#include <meshoptimizer.h>
#include <vector>
#include <cstring>

extern "C" {

JNIEXPORT jlong JNICALL Java_com_ultra_hd4000_render_MeshOptimizer_optimizeVertexCache
  (JNIEnv* env, jclass clazz, jintArray indices, jint indexCount, jint vertexCount) {
    
    jint* indicesPtr = env->GetIntArrayElements(indices, nullptr);
    jsize length = env->GetArrayLength(indices);
    
    std::vector<unsigned int> optimized(length);
    size_t optimizedCount = meshopt_optimizeVertexCache(
        optimized.data(),
        reinterpret_cast<const unsigned int*>(indicesPtr),
        indexCount,
        vertexCount
    );
    
    jintArray result = env->NewIntArray(optimizedCount);
    env->SetIntArrayRegion(result, 0, optimizedCount, reinterpret_cast<jint*>(optimized.data()));
    
    env->ReleaseIntArrayElements(indices, indicesPtr, JNI_ABORT);
    return reinterpret_cast<jlong>(result);
}

JNIEXPORT jlong JNICALL Java_com_ultra_hd4000_render_MeshOptimizer_optimizeOverdraw
  (JNIEnv* env, jclass clazz, jintArray indices, jint indexCount, jint vertexCount,
   jfloatArray positions, jint positionStride, jfloat threshold) {
    
    jint* indicesPtr = env->GetIntArrayElements(indices, nullptr);
    jfloat* positionsPtr = env->GetFloatArrayElements(positions, nullptr);
    jsize indicesLength = env->GetArrayLength(indices);
    
    std::vector<unsigned int> optimized(indicesLength);
    size_t optimizedCount = meshopt_optimizeOverdraw(
        optimized.data(),
        reinterpret_cast<const unsigned int*>(indicesPtr),
        indexCount,
        positionsPtr,
        vertexCount,
        positionStride,
        threshold
    );
    
    jintArray result = env->NewIntArray(optimizedCount);
    env->SetIntArrayRegion(result, 0, optimizedCount, reinterpret_cast<jint*>(optimized.data()));
    
    env->ReleaseIntArrayElements(indices, indicesPtr, JNI_ABORT);
    env->ReleaseFloatArrayElements(positions, positionsPtr, JNI_ABORT);
    return reinterpret_cast<jlong>(result);
}

JNIEXPORT void JNICALL Java_com_ultra_hd4000_render_MeshOptimizer_optimizeVertexFetch
  (JNIEnv* env, jclass clazz, jbyteArray vertices, jint vertexCount, jint vertexSize,
   jintArray indices, jint indexCount, jbyteArray output) {
    
    jbyte* verticesPtr = env->GetByteArrayElements(vertices, nullptr);
    jint* indicesPtr = env->GetIntArrayElements(indices, nullptr);
    jbyte* outputPtr = env->GetByteArrayElements(output, nullptr);
    
    meshopt_optimizeVertexFetch(
        outputPtr,
        reinterpret_cast<const unsigned int*>(indicesPtr),
        indexCount,
        verticesPtr,
        vertexCount,
        vertexSize
    );
    
    env->ReleaseByteArrayElements(vertices, verticesPtr, JNI_ABORT);
    env->ReleaseIntArrayElements(indices, indicesPtr, JNI_ABORT);
    env->ReleaseByteArrayElements(output, outputPtr, 0);
}

JNIEXPORT jfloatArray JNICALL Java_com_ultra_hd4000_render_MeshOptimizer_analyzeVertexCache
  (JNIEnv* env, jclass clazz, jintArray indices, jint indexCount, jint vertexCount) {
    
    jint* indicesPtr = env->GetIntArrayElements(indices, nullptr);
    
    meshopt_cache_statistics_t stats = meshopt_analyzeVertexCache(
        reinterpret_cast<const unsigned int*>(indicesPtr),
        indexCount,
        vertexCount
    );
    
    env->ReleaseIntArrayElements(indices, indicesPtr, JNI_ABORT);
    
    jfloatArray result = env->NewFloatArray(2);
    jfloat statsArray[2] = {stats.atvr, stats.acmr};
    env->SetFloatArrayRegion(result, 0, 2, statsArray);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_ultra_hd4000_render_MeshOptimizer_quantizeFloat
  (JNIEnv* env, jclass clazz, jfloatArray source, jint count, jfloat rangeMin, 
   jfloat rangeMax, jint bits) {
    
    jfloat* sourcePtr = env->GetFloatArrayElements(source, nullptr);
    
    size_t outSize = meshopt_quantizeFloat(nullptr, sourcePtr, count, rangeMin, rangeMax, bits);
    std::vector<uint8_t> output(outSize);
    meshopt_quantizeFloat(output.data(), sourcePtr, count, rangeMin, rangeMax, bits);
    
    env->ReleaseFloatArrayElements(source, sourcePtr, JNI_ABORT);
    
    jbyteArray result = env->NewByteArray(outSize);
    env->SetByteArrayRegion(result, 0, outSize, reinterpret_cast<jbyte*>(output.data()));
    return result;
}

}