const AI_CACHE_TTL_MS = Number.parseInt(process.env.AI_CACHE_TTL_HOURS ?? "12", 10) * 60 * 60 * 1000;
const MAX_AI_EVENTS = Number.parseInt(process.env.MAX_AI_EVENTS ?? "18", 10);
const AI_TARGET_POOL_SIZE = Number.parseInt(process.env.AI_TARGET_POOL_SIZE ?? String(Math.max(MAX_AI_EVENTS * 8, 120)), 10);
const AI_HTTP_TIMEOUT_MS = Number.parseInt(process.env.AI_HTTP_TIMEOUT_MS ?? "22000", 10);
const MAX_AI_FIELD = 520;

const REQUIRED_JSON_KEYS = [
  "titreAnalyse",
  "lectureRapide",
  "avantageFavori",
  "dangerOutsider",
  "matchUpCle",
  "pointsQuiComptent",
  "scoreProbable",
  "confianceTexte",
  "favoriLogique",
  "dangerAdversaire",
  "reponseStrategique",
  "avantagesExploitables",
  "avantagesNeutralises",
  "scenarioPrincipal",
  "scenarioAlternatif",
  "pointsASurveiller",
  "confianceIA",
  "sourcesUtilisees",
  "donneesManquantes",
  "niveauRisque",
  "modeleUtilise",
  "erreursOuLimites",
];

const PAID_PROVIDERS_DISABLED = [
  "OpenAI API directe avec clé utilisateur",
  "Claude / Anthropic direct avec clé utilisateur",
  "xAI / Grok direct avec clé utilisateur",
  "Cohere direct avec clé utilisateur",
  "Toute clé payante, facturation obligatoire ou carte bancaire dans l’APK",
];

export function multiAiProviderDiagnostics() {
  const providers = configuredFreeProviders();
  return {
    configured: allFreeProviderNames(),
    freeEnabled: providers.map((provider) => provider.label),
    paidDisabled: PAID_PROVIDERS_DISABLED,
    mode: normalizedAiMode(),
  };
}

export async function enrichResultsWithMultiAi({
  results,
  eventsById,
  newsByEventId,
  diagnostics,
  db = null,
}) {
  diagnostics.aiErrors = Array.isArray(diagnostics.aiErrors) ? diagnostics.aiErrors : [];
  diagnostics.aiCalled = Number(diagnostics.aiCalled || 0);
  diagnostics.aiResponded = Number(diagnostics.aiResponded || 0);
  diagnostics.aiCacheHits = Number(diagnostics.aiCacheHits || 0);
  diagnostics.aiFusionCount = Number(diagnostics.aiFusionCount || 0);
  diagnostics.aiFallbackUsed = Number(diagnostics.aiFallbackUsed || 0);
  diagnostics.aiQuotaReached = Boolean(diagnostics.aiQuotaReached);
  const providerState = multiAiProviderDiagnostics();
  diagnostics.aiConfigured = providerState.configured;
  diagnostics.aiFreeEnabled = providerState.freeEnabled;
  diagnostics.aiPaidDisabled = providerState.paidDisabled;
  diagnostics.aiMode = providerState.mode;

  const providers = configuredFreeProviders();
  const aiRequests = db ? await loadAiRequests(db, diagnostics) : [];
  const aiBudget = providers.length > 0 ? MAX_AI_EVENTS : Math.min(MAX_AI_EVENTS, 60);
  const candidates = selectAiTargets(results, eventsById, diagnostics.aiMode, providers.length > 0, AI_TARGET_POOL_SIZE, aiRequests);
  diagnostics.aiRequestsMatched = candidates.filter((result) => findAiRequestForResult(result, eventsById.get(result.eventId), aiRequests)).length;
  const cached = db ? await loadAiCache(db, candidates, diagnostics) : new Map();
  const targets = candidates
    .filter((result) => !cached.has(result.eventId))
    .slice(0, aiBudget);
  console.log(`[ai] candidates=${candidates.length}, nouvelles=${targets.length}, cache=${cached.size}, fournisseurs=${providers.map((provider) => provider.label).join(", ") || "aucun"}`);
  const targetIds = new Set(targets.map((result) => result.eventId));
  const output = [];
  const fulfilledRequestIds = new Set();
  let targetIndex = 0;

  for (const result of results) {
    const event = eventsById.get(result.eventId);
    const newsContext = newsByEventId.get(result.eventId);
    const aiRequest = findAiRequestForResult(result, event, aiRequests);
    const cacheHit = cached.get(result.eventId);
    if (cacheHit) {
      diagnostics.aiCacheHits += 1;
      if (aiRequest && isUsableExternalAiCache(cacheHit.aiAnalysis)) fulfilledRequestIds.add(aiRequest.id);
      output.push({
        ...result,
        aiAnalysis: cacheHit.aiAnalysis,
        aiDiagnostic: cacheHit.aiDiagnostic,
      });
      continue;
    }
    if (!targetIds.has(result.eventId)) {
      output.push(result);
      continue;
    }
    targetIndex += 1;

    if (providers.length === 0) {
      diagnostics.aiFallbackUsed += 1;
      console.log(`[ai] ${targetIndex}/${targets.length} pré-analyse locale ${compactAiText(result.eventName || result.eventId, 90)}`);
      output.push(applyAiBundle(result, buildLocalAiFallback({
        result,
        event,
        newsContext,
        reason: "Analyse locale utilisée, IA externe indisponible.",
        providers,
        called: [],
      })));
      continue;
    }

    const dossier = buildAiInputDossier(result, event, newsContext);
    const selectedProviders = selectProvidersForMode(providers, diagnostics.aiMode, result, event);
    console.log(`[ai] ${targetIndex}/${targets.length} appel ${selectedProviders.map((provider) => provider.label).join(" + ")} · ${compactAiText(result.eventName || result.eventId, 90)}`);
    diagnostics.aiCalled += selectedProviders.length;
    const startedAt = Date.now();
    const providerResponses = [];
    const providerErrors = [];
    for (const provider of selectedProviders) {
      const callStart = Date.now();
      try {
        const response = await callProvider(provider, dossier);
        providerResponses.push({
          ...response,
          provider: provider.label,
          responseMs: Date.now() - callStart,
        });
        diagnostics.aiResponded += 1;
      } catch (error) {
        const message = compactAiText(error?.message || String(error), 260);
        providerErrors.push(`${provider.label}: ${message}`);
        diagnostics.aiErrors.push(`${provider.label}: ${message}`);
        if (isQuotaError(error)) diagnostics.aiQuotaReached = true;
      }
      await sleep(120);
    }

    if (providerResponses.length === 0) {
      diagnostics.aiFallbackUsed += 1;
      console.log(`[ai] ${targetIndex}/${targets.length} aucune réponse IA, pré-analyse locale`);
      output.push(applyAiBundle(result, buildLocalAiFallback({
        result,
        event,
        newsContext,
        reason: "Analyse locale utilisée, IA externe indisponible ou quota atteint.",
        providers,
        called: selectedProviders,
        errors: providerErrors,
        responseMs: Date.now() - startedAt,
      })));
      continue;
    }

    const fused = fuseAiResponses({
      result,
      event,
      newsContext,
      providerResponses,
      providerErrors,
      responseMs: Date.now() - startedAt,
      mode: diagnostics.aiMode,
      providers,
      called: selectedProviders,
    });
    if (providerResponses.length >= 2) diagnostics.aiFusionCount += 1;
    console.log(`[ai] ${targetIndex}/${targets.length} réponse(s) IA=${providerResponses.length}`);
    const enriched = applyAiBundle(result, fused);
    if (aiRequest && isUsableExternalAiCache(enriched.aiAnalysis)) fulfilledRequestIds.add(aiRequest.id);
    output.push(enriched);
  }

  if (db && fulfilledRequestIds.size > 0) {
    await markAiRequestsDone(db, fulfilledRequestIds, diagnostics);
  }

  return output;
}

