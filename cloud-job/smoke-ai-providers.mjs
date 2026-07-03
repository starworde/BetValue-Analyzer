import { writeFile } from "node:fs/promises";
import { __testOnlyMultiAi } from "./multi-ai.mjs";

const DEFAULT_SPORTS = [
  "soccer",
  "rugby",
  "tennis",
  "cycling",
  "racing",
  "basketball",
  "baseball",
  "volleyball",
  "handball",
  "golf",
  "mma",
  "football",
];

const startedAt = Date.now();
const providers = filterProviders(__testOnlyMultiAi.configuredFreeProviders());
const sports = parseCsv(process.env.AI_SMOKE_SPORTS || DEFAULT_SPORTS.join(","));
const failOnProviderError = process.env.AI_SMOKE_FAIL_ON_PROVIDER_ERROR === "1";
const maxProviders = Number(process.env.AI_SMOKE_MAX_PROVIDERS || 0);
const selectedProviders = maxProviders > 0 ? providers.slice(0, maxProviders) : providers;
const reportPath = process.env.AI_SMOKE_REPORT_PATH || "ai-smoke-report.json";
const delayMs = Number(process.env.AI_SMOKE_DELAY_MS || 1500);
const quotaRetries = Number(process.env.AI_SMOKE_QUOTA_RETRIES || 1);
const quotaRetryDelayMs = Number(process.env.AI_SMOKE_QUOTA_RETRY_DELAY_MS || 20000);

const report = {
  status: "success",
  generatedAt: new Date().toISOString(),
  durationMs: 0,
  sports,
  providerCount: selectedProviders.length,
  providersConfigured: providers.map(providerSummary),
  providersTested: selectedProviders.map(providerSummary),
  skippedProviders: skippedConfiguredProviders(),
  results: [],
  summary: {},
};

if (selectedProviders.length === 0) {
  report.status = "skipped";
  report.summary = {
    ok: 0,
    skipped: report.skippedProviders.length,
    error: 0,
    quota: 0,
    note: "Aucun fournisseur IA avec secret/backend disponible dans cet environnement.",
  };
  await finish(report);
  process.exit(0);
}

for (const sport of sports) {
  for (const provider of selectedProviders) {
    const dossier = sampleDossierForSport(sport);
    const started = Date.now();
    try {
      const response = await callProviderWithSmokeRetry(provider, dossier);
      const validation = validateAiResponse(response);
      report.results.push({
        status: validation.ok ? "ok" : "error",
        provider: providerSummary(provider),
        sport,
        durationMs: Date.now() - started,
        confidence: response.confianceIA,
        scenarioPrincipal: compact(response.scenarioPrincipal, 180),
        scoreProbable: compact(response.scoreProbable, 120),
        missing: validation.missing,
      });
    } catch (error) {
      const status = Number(error?.status || 0);
      report.results.push({
        status: status === 429 || /quota|rate limit|too many requests|resource_exhausted/i.test(error?.message || "") ? "quota" : "error",
        provider: providerSummary(provider),
        sport,
        durationMs: Date.now() - started,
        error: compact(error?.message || String(error), 320),
      });
    }
    await sleep(delayMs);
  }
}

const counts = countBy(report.results.map((result) => result.status));
report.summary = {
  ok: counts.ok || 0,
  skipped: report.skippedProviders.length,
  error: counts.error || 0,
  quota: counts.quota || 0,
};
if ((report.summary.error > 0 || report.summary.quota > 0) && failOnProviderError) {
  report.status = "failed";
}
await finish(report);
if (report.status === "failed") process.exit(1);

function filterProviders(allProviders) {
  const requested = parseCsv(process.env.AI_SMOKE_PROVIDERS || "");
  if (requested.length === 0) return allProviders;
  const wanted = new Set(requested.map((value) => value.toLowerCase()));
  return allProviders.filter((provider) => {
    const keys = [
      provider.id,
      provider.label,
      provider.family,
      provider.kind,
      provider.model,
    ].map((value) => String(value || "").toLowerCase());
    return keys.some((key) => wanted.has(key) || [...wanted].some((wantedKey) => key.includes(wantedKey)));
  });
}

function skippedConfiguredProviders() {
  const skipped = [];
  if (!process.env.GEMINI_API_KEY) skipped.push({ id: "gemini", reason: "GEMINI_API_KEY absent" });
  else if (process.env.GEMINI_ENABLED !== "1") skipped.push({ id: "gemini", reason: "GEMINI_ENABLED different de 1" });
  if (!process.env.ANTHROPIC_API_KEY && !process.env.CLAUDE_API_KEY) skipped.push({ id: "claude", reason: "ANTHROPIC_API_KEY/CLAUDE_API_KEY absent" });
  if (!process.env.GROQ_API_KEY) skipped.push({ id: "groq", reason: "GROQ_API_KEY absent" });
  if (!process.env.MISTRAL_API_KEY) skipped.push({ id: "mistral-api", reason: "MISTRAL_API_KEY absent" });
  if (!process.env.OPENROUTER_API_KEY) skipped.push({ id: "openrouter", reason: "OPENROUTER_API_KEY absent" });
  return skipped;
}

function providerSummary(provider) {
  return {
    id: provider.id,
    label: provider.label,
    family: provider.family,
    kind: provider.kind,
    model: provider.model,
  };
}

function validateAiResponse(response) {
  const required = [
    "lectureRapide",
    "avantageFavori",
    "dangerOutsider",
    "matchUpCle",
    "scenarioPrincipal",
    "scenarioAlternatif",
    "confianceIA",
  ];
  const missing = required.filter((key) => !String(response?.[key] || "").trim());
  return { ok: missing.length === 0, missing };
}

