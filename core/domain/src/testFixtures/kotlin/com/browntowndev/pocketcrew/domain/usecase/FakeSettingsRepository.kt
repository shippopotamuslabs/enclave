package com.browntowndev.pocketcrew.domain.usecase
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.Assertions


/**
 * Fake implementation of SettingsRepository for testing.
 * Allows controlling the settings flow and verifying method calls.
 */
class FakeSettingsRepository : SettingsRepository {

    private val _settingsFlow = MutableStateFlow(SettingsData())
    override val settingsFlow: Flow<SettingsData> = _settingsFlow.asStateFlow()

    private var updateThemeCallCount = 0
    private var lastThemeValue: AppTheme? = null
    private var updateHapticPressCallCount = 0
    private var lastHapticPressValue: Boolean? = null
    private var updateHapticResponseCallCount = 0
    private var lastHapticResponseValue: Boolean? = null
    private var updateCustomizationEnabledCallCount = 0
    private var lastCustomizationEnabledValue: Boolean? = null
    private var updateSelectedPromptOptionCallCount = 0
    private var lastPromptOptionValue: SystemPromptOption? = null
    private var updateCustomPromptTextCallCount = 0
    private var lastCustomPromptTextValue: String? = null
    private var updateAllowMemoriesCallCount = 0
    private var lastAllowMemoriesValue: Boolean? = null

    // Methods to simulate errors
    var shouldThrowOnUpdateTheme = false
    var shouldThrowOnUpdateHapticPress = false
    var shouldThrowOnUpdateHapticResponse = false
    var shouldThrowOnUpdateCustomizationEnabled = false
    var shouldThrowOnUpdateSelectedPromptOption = false
    var shouldThrowOnUpdateCustomPromptText = false
    var shouldThrowOnUpdateAllowMemories = false

    override suspend fun updateTheme(theme: AppTheme) {
        if (shouldThrowOnUpdateTheme) throw RuntimeException("Simulated error")
        updateThemeCallCount++
        lastThemeValue = theme
        _settingsFlow.value = _settingsFlow.value.copy(theme = theme)
    }

    override suspend fun updateHapticPress(value: Boolean) {
        if (shouldThrowOnUpdateHapticPress) throw RuntimeException("Simulated error")
        updateHapticPressCallCount++
        lastHapticPressValue = value
        _settingsFlow.value = _settingsFlow.value.copy(hapticPress = value)
    }

    override suspend fun updateHapticResponse(value: Boolean) {
        if (shouldThrowOnUpdateHapticResponse) throw RuntimeException("Simulated error")
        updateHapticResponseCallCount++
        lastHapticResponseValue = value
        _settingsFlow.value = _settingsFlow.value.copy(hapticResponse = value)
    }

    override suspend fun updateCustomizationEnabled(enabled: Boolean) {
        if (shouldThrowOnUpdateCustomizationEnabled) throw RuntimeException("Simulated error")
        updateCustomizationEnabledCallCount++
        lastCustomizationEnabledValue = enabled
        _settingsFlow.value = _settingsFlow.value.copy(customizationEnabled = enabled)
    }

    override suspend fun updateSelectedPromptOption(option: SystemPromptOption) {
        if (shouldThrowOnUpdateSelectedPromptOption) throw RuntimeException("Simulated error")
        updateSelectedPromptOptionCallCount++
        lastPromptOptionValue = option
        _settingsFlow.value = _settingsFlow.value.copy(selectedPromptOption = option)
    }

    override suspend fun updateCustomPromptText(text: String) {
        if (shouldThrowOnUpdateCustomPromptText) throw RuntimeException("Simulated error")
        updateCustomPromptTextCallCount++
        lastCustomPromptTextValue = text
        _settingsFlow.value = _settingsFlow.value.copy(customPromptText = text)
    }

    override suspend fun updateAllowMemories(allowed: Boolean) {
        if (shouldThrowOnUpdateAllowMemories) throw RuntimeException("Simulated error")
        updateAllowMemoriesCallCount++
        lastAllowMemoriesValue = allowed
        _settingsFlow.value = _settingsFlow.value.copy(allowMemories = allowed)
    }

    // Verification methods
    fun verifyUpdateThemeCalled(times: Int, theme: AppTheme? = null) {
        assertEquals(times, updateThemeCallCount)
        if (theme != null) {
            assertEquals(theme, lastThemeValue)
        }
    }

    fun verifyUpdateHapticPressCalled(times: Int, value: Boolean? = null) {
        assertEquals(times, updateHapticPressCallCount)
        if (value != null) {
            assertEquals(value, lastHapticPressValue)
        }
    }

    fun verifyUpdateHapticResponseCalled(times: Int, value: Boolean? = null) {
        assertEquals(times, updateHapticResponseCallCount)
        if (value != null) {
            assertEquals(value, lastHapticResponseValue)
        }
    }

    fun verifyUpdateCustomizationEnabledCalled(times: Int, enabled: Boolean? = null) {
        assertEquals(times, updateCustomizationEnabledCallCount)
        if (enabled != null) {
            assertEquals(enabled, lastCustomizationEnabledValue)
        }
    }

    fun verifyUpdateSelectedPromptOptionCalled(times: Int, option: SystemPromptOption? = null) {
        assertEquals(times, updateSelectedPromptOptionCallCount)
        if (option != null) {
            assertEquals(option, lastPromptOptionValue)
        }
    }

    fun verifyUpdateCustomPromptTextCalled(times: Int, text: String? = null) {
        assertEquals(times, updateCustomPromptTextCallCount)
        if (text != null) {
            assertEquals(text, lastCustomPromptTextValue)
        }
    }

    fun verifyUpdateAllowMemoriesCalled(times: Int, allowed: Boolean? = null) {
        assertEquals(times, updateAllowMemoriesCallCount)
        if (allowed != null) {
            assertEquals(allowed, lastAllowMemoriesValue)
        }
    }

    fun resetCallCounts() {
        updateThemeCallCount = 0
        updateHapticPressCallCount = 0
        updateHapticResponseCallCount = 0
        updateCustomizationEnabledCallCount = 0
        updateSelectedPromptOptionCallCount = 0
        updateCustomPromptTextCallCount = 0
        updateAllowMemoriesCallCount = 0
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        Assertions.assertEquals(expected, actual)
    }
}
