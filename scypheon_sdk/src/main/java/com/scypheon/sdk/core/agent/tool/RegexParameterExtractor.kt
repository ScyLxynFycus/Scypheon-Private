package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.agent.ooda.ParameterExtractor
import com.scypheon.sdk.core.agent.skills.AgentSkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegexParameterExtractor @Inject constructor() : ParameterExtractor {
    companion object {
        private val DRUG_PATTERN = Regex("\\b([a-zA-Z]+(?:\\s[a-zA-Z]+)*)\\b", RegexOption.IGNORE_CASE)
        private val DOSAGE_PATTERN = Regex("(\\d+)\\s*(mg|g|ml|tablet|kapsul)", RegexOption.IGNORE_CASE)
        private val AGE_PATTERN = Regex("(dewasa|anak|bayi|lansia|pediatric|adult|elderly)", RegexOption.IGNORE_CASE) 
    }

    override suspend fun extract(query: String, tool: Tool): Map<String, String> = withContext(Dispatchers.Default) {
        val params = mutableMapOf<String, String>()
        val lower = query.lowercase()

        if (tool.name.contains("drug", ignoreCase = true) || tool.name.contains("dosage", ignoreCase = true)) {       
            DRUG_PATTERN.find(query)?.value?.let { params["drug"] = it.trim() }
            DOSAGE_PATTERN.find(query)?.let { match ->
                params["amount"] = match.groupValues[1]
                params["unit"] = match.groupValues[2]
            }
            AGE_PATTERN.find(lower)?.value?.let { params["age_group"] = it }
        }

        if (params.isEmpty()) params["raw_query"] = query
        params
    }
}
