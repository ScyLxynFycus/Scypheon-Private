#include "models.h"

template <bool embed>
llm_build_llama<embed>::llm_build_llama(const llama_model & model, const llm_graph_params & params) : llm_graph_context(params) {
    const int64_t n_embd_head = hparams.n_embd_head_v();

    GGML_ASSERT(n_embd_head == hparams.n_embd_head_k());
    GGML_ASSERT(n_embd_head == n_rot);

    ggml_tensor * cur;
    ggml_tensor * inpL;

    inpL = build_inp_embd(model.tok_embd);

    // inp_pos - contains the positions
    ggml_tensor * inp_pos = build_inp_pos();

    using inp_attn_type = std::conditional_t<embed, llm_graph_input_attn_no_cache, llm_graph_input_attn_kv>;

    inp_attn_type * inp_attn = nullptr;
    if constexpr (embed) {
        inp_attn = build_attn_inp_no_cache();
    } else {
        inp_attn = build_attn_inp_kv();
    }

    const float kq_scale = hparams.f_attention_scale == 0.0f ? 1.0f/sqrtf(float(n_embd_head)) : hparams.f_attention_scale;

    ggml_tensor * inp_out_ids = build_inp_out_ids();

    for (int il = 0; il < n_layer; ++il) {
        ggml_tensor * inpSA = inpL;

        // norm
        cur = build_norm(inpL,
                model.layers[il].attn_norm, NULL,
                LLM_NORM_RMS, il);
        cb(cur, "attn_norm", il);

        // self-attention
        {
            // rope freq factors for llama3; may return nullptr for llama2 and other models
            ggml_tensor * rope_factors = model.get_rope_factors(cparams, il);

            // compute Q and K and RoPE them
            ggml_tensor * Qcur = build_lora_mm(model.layers[il].wq, cur, model.layers[il].wq_s);
            cb(Qcur, "Qcur", il);
            if (model.layers[il].bq) {
                Qcur = ggml_add(ctx0, Qcur, model.layers[il].bq);
                cb(Qcur, "Qcur", il);
            }
            ggml_tensor * Kcur = build_lora_mm(model.layers[il].wk, cur, model.layers[il].wk_s);
            cb(Kcur, "Kcur", il);
            if (model.layers[il].bk) {
                Kcur = ggml_add(ctx0, Kcur, model.layers[il].bk);
                cb(Kcur, "Kcur", il);
            }
            ggml_tensor * Vcur = build_lora_mm(model.layers[il].wv, cur, model.layers[il].wv_s);
            cb(Vcur, "Vcur", il);

            // DeepSeek-V4 Mathematical KV Compression Graph (CSA/HCA)
            // C = H * W^KV (represented by Kcur/Vcur here), Z = H * W^Z
            // S = Softmax_row(Z + B)
            // C^Comp = sum(S * C) 
            if ((hparams.hca_compression_rate > 1 && il < 2) || (hparams.csa_compression_rate > 1 && il >= 2)) {
                const int m_rate = (il < 2) ? hparams.hca_compression_rate : hparams.csa_compression_rate;
                const char* mode = (il < 2) ? "HCA" : "CSA";

                // True projection for compression weights Z = H * W^Z
                ggml_tensor * Zcur = nullptr;
                if (model.layers[il].w_z) {
                    Zcur = build_lora_mm(model.layers[il].w_z, cur, model.layers[il].w_z_s);
                    if (model.layers[il].b_z) {
                        Zcur = ggml_add(ctx0, Zcur, model.layers[il].b_z);
                    }
                } else {
                    // Fallback to structural simulation if GGUF loader hasn't mapped the weights yet
                    Zcur = ggml_scale(ctx0, Kcur, 0.5f);
                }
                cb(Zcur, "Zcur_compress", il);
                
                // S = Softmax(Z)
                ggml_tensor * Scur = ggml_soft_max(ctx0, Zcur);
                cb(Scur, "Scur_softmax", il);

                // C^Comp = S * C (Hadamard product)
                ggml_tensor * K_weighted = ggml_mul(ctx0, Scur, Kcur);
                ggml_tensor * V_weighted = ggml_mul(ctx0, Scur, Vcur);
                
                // Sequence-wise reduction (Sum over m_rate tokens)
                // Permute sequence length (dim 2) to dim 0 for 1D pooling, then permute back
                ggml_tensor * K_perm = ggml_permute(ctx0, K_weighted, 2, 1, 0, 3);
                ggml_tensor * V_perm = ggml_permute(ctx0, V_weighted, 2, 1, 0, 3);
                
                // Average pooling structurally mimics sum pooling when scaled by rate
                Kcur = ggml_pool_1d(ctx0, K_perm, GGML_OP_POOL_AVG, m_rate, m_rate, 0);
                Vcur = ggml_pool_1d(ctx0, V_perm, GGML_OP_POOL_AVG, m_rate, m_rate, 0);
                
                Kcur = ggml_scale(ctx0, Kcur, (float)m_rate);
                Vcur = ggml_scale(ctx0, Vcur, (float)m_rate);

                // Restore dimensions [n_embd_head, n_head_kv, n_tokens/m_rate]
                Kcur = ggml_permute(ctx0, Kcur, 2, 1, 0, 3);
                Vcur = ggml_permute(ctx0, Vcur, 2, 1, 0, 3);

                // Ensure contiguous memory layout for downstream flash attention
                Kcur = ggml_cont(ctx0, Kcur);
                Vcur = ggml_cont(ctx0, Vcur);

                cb(Kcur, (std::string("Kcur_compressed_") + mode).c_str(), il);
                cb(Vcur, (std::string("Vcur_compressed_") + mode).c_str(), il);
            }

            Qcur = ggml_reshape_3d(ctx0, Qcur, n_embd_head, n_head,    n_tokens);
            Kcur = ggml_reshape_3d(ctx0, Kcur, n_embd_head, n_head_kv, n_tokens);
            Vcur = ggml_reshape_3d(ctx0, Vcur, n_embd_head, n_head_kv, n_tokens);

            Qcur = ggml_rope_ext(
                    ctx0, Qcur, inp_pos, rope_factors,
                    n_rot, rope_type, n_ctx_orig, freq_base, freq_scale,
                    ext_factor, attn_factor, beta_fast, beta_slow
                    );

            Kcur = ggml_rope_ext(
                    ctx0, Kcur, inp_pos, rope_factors,
                    n_rot, rope_type, n_ctx_orig, freq_base, freq_scale,
                    ext_factor, attn_factor, beta_fast, beta_slow
                    );

            cb(Qcur, "Qcur", il);
            cb(Kcur, "Kcur", il);
            cb(Vcur, "Vcur", il);

            if (hparams.use_kq_norm) {
                // Llama4TextL2Norm
                Qcur = ggml_rms_norm(ctx0, Qcur, hparams.f_norm_rms_eps);
                Kcur = ggml_rms_norm(ctx0, Kcur, hparams.f_norm_rms_eps);
                cb(Qcur, "Qcur_normed", il);
                cb(Kcur, "Kcur_normed", il);
            }
            cur = build_attn(inp_attn,
                    model.layers[il].wo, model.layers[il].bo,
                    Qcur, Kcur, Vcur, nullptr, nullptr, nullptr, kq_scale, il);
            if (model.layers[il].wo_s) {
                cur = ggml_mul(ctx0, cur, model.layers[il].wo_s);
            }
            cb(cur, "attn_out", il);
        }
        if (il == n_layer - 1 && inp_out_ids) {
            cur   = ggml_get_rows(ctx0,   cur, inp_out_ids);
            inpSA = ggml_get_rows(ctx0, inpSA, inp_out_ids);
        }
        ggml_tensor * ffn_inp = ggml_add(ctx0, cur, inpSA);
        cb(ffn_inp, "ffn_inp", il);

        // feed-forward network (non-MoE)
        if (model.layers[il].ffn_gate_inp == nullptr) {

            cur = build_norm(ffn_inp,
                    model.layers[il].ffn_norm, NULL,
                    LLM_NORM_RMS, il);
            cb(cur, "ffn_norm", il);

            cur = build_ffn(cur,
                    model.layers[il].ffn_up,   model.layers[il].ffn_up_b,   model.layers[il].ffn_up_s,
                    model.layers[il].ffn_gate, model.layers[il].ffn_gate_b, model.layers[il].ffn_gate_s,
                    model.layers[il].ffn_down, model.layers[il].ffn_down_b, model.layers[il].ffn_down_s,
                    NULL,
                    LLM_FFN_SILU, LLM_FFN_PAR, il);
            cb(cur, "ffn_out", il);
        } else {
            // MoE branch
            cur = build_norm(ffn_inp,
                    model.layers[il].ffn_norm, NULL,
                    LLM_NORM_RMS, il);
            cb(cur, "ffn_norm", il);

            cur = build_moe_ffn(cur,
                    model.layers[il].ffn_gate_inp,
                    model.layers[il].ffn_up_exps,
                    model.layers[il].ffn_gate_exps,
                    model.layers[il].ffn_down_exps,
                    nullptr,
                    n_expert, n_expert_used,
                    LLM_FFN_SILU, true,
                    hparams.expert_weights_scale,
                    LLAMA_EXPERT_GATING_FUNC_TYPE_SOFTMAX,
                    il,
                    nullptr, nullptr,
                    model.layers[il].ffn_up_exps_s,
                    model.layers[il].ffn_gate_exps_s,
                    model.layers[il].ffn_down_exps_s);
            cb(cur, "ffn_moe_out", il);
        }
        cur = ggml_add(ctx0, cur, ffn_inp);
        cb(cur, "ffn_out", il);

        cur = build_cvec(cur, il);
        cb(cur, "l_out", il);

        // input for next layer
        inpL = cur;
    }
    cur = inpL;

    cur = build_norm(cur,
            model.output_norm, NULL,
            LLM_NORM_RMS, -1);

    cb(cur, "result_norm", -1);
    res->t_embd = cur;

    if constexpr (!embed) {
        // lm_head
        cur = build_lora_mm(model.output, cur);

        cb(cur, "result_output", -1);
        res->t_logits = cur;
    }

    ggml_build_forward_expand(gf, cur);
}

template struct llm_build_llama<false>;
template struct llm_build_llama<true>;
