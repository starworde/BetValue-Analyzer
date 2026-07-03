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
    GITHUB_MODELS_MULTI_LIMIT: "3",
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
    ["openai/gpt-4o-mini", "mistral-small-2503", "openai/gpt-4o"],
    "Le pool GitHub Models par défaut doit tenter GPT + Mistral + GPT secours.",
  );
  assert.deepEqual(
    providers.map((provider) => provider.family),
    ["openai", "mistral", "openai"],
    "Les modèles GitHub doivent être classés par vraie famille IA.",
  );
}

function testGeminiAndClaudeBackendProvidersAreDetectedWithoutLeakingSecrets() {
  resetEnv({
    GITHUB_TOKEN: "gh-test-token",
    GEMINI_API_KEY: "gemini-secret-value",
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

function testProviderDisablingErrors() {
  assert.equal(__testOnlyMultiAi.isProviderDisablingError({ status: 429, message: "Too Many Requests" }), true);
  assert.equal(__testOnlyMultiAi.isProviderDisablingError({ status: 403, message: "forbidden" }), true);
  assert.equal(__testOnlyMultiAi.isProviderDisablingError({ status: 500, message: "temporary server error" }), false);
}
