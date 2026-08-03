package com.scypheon.sdk.core.agent.tool

import com.scypheon.sdk.core.agent.tool.executors.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolModule {

    @Binds
    @IntoMap
    @StringKey("calculate_basic")
    abstract fun bindCalculateBasic(tool: CalculateBasicTool): Tool

    @Binds
    @IntoMap
    @StringKey("request_clarification")
    abstract fun bindClarification(tool: ClarificationTool): Tool

    @Binds
    @IntoMap
    @StringKey("get_drug_dosage")
    abstract fun bindDrugDosage(tool: DrugDosageTool): Tool

    @Binds
    @IntoMap
    @StringKey("check_interaction")
    abstract fun bindDrugInteraction(tool: DrugInteractionTool): Tool

    @Binds
    @IntoMap
    @StringKey("get_first_aid")
    abstract fun bindFirstAid(tool: FirstAidTool): Tool

    @Binds
    @IntoMap
    @StringKey("format_dyslexia")
    abstract fun bindFormatDyslexia(tool: FormatDyslexiaTool): Tool

    @Binds
    @IntoMap
    @StringKey("general_query")
    abstract fun bindGeneralQuery(tool: GeneralQueryTool): Tool

    @Binds
    @IntoMap
    @StringKey("get_audit_log")
    abstract fun bindGetAuditLog(tool: GetAuditLogTool): Tool

    @Binds
    @IntoMap
    @StringKey("get_lesson_summary")
    abstract fun bindLessonSummary(tool: LessonSummaryTool): Tool

    @Binds
    @IntoMap
    @StringKey("start_english_tutor")
    abstract fun bindStartEnglishTutor(tool: StartEnglishTutorTool): Tool

    @Binds
    @IntoMap
    @StringKey("discover_wikipedia")
    abstract fun bindWikipedia(tool: WikipediaTool): Tool

    @Binds
    @IntoMap
    @StringKey("discover_duckduckgo")
    abstract fun bindDuckDuckGo(tool: DuckDuckGoTool): Tool

    @Binds
    @IntoMap
    @StringKey("discover_openfda")
    abstract fun bindOpenFda(tool: OpenFdaTool): Tool

    @Binds
    @IntoMap
    @StringKey("web_crawl_fandom")
    abstract fun bindFandom(tool: FandomTool): Tool

    // [v1.4.0-SAR] Critical: ClinicalDosageTool was missing from DI — core medical feature was dead code
    @Binds
    @IntoMap
    @StringKey("calculate_clinical_dosage")
    abstract fun bindClinicalDosage(tool: ClinicalDosageTool): Tool
}
