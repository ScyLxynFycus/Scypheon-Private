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
    pqc/PqcSignatureWrapper.cpp
    pqc/PqcKemWrapper.cpp
)

# ------------------------------------------------------------------
# [MATH ENGINE] Math Engine Compilation
# ------------------------------------------------------------------
add_library(scypheon_math SHARED
    math/src/NativeMathEngine.cpp
    math/src/MathNode.cpp
    math/src/ExpressionParser.cpp
    math/src/ODESolver.cpp
)
target_include_directories(scypheon_math PRIVATE math/include)
target_link_libraries(scypheon_math android log)

# ------------------------------------------------------------------
# [PQC] liboqs Compilation (Static Library)
# ------------------------------------------------------------------
set(LIBOQS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/liboqs)
set(LIBOQS_BUILD_DIR ${CMAKE_CURRENT_BINARY_DIR}/liboqs-build)

include(ExternalProject)
ExternalProject_Add(
    liboqs_project
    SOURCE_DIR ${LIBOQS_DIR}
    BINARY_DIR ${LIBOQS_BUILD_DIR}
    CMAKE_ARGS
        -DCMAKE_BUILD_TYPE=Release
        -DBUILD_SHARED_LIBS=OFF
        -DOQS_BUILD_ONLY_LIB=ON
        -DOQS_ENABLE_SIG_ML_DSA_44=ON
        -DOQS_ENABLE_SIG_ML_DSA_65=ON
        -DOQS_ENABLE_SIG_ML_DSA_87=OFF
        -DOQS_ENABLE_KEM_ML_KEM_512=ON
        -DOQS_ENABLE_KEM_ML_KEM_768=ON
        -DOQS_ENABLE_KEM_ML_KEM_1024=OFF
        -DOQS_DIST_BUILD=OFF
        -DOQS_OPT_TARGET=generic
        -DOQS_USE_OPENSSL=OFF
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS ${LIBOQS_BUILD_DIR}/lib/liboqs.a
)

add_dependencies(${CMAKE_PROJECT_NAME} liboqs_project)
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE 
    ${LIBOQS_BUILD_DIR}/include
    ${LIBOQS_DIR}/src
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    ${LIBOQS_BUILD_DIR}/lib/liboqs.a
    android
    log
)
