# Firebase setup BetValue Analyzer

Le code Android est branché sur le projet Firebase `betvalue-analyzer` via `app/google-services.json`.

## État attendu Firebase

- Authentication anonyme activée.
- Cloud Firestore activé sur la base `(default)`.
- Règles Firestore déployées depuis `firestore.rules`.
- GitHub Actions autorisé à écrire via un compte de service.

Collections :

- `/shared_results/{eventId}` : résultats collaboratifs envoyés par Android.
- `/cloud_results/{eventId}` : événements, pronostics et analyses préparés par GitHub Actions.
- `/cloud_diagnostics/current` : dernier état du job cloud, sources, erreurs, quota et diagnostic IA.

## Déploiement règles

```powershell
firebase deploy --only firestore:rules --project betvalue-analyzer
```

Si la CLI Firebase manque :

```powershell
npm install -g firebase-tools
firebase login
firebase deploy --only firestore:rules --project betvalue-analyzer
```

## Secrets GitHub Actions

À créer dans `Settings > Secrets and variables > Actions` :

- `FIREBASE_PROJECT_ID` : `betvalue-analyzer`
- `FIREBASE_SERVICE_ACCOUNT_JSON` : JSON complet du compte de service Firebase, brut ou base64.

Secrets IA gratuits optionnels :

- `GEMINI_API_KEY`
- `GROQ_API_KEY`
- `MISTRAL_API_KEY` si `MISTRAL_FREE_ENABLED=1`
- `OPENROUTER_API_KEY` si `OPENROUTER_FREE_MODEL` pointe vers un modèle `:free`

Ne jamais committer ces secrets.

## Couche IA gratuite

L’APK ne contient aucune clé. GitHub Actions peut enrichir les événements avec des modèles gratuits, stocker une analyse fusionnée dans Firestore, puis Android l’affiche sous “Analyse IA approfondie”.

Si aucun modèle gratuit n’est configuré, si un quota est atteint ou si Firestore refuse temporairement une lecture/écriture, l’application continue avec le cache et le moteur local.

## Diagnostic rapide

```powershell
powershell -ExecutionPolicy Bypass -File tools\Test-FirebaseCloud.ps1
```

À vérifier après un run GitHub Actions :

- `cloud_diagnostics/current.status = success`
- `cloud_diagnostics/current.eventsFound > 0`
- `cloud_diagnostics/current.resultsWritten > 0`
- `cloud_diagnostics/current.aiPaidDisabled` contient les fournisseurs payants désactivés
- `cloud_diagnostics/current.aiFreeEnabled` indique les fournisseurs gratuits réellement configurés

Le plan Spark gratuit peut atteindre ses quotas Firestore. Dans ce cas l’erreur doit apparaître dans Réglages, sans bloquer l’application.
