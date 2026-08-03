@echo off
set "VCVARS_PATH=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS_PATH%" (
    set "VCVARS_PATH=C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
)
call "%VCVARS_PATH%"
cl /EHsc /O2 /I. "%~dp0..\llama\src\main\cpp\local-llama\ggml\src\ggml-vulkan\vulkan-shaders\vulkan-shaders-gen.cpp" /Fe:"%~dp0vulkan-shaders-gen-optimized.exe"
