@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64
cl /EHsc /O2 /I. D:\AuraLink\VITREON\llama\src\main\cpp\local-llama\ggml\src\ggml-vulkan\vulkan-shaders\vulkan-shaders-gen.cpp /Fe:D:\AuraLink\scypheon_private\vulkan-shaders-gen-optimized.exe
