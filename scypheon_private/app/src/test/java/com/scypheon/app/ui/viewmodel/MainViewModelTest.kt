package com.scypheon.app.ui.viewmodel

import android.app.Application
import com.scypheon.app.ui.MainViewModel
import com.scypheon.app.data.repository.ScypheonRepository
import com.scypheon.sdk.core.humanitarian.education.LiveEnglishTutor
import com.scypheon.sdk.core.humanitarian.psychology.ReminiscenceCompanion
import com.scypheon.sdk.core.humanitarian.accessibility.DeafEnvironmentGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.GestureGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.KineticGuardian
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.memory.ContextSummarizer
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.memory.GraphMemoryManager
import com.scypheon.sdk.core.provision.ModelProvisioner
import com.scypheon.app.provision.HuggingFaceClient
import com.scypheon.sdk.core.security.AegisVault
import com.scypheon.sdk.core.gateway.SensoryHooks
import com.scypheon.app.data.local.HardwarePreferences
import com.scypheon.sdk.core.resilience.AegisThermalGovernor
import com.scypheon.sdk.core.resilience.ThermalLevel
import com.scypheon.sdk.core.safety.helios.PromptBuilder
import com.scypheon.sdk.core.safety.SafetyRouter
import com.scypheon.sdk.core.safety.helios.SafetyRuleSeeder
import com.scypheon.sdk.core.safety.helios.ToolAuthorizationGateway
import com.scypheon.sdk.live.core.domain.LiveStateMachine
import com.scypheon.sdk.live.safety.SafetyTrustLayer
import com.scypheon.sdk.core.live.ContinuousSpeechRecognizer
import com.scypheon.sdk.core.live.LiveVisionPipeline
import com.scypheon.sdk.core.live.LiveAudioPipeline
import com.scypheon.sdk.core.engine.InitializationState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val application = mockk<Application>(relaxed = true)
    private val repository = mockk<ScypheonRepository>(relaxed = true)
    private val chatSessionUseCase = mockk<com.scypheon.app.domain.usecase.ChatSessionUseCase>(relaxed = true)
    private val liveEnglishTutor = mockk<dagger.Lazy<LiveEnglishTutor>>(relaxed = true)
    private val reminiscenceCompanion = mockk<dagger.Lazy<ReminiscenceCompanion>>(relaxed = true)
    private val deafEnvironmentGuardian = mockk<dagger.Lazy<DeafEnvironmentGuardian>>(relaxed = true)
    private val gestureGuardian = mockk<dagger.Lazy<GestureGuardian>>(relaxed = true)
    private val kineticGuardian = mockk<dagger.Lazy<KineticGuardian>>(relaxed = true)
    private val blackBoxVault = mockk<BlackBoxVault>(relaxed = true)
    private val graphMemoryManager = mockk<GraphMemoryManager>(relaxed = true)
    private val modelManagementUseCase = mockk<com.scypheon.app.domain.usecase.ModelManagementUseCase>(relaxed = true)
    private val vault = mockk<AegisVault>(relaxed = true)
    private val sensoryHooks = mockk<SensoryHooks>(relaxed = true)
    private val hardwarePrefs = mockk<HardwarePreferences>(relaxed = true)
    private val thermalGovernor = mockk<AegisThermalGovernor>(relaxed = true)
    private val promptBuilder = mockk<PromptBuilder>(relaxed = true)
    private val safetyRouter = mockk<SafetyRouter>(relaxed = true)
    private val safetySeeder = mockk<SafetyRuleSeeder>(relaxed = true)
    private val toolGateway = mockk<ToolAuthorizationGateway>(relaxed = true)
    private val liveStateMachine = mockk<LiveStateMachine>(relaxed = true)
    private val safetyTrustLayer = mockk<SafetyTrustLayer>(relaxed = true)
    private val liveSpeechRecognizer = mockk<ContinuousSpeechRecognizer>(relaxed = true)
    private val liveVisionPipeline = mockk<LiveVisionPipeline>(relaxed = true)
    private val liveAudioPipeline = mockk<LiveAudioPipeline>(relaxed = true)
    private val intentRouter = mockk<com.scypheon.sdk.core.agent.SkillIntentRouter>(relaxed = true)
    private val getInferenceStreamUseCase = mockk<com.scypheon.app.domain.usecase.GetInferenceStreamUseCase>(relaxed = true)
    private val manageResourceReclamationUseCase = mockk<com.scypheon.app.domain.usecase.ManageResourceReclamationUseCase>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock AegisVoiceEngine constructor
        mockkConstructor(com.scypheon.sdk.core.voice.AegisVoiceEngine::class)
        every { anyConstructed<com.scypheon.sdk.core.voice.AegisVoiceEngine>().stop() } returns Unit

        // Mock required flow properties
        every { repository.processHealth } returns MutableStateFlow(true)
        every { repository.engineState } returns MutableStateFlow(InitializationState.Idle)
        every { repository.oomDiagnostic } returns MutableStateFlow(null)
        every { repository.vectorEngineState } returns MutableStateFlow(com.scypheon.sdk.core.memory.IVectorEngine.EngineState.Initializing)
        every { repository.memoryOptimizationActive } returns MutableStateFlow(false)

        every { thermalGovernor.thermalStatus } returns MutableStateFlow(ThermalLevel.NORMAL)
        every { thermalGovernor.currentTemperature } returns MutableStateFlow(30f)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `speech sanitization filters noise but preserves content`() = runTest {
        val vm = MainViewModel(
            application, repository, chatSessionUseCase, liveEnglishTutor, reminiscenceCompanion,
            deafEnvironmentGuardian, gestureGuardian, kineticGuardian, blackBoxVault,
            graphMemoryManager, modelManagementUseCase, vault, sensoryHooks, hardwarePrefs,
            thermalGovernor, promptBuilder, safetyRouter, safetySeeder, toolGateway,
            liveStateMachine, safetyTrustLayer, liveSpeechRecognizer, liveVisionPipeline,
            liveAudioPipeline, intentRouter, getInferenceStreamUseCase, manageResourceReclamationUseCase
        )

        // Cough/bracket removal
        assertEquals("Halo saya mau tanya", vm.sanitizeSpeechInput("Halo [cough] saya mau tanya [breathing]"))

        // Filler removal (English)
        assertEquals("Hello how are you", vm.sanitizeSpeechInput("Hello um uh how are you", Locale.ENGLISH))

        // Stutter removal
        assertEquals("ha", vm.sanitizeSpeechInput("ha ha ha", Locale.ENGLISH))

        // Empty after sanitization -> should not trigger inference
        assertEquals("", vm.sanitizeSpeechInput("[cough] um uh hmm"))
        
        // Preserve specific multilingual words
        assertEquals("um", vm.sanitizeSpeechInput("um", Locale("es"))) // "um" = mother in Spanish
        assertEquals("eh saya mau tanya", vm.sanitizeSpeechInput("eh saya mau tanya", Locale("id")))
    }

    @Test
    fun `hardCancelLiveSession terminates all components`() = runTest {
        val vm = MainViewModel(
            application, repository, chatSessionUseCase, liveEnglishTutor, reminiscenceCompanion,
            deafEnvironmentGuardian, gestureGuardian, kineticGuardian, blackBoxVault,
            graphMemoryManager, modelManagementUseCase, vault, sensoryHooks, hardwarePrefs,
            thermalGovernor, promptBuilder, safetyRouter, safetySeeder, toolGateway,
            liveStateMachine, safetyTrustLayer, liveSpeechRecognizer, liveVisionPipeline,
            liveAudioPipeline, intentRouter, getInferenceStreamUseCase, manageResourceReclamationUseCase
        )

        vm.startLiveMode()

        // Mock long-running inference and tts jobs
        vm.inferenceJob = launch(Dispatchers.IO) { delay(10000) }
        vm.ttsJob = launch(Dispatchers.IO) { delay(10000) }

        vm.hardCancelLiveSession()

        assertTrue(vm.inferenceJob!!.isCancelled)
        assertTrue(vm.ttsJob!!.isCancelled)
        verify { anyConstructed<com.scypheon.sdk.core.voice.AegisVoiceEngine>().stop() }
        verify { liveSpeechRecognizer.cancel() }
    }
}
