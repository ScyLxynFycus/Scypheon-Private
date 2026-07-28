package com.scypheon.sdk.core.agent.tool

/**
 * Tool: The atomic unit of capability in Scypheon.
 * Modeled after the "Claude Code" design pattern for high-fidelity agentic orchestration.
 */
interface Tool {
    /**
     * The primary identifier for the tool.
     */
    val name: String

    /**
     * Search keywords for legacy tool matching.
     */
    val keywords: List<String> get() = emptyList()

    /**
     * Safety flag: Does this tool involve clinical/medical data?
     */
    val isMedical: Boolean get() = false

    /**
     * Resource requirements for this tool.
     */
    val constraintProfile: ConstraintProfile get() = ConstraintProfile()

    /**
     * Optional aliases for backwards compatibility.
     */
    val aliases: List<String> get() = emptyList()

    /**
     * A clear description for the LLM to understand when and how to use this tool.
     */
    val triggerDescription: String; val description: String

    /**
     * JSON Schema defining the input parameters.
     */
    val inputSchema: String

    /**
     * Executes the tool logic.
     */
    suspend fun call(
        args: Map<String, Any?>,
        context: ExecutionContext
    ): ToolResult

    /**
     * Safety check: Does this tool perform destructive actions (delete/overwrite)?
     */
    fun isDestructive(args: Map<String, Any?>): Boolean = false

    /**
     * Read-only check: Does this tool only read data without side effects?
     * Read-only tools are always concurrency-safe and can be batched for parallel execution.
     * Claude Code pattern: isReadOnly() + isConcurrencySafe() determine execution strategy.
     */
    fun isReadOnly(args: Map<String, Any?>): Boolean = false

    /**
     * Concurrency check: Can multiple instances of this tool run in parallel?
     * Defaults to the value of isReadOnly()  Eread-only tools are inherently safe.
     */
    fun isConcurrencySafe(args: Map<String, Any?>): Boolean = isReadOnly(args)

    /**
     * Resilience check: Is this tool available in the current environment?
     */
    fun isEnabled(): Boolean = true

    /**
     * Privacy check: Does the user need to authorize this specific call?
     */
    suspend fun checkPermissions(
        args: Map<String, Any?>,
        context: ExecutionContext
    ): Boolean = true

    /**
     * Validates the input arguments.
     */
    fun validate(args: Map<String, Any?>): ValidationResult = ValidationResult(true)

    /**
     * User-friendly description of what the tool is currently doing.
     * Used for UI progress updates.
     */
    fun getActivityDescription(args: Map<String, Any?>): String? = null

    data class ConstraintProfile(
        val powerCost: Int = 1,
        val thermalImpact: Int = 1,
        val requiresNetwork: Boolean = false
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )
}

