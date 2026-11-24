package eus.busdonostia.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A5-11 / AGENTS.md §1 Nivel 2: Guardián de tokens de diseño y animaciones.
 *
 * Vigila que no se introduzcan nuevos literales `XX.dp` ni animaciones inline en `ui/`.
 * Utiliza un mecanismo ratchet: el número total de literales dp (excluyendo Nivel 3:
 * 0.dp, 1.dp hairline, 2.dp ajuste óptico) no puede superar la línea base registrada
 * y debe descender hacia 0 conforme se ejecute el ticket A5-11.
 */
class DesignTokensGuardTest {

    private val uiDir = File("src/main/kotlin/eus/busdonostia/app/ui")

    // Línea base tras auditoría 2026-11-23 y migración de SettingsScreen (165 - 12 = 153).
    // Cada refactorización del ticket A5-11 debe reducir este número.
    private val maxAllowedInlineDp = 153

    // Excepciones permitidas por Nivel 3 (AGENTS.md):
    // 0 = identidad matemática, 1 = hairline/borde sutil, 2 = ajuste óptico fino
    private val allowedInlineDpValues = setOf(0, 1, 2)

    private val dpRegex = Regex("""\b(\d+)\.dp\b""")
    private val inlineTweenRegex = Regex("""\btween\s*\(\s*\d+""")

    @Test
    fun noSeSuperaLineaBaseDeDpInline() {
        assertTrue("El directorio ui debe existir: ${uiDir.absolutePath}", uiDir.exists())

        val ktFiles = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { !it.path.contains("/ui/theme/") }
            .toList()

        assertTrue("Deben encontrarse archivos Kotlin en ui/", ktFiles.isNotEmpty())

        var totalViolations = 0
        val violationsByFile = mutableMapOf<String, Int>()

        for (file in ktFiles) {
            val lines = file.readLines()
            var fileViolations = 0
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEach
                dpRegex.findAll(line).forEach { match ->
                    val value = match.groupValues[1].toIntOrNull()
                    if (value != null && value !in allowedInlineDpValues) {
                        fileViolations++
                    }
                }
            }
            if (fileViolations > 0) {
                totalViolations += fileViolations
                violationsByFile[file.name] = fileViolations
            }
        }

        val breakdown = violationsByFile.entries.sortedByDescending { it.value }
            .joinToString("\n") { "  ${it.key}: ${it.value}" }

        assertTrue(
            "Regresión A5-11 detectada: $totalViolations literales XX.dp inline (máximo permitido: $maxAllowedInlineDp).\n" +
                "Desglose por archivo:\n$breakdown\n" +
                "Usa DesignTokens.spacing* o DesignTokens.* en su lugar.",
            totalViolations <= maxAllowedInlineDp,
        )
    }

    @Test
    fun ningunaAnimacionInlineFueraDeMotionTokens() {
        val ktFiles = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { !it.path.contains("/ui/theme/") }
            .toList()

        val filesWithInlineTween = mutableListOf<String>()

        for (file in ktFiles) {
            val content = file.readText()
            if (inlineTweenRegex.containsMatchIn(content)) {
                filesWithInlineTween.add(file.name)
            }
        }

        assertTrue(
            "AGENTS.md §1 Nivel 2: Prohibido declarar tween(ms) inline en composables. " +
                "Usa MotionTokens. Encontrado en: $filesWithInlineTween",
            filesWithInlineTween.isEmpty(),
        )
    }
}