export function buildAiInputDossier(result, event, newsContext) {
  const sport = result.sport || event?.sport || "";
  return {
    sport,
    competition: result.competition || event?.competition || "",
    date: new Date(result.eventDate || event?.eventDate || Date.now()).toISOString(),
    evenement: result.eventName || event?.eventName || "",
    participants: [result.homeTeam || event?.homeTeam, result.awayTeam || event?.awayTeam].filter(Boolean),
    classement: extractLines(result.statSummary, ["classement", "ranking", "record", "bilan"]),
    formeRecente: extractLines(result.statSummary, ["forme", "dynamique", "matchs", "victoire", "défaite"]),
    statistiquesRecentes: compactAiText(result.statSummary || event?.rawStats || "", 1500),
    confrontationsDirectes: extractLines(result.statSummary + "\n" + result.contextInsights, ["h2h", "face-à-face", "confrontation"]),
    blessures: extractLines(result.contextInsights + "\n" + result.negativeArguments, ["bless", "forfait", "injur"]),
    suspensions: extractLines(result.contextInsights + "\n" + result.negativeArguments, ["suspend", "carton"]),
    retours: extractLines(result.contextInsights + "\n" + result.negativeArguments, ["retour", "return"]),
    compositions: compactAiText([result.homeLineupStatus, result.homeLineup, result.awayLineupStatus, result.awayLineup].filter(Boolean).join("\n"), 1400),
    surfaceTerrainCircuitParcours: sportSpecificSurfaceLine(sport, result, event),
    meteo: extractLines(result.contextInsights + "\n" + result.sourceDetails, ["météo", "pluie", "vent", "chaleur"]),
    fatigueDeplacementCalendrier: extractLines(result.contextInsights + "\n" + result.negativeArguments, ["fatigue", "déplacement", "calendrier", "back-to-back"]),
    actualitesRecentes: newsContext?.titles?.slice(0, 6) || [],
    sourcesUtilisees: compactAiText([
      sourceNameForAi(result.sourceName),
      result.sourceDetails,
      sourceNameForAi(event?.sourceName),
    ].filter(Boolean).join("\n"), 1200),
    donneesManquantes: missingDataHints(result, sport),
    analyseLocaleInitiale: {
      selection: result.selection,
      marche: result.market,
      confiance: result.confidenceScore,
      risque: result.riskLevel,
      scenario: result.expectedScore,
    },
  };
}

function allFreeProviderNames() {
  return ["GitHub Models via Actions", "Gemini free tier", "Groq free tier", "Mistral free si disponible", "OpenRouter modèles :free"];
}

function configuredFreeProviders() {
  const providers = [];
  if (process.env.GITHUB_TOKEN && process.env.GITHUB_MODELS_ENABLED !== "0") {
    providers.push({
      id: "github-models",
      label: "GitHub Models via Actions",
      model: process.env.GITHUB_MODELS_MODEL || "openai/gpt-4o",
      apiKey: process.env.GITHUB_TOKEN,
      endpoint: "https://models.github.ai/inference/chat/completions",
      kind: "github-models",
      jsonMode: false,
      extraHeaders: {
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
      },
    });
  }
  if (process.env.GEMINI_API_KEY) {
    providers.push({
      id: "gemini",
      label: "Gemini free tier",
      model: process.env.GEMINI_MODEL || "gemini-1.5-flash",
      apiKey: process.env.GEMINI_API_KEY,
      kind: "gemini",
    });
  }
  if (process.env.GROQ_API_KEY) {
    providers.push({
      id: "groq",
      label: "Groq free tier",
      model: process.env.GROQ_MODEL || "llama-3.1-8b-instant",
      apiKey: process.env.GROQ_API_KEY,
      endpoint: "https://api.groq.com/openai/v1/chat/completions",
      kind: "openai-compatible",
      jsonMode: true,
    });
  }
  if (process.env.MISTRAL_API_KEY && process.env.MISTRAL_FREE_ENABLED === "1") {
    providers.push({
      id: "mistral",
      label: "Mistral free activé",
      model: process.env.MISTRAL_MODEL || "mistral-small-latest",
      apiKey: process.env.MISTRAL_API_KEY,
      endpoint: "https://api.mistral.ai/v1/chat/completions",
      kind: "openai-compatible",
      jsonMode: false,
    });
  }
  const openRouterModel = process.env.OPENROUTER_FREE_MODEL || "google/gemma-3n-e4b-it:free";
  if (process.env.OPENROUTER_API_KEY && openRouterModel.includes(":free")) {
    providers.push({
      id: "openrouter",
      label: "OpenRouter modèle gratuit",
      model: openRouterModel,
      apiKey: process.env.OPENROUTER_API_KEY,
      endpoint: "https://openrouter.ai/api/v1/chat/completions",
      kind: "openai-compatible",
      jsonMode: true,
      extraHeaders: {
        "HTTP-Referer": "https://github.com/starworde/BetValue-Analyzer",
        "X-Title": "BetValue Analyzer",
      },
    });
  }
  return providers;
}

