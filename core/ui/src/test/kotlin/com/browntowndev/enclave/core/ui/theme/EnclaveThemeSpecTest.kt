package com.browntowndev.enclave.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnclaveThemeSpecTest {
    @Test
    fun factory_resolvesRequestedFamilyAndDarkFlag() {
        val neoDark = EnclaveThemeSpecFactory.resolve(EnclaveThemeId.NeoBrutalist, dark = true)
        val tonalLight = EnclaveThemeSpecFactory.resolve(EnclaveThemeId.Tonal, dark = false)

        assertEquals(EnclaveThemeId.NeoBrutalist, neoDark.id)
        assertTrue(neoDark.dark)
        assertEquals(EnclaveThemeId.Tonal, tonalLight.id)
        assertFalse(tonalLight.dark)
    }

    @Test
    fun neo_usesHardShadowAndNeoGeometry_tonalDoesNot() {
        val neo = NeoBrutalistSpec.resolve(dark = true)
        val tonal = TonalSpec.resolve(dark = true)

        assertTrue(neo.elevations.useHardShadow)
        assertEquals(EnclaveNeoTokens.cornerRadius, neo.shapes.panelCorner)
        assertEquals(EnclaveNeoTokens.borderWidth, neo.shapes.borderWidth)
        assertFalse(tonal.elevations.useHardShadow)
        assertEquals(0.dp, tonal.elevations.buttonShadowOffset)
        assertEquals(12.dp, tonal.shapes.panelCorner)
        assertEquals(1.dp, tonal.shapes.borderWidth)
    }

    @Test
    fun neoDark_keepsPeachAccent_tonalLightKeepsMaterialAccent() {
        assertEquals(PeachAccent, NeoBrutalistSpec.resolve(dark = true).colors.accent)
        assertEquals(PurpleLightPrimary, TonalSpec.resolve(dark = false).colors.accent)
    }

    @Test
    fun glass_tonalEnabledWithTranslucentTint_neoDisabled() {
        val tonalDark = TonalSpec.resolve(dark = true)
        val tonalLight = TonalSpec.resolve(dark = false)
        val neoDark = NeoBrutalistSpec.resolve(dark = true)
        val neoLight = NeoBrutalistSpec.resolve(dark = false)

        assertTrue(tonalDark.glass.enabled)
        assertTrue(tonalLight.glass.enabled)
        assertTrue(tonalDark.glass.tint.alpha < 1f)
        assertTrue(tonalLight.glass.tint.alpha < 1f)
        assertTrue(tonalDark.glass.blurRadius > 0.dp)
        assertFalse(neoDark.glass.enabled)
        assertFalse(neoLight.glass.enabled)
        assertEquals(0.dp, neoDark.glass.blurRadius)
    }

    @Test
    fun colorMode_resolvesDarkFromSystemOnlyInSystemMode() {
        assertTrue(EnclaveColorMode.System.resolveDark(isSystemDark = true))
        assertFalse(EnclaveColorMode.System.resolveDark(isSystemDark = false))
        assertTrue(EnclaveColorMode.Dark.resolveDark(isSystemDark = false))
        assertFalse(EnclaveColorMode.Light.resolveDark(isSystemDark = true))
    }
}
