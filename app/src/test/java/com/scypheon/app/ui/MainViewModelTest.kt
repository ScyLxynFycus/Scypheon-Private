package com.scypheon.app.ui

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.scypheon.app.data.repository.ScypheonRepository
import com.scypheon.sdk.core.humanitarian.accessibility.DeafEnvironmentGuardian
import com.scypheon.sdk.core.humanitarian.accessibility.KineticGuardian
import com.scypheon.sdk.core.humanitarian.education.LiveEnglishTutor
import com.scypheon.sdk.core.humanitarian.psychology.ReminiscenceCompanion
import com.scypheon.sdk.core.memory.ContextSummarizer
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.memory.GraphMemoryManager
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.utils.Result
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var repository: ScypheonRepository
    private lateinit var liveEnglishTutor: LiveEnglishTutor
    private lateinit var reminiscenceCompanion: ReminiscenceCompanion
    private lateinit var deafEnvironmentGuardian: DeafEnvironmentGuardian
    private lateinit var gestureGuardian: com.scypheon.sdk.core.humanitarian.accessibility.GestureGuardian
    private lateinit var kineticGuardian: KineticGuardian
    private lateinit var blackBoxVault: BlackBoxVault
    private lateinit var contextSummarizer: ContextSummarizer
    private lateinit var dualMemoryManager: DualMemoryManager
    private lateinit var graphMemoryManager: GraphMemoryManager
    private lateinit var modelProvisioner: com.scypheon.sdk.core.provision.ModelProvisioner
    private lateinit var vault: com.scypheon.sdk.core.security.AegisVault
    private lateinit var sensoryHooks: com.scypheon.sdk.core.gateway.SensoryHooks
    private lateinit var promptGuard: com.scypheon.sdk.core.security.PromptGuard
    private lateinit var inferenceGovernor: com.scypheon.sdk.core.engine.InferenceGovernor
    private lateinit var circuitBreaker: com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
    private lateinit var manifestVerifier: com.scypheon.sdk.core.security.ModelManifestVerifier
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        liveEnglishTutor = mockk(relaxed = true)
        reminiscenceCompanion = mockk(relaxed = true)
        deafEnvironmentGuardian = mockk(relaxed = true)
        gestureGuardian = mockk(relaxed = true)
        kineticGuardian = mockk(relaxed = true)
        blackBoxVault = mockk(relaxed = true)
        contextSummarizer = mockk(relaxed = true)
        dualMemoryManager = mockk(relaxed = true)
        graphMemoryManager = mockk(relaxed = true)
        modelProvisioner = mockk(relaxed = true)
        vault = mockk(relaxed = true)
        sensoryHooks = mockk(relaxed = true)

        promptGuard = mockk(relaxed = true)
        inferenceGovernor = mockk(relaxed = true)
        circuitBreaker = mockk(relaxed = true)
        manifestVerifier = mockk(relaxed = true)


        viewModel = MainViewModel(
            application,
            repository,
            liveEnglishTutor,
            reminiscenceCompanion,
            deafEnvironmentGuardian,
            gestureGuardian,
            kineticGuardian,
            blackBoxVault,
            contextSummarizer,
            dualMemoryManager,
            graphMemoryManager,
            modelProvisioner,
            vault,
            sensoryHooks,
            promptGuard,
            inferenceGovernor,
            circuitBreaker,
            manifestVerifier
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }


    @Test
    fun `toggleFeature starts correct feature and updates state`() {
        // Arrange
        every { liveEnglishTutor.isListening } returns false
        every { reminiscenceCompanion.isListening } returns false
        every { deafEnvironmentGuardian.isListening } returns false


        // Act
        viewModel.toggleFeature("LiveEnglishTutor")

        // Assert
        assertEquals("LiveEnglishTutor", viewModel.uiState.value.activeFeature)
        verify(exactly = 1) { liveEnglishTutor.startListening() }
    }

}
