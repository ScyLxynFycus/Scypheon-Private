package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.BaseTool
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.intelligence.graph.WebDiscoveryProvider
import com.scypheon.sdk.core.safety.helios.ToolAuthorizationGateway
import timber.log.Timber
import javax.inject.Inject

class OpenFdaTool @Inject constructor(
    private val webProvider: WebDiscoveryProvider,
    private val authGateway: ToolAuthorizationGateway,
    private val pharmacopeiaDao: com.scypheon.sdk.core.humanitarian.medical.PharmacopeiaDao
) : BaseTool() {
    override val name: String = "discover_openfda"
    override val description: String = "Fetches official drug safety data and adverse event reports from OpenFDA/WHO. Requires explicit user authorization."
    override val inputSchema: String = """
        {
            "type": "object",
            "properties": {
                "drug": { "type": "string", "description": "The name of the medicinal product" }
            },
            "required": ["drug"]
        }
    """.trimIndent()

    override suspend fun checkPermissions(args: Map<String, Any?>, context: ExecutionContext): Boolean {
        // [HELIOS L4] Risk-Tiered Authorization
        val drug = args["drug"] as? String ?: ""
        val result = authGateway.authorize(name, mapOf("drug" to drug))
        
        // If it needs user consent, for now we assume the UI handles the [AWAITING_APPROVAL] flag.
        // But since checkPermissions is a blocking boolean check in ToolMesh, we return true if authorized,
        // or false if it needs consent (and we expect the orchestrator to handle the pause).
        
        // NOTE: In this architecture, if needsUserConsent is true, we return false to block immediate execution.
        return result.isAuthorized && !result.needsUserConsent
    }

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val drug = args["drug"] as? String ?: return ToolResult.Error("Missing drug name", null, 0)
        val start = System.currentTimeMillis()
        
        // 1. OFFLINE FIRST: Check local PharmacopeiaDao
        try {
            val localEntry = pharmacopeiaDao.getByDrugName(drug) ?: pharmacopeiaDao.getEntryByGenericName(drug)
            
            if (localEntry != null) {
                Timber.i("💊 [OFFLINE_FIRST] Data for $drug found in local Pharmacopeia. Skipping OpenFDA.")
                return ToolResult.Success(
                    data = "[LOCAL_DATA] $drug: ${localEntry.dosage}\nIndikasi: ${localEntry.indications}", 
                    latencyMs = System.currentTimeMillis() - start,
                    metadata = mapOf("source" to "Local Offline Database")
                )
            }
        } catch (e: Exception) {
            Timber.w("Offline check failed, proceeding to network if allowed.")
        }

        // 2. Network Check
        if (!context.allowNetwork) {
            return ToolResult.Error(
                "Offline Mode Active: No local data found for '$drug' and internet access is disabled in settings.",
                null,
                System.currentTimeMillis() - start
            )
        }

        // 3. ONLINE FALLBACK
        return try {
            val summary = webProvider.discoverOpenFDA(drug)
            if (summary != null) {
                ToolResult.Success(data = summary, latencyMs = System.currentTimeMillis() - start, metadata = mapOf("source" to "OpenFDA (Online)"))
            } else {
                ToolResult.Error("No data found on OpenFDA for: $drug", null, System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            ToolResult.Error("OpenFDA query failed: ${e.message}", e, System.currentTimeMillis() - start)
        }
    }
}
