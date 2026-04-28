# Host toolchain for vulkan-shaders-gen (Windows native)
# This allows the shader generator to compile for Windows while
# the main library compiles for Android

set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_C_COMPILER cl)
set(CMAKE_CXX_COMPILER cl)
