@echo off
set "VCVARS_PATH=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS_PATH%" (
    set "VCVARS_PATH=C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
)

if not exist "%VCVARS_PATH%" (
    echo Error: vcvars64.bat not found.
    exit /b 1
)

call "%VCVARS_PATH%"
cl /EHsc /O2 /std:c++17 /I. D:\AuraLink\VITREON\llama\src\main\cpp\local-llama\ggml\src\ggml-vulkan\vulkan-shaders\vulkan-shaders-gen.cpp /Fe:D:\AuraLink\scypheon_private\vulkan-shaders-gen-new.exe
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed.
    exit /b %ERRORLEVEL%
)

echo Compilation successful: D:\AuraLink\scypheon_private\vulkan-shaders-gen-new.exe
