# SCYPHEON PRECISION PATCH v13.0.4 [THE FINAL HIJACK - ULTRA CONSERVATIVE]
import os
import sys
import glob

def normalize(content):
    """Normalize newlines for comparison."""
    return content.replace('\r\n', '\n').strip()

def write_if_changed(path, content):
    """Only write to file if content has changed to avoid triggering build loops."""
    if os.path.exists(path):
        with open(path, 'r', encoding='utf-8') as f:
            existing = f.read()
            if normalize(existing) == normalize(content):
                print(f"  [SKIP] {os.path.basename(path)} is already up to date.")
                return False
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(content)
    print(f"  [OK] {os.path.basename(path)} updated.")
    return True

def main():
    try:
        project_root = "D:/AuraLink/VITREON/llama"
        prebuilt_gen = "D:/AuraLink/prebuild/vulkan-shaders-gen.exe"
        if len(sys.argv) > 1:
            raw_path = sys.argv[1].replace('\\', '/')
            if os.path.isdir(raw_path): project_root = raw_path

        print(f"Targeting: {project_root}")
        
        # 🛠️ Absolute MSVC & SDK Paths
        msvc_base = "C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207"
        msvc_path = f"{msvc_base}/bin/Hostx64/x64/cl.exe"
        sdk_base = "C:/Program Files (x86)/Windows Kits/10/Lib/10.0.26100.0"
        lib_paths = [f"{msvc_base}/lib/x64", f"{sdk_base}/um/x64", f"{sdk_base}/ucrt/x64"]
        sdk_inc_base = "C:/Program Files (x86)/Windows Kits/10/Include/10.0.26100.0"
        inc_paths = [f"{msvc_base}/include", f"{sdk_inc_base}/ucrt", f"{sdk_inc_base}/um",
            f"{sdk_inc_base}/shared", f"{sdk_inc_base}/winrt", f"{sdk_inc_base}/cppwinrt"]
        
        cxx_roots = glob.glob(os.path.join(project_root, ".cxx", "Release", "*", "arm64-v8a").replace('\\', '/'))
        targets = [project_root]
        search_pattern = os.path.join(project_root, ".cxx", "Release", "*", "arm64-v8a", "_deps", "llama-src").replace('\\', '/')
        targets.extend(glob.glob(search_pattern))
        targets = list(set([t for t in targets if os.path.exists(t)]))

        for target_dir in targets:
            print(f"--- Patching {os.path.basename(target_dir)} ---")
            
            # --- 1. Fix conv2d_mm.comp ---
            shader_dir = f"{target_dir}/ggml/src/ggml-vulkan/vulkan-shaders"
            conv_path = f"{shader_dir}/conv2d_mm.comp"
            if os.path.exists(conv_path):
                with open(conv_path, 'r', encoding='utf-8') as f: content = f.read()
                if 'uint32_t CRS_remainder =' not in content:
                    content = content.replace('CRS_remainder = CRS_idx_a % (KW * KH);', 'uint32_t CRS_remainder = CRS_idx_a % (KW * KH);')
                    write_if_changed(conv_path, content)

            # --- 2. Fix ggml-vulkan/CMakeLists.txt (NUCLEAR BYPASS) ---
            vk_cmake = f"{target_dir}/ggml/src/ggml-vulkan/CMakeLists.txt"
            if os.path.exists(vk_cmake):
                with open(vk_cmake, 'r', encoding='utf-8') as f: content = f.read()
                original = content
                
                # REPAIR: Remove the mangled hashes I accidentally added earlier (v13.0.2 damage)
                # But do it VERY specifically to ONLY touch the prebuild path
                content = content.replace('"D:/AuraLink/prebuild/# vulkan-shaders-gen.exe"', f'"{prebuilt_gen}"')
                content = content.replace('"D:/AuraLink/prebuild/vulkan-shaders-gen.exe"', f'"{prebuilt_gen}"') # Identity
                
                # ExternalProject_Add bypass
                # Note: This literal match must be EXACT.
                if 'ExternalProject_Add(' in content and 'vulkan-shaders-gen' in content and 'DISABLED' not in content:
                     # This is a fallback if the previous replacement didn't match perfectly
                     content = content.replace('ExternalProject_Add(', '    # ExternalProject_Add(')
                     content = content.replace('vulkan-shaders-gen', 'vulkan-shaders-gen) DISABLED: Using prebuilt binary')

                # Command bypass
                content = content.replace('set (_ggml_vk_genshaders_cmd "${_ggml_vk_genshaders_dir}/vulkan-shaders-gen${_ggml_vk_host_suffix}")', f'set (_ggml_vk_genshaders_cmd "{prebuilt_gen}")')
                
                # Targeted comment-outs for dependencies (avoids matching paths)
                content = content.replace('DEPENDS vulkan-shaders-gen', 'DEPENDS # vulkan-shaders-gen')
                
                if normalize(content) != normalize(original): write_if_changed(vk_cmake, content)

            # --- 3. Fix llama-src/CMakeLists.txt (HTTPLIB REMOVAL) ---
            llama_cmake = f"{target_dir}/CMakeLists.txt"
            if os.path.exists(llama_cmake):
                with open(llama_cmake, 'r', encoding='utf-8') as f: content = f.read()
                original = content
                content = content.replace('add_subdirectory(vendor/cpp-httplib)', '# [SCYPHEON] DISABLED: Breaks Windows command limit (8191 chars)')
                if normalize(content) != normalize(original):
                    write_if_changed(llama_cmake, content)

            # --- 4. Fix ops.cpp ---
            ops_path = f"{target_dir}/ggml/src/ggml-cpu/ops.cpp"
            if os.path.exists(ops_path):
                with open(ops_path, 'r', encoding='utf-8') as f: content = f.read()
                original = content
                content = content.replace('if (dst->type == GGML_TYPE_TURBO3_0 || dst->type == GGML_TYPE_TURBO4_0 || dst->type == GGML_TYPE_TURBO2_0) {\n    {\n', 'if (dst->type == GGML_TYPE_TURBO3_0 || dst->type == GGML_TYPE_TURBO4_0 || dst->type == GGML_TYPE_TURBO2_0) {\n')
                if normalize(content) != normalize(original): write_if_changed(ops_path, content)

        # --- 5. Final Hijack ---
        for root in cxx_roots:
            print(f"--- Final Hijack: {os.path.basename(os.path.dirname(root))} ---")
            lib_env = ";".join([p.replace('/', '\\\\') for p in lib_paths])
            inc_env = ";".join([p.replace('/', '\\\\') for p in inc_paths])
            hijack = "".join([f'set(ENV{{LIB}} "{lib_env}")\n', f'set(ENV{{INCLUDE}} "{inc_env}")\n', f'set(CMAKE_C_COMPILER "{msvc_path}")\n', f'set(CMAKE_CXX_COMPILER "{msvc_path}")\n'])
            for file_name in ["_deps/llama-src/ggml/src/ggml-vulkan/cmake/host-toolchain.cmake.in", "host-toolchain.cmake"]:
                path = os.path.join(root, file_name).replace('\\', '/')
                if os.path.exists(path): write_if_changed(path, hijack)

        print("=== v13.0.4 ULTRA-CONSERVATIVE HIJACK COMPLETE ===")
    except Exception as e:
        print(f"!! Patch Error: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    main()
