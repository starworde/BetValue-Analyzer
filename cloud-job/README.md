# BetValue cloud job

Ce dossier contient le job Node.js lancé par GitHub Actions. Il prépare les événements sportifs publics, calcule les résultats consolidés, enrichit certains matchs avec une couche multi‑IA gratuite si des clés sont disponibles, puis écrit le tout dans Firestore.

Il ne remplace pas l’analyse locale Android : il prémâche les dossiers lourds pour que l’application reste fluide et puisse afficher des analyses déjà calculées.

## Collections Firestore

- `/cloud_results/{eventId}` : événements et analyses préparés automatiquement ;
- `/cloud_diagnostics/current` : état du dernier job, sources, erreurs, quotas et diagnostic IA ;
- `/shared_results/{eventId}` : résultats collaboratifs légers envoyés par les téléphones.

## Secrets obligatoires

- `FIREBASE_SERVICE_ACCOUNT_JSON` : JSON complet d’un compte de service Firebase, brut ou encodé base64.
- `FIREBASE_PROJECT_ID` : identifiant du projet Firebase, par exemple `betvalue-analyzer`.

## Secrets IA gratuits optionnels

Aucune clé IA ne doit être dans l’APK Android.

- `GEMINI_API_KEY`
- `GROQ_API_KEY`
- `MISTRAL_API_KEY` avec `MISTRAL_FREE_ENABLED=1` seulement si l’usage gratuit est confirmé
- `OPENROUTER_API_KEY` avec `OPENROUTER_FREE_MODEL` contenant obligatoirement `:free`

Fournisseurs payants désactivés dans le diagnostic : OpenAI, Claude/Anthropic, xAI/Grok, Cohere et services à facturation obligatoire.

## Variables utiles

- `AI_MODE` : `automatic`, `economique`, `double`, `renforce` ou `complet`.
- `MAX_AI_EVENTS` : nombre maximal d’événements enrichis par IA externe par run.
- `AI_CACHE_TTL_HOURS` : durée de réutilisation du cache IA Firestore.
- `EVENT_LOOKAHEAD_DAYS` : horizon calendrier.
- `MAX_RESULTS_TO_WRITE` : limite d’écriture Firestore par run.

## Commandes

```bash
npm install --omit=dev
npm start
```

Vérification syntaxe :

```bash
node --check index.mjs
node --check multi-ai.mjs
```

## Diagnostic attendu

`cloud_diagnostics/current` expose notamment :

- `status`, `eventsFound`, `resultsWritten`, `eventsBySport`, `sportsWithoutEvents` ;
- `aiConfigured`, `aiFreeEnabled`, `aiPaidDisabled`, `aiMode` ;
- `aiCalled`, `aiResponded`, `aiErrors`, `aiCacheHits`, `aiFusionCount`, `aiFallbackUsed`, `aiQuotaReached`.

Si aucun fournisseur IA gratuit n’est configuré, le job écrit quand même des résultats avec fallback local et le diagnostic l’indique clairement.
