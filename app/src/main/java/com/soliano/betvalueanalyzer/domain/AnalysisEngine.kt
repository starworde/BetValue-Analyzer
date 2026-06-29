package com.soliano.betvalueanalyzer.domain

import kotlin.math.roundToInt

/**
 * Moteur MVP transparent fondé sur des règles. Il ne prétend pas prédire un résultat :
 * il transforme uniquement les signaux saisis par l'utilisateur en une estimation prudente.
 */
object AnalysisEngine {

    fun analyze(input: ManualAnalysisInput): AnalysisResult {
        require(input.odds > 1.0) { "La cote doit être supérieure à 1,00." }
        require(input.participantA.isNotBlank() && input.participantB.isNotBlank()) {
            "Les participants sont obligatoires."
        }

        val profile = profileFor(input.sport)
        val implied = (1.0 / input.odds).coerceIn(0.01, 0.99)
        val formGap = (input.formA - input.formB) / 100.0
        val homeBoost = if (input.homeAdvantage) profile.homeImpact else 0.0
        val rawEstimate = 0.50 + (formGap * profile.formImpact) + homeBoost

        // Les données faibles et l'incertitude ramènent volontairement l'estimation vers 50 %.
        val qualityFactor = 0.35 + (input.dataQuality.coerceIn(0, 100) / 100.0 * 0.65)
        val certaintyFactor = 1.0 - (input.uncertainty.coerceIn(0, 100) / 100.0 * 0.55)
        val estimated = (0.50 + (rawEstimate - 0.50) * qualityFactor * certaintyFactor)
            .coerceIn(0.08, 0.92)

        val edge = estimated - implied
        val expectedValue = estimated * input.odds - 1.0
        val probabilityPoints = estimated * 55.0
        val valuePoints = (edge * 80.0).coerceIn(-10.0, 20.0)
        val qualityPoints = input.dataQuality.coerceIn(0, 100) / 100.0 * 20.0
        val certaintyPoints = (100 - input.uncertainty.coerceIn(0, 100)) / 100.0 * 15.0
        val volatilityPenalty = profile.volatility * 7.0
        val score = (probabilityPoints + valuePoints + qualityPoints + certaintyPoints - volatilityPenalty)
            .roundToInt()
            .coerceIn(0, 100)

        val risk = riskLevel(input, profile, estimated)
        val value = valueLevel(expectedValue)
        val category = category(score, input.dataQuality, edge, input.odds, risk)
        val positives = positiveArguments(input, profile, edge, expectedValue)
        val negatives = negativeArguments(input, profile, edge, expectedValue)
        val stake = suggestedStake(score, risk, expectedValue, input.dataQuality)
        val explanation = buildExplanation(input, score, risk, value, edge, category)

        return AnalysisResult(
            impliedProbability = implied,
            estimatedProbability = estimated,
            valueEdge = edge,
            expectedValue = expectedValue,
            confidenceScore = score,
            confidenceLabel = confidenceLabel(score),
            riskLevel = risk,
            valueLevel = value,
            category = category,
            positiveArguments = positives,
            negativeArguments = negatives,
            explanation = explanation,
            suggestedStakePercent = stake,
        )
    }

    fun profileFor(sport: String): SportProfile {
        val normalized = sport.lowercase()
        return when {
            normalized.contains("football") || normalized.contains("futsal") ->
                SportProfile("Sports collectifs", 0.34, 0.035, 0.35, "forme, domicile, absences et efficacité")
            normalized.contains("tennis") || normalized.contains("badminton") || normalized.contains("squash") || normalized.contains("padel") ->
                SportProfile("Duel individuel", 0.40, 0.0, 0.30, "surface, fatigue, service et face-à-face")
            normalized.contains("basket") || normalized.contains("handball") || normalized.contains("volley") ->
                SportProfile("Sports de score", 0.38, 0.025, 0.28, "rythme, efficacité, repos et absences")
            normalized.contains("rugby") || normalized.contains("hockey") || normalized.contains("baseball") ->
                SportProfile("Sports collectifs", 0.35, 0.03, 0.32, "forme, titulaires, calendrier et contexte")
            normalized.contains("formule") || normalized.contains("nascar") || normalized.contains("rallye") || normalized.contains("motogp") || normalized.contains("speedway") ->
                SportProfile("Sports mécaniques", 0.29, 0.0, 0.62, "qualifications, circuit, météo et fiabilité")
            normalized.contains("cycl") || normalized.contains("biathlon") || normalized.contains("ski") || normalized.contains("golf") ->
                SportProfile("Performance individuelle", 0.31, 0.0, 0.58, "profil, météo, fatigue et régularité")
            normalized.contains("mma") || normalized.contains("boxe") || normalized.contains("combat") ->
                SportProfile("Combat", 0.36, 0.0, 0.50, "style, cardio, allonge, opposition et inactivité")
            normalized.contains("counter") || normalized.contains("league of legends") || normalized.contains("dota") || normalized.contains("valorant") || normalized.contains("e-sport") ->
                SportProfile("E-sport", 0.37, 0.0, 0.42, "patch, maps, roster, forme et picks/bans")
            else -> SportProfile("Multi-sports", 0.32, 0.015, 0.45, "forme, contexte et qualité des données")
        }
    }

