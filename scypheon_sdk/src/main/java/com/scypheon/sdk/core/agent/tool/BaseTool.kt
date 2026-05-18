package com.scypheon.sdk.core.agent.tool

/**
 * Base implementation of [Tool] to reduce boilerplate.
 * Implements the "Claude Code" pattern for pluggable agentic capabilities.
 */
abstract class BaseTool : Tool {
    override val aliases: List<String> = emptyList()
    override val keywords: List<String> = emptyList()
    override val isMedical: Boolean = false
    override val constraintProfile: Tool.ConstraintProfile = Tool.ConstraintProfile()
    
    override fun isDestructive(args: Map<String, Any?>): Boolean = false
    override fun isConcurrencySafe(args: Map<String, Any?>): Boolean = false
    
    override fun isEnabled(): Boolean = true
    
    override suspend fun checkPermissions(
        args: Map<String, Any?>,
        context: ExecutionContext
    ): Boolean = true
    
    override fun getActivityDescription(args: Map<String, Any?>): String? = null
}
