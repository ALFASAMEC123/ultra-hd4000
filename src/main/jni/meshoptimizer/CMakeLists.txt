cmake_minimum_required(VERSION 3.20)
project(meshoptimizer_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Meshoptimizer source (vendored)
add_library(meshoptimizer STATIC
    meshoptimizer.h
    meshoptimizer.cpp
)

# JNI wrapper
add_library(meshoptimizer_jni SHARED meshoptimizer_jni.cpp)
target_link_libraries(meshoptimizer_jni meshoptimizer)

# Find JNI headers
find_package(JNI REQUIRED)
target_include_directories(meshoptimizer_jni PRIVATE ${JNI_INCLUDE_DIRS})

# Platform-specific settings
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    target_link_libraries(meshoptimizer_jni dl)
    set_target_properties(meshoptimizer_jni PROPERTIES
        OUTPUT_NAME "meshoptimizer_jni"
        PREFIX ""
        SUFFIX ".so"
    )
elseif(CMAKE_SYSTEM_NAME STREQUAL "Windows")
    set_target_properties(meshoptimizer_jni PROPERTIES
        OUTPUT_NAME "meshoptimizer_jni"
        PREFIX ""
        SUFFIX ".dll"
    )
elseif(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    set_target_properties(meshoptimizer_jni PROPERTIES
        OUTPUT_NAME "meshoptimizer_jni"
        PREFIX "lib"
        SUFFIX ".dylib"
    )
endif()

# Optimization flags
target_compile_options(meshoptimizer PRIVATE -O3 -march=native -fPIC)
target_compile_options(meshoptimizer_jni PRIVATE -O3 -march=native -fPIC)