async function callProviderWithSmokeRetry(provider, dossier) {
  let lastError = null;
  for (let attempt = 0; attempt <= quotaRetries; attempt += 1) {
    try {
      return await __testOnlyMultiAi.callProvider(provider, dossier);
    } catch (error) {
      lastError = error;
      if (!isQuotaLikeError(error) || attempt >= quotaRetries) break;
      await sleep(quotaRetryDelayMs * (attempt + 1));
    }
  }
  throw lastError;
}

function isQuotaLikeError(error) {
  return Number(error?.status || 0) === 429
    || /quota|rate limit|too many requests|resource_exhausted/i.test(error?.message || "");
}

function sampleDossierForSport(sport) {
  const base = {
    sport,
    date: new Date(Date.now() + 36 * 60 * 60 * 1000).toISOString(),
    sourcesUtilisees: "Smoke test interne BetValue : dossier synthétique sans actualité réelle.",
    donneesManquantes: ["news réelles", "compositions officielles", "météo confirmée"],
    analyseLocaleInitiale: {
      selection: "Scénario test",
      marche: "Analyse sportive",
      confiance: 55,
      risque: "Moyen",
      scenario: "à contextualiser",
    },
  };
  const examples = {
    soccer: {
      competition: "Smoke Football",
      evenement: "France — Suède",
      participants: ["France", "Suède"],
      statistiquesRecentes: "France : 6 matchs, 2.0 buts marqués/match, pressing haut. Suède : transitions rapides, 1.2 buts encaissés/match.",
      blessures: "Aucune blessure confirmée dans le dossier test.",
      compositions: "Compositions probables non confirmées.",
      fatigueDeplacementCalendrier: "France sort d’un calendrier dense ; Suède plus fraîche.",
    },
    rugby: {
      competition: "Smoke Rugby",
      evenement: "Toulouse — Montpellier",
      participants: ["Toulouse", "Montpellier"],
      statistiquesRecentes: "Toulouse fort en occupation et touche. Montpellier dangereux si discipline adverse faible.",
      surfaceTerrainCircuitParcours: "Terrain neutre dans le dossier test.",
    },
    tennis: {
      competition: "Smoke Tennis ATP",
      evenement: "Joueur A — Joueur B",
      participants: ["Joueur A", "Joueur B"],
      statistiquesRecentes: "Joueur A : 72% jeux de service tenus, fatigue récente. Joueur B : bon retour sur seconde balle.",
      surfaceTerrainCircuitParcours: "Gazon, conditions rapides.",
      confrontationsDirectes: "Face-à-face : 1-1, aucun sur gazon.",
    },
    cycling: {
      competition: "Smoke Cyclisme",
      evenement: "Étape vallonnée",
      participants: ["Favori grimpeur", "Outsider puncheur"],
      statistiquesRecentes: "Profil vallonné, arrivée en bosse, équipe du favori solide mais vent latéral possible.",
      surfaceTerrainCircuitParcours: "Parcours vallonné, météo à confirmer.",
    },
    racing: {
      competition: "Smoke F1",
      evenement: "Grand Prix test",
      participants: ["Pilote A", "Pilote B"],
      statistiquesRecentes: "Pilote A rapide sur relais longs. Pilote B meilleur en qualifications, pneus à surveiller.",
      surfaceTerrainCircuitParcours: "Circuit urbain, dépassements difficiles.",
    },
    basketball: {
      competition: "Smoke Basket",
      evenement: "Équipe A — Équipe B",
      participants: ["Équipe A", "Équipe B"],
      statistiquesRecentes: "Équipe A rythme élevé, forte au rebond. Équipe B adresse extérieure irrégulière.",
      fatigueDeplacementCalendrier: "Équipe B en back-to-back.",
    },
    baseball: {
      competition: "Smoke Baseball",
      evenement: "Team A — Team B",
      participants: ["Team A", "Team B"],
      statistiquesRecentes: "Team A lanceur partant stable. Team B bullpen fatigué, attaque en hausse sur 7 jours.",
    },
    volleyball: {
      competition: "Smoke Volley",
      evenement: "Équipe A — Équipe B",
      participants: ["Équipe A", "Équipe B"],
      statistiquesRecentes: "Équipe A forte au service. Équipe B réception fragile mais blocs efficaces.",
    },
    handball: {
      competition: "Smoke Handball",
      evenement: "Club A — Club B",
      participants: ["Club A", "Club B"],
      statistiquesRecentes: "Club A gardien en forme, montée de balle rapide. Club B exclusions fréquentes.",
    },
    golf: {
      competition: "Smoke Golf",
      evenement: "Tournoi test",
      participants: ["Golfeur A", "Golfeur B"],
      statistiquesRecentes: "Golfeur A putting solide. Golfeur B précis au drive, météo venteuse possible.",
      surfaceTerrainCircuitParcours: "Parcours long, rough épais.",
    },
    mma: {
      competition: "Smoke MMA",
      evenement: "Combattant A — Combattant B",
      participants: ["Combattant A", "Combattant B"],
      statistiquesRecentes: "Combattant A lutte dominante. Combattant B striking plus dangereux mais cardio incertain.",
    },
    football: {
      competition: "Smoke Football US",
      evenement: "Team A — Team B",
      participants: ["Team A", "Team B"],
      statistiquesRecentes: "Team A pass rush fort. Team B quarterback mobile, secondary diminuée.",
    },
  };
  return { ...base, ...(examples[sport] || examples.soccer) };
}

function parseCsv(value) {
  return String(value || "")
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function countBy(values) {
  return values.reduce((accumulator, value) => {
    accumulator[value] = (accumulator[value] || 0) + 1;
    return accumulator;
  }, {});
}

function compact(value, max) {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, max);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function finish(value) {
  value.durationMs = Date.now() - startedAt;
  await writeFile(reportPath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(value, null, 2));
}
