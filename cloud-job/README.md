# BetValue cloud job

Ce dossier contient le job Node.js lancé par GitHub Actions. Il prépare les événements sportifs publics, calcule les résultats consolidés, enrichit certains matchs avec une couche Analyste IA côté cloud, puis écrit le tout dans Firestore.

Il ne remplace pas l’analyse locale Android : il prémâche les dossiers lourds pour que l’application reste fluide et puisse afficher des analyses déjà calculées.

## Collections Firestore

- `/cloud_results/{eventId}` : événements et analyses préparés automatiquement ;
- `/cloud_diagnostics/current` : état du dernier job, sources, erreurs, quotas et diagnostic IA ;
- `/shared_results/{eventId}` : résultats collaboratifs légers envoyés par les téléphones ;
- `/ai_requests/{requestId}` : demandes de priorité IA créées par l’app pour les sports/compétitions favoris.

## Secrets obligatoires

- `FIREBASE_SERVICE_ACCOUNT_JSON` : JSON complet d’un compte de service Firebase, brut ou encodé base64.
- `FIREBASE_PROJECT_ID` : identifiant du projet Firebase, par exemple `betvalue-analyzer`.

## IA cloud

Aucune clé IA ne doit être dans l’APK Android.

Le workflow active GitHub Models via le `GITHUB_TOKEN` fourni par GitHub Actions, avec la permission `models: read`. C’est le chemin par défaut pour obtenir une vraie analyse IA sans clé dans l’application.

Variables GitHub optionnelles :

- `GITHUB_MODELS_MODEL` : modèle GitHub Models principal à utiliser, par défaut `mistral-ai/mistral-medium-2505`.
- `GITHUB_MODELS_MODEL_POOL` : liste de modèles GitHub Models à tenter en mode multi-IA.
- `GITHUB_MODELS_FALLBACK_MODELS` : liste de secours utilisée si aucun pool n’est configuré. Par défaut, le job tente `mistral-ai/mistral-medium-2505`, puis des modèles gratuits testés en renfort : `openai/gpt-4.1-mini`, `deepseek/deepseek-v3-0324`, `cohere/cohere-command-a`, `microsoft/phi-4-mini-instruct`, `meta/llama-3.3-70b-instruct` et `mistral-ai/ministral-3b`. Le premier est priorisé parce qu'il a été le plus stable au dernier smoke gratuit, les autres restent disponibles en redondance.

Secrets IA gratuits optionnels en renfort :

- `GEMINI_API_KEY` avec `GEMINI_ENABLED=1` seulement quand le quota Gemini est confirmé stable en smoke
- `GROQ_API_KEY`
- `MISTRAL_API_KEY` avec `MISTRAL_FREE_ENABLED=1` seulement si l’usage gratuit est confirmé
- `OPENROUTER_API_KEY` avec `OPENROUTER_FREE_MODEL` contenant obligatoirement `:free`

Fournisseurs IA backend optionnels, jamais dans l'APK :

- `ANTHROPIC_API_KEY` ou `CLAUDE_API_KEY` active Claude via l'API Messages Anthropic côté GitHub Actions.
- `ANTHROPIC_MODEL` ou `CLAUDE_MODEL` permet de choisir le modèle Claude, par défaut `claude-haiku-4-5`, avec `claude-3-5-haiku-latest` en secours.
- `ANTHROPIC_FALLBACK_MODELS` ou `CLAUDE_FALLBACK_MODELS` peut contenir une liste séparée par virgules.
- `ANTHROPIC_ENABLED=0` ou `CLAUDE_ENABLED=0` désactive Claude même si une clé existe.
- `GEMINI_ENABLED=1` active Gemini. Sans cette variable, Gemini reste prêt côté backend mais non appelé, pour éviter de casser les runs quand le quota Google gratuit est épuisé.

