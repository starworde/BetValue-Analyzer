import assert from "node:assert/strict";
import {
  __testOnlyMultiAi,
  multiAiProviderDebugSnapshot,
  multiAiProviderDiagnostics,
} from "./multi-ai.mjs";

const ORIGINAL_ENV = { ...process.env };

try {
  testDefaultGithubPoolUsesDifferentFamilies();
  testGeminiAndClaudeBackendProvidersAreDetectedWithoutLeakingSecrets();
  testDoubleModePrioritizesDifferentAiFamilies();
  testPriorityRequestsUseRedundantAiEvenInEconomicMode();
  testCompetitionFavoritesArePrioritizedBeforeSportFavoritesAndRest();
  testDispatchSplitsSportsByAssignedAiAndKeepsFallbacks();
  testProviderDisablingErrors();
  console.log("[test-multi-ai] OK");
} finally {
  process.env = ORIGINAL_ENV;
}

function resetEnv(overrides = {}) {
  process.env = {
    ...ORIGINAL_ENV,
    GITHUB_TOKEN: "",
    GITHUB_MODELS_ENABLED: "1",
    GITHUB_MODELS_MULTI: "1",
    GITHUB_MODELS_MULTI_LIMIT: "",
    GITHUB_MODELS_MODEL: "",
    GITHUB_MODELS_MODEL_POOL: "",
    GITHUB_MODELS_FALLBACK_MODELS: "",
    GEMINI_API_KEY: "",
    GEMINI_ENABLED: "",
    GEMINI_MODEL: "",
    ANTHROPIC_API_KEY: "",
    CLAUDE_API_KEY: "",
    ANTHROPIC_ENABLED: "",
    CLAUDE_ENABLED: "",
    ANTHROPIC_MODEL: "",
    CLAUDE_MODEL: "",
    GROQ_API_KEY: "",
    MISTRAL_API_KEY: "",
    OPENROUTER_API_KEY: "",
    ...overrides,
  };
}

function testDefaultGithubPoolUsesDifferentFamilies() {
  resetEnv({ GITHUB_TOKEN: "gh-test-token" });
  const providers = multiAiProviderDebugSnapshot();
  assert.deepEqual(
    providers.map((provider) => provider.model),
    [
      "mistral-ai/mistral-medium-2505",
      "openai/gpt-4.1-mini",
      "deepseek/deepseek-v3-0324",
      "cohere/cohere-command-a",
      "microsoft/phi-4-mini-instruct",
      "meta/llama-3.3-70b-instruct",
      "mistral-ai/ministral-3b",
    ],
    "Le pool GitHub Models par défaut doit tenter plusieurs modèles top tier gratuits/testables.",
  );
  assert.deepEqual(
    providers.map((provider) => provider.family),
    ["mistral", "openai", "deepseek", "cohere", "microsoft", "meta", "mistral"],
    "Les modèles GitHub doivent être classés par vraie famille IA.",
  );
}

function testGeminiAndClaudeBackendProvidersAreDetectedWithoutLeakingSecrets() {
  resetEnv({
    GITHUB_TOKEN: "gh-test-token",
    GEMINI_API_KEY: "gemini-secret-value",
    GEMINI_ENABLED: "1",
    ANTHROPIC_API_KEY: "anthropic-secret-value",
    GROQ_API_KEY: "groq-secret-value",
  });
  const diagnostic = multiAiProviderDiagnostics();
  const details = diagnostic.freeEnabledDetails;
  assert(details.some((provider) => provider.family === "gemini"), "Gemini doit être activable côté backend.");
  assert(details.some((provider) => provider.family === "claude"), "Claude doit être activable côté backend.");
  assert(details.some((provider) => provider.family === "groq"), "Groq doit rester disponible comme autre fallback backend.");
  const serialized = JSON.stringify(diagnostic);
  assert(!serialized.includes("gemini-secret-value"), "Le diagnostic ne doit jamais exposer la clé Gemini.");
  assert(!serialized.includes("anthropic-secret-value"), "Le diagnostic ne doit jamais exposer la clé Claude/Anthropic.");
  assert(!serialized.includes("groq-secret-value"), "Le diagnostic ne doit jamais exposer la clé Groq.");
}

function testDoubleModePrioritizesDifferentAiFamilies() {
  const providers = [
    { id: "gpt-mini", label: "GPT mini", family: "openai" },
    { id: "gpt-large", label: "GPT large", family: "openai" },
    { id: "gemini", label: "Gemini", family: "gemini" },
    { id: "claude", label: "Claude", family: "claude" },
  ];
  const plan = __testOnlyMultiAi.providerCallPlanForMode(providers, "double", {
    confidenceScore: 52,
    category: "mitige",
  });
  assert.equal(plan.desiredResponses, 2, "Le mode double doit viser deux réponses IA.");
  assert.deepEqual(
    plan.candidates.slice(0, 3).map((provider) => provider.family),
    ["openai", "gemini", "claude"],
    "Le fallback doit essayer les familles différentes avant un deuxième modèle OpenAI.",
  );
}

