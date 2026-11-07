cmake_minimum_required(VERSION 3.22.1)
project("pocketcoded_jni" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Options for KleidiAI ARM micro-kernels and Adreno/Mali Vulkan GPU offloading
option(GGML_USE_KLEIDIAI "Enable ARM KleidiAI int4/int8 GEMM/GEMV micro-kernels" ON)
option(GGML_VULKAN "Enable Vulkan compute backend for Adreno/Mali GPU offloading" ON)
option(POCKET_LINK_EXTERNAL_LLAMA "Link against full llama.cpp submodule if present" OFF)

# ARM64 (arm64-v8a) hardware acceleration flags: dotprod, i8mm, FP16 vector arithmetic
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    message(STATUS "PocketCodeD: Enabling ARMv8.2-A + dotprod + i8mm + fp16 + KleidiAI optimizations")
    add_compile_options(
        -march=armv8.2-a+dotprod+i8mm+fp16
        -O3
        -DNDEBUG
        -ffast-math
        -funroll-loops
        -fno-finite-math-only
    )
    add_compile_definitions(
        GGML_USE_CPU_AARCH64=1
        __ARM_NEON=1
        __ARM_FEATURE_DOTPROD=1
        __ARM_FEATURE_MATMUL_INT8=1
        __ARM_FEATURE_FP16_VECTOR_ARITHMETIC=1
    )
elseif(${ANDROID_ABI} STREQUAL "x86_64")
    add_compile_options(-O3 -DNDEBUG -ffast-math -mavx2 -mfma -mf16c)
endif()

if(GGML_USE_KLEIDIAI)
    add_compile_definitions(GGML_USE_KLEIDIAI=1)
endif()

if(GGML_VULKAN)
    add_compile_definitions(GGML_USE_VULKAN=1)
endif()

# Check if llama.cpp source tree is available under cpp/llama.cpp
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    set(GGML_USE_KLEIDIAI ON CACHE BOOL "Enable KleidiAI" FORCE)
    set(GGML_VULKAN ${GGML_VULKAN} CACHE BOOL "Enable Vulkan" FORCE)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(llama.cpp)
    add_compile_definitions(POCKET_HAS_LLAMA_CPP_HEADERS=1)
endif()

add_library(
    pocketcoded_jni
    SHARED
    PocketEngine.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_include_directories(
    pocketcoded_jni
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(TARGET llama)
    target_link_libraries(
        pocketcoded_jni
        PRIVATE
        llama
        ggml
        ${log-lib}
        ${android-lib}
    )
else()
    target_link_libraries(
        pocketcoded_jni
        PRIVATE
        ${log-lib}
        ${android-lib}
    )
endif()