Les clés OpenAI/Claude/Gemini ne sont jamais lues depuis Android. Elles doivent rester dans les secrets GitHub/Firebase/backend.
Si `GEMINI_API_KEY`, `ANTHROPIC_API_KEY` ou `CLAUDE_API_KEY` ne sont pas configurés dans GitHub, ces fournisseurs restent disponibles dans l’architecture mais ne sont pas appelés en production.

## Variables utiles

- `AI_MODE` : `automatic`, `economique`, `double`, `renforce` ou `complet`.
- `MAX_AI_EVENTS` : nombre maximal d’événements enrichis par IA externe par run. Le workflow garde un petit batch pour rester sous les quotas gratuits GitHub Models.
- `AI_CACHE_TTL_HOURS` : durée de réutilisation du cache IA Firestore.
- `EVENT_LOOKAHEAD_DAYS` : horizon calendrier.
- `MAX_RESULTS_TO_WRITE` : limite d’écriture Firestore par run.

En mode `double`, le job vise deux réponses IA issues de familles différentes quand elles sont disponibles : par exemple OpenAI GitHub Models + Mistral GitHub Models, ou GitHub Models + Gemini/Claude/Groq si les secrets backend existent. Si un fournisseur échoue, renvoie une 401/403/429 ou atteint son quota, le job tente le fournisseur suivant au lieu de bloquer toute l'analyse.

## Routage IA par favoris

Le job ne traite plus les analyses comme une file unique. Il classe d'abord les demandes IA en trois niveaux :

1. competition favorite : ligue/coupe en football ou rugby, tournoi/Grand Chelem en tennis, GP/course en F1/NASCAR, course/etape en cyclisme ;
2. sport favori ;
3. reste du calendrier.

Chaque niveau recoit une attribution IA stable : une competition ou un sport a un fournisseur IA preferentiel, puis des fournisseurs de secours. Si le fournisseur preferentiel renvoie une erreur, un quota, une 401/403/429 ou devient indisponible, le job bascule automatiquement vers l'IA suivante au lieu de bloquer l'analyse. Les logs affichent `route=... -> fournisseur` pour auditer cette repartition dans GitHub Actions.

## Commandes

```bash
npm install --omit=dev
npm start
```

Vérification syntaxe :

```bash
npm run check
```

Smoke test réel des IA :

```bash
npm run smoke:ai
```

Le workflow GitHub manuel `BetValue AI provider smoke test` appelle les fournisseurs IA réellement disponibles et les teste sur un panel de sports (`soccer`, `rugby`, `tennis`, `cycling`, `racing`, `basketball`, `baseball`, `volleyball`, `handball`, `golf`, `mma`, `football`). Les fournisseurs sans secret backend, par exemple Gemini sans `GEMINI_API_KEY` ou Claude sans `ANTHROPIC_API_KEY`/`CLAUDE_API_KEY`, sont notés `SKIPPED` dans le rapport au lieu d’être faussement validés.

Le workflow GitHub déploie `../firestore.rules` via `deploy-rules.mjs` et le Firebase Admin SDK, avec le même `FIREBASE_SERVICE_ACCOUNT_JSON` que le job cloud. Il ne dépend pas d’un `firebase login` interactif.

## Diagnostic attendu

`cloud_diagnostics/current` expose notamment :

- `status`, `eventsFound`, `resultsWritten`, `eventsBySport`, `sportsWithoutEvents` ;
- `aiConfigured`, `aiFreeEnabled`, `aiPaidDisabled`, `aiMode` ;
- `aiCalled`, `aiResponded`, `aiErrors`, `aiCacheHits`, `aiFusionCount`, `aiFallbackUsed`, `aiQuotaReached`.
- `aiRequestsRead`, `aiRequestsMatched`, `aiRequestsCompleted` pour contrôler l’effet des favoris sur la file IA.

Si aucune IA cloud ne répond, le job conserve le résultat sans fausse analyse IA. L’application affiche alors l’analyse IA comme indisponible/en attente au lieu de réutiliser un résumé local.
