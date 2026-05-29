# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# [SCYPHEON FIX] Force Ninja to use response files to bypass Windows command-line limits (8191 chars)
set(CMAKE_NINJA_FORCE_RESPONSE_FILE ON CACHE BOOL "" FORCE)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("llama-android")

#include(FetchContent)
#FetchContent_Declare(
#        llama
#        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
#        GIT_TAG        master
#)

# Also provides "common"
#FetchContent_MakeAvailable(llama)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#

# Configure Llama.cpp options -> Disable Curl/Network to prevent build failure
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

# 笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊・
# GPU ACCELERATION - VULKAN (Basic mode)
# Advanced features (COOPMAT, INTEGER_DOT) disabled in ggml-vulkan/CMakeLists
# Mali G77 gets basic Vulkan compute shaders
# 笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊・


set(GGML_VULKAN ON CACHE BOOL "" FORCE)                  # ENABLED: Massively faster on S20u GPU



set(GGML_OPENCL ON CACHE BOOL "" FORCE)                  # ENABLED: Stable fallback for Vulkan crashes
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/local-llama/ggml/src/ggml-opencl)

# Pre-built shader generator
set(GGML_VULKAN_SHADERS_GEN OFF CACHE BOOL "" FORCE) # Disable building it
set(GGML_VULKAN_SHADERS_GEN_BINARY "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../prebuild/vulkan-shaders-gen.exe" CACHE FILEPATH "" FORCE)
set(GGML_VULKAN_SHADERS_GEN_EXE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../prebuild/vulkan-shaders-gen.exe" CACHE FILEPATH "" FORCE)

# Vulkan C++ headers from host SDK (NDK only has libvulkan.so, not vulkan.hpp)
if(DEFINED ENV{VULKAN_SDK})
    set(VULKAN_SDK_INCLUDE "$ENV{VULKAN_SDK}/Include")
    include_directories(SYSTEM ${VULKAN_SDK_INCLUDE})
endif()

# Vulkan SDK headers
# Uncomment these if compiling on your local Windows PC. Otherwise Android NDK provides them.
# set(Vulkan_INCLUDE_DIR "$ENV{VULKAN_SDK}/Include" CACHE PATH "" FORCE)
# set(Vulkan_GLSLC_EXECUTABLE "$ENV{VULKAN_SDK}/Bin/glslc.exe" CACHE FILEPATH "" FORCE)

# 笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊・
# CPU FALLBACK FLAGS - Safe for Exynos 990 / ARM64
# Samsung S20 Ultra uses Exynos 990 which lacks newer ARM extensions
# 笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊絶武笊・
set(GGML_CPU_AARCH64 ON CACHE BOOL "" FORCE)          # ENABLED: Modern ARM optimizations for SD865+
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)               # OFF: Don't detect host CPU (we cross-compile)
set(GGML_SVE OFF CACHE BOOL "" FORCE)                  # OFF: SVE not supported on Exynos 990
set(GGML_F16C OFF CACHE BOOL "" FORCE)                 # OFF: x86 only
set(LLAMA_NATIVE OFF CACHE BOOL "" FORCE)              # OFF: Cross-compile safe
set(GGML_RPC OFF CACHE BOOL "" FORCE)                  # OFF: No need for remote inference

# ═══════════════════════════════════════════════════════════════════════════════
# CRITICAL: Force basic ARMv8-A without DOTPROD/I8MM extensions
# Exynos 990 claims DOTPROD support but crashes on some instructions
# Using -march=armv8-a forces scalar implementations that are stable
# ═══════════════════════════════════════════════════════════════════════════════
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)  # Force basic ARMv8
set(SCYPHEON_ARCH_FLAGS "-O2 -march=armv8-a -U__ARM_FEATURE_DOTPROD -U__ARM_FEATURE_MATMUL_INT8 -U__ARM_FEATURE_FP16_VECTOR_ARITHMETIC")
if (NOT CMAKE_CXX_FLAGS MATCHES "-march=armv8-a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${SCYPHEON_ARCH_FLAGS}" CACHE STRING "" FORCE)
endif()
if (NOT CMAKE_C_FLAGS MATCHES "-march=armv8-a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${SCYPHEON_ARCH_FLAGS}" CACHE STRING "" FORCE)
endif()

# Include custom downloaded vulkan headers if they exist
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# [SCYPHEON FIX] Use local subdirectory instead of FetchContent to "bake in" TURBO patches
add_subdirectory(local-llama)

set(llama_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/local-llama)

# [SCYPHEON LOOP FIX] Disabling automatic patching to break circular dependency
# execute_process(
#     COMMAND python "${CMAKE_CURRENT_SOURCE_DIR}/patch_llama.py" "${llama_SOURCE_DIR}"
#     WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
# )


# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        llama-android.cpp)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        llama
        common
        android
        log)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${llama_SOURCE_DIR}/common
        ${llama_SOURCE_DIR}/include)
