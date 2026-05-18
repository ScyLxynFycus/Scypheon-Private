package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.agent.ooda.ToolSchemaValidator
import com.scypheon.sdk.core.agent.ooda.ValidationResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonSchemaToolValidator @Inject constructor() : ToolSchemaValidator {

    override fun validate(toolName: String, params: Map<String, String>): ValidationResult {
        val errors = mutableListOf<String>()
        val sanitized = params.toMutableMap()

        when (toolName) {
            "get_drug_dosage", "check_interaction" -> {
                if (!sanitized.containsKey("drug") || sanitized["drug"].isNullOrBlank()) {
                    errors.add("Missing required parameter: drug")
                } else {
                    sanitized["drug"] = sanitized["drug"]!!.trim().lowercase()
                }
            }
            "check_allergy" -> {
                if (!sanitized.containsKey("patient_id") && !sanitized.containsKey("drug")) {
                    errors.add("Missing required parameter: patient_id or drug")
                }
            }
        }

        sanitized.forEach { (k, v) ->
            if (v.contains(Regex("[;\\-\\-]|<script|exec\\(|union\\s+select", RegexOption.IGNORE_CASE))) {
                errors.add("Injection pattern detected in parameter: $k")
            }
        }

        return ValidationResult(errors.isEmpty(), sanitized, errors)
    }
}
