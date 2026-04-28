#include <cstdint>

// Categorical Dummy Symbols for unimplemented TURBO variants
// These satisfy linker references from auto-generated compute wrappers.
// Definitions use standard C++ external linkage to match declarations in ggml-vulkan-shaders.hpp.

extern const uint64_t dequant_turbo4_0_len = 0;
extern const unsigned char dequant_turbo4_0_data[1] = {0};

extern const uint64_t matmul_id_subgroup_turbo4_0_f32_len = 0;
extern const unsigned char matmul_id_subgroup_turbo4_0_f32_data[1] = {0};
extern const uint64_t matmul_id_subgroup_turbo4_0_f32_aligned_len = 0;
extern const unsigned char matmul_id_subgroup_turbo4_0_f32_aligned_data[1] = {0};
extern const uint64_t matmul_id_subgroup_turbo4_0_f32_f16acc_len = 0;
extern const unsigned char matmul_id_subgroup_turbo4_0_f32_f16acc_data[1] = {0};
extern const uint64_t matmul_id_subgroup_turbo4_0_f32_aligned_f16acc_len = 0;
extern const unsigned char matmul_id_subgroup_turbo4_0_f32_aligned_f16acc_data[1] = {0};

extern const uint64_t matmul_turbo4_0_f32_len = 0;
extern const unsigned char matmul_turbo4_0_f32_data[1] = {0};
extern const uint64_t matmul_turbo4_0_f32_aligned_len = 0;
extern const unsigned char matmul_turbo4_0_f32_aligned_data[1] = {0};
extern const uint64_t matmul_turbo4_0_f32_f16acc_len = 0;
extern const unsigned char matmul_turbo4_0_f32_f16acc_data[1] = {0};
extern const uint64_t matmul_turbo4_0_f32_aligned_f16acc_len = 0;
extern const unsigned char matmul_turbo4_0_f32_aligned_f16acc_data[1] = {0};

extern const uint64_t set_rows_turbo4_0_i32_len = 0;
extern const unsigned char set_rows_turbo4_0_i32_data[1] = {0};
extern const uint64_t set_rows_turbo4_0_i32_rte_len = 0;
extern const unsigned char set_rows_turbo4_0_i32_rte_data[1] = {0};
extern const uint64_t set_rows_turbo4_0_i64_len = 0;
extern const unsigned char set_rows_turbo4_0_i64_data[1] = {0};
extern const uint64_t set_rows_turbo4_0_i64_rte_len = 0;
extern const unsigned char set_rows_turbo4_0_i64_rte_data[1] = {0};

// mul_mat_vec stubs
#define TURBO_STUBS(TYPE, PATH) \
    extern const uint64_t PATH##_##TYPE##_len = 0; \
    extern const unsigned char PATH##_##TYPE##_data[1] = {0}; \
    extern const uint64_t PATH##_##TYPE##_subgroup_len = 0; \
    extern const unsigned char PATH##_##TYPE##_subgroup_data[1] = {0}; \
    extern const uint64_t PATH##_##TYPE##_subgroup_no_shmem_len = 0; \
    extern const unsigned char PATH##_##TYPE##_subgroup_no_shmem_data[1] = {0};

TURBO_STUBS(turbo4_0_f16_f32, mul_mat_vec)
TURBO_STUBS(turbo4_0_f32_f32, mul_mat_vec)
TURBO_STUBS(turbo4_0_f32_f32, mul_mat_vec_id)

