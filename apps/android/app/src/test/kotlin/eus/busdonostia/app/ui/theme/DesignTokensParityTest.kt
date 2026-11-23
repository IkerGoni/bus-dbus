package eus.busdonostia.app.ui.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Paridad estricta `packages/contracts/design-tokens.json` ↔ código generado. Detecta una
 * plantilla del generador desalineada con el JSON (la ruta llega por `-Ddesign.tokens`).
 */
class DesignTokensParityTest {
    private val root: JsonObject = Json.parseToJsonElement(
        checkNotNull(System.getProperty("design.tokens")) { "Falta -Ddesign.tokens" }.let { File(it).readText() },
    ).jsonObject

    private fun flatten(prefix: String, source: JsonObject): Map<String, JsonPrimitive> = buildMap {
        source.forEach { (key, value) ->
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is JsonObject -> putAll(flatten(path, value))
                is JsonPrimitive -> put(path, value)
                else -> Unit
            }
        }
    }

    /** `navigation.x`/`transitions.x`/`microInteractions.x` → `x`; `livePulse.x` → `livePulseX`, etc. */
    private fun motionFieldName(path: String): String {
        val parts = path.split(".")
        return when (parts[0]) {
            "livePulse", "stagger" -> parts[0] + parts[1].replaceFirstChar { it.uppercase() }
            else -> parts[1]
        }
    }

    @Test
    fun `motion json coincide con FULL`() {
        val leaves = flatten("", root.getValue("motion").jsonObject)
        val fields = MotionTokens.ResolvedMotionTokens::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) }
            .associateBy { it.name }
        val checked = mutableSetOf<String>()

        leaves.forEach { (path, value) ->
            if (path == "maxBlockingDurationMs") return@forEach
            val fieldName = motionFieldName(path)
            val field = checkNotNull(fields[fieldName]) { "El JSON tiene '$path' y ResolvedMotionTokens no tiene '$fieldName'" }
            field.isAccessible = true
            val expected = (field.get(MotionTokens.ResolvedMotionTokens.FULL) as Number).toFloat()
            assertEquals("Desalineado: $path", value.float, expected, 0f)
            checked += fieldName
        }

        assertEquals("El JSON y ResolvedMotionTokens deben cubrir los mismos campos", fields.keys, checked)
        assertEquals(
            MotionTokens.MAX_BLOCKING_DURATION_MS,
            root.getValue("motion").jsonObject.getValue("maxBlockingDurationMs").jsonPrimitive.int,
        )
    }

    @Test
    fun `style json coincide con DesignTokens`() {
        val leaves = flatten("", root.getValue("style").jsonObject)
        // `Dp` es value class: los getters están manglados en JVM, comparamos los valores directamente.
        val actual = mapOf(
            "spacingDp.xxs" to DesignTokens.spacingXxs.value,
            "spacingDp.xs" to DesignTokens.spacingXs.value,
            "spacingDp.sm" to DesignTokens.spacingSm.value,
            "spacingDp.md" to DesignTokens.spacingMd.value,
            "spacingDp.lg" to DesignTokens.spacingLg.value,
            "spacingDp.xl" to DesignTokens.spacingXl.value,
            "cornerRadiusDp.chip" to DesignTokens.cornerRadiusChip.value,
            "cornerRadiusDp.banner" to DesignTokens.cornerRadiusBanner.value,
            "cornerRadiusDp.card" to DesignTokens.cornerRadiusCard.value,
            "cornerRadiusDp.pill" to DesignTokens.cornerRadiusPill.value,
            "componentsDp.timelineTerminalNode" to DesignTokens.timelineTerminalNode.value,
            "componentsDp.timelineIntermediateNode" to DesignTokens.timelineIntermediateNode.value,
            "componentsDp.liveSignalDot" to DesignTokens.liveSignalDotSize.value,
            "componentsDp.lineBadgeMinWidth" to DesignTokens.lineBadgeMinWidth.value,
            "componentsDp.lineBadgeSize" to DesignTokens.lineBadgeSize.value,
            "componentsDp.chipVisualHeight" to DesignTokens.chipVisualHeight.value,
            "componentsDp.touchTargetMin" to DesignTokens.touchTargetMin.value,
            "componentsDp.headerMinHeight" to DesignTokens.headerMinHeight.value,
            "componentsDp.skeletonRowHeight" to DesignTokens.skeletonRowHeight.value,
            "componentsDp.iconSmall" to DesignTokens.iconSmall.value,
            "componentsDp.iconLarge" to DesignTokens.iconLarge.value,
            "componentsDp.lineBadgeChipSize" to DesignTokens.lineBadgeChipSize.value,
            "componentsDp.progressIndicatorSize" to DesignTokens.progressIndicatorSize.value,
            "componentsDp.progressIndicatorStroke" to DesignTokens.progressIndicatorStroke.value,
            "componentsDp.arrivalRowMinHeight" to DesignTokens.arrivalRowMinHeight.value,
            "componentsDp.arrivalDividerInset" to DesignTokens.arrivalDividerInset.value,
            "componentsDp.stateMessageVerticalPadding" to DesignTokens.stateMessageVerticalPadding.value,
        )
        assertEquals("El JSON y DesignTokens deben cubrir los mismos estilos", actual.keys, leaves.keys)
        leaves.forEach { (path, value) ->
            assertEquals("Desalineado: $path", value.float, actual.getValue(path), 0f)
        }
    }
}
