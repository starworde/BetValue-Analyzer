package com.soliano.betvalueanalyzer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCleanupGuardTest {
    @Test
    fun `active source code no longer exposes old betting repository names`() {
        val sourceText = mainKotlinFiles()
            .filterNot { it.invariantSeparatorsPath.endsWith("remote/PublicSportsApiService.kt") }
            .joinToString("\n") { it.readText() }

        val forbiddenTokens = listOf(
            "OddsRepository",
            "OddsSyncWorker",
            "OddsSyncResult",
            "OddsLiveResult",
            "automaticValueBets",
            "automaticBets",
            "valueBets",
            "Betclic",
            "Non exposé à l’app",
        )

        forbiddenTokens.forEach { token ->
            assertFalse("Ancien reste actif détecté: $token", sourceText.contains(token))
        }
    }

    @Test
    fun `sports analysis repository is the active public sports facade`() {
        val repository = File("src/main/java/com/soliano/betvalueanalyzer/data/SportsAnalysisRepository.kt")
        val worker = File("src/main/java/com/soliano/betvalueanalyzer/sync/SportsSyncWorker.kt")

        assertTrue("SportsAnalysisRepository.kt doit exister", repository.exists())
        assertTrue("SportsSyncWorker.kt doit exister", worker.exists())
        assertTrue(repository.readText().contains("class SportsAnalysisRepository"))
        assertTrue(worker.readText().contains("class SportsSyncWorker"))
    }

    private fun mainKotlinFiles(): List<File> =
        File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private val File.invariantSeparatorsPath: String
        get() = path.replace('\\', '/')
}