function normalizedAiMode() {
  const raw = String(process.env.AI_MODE || "automatic").toLowerCase();
  if (["economic", "economique", "single"].includes(raw)) return "economique";
  if (["double", "two"].includes(raw)) return "double";
  if (["reinforced", "renforce", "all"].includes(raw)) return "renforce";
  if (["complete", "complet"].includes(raw)) return "complet";
  return "automatique";
}

function selectProvidersForMode(providers, mode, result) {
  if (providers.length <= 1) return providers;
  if (mode === "economique") return providers.slice(0, 1);
  if (mode === "double") return providers.slice(0, 2);
  if (mode === "renforce" || mode === "complet") return providers;
  const fragile = result.confidenceScore < 63 || ["exotique", "mitige"].includes(String(result.category).toLowerCase());
  return fragile ? providers.slice(0, Math.min(2, providers.length)) : providers.slice(0, 1);
}

function selectAiTargets(results, eventsById, mode, hasProviders, limit = null, aiRequests = []) {
  const hardLimit = limit ?? (hasProviders ? MAX_AI_EVENTS : Math.min(MAX_AI_EVENTS, 60));
  const all = results
    .map((result) => {
      const event = eventsById.get(result.eventId);
      const request = findAiRequestForResult(result, event, aiRequests);
      return { result, score: aiPriorityScore(result, event, mode, request) };
    })
    .sort((a, b) => b.score - a.score || a.result.eventDate - b.result.eventDate)
    .slice(0, hardLimit)
    .map((entry) => entry.result);
  return all;
}

function aiPriorityScore(result, event, mode, aiRequest = null) {
  let score = 0;
  if (aiRequest) {
    const requestPriority = Number(aiRequest.priority || 0);
    score += 160 + Math.min(120, Math.max(0, requestPriority));
    if (String(aiRequest.reason || "").includes("competition")) score += 30;
  }
  const startsInHours = (result.eventDate - Date.now()) / (60 * 60 * 1000);
  if (startsInHours >= -2 && startsInHours <= 72) score += 35;
  if (startsInHours > 72 && startsInHours <= 14 * 24) score += 16;
  if (result.confidenceScore < 58) score += 24;
  if (result.confidenceScore >= 68) score += 10;
  if (["soccer", "rugby", "tennis", "basketball", "volleyball", "handball", "cycling", "racing", "nascar"].includes(result.sport)) score += 8;
  if (String(result.contextInsights || "").toLowerCase().includes("aucun fait relevé")) score += 6;
  if (event?.rawStats) score += 4;
  if (mode === "renforce" || mode === "complet") score += 8;
  return score;
}

async function loadAiRequests(db, diagnostics) {
  try {
    const now = Date.now();
    const snapshot = await db.collection("ai_requests")
      .where("expiresAt", ">", now)
      .limit(300)
      .get();
    const requests = snapshot.docs
      .map((doc) => ({ id: doc.id, ...doc.data() }))
      .filter((request) => request.status === "pending")
      .filter((request) => String(request.eventId || "").trim())
      .filter((request) => Number(request.eventDate || 0) >= now - 48 * 60 * 60 * 1000)
      .sort((a, b) => Number(b.priority || 0) - Number(a.priority || 0));
    diagnostics.aiRequestsRead = requests.length;
    return requests;
  } catch (error) {
    diagnostics.aiRequestsRead = 0;
    diagnostics.aiErrors.push(`ai_requests:${compactAiText(error?.message || String(error), 160)}`);
    return [];
  }
}

async function markAiRequestsDone(db, requestIds, diagnostics) {
  const ids = Array.from(requestIds).filter(Boolean);
  if (ids.length === 0) return 0;
  let completed = 0;
  for (const chunk of chunked(ids, 450)) {
    const batch = db.batch();
    for (const id of chunk) {
      batch.set(db.collection("ai_requests").doc(id), {
        status: "done",
        completedAt: Date.now(),
      }, { merge: true });
    }
    await batch.commit();
    completed += chunk.length;
  }
  diagnostics.aiRequestsCompleted = Number(diagnostics.aiRequestsCompleted || 0) + completed;
  return completed;
}

function findAiRequestForResult(result, event, aiRequests = []) {
  if (!aiRequests.length) return null;
  const exact = aiRequests.find((request) => String(request.eventId || "") === String(result.eventId || ""));
  if (exact) return exact;
  return aiRequests.find((request) => aiRequestMatchesResult(request, result, event)) || null;
}

function aiRequestMatchesResult(request, result, event) {
  if (textKey(request.sport) !== textKey(result.sport || event?.sport)) return false;
  const requestDate = Number(request.eventDate || 0);
  const resultDate = Number(result.eventDate || event?.eventDate || 0);
  if (!requestDate || !resultDate || Math.abs(requestDate - resultDate) > 90 * 60 * 1000) return false;
  if (!textCompatible(request.competition, result.competition || event?.competition)) return false;
  const requestPair = participantPairKey([request.participantA, request.participantB]);
  const resultPair = participantPairKey([result.homeTeam || event?.homeTeam, result.awayTeam || event?.awayTeam]);
  if (requestPair && resultPair) return requestPair === resultPair;
  return textCompatible(request.eventName, result.eventName || event?.eventName);
}