function testPriorityRequestsUseRedundantAiEvenInEconomicMode() {
  const providers = [
    { id: "mistral", label: "Mistral", family: "mistral" },
    { id: "gpt", label: "GPT", family: "openai" },
    { id: "mini", label: "Mini", family: "openai" },
  ];
  const plan = __testOnlyMultiAi.providerCallPlanForMode(
    providers,
    "economique",
    { confidenceScore: 78, category: "safe" },
    null,
    { priority: 190, reason: "competition_and_sport_priority" },
  );
  assert.equal(
    plan.desiredResponses,
    2,
    "Un match favori/prioritaire doit garder deux avis IA meme en mode economique.",
  );
  assert.deepEqual(
    plan.candidates.slice(0, 2).map((provider) => provider.family),
    ["mistral", "openai"],
    "La redondance prioritaire doit chercher deux familles IA differentes.",
  );
}

function testCompetitionFavoritesArePrioritizedBeforeSportFavoritesAndRest() {
  assert.equal(
    __testOnlyMultiAi.targetPriorityTier({ priority: 120, reason: "competition_priority" }),
    0,
    "Une competition favorite doit rester au premier niveau meme avec une priorite numerique moyenne.",
  );
  assert.equal(
    __testOnlyMultiAi.targetPriorityTier({ priority: 200, reason: "sport_priority" }),
    1,
    "Un sport favori doit rester derriere les competitions favorites.",
  );
  assert.equal(
    __testOnlyMultiAi.targetPriorityTier({ priority: 40, reason: "background_refresh" }),
    2,
    "Le reste doit passer apres les favoris.",
  );

  const now = Date.now() + 60 * 60 * 1000;
  const results = [
    { eventId: "rest", eventDate: now, sport: "baseball", confidenceScore: 72, category: "safe" },
    { eventId: "sport-fav", eventDate: now, sport: "rugby", confidenceScore: 72, category: "safe" },
    { eventId: "competition-fav", eventDate: now, sport: "tennis", competition: "Wimbledon", confidenceScore: 72, category: "safe" },
  ];
  const selected = __testOnlyMultiAi.selectAiTargets(
    results,
    new Map(),
    "automatique",
    true,
    3,
    [
      { eventId: "sport-fav", priority: 200, reason: "sport_priority", sport: "rugby" },
      { eventId: "competition-fav", priority: 120, reason: "competition_priority", sport: "tennis", competition: "Wimbledon" },
    ],
  );
  assert.deepEqual(
    selected.map((result) => result.eventId),
    ["competition-fav", "sport-fav", "rest"],
    "La file IA doit traiter competition favorite, puis sport favori, puis le reste.",
  );
}

function testDispatchSplitsSportsByAssignedAiAndKeepsFallbacks() {
  const providers = [
    { id: "mistral", label: "Mistral", family: "mistral" },
    { id: "gpt", label: "GPT", family: "openai" },
    { id: "llama", label: "Llama", family: "meta" },
    { id: "cohere", label: "Cohere", family: "cohere" },
  ];
  const runtime = __testOnlyMultiAi.createProviderRuntimeState(providers);

  const targets = [
    {
      result: { sport: "football", competition: "Coupe du monde FIFA", eventName: "France - Suede" },
      request: { priority: 120, reason: "competition_priority", competition: "Coupe du monde FIFA", sport: "football" },
    },
    {
      result: { sport: "tennis", competition: "ATP · Wimbledon · Men's Singles", eventName: "Player A - Player B" },
      request: { priority: 120, reason: "competition_priority", competition: "Wimbledon", sport: "tennis" },
    },
    {
      result: { sport: "rugby", competition: "Top 14", eventName: "Toulouse - Montpellier" },
      request: { priority: 190, reason: "sport_priority", competition: "Top 14", sport: "rugby" },
    },
    {
      result: { sport: "racing", competition: "Formula 1 · British Grand Prix", eventName: "British Grand Prix - Race" },
      request: { priority: 120, reason: "competition_priority", competition: "British Grand Prix", sport: "racing" },
    },
  ];

  const firstProviderIds = targets.map((target) => __testOnlyMultiAi.orderProvidersForDispatch(
    providers,
    runtime,
    target.result,
    null,
    target.request,
  )[0].id);
  assert(
    new Set(firstProviderIds).size >= 2,
    "Les sports/competitions doivent etre repartis sur plusieurs IA au lieu de tout envoyer au meme modele.",
  );

  const footballOrder = __testOnlyMultiAi.orderProvidersForDispatch(
    providers,
    runtime,
    targets[0].result,
    null,
    targets[0].request,
  );
  const assignment = __testOnlyMultiAi.providerAssignmentForTarget(
    providers,
    targets[0].result,
    null,
    targets[0].request,
  );
  assert.equal(
    footballOrder[0].id,
    assignment.preferredProviderId,
    "Le premier fournisseur doit etre le proprietaire IA stable de cette competition.",
  );

  const fallbackOrder = __testOnlyMultiAi.orderProvidersForDispatch(
    providers.filter((provider) => provider.id !== footballOrder[0].id),
    runtime,
    targets[0].result,
    null,
    targets[0].request,
  );
  assert.notEqual(
    fallbackOrder[0].id,
    footballOrder[0].id,
    "Si l'IA proprietaire est indisponible, le dispatch doit basculer vers une autre IA.",
  );
}

function testProviderDisablingErrors() {
  assert.equal(__testOnlyMultiAi.isProviderDisablingError({ status: 429, message: "Too Many Requests" }), true);
  assert.equal(__testOnlyMultiAi.isProviderDisablingError({ status: 403, message: "forbidden" }), true);
  assert.equal(__testOnlyMultiAi.isProviderDisablingError({ status: 500, message: "temporary server error" }), false);
}