    private fun riskLevel(
        input: ManualAnalysisInput,
        profile: SportProfile,
        estimated: Double,
    ): String {
        val riskScore =
            (input.odds - 1.0).coerceAtMost(4.0) * 12.0 +
                input.uncertainty * 0.35 +
                profile.volatility * 25.0 +
                if (estimated < 0.45) 12.0 else 0.0
        return when {
            riskScore >= 58 -> "Élevé"
            riskScore >= 34 -> "Modéré"
            else -> "Faible"
        }
    }

    private fun valueLevel(expectedValue: Double): String = when {
        expectedValue >= 0.12 -> "Forte"
        expectedValue >= 0.04 -> "Positive"
        expectedValue >= -0.02 -> "Faible"
        else -> "Négative"
    }

    private fun category(
        score: Int,
        quality: Int,
        edge: Double,
        odds: Double,
        risk: String,
    ): String = when {
        quality < 40 -> "Données insuffisantes"
        score < 45 || edge < -0.08 -> "À éviter"
        edge >= 0.07 -> "Value bet"
        score >= 75 && risk != "Élevé" -> "Pari prudent"
        odds >= 3.25 -> "Risqué mais intéressant"
        else -> "À surveiller"
    }

    private fun positiveArguments(
        input: ManualAnalysisInput,
        profile: SportProfile,
        edge: Double,
        expectedValue: Double,
    ): List<String> = buildList {
        if (input.formA >= input.formB + 12) add("La forme saisie favorise nettement ${input.participantA}.")
        if (input.homeAdvantage) add("L'avantage du terrain est pris en compte.")
        if (input.dataQuality >= 75) add("La qualité déclarée des données est élevée.")
        if (edge >= 0.04) add("La probabilité estimée dépasse la probabilité implicite de la cote.")
        if (expectedValue > 0.0) add("L'espérance mathématique calculée est positive.")
        add("Profil ${profile.group.lowercase()} : priorité à ${profile.keySignal}.")
    }

    private fun negativeArguments(
        input: ManualAnalysisInput,
        profile: SportProfile,
        edge: Double,
        expectedValue: Double,
    ): List<String> = buildList {
        if (input.uncertainty >= 45) add("L'incertitude déclarée réduit fortement la confiance.")
        if (input.dataQuality < 65) add("Les données sont partielles ou de qualité moyenne.")
        if (input.formA < input.formB) add("La forme récente saisie favorise l'adversaire.")
        if (edge < 0.0) add("La cote implique une probabilité supérieure à l'estimation du modèle.")
        if (expectedValue < 0.0) add("L'espérance mathématique est négative à cette cote.")
        if (profile.volatility >= 0.50) add("Ce sport ou marché présente une volatilité naturellement élevée.")
        if (isEmpty()) add("Toute estimation sportive conserve un risque d'erreur et d'aléa.")
    }

    private fun suggestedStake(score: Int, risk: String, expectedValue: Double, quality: Int): Double {
        if (expectedValue <= 0.0 || score < 45 || quality < 40) return 0.0
        val base = when {
            score >= 90 -> 1.5
            score >= 75 -> 1.0
            score >= 60 -> 0.5
            else -> 0.25
        }
        return if (risk == "Élevé") base.coerceAtMost(0.25) else base.coerceAtMost(2.0)
    }

    private fun buildExplanation(
        input: ManualAnalysisInput,
        score: Int,
        risk: String,
        value: String,
        edge: Double,
        category: String,
    ): String {
        val edgeText = if (edge >= 0) "supérieure" else "inférieure"
        return "$category : l'estimation issue des signaux saisis est $edgeText à celle de la cote. " +
            "Confiance ${confidenceLabel(score).lowercase()}, risque ${risk.lowercase()} et value ${value.lowercase()}. " +
            "Vérifiez les informations de dernière minute avant ${input.eventDate.ifBlank { "l'événement" }}. " +
            "Cette analyse n'est ni une certitude ni une incitation à parier."
    }
}