function participantPairKey(values) {
  return values
    .map((value) => textKey(value))
    .filter(Boolean)
    .sort()
    .join("|");
}

function textCompatible(left, right) {
  const a = textKey(left);
  const b = textKey(right);
  if (!a || !b) return true;
  if (a === b || a.includes(b) || b.includes(a)) return true;
  const leftTokens = new Set(a.split(" ").filter((token) => token.length >= 4));
  const rightTokens = new Set(b.split(" ").filter((token) => token.length >= 4));
  let overlap = 0;
  for (const token of leftTokens) if (rightTokens.has(token)) overlap += 1;
  return overlap >= 2;
}

function textKey(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

async function loadAiCache(db, targets, diagnostics) {
  const output = new Map();
  for (const chunk of chunked(targets, 10)) {
    await Promise.all(chunk.map(async (result) => {
      try {
        const doc = await db.collection("cloud_results").doc(cloudDocumentIdFor(result.eventId)).get();
        const data = doc.exists ? doc.data() : null;
        if (!data?.aiAnalysis || !data?.aiDiagnostic) return;
        if (!isUsableExternalAiCache(data.aiAnalysis)) return;
        const updatedAt = Number(data.aiGeneratedAt || data.updatedAt || 0);
        if (updatedAt && Date.now() - updatedAt <= AI_CACHE_TTL_MS) {
          output.set(result.eventId, {
            aiAnalysis: String(data.aiAnalysis || ""),
            aiDiagnostic: String(data.aiDiagnostic || ""),
          });
        }
      } catch (error) {
        diagnostics.aiErrors.push(`cache:${compactAiText(error?.message || String(error), 160)}`);
      }
    }));
  }
  return output;
}

async function callProvider(provider, dossier) {
  const prompt = buildPrompt(dossier);
  const raw = provider.kind === "gemini"
    ? await callGemini(provider, prompt)
    : await callOpenAiCompatible(provider, prompt);
  const parsed = parseJsonObject(raw);
  return normalizeAiResponse(parsed, provider);
}

async function callGemini(provider, prompt) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(provider.model)}:generateContent?key=${encodeURIComponent(provider.apiKey)}`;
  const json = await postJson(url, {
    contents: [{ role: "user", parts: [{ text: prompt }] }],
    generationConfig: {
      responseMimeType: "application/json",
      temperature: 0.2,
      maxOutputTokens: 950,
    },
  });
  return (json.candidates || [])
    .flatMap((candidate) => candidate.content?.parts || [])
    .map((part) => part.text || "")
    .join("\n")
    .trim();
}

async function callOpenAiCompatible(provider, prompt) {
  const body = {
    model: provider.model,
    messages: [
      { role: "system", content: "Tu es un analyste sportif. Réponds uniquement en JSON valide." },
      { role: "user", content: prompt },
    ],
    temperature: 0.2,
    max_tokens: 950,
  };
  if (provider.jsonMode) body.response_format = { type: "json_object" };
  const json = await postJson(provider.endpoint, body, {
    Authorization: `Bearer ${provider.apiKey}`,
    ...(provider.extraHeaders || {}),
  });
  return String(json.choices?.[0]?.message?.content || "").trim();
}

async function postJson(url, body, extraHeaders = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), AI_HTTP_TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      method: "POST",
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        ...extraHeaders,
      },
      body: JSON.stringify(body),
    });
    const text = await response.text();
    if (!response.ok) {
      const error = new Error(`HTTP ${response.status} ${compactAiText(text, 200)}`);
      error.status = response.status;
      throw error;
    }
    return JSON.parse(text);
  } finally {
    clearTimeout(timer);
  }
}

function buildPrompt(dossier) {
  const participants = dossier.participants?.join(" vs ") || dossier.evenement || "événement";
  return [
    `Tu es une vraie couche Analyste IA pour BetValue Analyzer. Analyse ${participants} comme un analyste sportif, pas comme un résumé de statistiques.`,
    "Objectif : produire une réflexion sportive contextualisée, utilisable dans l'app, compacte et claire.",
    "Tu dois exploiter les données fournies : stats récentes, news, blessures, suspensions, retours, compositions, fatigue, calendrier, déplacement, surface/circuit/parcours/météo quand c’est disponible.",
    "Adapte le raisonnement au sport : football, rugby, tennis, cyclisme, F1/NASCAR, volley, basket, baseball, combat, golf, etc.",
    "Tu peux contredire le favori statistique si les signaux contextuels le justifient, mais explique pourquoi.",
    "Ne fais jamais de simple récap. Mets en avant les match-ups, signaux faibles, risques du favori et chemin crédible de l’outsider.",
    "N’invente aucune blessure, composition, météo, statistique ou déclaration. Si l’info manque, dis-le sobrement.",
    "Style demandé : proche d’un analyste humain, phrases courtes, pas de jargon inutile, pas de décimales brutes, pas de disclaimer de pari.",
    "Chaque champ doit faire 1 à 4 phrases maximum.",
    "pointsQuiComptent peut être une liste de 3 à 5 points séparés par des puces ou retours ligne.",
    `Format JSON obligatoire avec exactement ces clés : ${REQUIRED_JSON_KEYS.join(", ")}.`,
    "Sens des champs nouveaux : titreAnalyse = “Analyse IA — A vs B”; avantageFavori = pourquoi le choix logique tient; dangerOutsider = comment l’adversaire peut gêner; matchUpCle = duel tactique/sportif décisif; scoreProbable = score/état/top3 projeté selon le sport; confianceTexte = niveau humain type Faible, Moyenne, Moyenne +, Forte avec explication.",
    "Remplis aussi les anciens champs de compatibilité avec le même contenu : favoriLogique=avantageFavori, dangerAdversaire=dangerOutsider, reponseStrategique=matchUpCle, avantagesExploitables/avantagesNeutralises/pointsASurveiller utiles mais sans répétition.",
    "Dossier :",
    JSON.stringify(dossier, null, 2),
  ].join("\n\n");
}

function normalizeAiResponse(raw, provider) {
  const output = {};
  for (const key of REQUIRED_JSON_KEYS) {
    output[key] = normalizeField(raw?.[key]);
  }
  output.titreAnalyse = compactAiText(output.titreAnalyse || "Analyse IA", MAX_AI_FIELD);
  output.avantageFavori = compactAiText(output.avantageFavori || output.favoriLogique, MAX_AI_FIELD);
  output.dangerOutsider = compactAiText(output.dangerOutsider || output.dangerAdversaire, MAX_AI_FIELD);
  output.matchUpCle = compactAiText(output.matchUpCle || output.reponseStrategique, MAX_AI_FIELD);
  output.pointsQuiComptent = compactAiText(output.pointsQuiComptent || [output.avantagesExploitables, output.avantagesNeutralises, output.pointsASurveiller].filter(Boolean).join(" · "), MAX_AI_FIELD);
  output.scoreProbable = compactAiText(output.scoreProbable || "", MAX_AI_FIELD);
  output.confianceTexte = compactAiText(output.confianceTexte || output.niveauRisque || "", MAX_AI_FIELD);
  output.favoriLogique = compactAiText(output.favoriLogique || output.avantageFavori, MAX_AI_FIELD);
  output.dangerAdversaire = compactAiText(output.dangerAdversaire || output.dangerOutsider, MAX_AI_FIELD);
  output.reponseStrategique = compactAiText(output.reponseStrategique || output.matchUpCle, MAX_AI_FIELD);
  output.modeleUtilise = compactAiText(output.modeleUtilise || `${provider.label} · ${provider.model}`, MAX_AI_FIELD);
  output.confianceIA = normalizeConfidence(output.confianceIA);
  return output;
}

function fuseAiResponses({ result, event, newsContext, providerResponses, providerErrors, responseMs, mode, providers, called }) {
  const final = {};
  for (const key of REQUIRED_JSON_KEYS) {
    if (key === "modeleUtilise") continue;
    if (key === "confianceIA") continue;
    final[key] = fusedField(providerResponses, key);
  }
  const confidences = providerResponses.map((response) => Number(response.confianceIA)).filter(Number.isFinite);
  final.confianceIA = confidences.length
    ? Math.round(confidences.reduce((sum, value) => sum + value, 0) / confidences.length)
    : result.confidenceScore;
  final.modeleUtilise = providerResponses.map((response) => response.modeleUtilise).join(" + ");

  const principalAgreement = agreementSummary(providerResponses, "scenarioPrincipal");
  const dangerAgreement = agreementSummary(providerResponses, "dangerAdversaire");
  const fusionStatus = providerResponses.length >= 2
    ? `Fusion faite : ${principalAgreement}. ${dangerAgreement}.`
    : "Analyse générée par une seule IA. Fusion impossible.";
  const diagnostic = buildDiagnostic({
    mode,
    providers,
    called,
    responded: providerResponses,
    errors: providerErrors,
    responseMs,
    fusionDone: providerResponses.length >= 2,
    fallbackUsed: false,
    quotaReached: providerErrors.some((error) => /429|quota|rate/i.test(error)),
  });
  const analysis = {
    source: "multi-ai-cloud",
    generatedAt: Date.now(),
    eventId: result.eventId,
    sport: result.sport,
    providerCount: providerResponses.length,
    fusionStatus,
    ...final,
    accordEntreIA: fusionStatus,
    reponsesModeles: providerResponses.map((response) => ({
      provider: response.provider,
      modeleUtilise: response.modeleUtilise,
      confianceIA: response.confianceIA,
      scenarioPrincipal: response.scenarioPrincipal,
      dangerAdversaire: response.dangerAdversaire,
      responseMs: response.responseMs,
    })),
  };
  return {
    aiAnalysis: safeJsonString(analysis, 6000),
    aiDiagnostic: safeJsonString(diagnostic, 2600),
    contextInsights: compactAiText([
      result.contextInsights,
      `Analyse multi-IA : ${fusionStatus}`,
      newsContext?.titles?.length ? `News utilisées : ${newsContext.titles.slice(0, 3).join(" · ")}` : "",
    ].filter(Boolean).join("\n"), 2400),
    positiveArguments: compactAiText([result.positiveArguments, final.avantagesExploitables].filter(Boolean).join("\n"), 1200),
    negativeArguments: compactAiText([result.negativeArguments, final.dangerAdversaire, final.avantagesNeutralises].filter(Boolean).join("\n"), 1200),
    sourceDetails: compactAiText([result.sourceDetails, `IA appelées : ${called.map((provider) => provider.label).join(", ")}`].filter(Boolean).join("\n"), 2400),
    sourceAgreement: providerResponses.length >= 2 ? Math.max(result.sourceAgreement, 70) : result.sourceAgreement,
  };
}

function buildLocalAiFallback({ result, event, newsContext, reason, providers, called, errors = [], responseMs = 0 }) {
  const sport = result.sport;
  const matchup = localMatchupLines(sport, result);
  const analysis = {
    source: "local-preanalysis",
    generatedAt: Date.now(),
    eventId: result.eventId,
    sport,
    providerCount: 0,
    titreAnalyse: `Pré-analyse locale — ${[result.homeTeam, result.awayTeam].filter(Boolean).join(" vs ") || result.eventName || result.competition || "événement"}`,
    lectureRapide: compactAiText(`${reason} Lecture locale : ${result.selection} reste le scénario affiché, avec prudence.`, MAX_AI_FIELD),
    avantageFavori: compactAiText(`Avantage détecté : ${result.selection || "non tranché"} ressort des données déjà consolidées, mais sans validation IA externe.`, MAX_AI_FIELD),
    dangerOutsider: matchup.danger,
    matchUpCle: matchup.response,
    pointsQuiComptent: compactAiText([matchup.exploitable, matchup.neutralized, missingDataHints(result, sport).join(" · ")].filter(Boolean).join("\n"), MAX_AI_FIELD),
    favoriLogique: compactAiText(`Favori logique : ${result.selection || "non tranché"} selon les données déjà consolidées.`, MAX_AI_FIELD),
    dangerAdversaire: matchup.danger,
    reponseStrategique: matchup.response,
    avantagesExploitables: matchup.exploitable,
    avantagesNeutralises: matchup.neutralized,
    scenarioPrincipal: compactAiText(result.expectedScore || result.selection || "Surveillance, scénario principal non consolidé.", MAX_AI_FIELD),
    scoreProbable: compactAiText(result.expectedScore || "", MAX_AI_FIELD),
    scenarioAlternatif: matchup.alternative,
    pointsASurveiller: compactAiText(missingDataHints(result, sport).join(" · ") || "Dernières infos, blessures, compositions et sources publiques.", MAX_AI_FIELD),
    confianceIA: result.confidenceScore,
    confianceTexte: "Pré-analyse locale : confiance limitée tant que l’IA cloud n’a pas répondu.",
    sourcesUtilisees: compactAiText([sourceNameForAi(result.sourceName), result.sourceDetails].filter(Boolean).join(" · "), MAX_AI_FIELD),
    donneesManquantes: compactAiText(missingDataHints(result, sport).join(" · "), MAX_AI_FIELD),
    niveauRisque: result.riskLevel || "Données à surveiller",
    modeleUtilise: "Fallback local BetValue",
    erreursOuLimites: reason,
    accordEntreIA: "Analyse locale utilisée, IA externe indisponible.",
    reponsesModeles: [],
  };
  const diagnostic = buildDiagnostic({
    mode: normalizedAiMode(),
    providers,
    called,
    responded: [],
    errors,
    responseMs,
    fusionDone: false,
    fallbackUsed: true,
    quotaReached: errors.some((error) => /429|quota|rate/i.test(error)),
  });
  return {
    aiAnalysis: safeJsonString(analysis, 6000),
    aiDiagnostic: safeJsonString(diagnostic, 2600),
    contextInsights: compactAiText([result.contextInsights, reason].filter(Boolean).join("\n"), 2400),
    positiveArguments: result.positiveArguments,
    negativeArguments: result.negativeArguments,
    sourceDetails: result.sourceDetails,
    sourceAgreement: result.sourceAgreement,
  };
}

function applyAiBundle(result, bundle) {
  return {
    ...result,
    aiAnalysis: bundle.aiAnalysis || "",
    aiDiagnostic: bundle.aiDiagnostic || "",
    contextInsights: bundle.contextInsights ?? result.contextInsights,
    positiveArguments: bundle.positiveArguments ?? result.positiveArguments,
    negativeArguments: bundle.negativeArguments ?? result.negativeArguments,
    sourceDetails: bundle.sourceDetails ?? result.sourceDetails,
    sourceAgreement: bundle.sourceAgreement ?? result.sourceAgreement,
    aiGeneratedAt: Date.now(),
  };
}

function buildDiagnostic({ mode, providers, called, responded, errors, responseMs, fusionDone, fallbackUsed, quotaReached }) {
  return {
    iaConfigurees: allFreeProviderNames(),
    iaGratuitesActivees: providers.map((provider) => provider.label),
    iaPayantesDesactivees: PAID_PROVIDERS_DISABLED,
    iaAppelees: called.map((provider) => provider.label),
    iaRepondues: responded.map((response) => response.provider),
    iaEnErreur: errors,
    tempsReponseMs: responseMs,
    fusionFaite: fusionDone,
    modeleUtilise: responded.map((response) => response.modeleUtilise).filter(Boolean),
    dateGeneration: new Date().toISOString(),
    coutEstime: "0 €",
    quotaAtteint: quotaReached,
    fallbackUtilise: fallbackUsed,
    mode,
  };
}

function localMatchupLines(sport, result) {
  const generic = {
    danger: "Danger adverse : exploitable seulement si les dernières infos confirment forme, rythme et disponibilité.",
    response: "Réponse stratégique : l’avantage initial peut être réduit si l’adversaire neutralise le point fort principal.",
    exploitable: "Avantages exploitables : forme récente, qualité de source et contexte confirmé.",
    neutralized: "Avantages neutralisés : tout point non confirmé par plusieurs sources reste secondaire.",
    alternative: "Scénario alternatif : match plus serré que prévu si les infos d’effectif ou de rythme changent.",
  };
  if (sport === "tennis") return {
    danger: "Danger adverse : retour de service, surface et fatigue peuvent réduire l’avantage du favori.",
    response: "Réponse stratégique : comparer service contre retour, mobilité contre longueur des échanges, fraîcheur contre charge récente.",
    exploitable: "Avantages exploitables : surface favorable, tenue du service, breaks obtenus et historique H2H confirmé.",
    neutralized: "Avantages neutralisés : gros service moins décisif si l’adversaire retourne bien ou rallonge les échanges.",
    alternative: "Scénario alternatif : match en sets longs si le retour adverse tient ou si la fatigue apparaît.",
  };
  if (sport === "rugby") return {
    danger: "Danger adverse : conquête, mêlée, touche et discipline peuvent inverser la lecture.",
    response: "Réponse stratégique : vérifier si l’équipe répond en première ligne, couverture au pied et efficacité du buteur.",
    exploitable: "Avantages exploitables : domination conquête, occupation, discipline et buteur fiable.",
    neutralized: "Avantages neutralisés : puissance réduite si l’adversaire tient la mêlée ou ralentit les sorties.",
    alternative: "Scénario alternatif : score serré si la discipline ou la météo coupe le rythme.",
  };
  if (sport === "cycling") return {
    danger: "Danger adverse : échappée, météo, rôle d’équipe ou final technique peuvent contredire le favori.",
    response: "Réponse stratégique : vérifier si l’équipe du favori peut contrôler le peloton et protéger le leader.",
    exploitable: "Avantages exploitables : parcours adapté, startlist confirmée, météo compatible et rôle clair.",
    neutralized: "Avantages neutralisés : favori isolé ou course non contrôlée = avantage fortement réduit.",
    alternative: "Scénario alternatif : outsider crédible si le profil favorise échappée, sprint réduit ou montagne.",
  };
  if (sport === "racing" || sport === "nascar") return {
    danger: "Danger adverse : rythme long relais, pneus, stratégie et safety car peuvent peser plus que le tour rapide.",
    response: "Réponse stratégique : vérifier grille, dégradation pneus, fiabilité et météo du circuit.",
    exploitable: "Avantages exploitables : rythme course confirmé, position de départ et stratégie propre.",
    neutralized: "Avantages neutralisés : vitesse pure moins utile si le circuit ou les pneus ne la récompensent pas.",
    alternative: "Scénario alternatif : podium différent si stratégie, incident ou pluie modifie la hiérarchie.",
  };
  return generic;
}

function fusedField(responses, key) {
  const values = responses.map((response) => normalizeField(response[key])).filter(Boolean);
  if (values.length === 0) return "";
  const grouped = groupSimilar(values);
  const strongest = grouped.sort((a, b) => b.items.length - a.items.length || b.text.length - a.text.length)[0];
  if (strongest.items.length >= 2) return compactAiText(strongest.text, MAX_AI_FIELD);
  return compactAiText(values[0], MAX_AI_FIELD);
}

function agreementSummary(responses, key) {
  if (responses.length < 2) return "Une seule IA disponible";
  const values = responses.map((response) => normalizeField(response[key])).filter(Boolean);
  const grouped = groupSimilar(values);
  const best = grouped.sort((a, b) => b.items.length - a.items.length)[0];
  if (best?.items?.length >= 2) return `accord sur ${compactAiText(best.text, 120)}`;
  return "désaccord ou angles différents entre IA";
}

function groupSimilar(values) {
  const groups = [];
  for (const value of values) {
    const key = tokenSet(value);
    const existing = groups.find((group) => jaccard(key, group.tokens) >= 0.35);
    if (existing) existing.items.push(value);
    else groups.push({ text: value, tokens: key, items: [value] });
  }
  return groups;
}

function tokenSet(value) {
  return new Set(String(value).toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").split(/[^a-z0-9]+/).filter((token) => token.length >= 4));
}

function jaccard(a, b) {
  if (!a.size || !b.size) return 0;
  let intersection = 0;
  for (const token of a) if (b.has(token)) intersection += 1;
  return intersection / (a.size + b.size - intersection);
}

function parseJsonObject(value) {
  const text = String(value || "").trim();
  try {
    return JSON.parse(text);
  } catch {
    const match = text.match(/\{[\s\S]*\}/);
    if (match) return JSON.parse(match[0]);
    throw new Error("Réponse IA non JSON");
  }
}

export function isUsableExternalAiCache(value) {
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    const source = String(parsed?.source || "");
    const providerCount = Number(parsed?.providerCount || 0);
    return providerCount > 0 && !/fallback|local-preanalysis/i.test(source) && hasStrongExternalAiShape(parsed);
  } catch {
    return false;
  }
}

function hasStrongExternalAiShape(value) {
  const required = [
    value?.lectureRapide,
    value?.avantageFavori || value?.favoriLogique,
    value?.dangerOutsider || value?.dangerAdversaire,
    value?.matchUpCle || value?.reponseStrategique,
    value?.scenarioPrincipal,
    value?.scenarioAlternatif,
  ].map((item) => compactAiText(item, 520));
  if (required.filter((line) => line.length >= 35).length < 4) return false;
  const body = required.concat([
    value?.titreAnalyse,
    value?.pointsQuiComptent,
    value?.confianceTexte,
  ]).join(" ").toLowerCase();
  const legacyFlatSignals = [
    "signal présent dans les données",
    "signal present dans les donnees",
    "doit être lu comme une conclusion",
    "doit etre lu comme une conclusion",
    "pas juste repris du tableau",
    "ce que ça change",
    "ce que ca change",
    "conclusion provisoire",
    "lignes de données relues localement",
    "lignes de donnees relues localement",
    "analyse correcte local",
  ];
  return !legacyFlatSignals.some((signal) => body.includes(signal));
}

function normalizeField(value) {
  if (Array.isArray(value)) return compactAiText(value.filter(Boolean).join(" · "), MAX_AI_FIELD);
  if (value && typeof value === "object") return compactAiText(Object.values(value).filter(Boolean).join(" · "), MAX_AI_FIELD);
  return compactAiText(value, MAX_AI_FIELD);
}

function normalizeConfidence(value) {
  const number = Number(String(value).match(/\d+/)?.[0] ?? value);
  return Number.isFinite(number) ? Math.max(1, Math.min(100, Math.round(number))) : 50;
}

function sportSpecificSurfaceLine(sport, result, event) {
  const text = `${result.statSummary || ""}\n${result.contextInsights || ""}\n${event?.rawStats || ""}`;
  if (sport === "tennis") return extractLines(text, ["surface", "terre", "gazon", "dur", "indoor"]).join(" · ");
  if (sport === "cycling") return extractLines(text, ["parcours", "montagne", "sprint", "chrono", "vent"]).join(" · ");
  if (sport === "racing" || sport === "nascar") return extractLines(text, ["circuit", "pneus", "grille", "qualifications", "météo"]).join(" · ");
  return extractLines(text, ["terrain", "domicile", "extérieur", "neutre", "météo"]).join(" · ");
}

function missingDataHints(result, sport) {
  const text = `${result.statSummary}\n${result.contextInsights}\n${result.sourceDetails}`.toLowerCase();
  const required = {
    soccer: ["compositions officielles", "blessures/suspensions", "xG/tirs", "météo"],
    rugby: ["compositions", "mêlée/touche", "discipline", "buteurs"],
    tennis: ["surface", "service/retour", "fatigue", "H2H"],
    cycling: ["startlist", "parcours", "météo", "rôles d’équipe"],
    racing: ["qualifications", "rythme long relais", "pneus", "météo"],
    nascar: ["qualifications", "long run", "pneus", "stratégie"],
    volleyball: ["réception", "service", "contres", "rotations"],
    handball: ["gardiens", "exclusions", "rythme", "rotations"],
  }[sport] || ["forme récente", "infos d’effectif", "sources récentes"];
  return required.filter((item) => !text.includes(item.split(/[ /]/)[0])).slice(0, 5);
}

function extractLines(value, needles) {
  const normalizedNeedles = needles.map((needle) => String(needle).toLowerCase());
  return String(value || "")
    .split(/\n|[;|]/)
    .map((line) => compactAiText(line, 220))
    .filter((line) => normalizedNeedles.some((needle) => line.toLowerCase().includes(needle)))
    .slice(0, 6);
}

function safeJsonString(value, max) {
  const full = JSON.stringify(value);
  if (full.length <= max) return full;

  const compacted = compactJsonValue(value);
  const compactedText = JSON.stringify(compacted);
  if (compactedText.length <= max) return compactedText;

  const minimal = {
    source: value?.source || "multi-ai-cloud",
    generatedAt: value?.generatedAt || Date.now(),
    eventId: value?.eventId || "",
    sport: value?.sport || "",
    providerCount: value?.providerCount || 0,
    lectureRapide: compactAiText(value?.lectureRapide, 360),
    avantageFavori: compactAiText(value?.avantageFavori, 260),
    dangerOutsider: compactAiText(value?.dangerOutsider, 260),
    matchUpCle: compactAiText(value?.matchUpCle, 260),
    pointsQuiComptent: compactAiText(value?.pointsQuiComptent, 260),
    favoriLogique: compactAiText(value?.favoriLogique, 260),
    dangerAdversaire: compactAiText(value?.dangerAdversaire, 300),
    reponseStrategique: compactAiText(value?.reponseStrategique, 300),
    avantagesExploitables: compactAiText(value?.avantagesExploitables, 260),
    avantagesNeutralises: compactAiText(value?.avantagesNeutralises, 260),
    scenarioPrincipal: compactAiText(value?.scenarioPrincipal, 320),
    scoreProbable: compactAiText(value?.scoreProbable, 140),
    scenarioAlternatif: compactAiText(value?.scenarioAlternatif, 280),
    confianceTexte: compactAiText(value?.confianceTexte, 180),
    pointsASurveiller: compactAiText(value?.pointsASurveiller, 260),
    confianceIA: value?.confianceIA || 50,
    sourcesUtilisees: compactAiText(value?.sourcesUtilisees, 220),
    donneesManquantes: compactAiText(value?.donneesManquantes, 220),
    niveauRisque: compactAiText(value?.niveauRisque, 120),
    modeleUtilise: compactAiText(value?.modeleUtilise, 180),
    erreursOuLimites: "Analyse raccourcie pour respecter la taille Firestore ; les champs utiles sont conservés.",
    accordEntreIA: compactAiText(value?.accordEntreIA, 220),
  };
  const minimalText = JSON.stringify(minimal);
  if (minimalText.length <= max) return minimalText;
  return JSON.stringify({
    source: minimal.source,
    generatedAt: minimal.generatedAt,
    eventId: minimal.eventId,
    sport: minimal.sport,
    providerCount: minimal.providerCount,
    lectureRapide: compactAiText(minimal.lectureRapide, 180),
    scenarioPrincipal: compactAiText(minimal.scenarioPrincipal, 180),
    scenarioAlternatif: compactAiText(minimal.scenarioAlternatif, 140),
    confianceIA: minimal.confianceIA,
    donneesManquantes: compactAiText(minimal.donneesManquantes, 140),
    modeleUtilise: compactAiText(minimal.modeleUtilise, 100),
    erreursOuLimites: "Analyse raccourcie pour respecter la taille Firestore.",
  });
}

function compactJsonValue(value) {
  if (Array.isArray(value)) return value.slice(0, 4).map(compactJsonValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => {
      if (key === "reponsesModeles" && Array.isArray(item)) {
        return [key, item.slice(0, 3).map((response) => ({
          provider: compactAiText(response.provider, 80),
          modeleUtilise: compactAiText(response.modeleUtilise, 120),
          confianceIA: response.confianceIA,
          scenarioPrincipal: compactAiText(response.scenarioPrincipal, 220),
          dangerAdversaire: compactAiText(response.dangerAdversaire, 180),
          responseMs: response.responseMs,
        }))];
      }
      return [key, compactJsonValue(item)];
    }));
  }
  if (typeof value === "string") return compactAiText(value, 340);
  return value;
}

function compactAiText(value, max = MAX_AI_FIELD) {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, max);
}

function sourceNameForAi(value) {
  const text = compactAiText(value, 220);
  if (!text) return "";
  if (/consensus maison/i.test(text)) return "";
  if (/fallback/i.test(text)) return "";
  return text;
}

function cloudDocumentIdFor(eventId) {
  return compactAiText(String(eventId || "unknown-event")
    .replace(/[^A-Za-z0-9_-]+/g, "_")
    .replace(/^_+|_+$/g, ""), 180) || "unknown-event";
}

function chunked(values, size) {
  const chunks = [];
  for (let index = 0; index < values.length; index += size) chunks.push(values.slice(index, index + size));
  return chunks;
}

function isQuotaError(error) {
  return error?.status === 429 || /quota|rate limit|resource_exhausted|too many requests|models/i.test(error?.message || "");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
