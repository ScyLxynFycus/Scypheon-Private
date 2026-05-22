cmake_minimum_required(VERSION 3.22.1)

project("scypheon_native")

# [SCYPHEON FIX] Bypass Windows command-line limits (8191 chars)
set(CMAKE_NINJA_FORCE_RESPONSE_FILE ON CACHE BOOL "" FORCE)

# [HARDENING] Production Flags for Isolated Process
# Synchronized with Vitreon Edge Engine settings
set(SCYPHEON_ARCH_FLAGS "-O3 -fPIC -march=armv8-a -U__ARM_FEATURE_DOTPROD -U__ARM_FEATURE_MATMUL_INT8 -U__ARM_FEATURE_FP16_VECTOR_ARITHMETIC")
# [HARDENING] Silence noisy vendor warnings
set(SCYPHEON_WARN_FLAGS "-Wno-missing-prototypes -Wno-unused-parameter")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${SCYPHEON_ARCH_FLAGS} ${SCYPHEON_WARN_FLAGS}")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${SCYPHEON_ARCH_FLAGS} ${SCYPHEON_WARN_FLAGS}")

# [HARDENING] Silence OpenCL version warnings and force-include ARM ACLE for yield intrinsics
add_compile_definitions(CL_TARGET_OPENCL_VERSION=300)
if(ANDROID)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -include arm_acle.h")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -include arm_acle.h")
endif()

# [HARDENING] GPU & CPU Acceleration Flags (Vitreon Match)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(GGML_OPENCL ON CACHE BOOL "" FORCE)
set(GGML_CPU_AARCH64 ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# Vulkan Shader Generator (Patched for Android)
set(GGML_VULKAN_SHADERS_GEN OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_SHADERS_GEN_EXE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../prebuild/vulkan-shaders-gen-patched.exe" CACHE FILEPATH "" FORCE)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama/src/main/cpp/local-llama/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama/src/main/cpp/local-llama/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama/src/main/cpp/local-llama/common
)

if(DEFINED ENV{VULKAN_SDK})
    include_directories($ENV{VULKAN_SDK}/Include)
elseif(EXISTS "D:/VULKAN/Include")
    include_directories("D:/VULKAN/Include")
endif()

add_subdirectory(../../../../llama/src/main/cpp/local-llama llama_build)

add_library(${CMAKE_PROJECT_NAME} SHARED
    llama-android.cpp
    GemmaInference.cpp
    AuditChain.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    android
    log
)
