import sys
import os
import subprocess
import argparse

# Comprehensive type list for full combinatorial coverage
type_names = [
    "f32", "f16", "q4_0", "q4_1", "q5_0", "q5_1", "q8_0", "q8_1", "q2_k", "q3_k", "q4_k", "q5_k", "q6_k",
    "iq1_s", "iq1_m", "iq2_xxs", "iq2_xs", "iq2_s", "iq3_xxs", "iq3_s", "iq4_xs", "iq4_nl",
    "mxfp4", "bf16", "turbo2_0", "turbo3_0", "turbo4_0"
]

binary_ops_files = ["add.comp", "sub.comp", "mul.comp", "div.comp", "multi_add.comp"]
unary_ops_files = ["exp.comp", "log.comp", "gelu.comp", "silu.comp", "relu.comp"]

def to_uppercase(s):
    return s.upper()

def compile_shader(glslc, in_path, out_path, defines, name):
    if not glslc or not in_path: return False
    # Use vulkan1.3 for any modern/complex variants
    target_env = "--target-env=vulkan1.3" if ("_cm1" in name or "_cm2" in name or "conv2d" in name) else "--target-env=vulkan1.2"
    
    # Core defines required for stability
    d = {"UNROLL": " ", "FLOAT_TYPE": "float", "A_TYPE": "float", "B_TYPE": "float", "D_TYPE": "float"}
    d.update(defines)
    
    cmd = [glslc, "-fshader-stage=compute", target_env, in_path, "-o", out_path, "-O"]
    for k, v in d.items():
        cmd.append(f"-D{k}={v}")
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        if "extension not supported" not in result.stderr:
            print(f"! Failed {name}: {result.stderr.strip()[:100]}")
        return False
    return True

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--glslc", required=False)
    parser.add_argument("--output-dir", required=False)
    parser.add_argument("--target-hpp", required=False)
    parser.add_argument("--target-cpp", required=False)
    parser.add_argument("--source", required=False)
    args = parser.parse_args()

    glslc = args.glslc
    output_dir = args.output_dir
    target_hpp = args.target_hpp
    target_cpp = args.target_cpp
    source_full = args.source
    source_base = os.path.basename(source_full) if source_full else None

    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir, exist_ok=True)

    outputs = []
    
    # 1. Binary & Unary (Standard)
    ops = binary_ops_files + unary_ops_files
    for op_file in ops:
        base = op_file.replace(".comp", "")
        for t0 in ["f32", "f16"]:
            for t1 in ["f32", "f16"]:
                vname = f"{base}_{t0}_{t1}_f32"
                if source_base == op_file:
                    defs = {"A_TYPE": "float16_t" if t0=="f16" else "float", "B_TYPE": "float16_t" if t1=="f16" else "float", "D_TYPE": "float"}
                    out_spv = os.path.join(output_dir, vname + ".spv")
                    if compile_shader(glslc, source_full, out_spv, defs, vname): outputs.append((vname, out_spv))
                elif not source_base: outputs.append((vname, None))

    # 2. mul_mat_vec [THE BIG LOOP]
    for tname in type_names:
        expected = "mul_mat_vec_" + tname + ".comp" if (tname.endswith("_k") or tname.startswith("iq1_") or tname.startswith("iq2_") or tname.startswith("iq3_")) else "mul_mat_vec.comp"
        variants = ["_f32_f32", "_f16_f32", "_f32_f32_subgroup", "_f16_f32_subgroup", "_f32_f32_subgroup_no_shmem", "_f16_f32_subgroup_no_shmem"]
        for suffix in variants:
            for is_id in [False, True]:
                vname = f"mul_mat_vec_{'id_' if is_id else ''}{tname}{suffix}"
                if source_base == expected:
                    d = {"DATA_A_" + to_uppercase(tname): "1", "MUL_MAT_ID": "1" if is_id else "0"}
                    if "f16" in suffix: d.update({"B_TYPE": "float16_t", "B_TYPE_VEC2": "f16vec2", "B_TYPE_VEC4": "f16vec4"})
                    if "subgroup" in suffix: d["USE_SUBGROUP_ADD"] = "1"
                    if "no_shmem" in suffix: d["USE_SUBGROUP_ADD_NO_SHMEM"] = "1"
                    out_spv = os.path.join(output_dir, vname + ".spv")
                    if compile_shader(glslc, source_full, out_spv, d, vname): outputs.append((vname, out_spv))
                elif not source_base: outputs.append((vname, None))

    # 3. Flash Attention [COMBINATORIAL RECOVERY]
    fa_types = ["f32", "f16", "q4_0", "q4_1", "q5_0", "q5_1", "q8_0", "iq4_nl", "turbo3_0"]
    for tname in fa_types:
        for f16acc in [False, True]:
            for fp32_dev in [False, True]:
                for path in ["", "_cm1", "_cm2"]:
                    vname = f"flash_attn_f32_f16_{tname}{'_f16acc' if f16acc else ''}{'_fp32' if fp32_dev else ''}{path}"
                    expected = f"flash_attn{path}.comp"
                    if source_base == expected:
                        # Determine BLOCK_BYTE_SIZE & BLOCK_SIZE
                        bbs = "16"
                        bs = "1"
                        if tname == "f32": bbs = "16"; bs = "4"
                        elif tname == "f16": bbs = "2"
                        elif tname == "q4_0": bbs = "18"
                        elif tname == "q4_1": bbs = "20"
                        elif tname == "q5_0": bbs = "22"
                        elif tname == "q5_1": bbs = "24"
                        elif tname == "q8_0": bbs = "34"
                        elif tname.startswith("turbo"): bbs = "128"
                        elif tname == "iq4_nl": bbs = "18"

                        d = {
                            "DATA_A_" + to_uppercase(tname): "1",
                            "ACC_TYPE": "float16_t" if f16acc else "float",
                            "FLOAT_TYPE": "float" if fp32_dev else "float16_t",
                            "BLOCK_BYTE_SIZE": bbs,
                            "BLOCK_SIZE": bs
                        }
                        if "cm1" in path: d["COOPMAT"] = "1"
                        if "cm2" in path and tname != "f16": d["DEQUANTFUNC"] = "dequantFunc" + to_uppercase(tname)
                        out_spv = os.path.join(output_dir, vname + ".spv")
                        if compile_shader(glslc, source_full, out_spv, d, vname): outputs.append((vname, out_spv))
                    elif not source_base: outputs.append((vname, None))

    # 4. Copy & Extra (De-duplicate exports)
    seen_vnames = set()
    if target_hpp:
        with open(target_hpp, "w") as f:
            f.write("#pragma once\n#include <stdint.h>\n\n")
            outputs.sort()
            for name, _ in outputs:
                if name in seen_vnames: continue
                seen_vnames.add(name)
                f.write(f"extern const uint64_t {name}_len;\nextern const unsigned char {name}_data[];\n\n")

    if target_cpp and source_base:
        with open(target_cpp, "w") as f:
            f.write(f'#include "{os.path.basename(target_hpp)}"\n\n')
            for name, spv_path in outputs:
                if not spv_path or not os.path.exists(spv_path): continue
                with open(spv_path, "rb") as bf: data = bf.read()
                f.write(f"const uint64_t {name}_len = {len(data)};\nconst unsigned char {name}_data[{len(data)}] = {{\n    ")
                for i, b in enumerate(data):
                    f.write(f"0x{b:02x},")
                    if (i+1)%12 == 0: f.write("\n    ")
                f.write("\n};\n\n")

if __name__ == "__main__":
    main()
