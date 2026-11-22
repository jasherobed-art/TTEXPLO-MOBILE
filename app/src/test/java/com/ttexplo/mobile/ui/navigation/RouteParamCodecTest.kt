package com.ttexplo.mobile.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteParamCodecTest {

    @Test
    fun roundTrip_preservesSpecialCharactersAccentsAndQuotes() {
        val inputs = listOf(
            "MINA 100% NORTE",
            "BANCO A/B + C?ref=1#2&ok",
            "Polvorín Nº 1 — Cañón",
            "1 1/4\" X 16\" (ALTO EXPLOSIVO)",
            "JUAN PÉREZ / TURNO 2 + 50%",
            "F-2026/05#001"
        )

        for (input in inputs) {
            val encoded = RouteParamCodec.encode(input)
            assertTrue("Debe tener prefijo b64~", encoded.startsWith("b64~"))
            assertFalse("No debe contener '/'", encoded.contains('/'))
            assertFalse("No debe contener '%'", encoded.contains('%'))
            assertFalse("No debe contener '+'", encoded.contains('+'))
            assertFalse("No debe contener '?'", encoded.contains('?'))
            assertFalse("No debe contener '#'", encoded.contains('#'))
            assertEquals(input, RouteParamCodec.decode(encoded))
        }
    }

    @Test
    fun encode_usesFallbackForBlankStrings() {
        assertEquals("-", RouteParamCodec.decode(RouteParamCodec.encode("   ")))
        assertEquals("1", RouteParamCodec.decode(RouteParamCodec.encode("", fallback = "1")))
    }

    @Test
    fun decode_handlesNullEmptyAndMalformedLegacyPercentWithoutCrash() {
        assertEquals("", RouteParamCodec.decode(null))
        assertEquals("", RouteParamCodec.decode(""))
        // Legacy plain string with trailing '%' that previously threw IllegalArgumentException
        assertEquals("MINA 100%", RouteParamCodec.decode("MINA 100%"))
        assertEquals("b64~!!!invalid!!!", RouteParamCodec.decode("b64~!!!invalid!!!"))
    }

    @Test
    fun screenCreateRoutes_produceSafeNavigationSegments() {
        val despachoRoute = Screen.DespachoScan.createRoute(
            vale = "V-100%/A",
            empresa = "MINERA DEL NORTE + SUR",
            lugar = "NIVEL 450?SECCION#2",
            transportista = "J. PÉREZ / U-12",
            turno = "",
            voladura = "VOL 100% #3",
            id = 42L
        )
        val pathPart = despachoRoute.substringBefore("?id=")
        val segments = pathPart.split("/")
        // despacho_scan + 6 encoded path arguments = 7 segments
        assertEquals(7, segments.size)
        assertFalse(pathPart.contains("%"))
        assertTrue(despachoRoute.endsWith("?id=42"))
        assertEquals("V-100%/A", RouteParamCodec.decode(segments[1]))
        assertEquals("1", RouteParamCodec.decode(segments[5]))

        val entradasRoute = Screen.EntradasScan.createRoute(
            prov = "EXSA / ORICA 100%",
            polv = "Polvorín Nº 2",
            fact = "FAC #99?A+B",
            id = 7L
        )
        val entradasSegments = entradasRoute.substringBefore("?id=").split("/")
        assertEquals(4, entradasSegments.size)
        assertFalse(entradasRoute.substringBefore("?id=").contains("%"))
        assertEquals("EXSA / ORICA 100%", RouteParamCodec.decode(entradasSegments[1]))
        assertEquals("Polvorín Nº 2", RouteParamCodec.decode(entradasSegments[2]))
        assertEquals("FAC #99?A+B", RouteParamCodec.decode(entradasSegments[3]))
    }
}
