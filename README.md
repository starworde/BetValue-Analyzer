# BetValue Analyzer

Application Android + web app de suivi sportif et d’analyse pré‑match/live. Elle récupère automatiquement les événements publics, consolide les statistiques utiles par sport, puis affiche des pronostics et une lecture “Analyste IA” quand des données suffisantes existent.

Le projet ne contient aucune clé IA ni secret Firebase dans l’APK. Les enrichissements IA se font côté GitHub Actions via GitHub Models + Firestore, avec cache et pré‑analyse locale seulement si l’IA cloud ne répond pas.

## Ce que fait l’application

- événements à venir par sport, ligue, tournoi, GP ou compétition ;
- favoris sport/compétition pour prioriser l’accueil ;
- mode live limité aux événements en cours, 30 minutes avant départ et 30 minutes après fin ;
- score live pour les sports de match, classement/top 3 pour courses et GP ;
- statistiques adaptées par sport : football, rugby, tennis, cyclisme, F1/NASCAR, volley, basket, handball, baseball, hockey sur glace, golf, sports de combat, etc. ;
- compositions terrain football/rugby quand elles sont disponibles ;
- infos joueurs : absences, retours, suspensions, forme, charge récente et impact potentiel ;
- analyse IA approfondie : contexte, dynamique, match-up, favori fragile, outsider crédible, conclusion argumentée ;
- diagnostic cloud/IA dans les réglages.

Sports retirés de l’interface : snooker, football australien, fléchettes, cricket et hockey sur gazon.

## Couche IA

La couche IA est conçue pour raisonner, pas seulement reformuler les statistiques déjà visibles :

- collecte d’un dossier structuré par événement ;
- appel GitHub Models côté cloud via `GITHUB_TOKEN` Actions, sans clé dans l’APK ;
- appels optionnels de modèles gratuits en renfort si des secrets dédiés existent ;
- fusion des réponses quand plusieurs IA répondent ;
- stockage Firestore avec durée de cache ;
- affichage Android en cartes courtes et lisibles ;
- pré‑analyse locale uniquement si l’IA cloud ne répond pas, affichée comme telle.

Fournisseur IA cloud par défaut :

- GitHub Models via GitHub Actions, permission `models: read`, modèle par défaut `openai/gpt-4o`.

Fournisseurs gratuits optionnels en renfort côté GitHub Actions :

- Gemini free tier ;
- Groq free tier ;
- Mistral uniquement si un mode gratuit est explicitement activé ;
- OpenRouter uniquement avec un modèle `:free`.

Fournisseurs payants directs désactivés par défaut : clés OpenAI/Claude/xAI/Cohere côté APK et tout service nécessitant facturation obligatoire ou carte bancaire dans l’application.

## Cloud gratuit

Le cloud repose sur :

- GitHub Actions pour préparer les événements et analyses ;
- Firebase Authentication anonyme côté Android ;
- Firestore pour partager les résultats déjà calculés ;
- cache Firestore pour éviter les appels répétés et protéger les quotas gratuits.

Collections utilisées :

- `/cloud_results/{eventId}` : résultats préparés par GitHub Actions ;
- `/cloud_diagnostics/current` : état du dernier job ;
- `/shared_results/{eventId}` : résultats collaboratifs légers envoyés par les téléphones ;
- `/ai_requests/{requestId}` : priorités IA sur événements publics issus des favoris.

## Web app

Le dossier `web/` contient une version web légère pour consultation PC. Elle utilise les mêmes principes d’affichage : accueil, live, sports, diagnostics et données cloud quand elles sont disponibles.

## Générer l’APK

Prérequis : JDK 17 et Android SDK.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug exportVersionedApk
```

APK exportée :

```text
output/apk/BetValueAnalyzer-v<versionCode>.apk
```

## Validation attendue

- `testDebugUnitTest` ;
- `lintDebug` ;
- syntaxe Node du cloud job ;
- diagnostic Firebase si quota disponible ;
- APK versionnée générée seulement après les vérifications.

## Secrets à ne jamais committer

- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_PROJECT_ID`
- `GEMINI_API_KEY`
- `GROQ_API_KEY`
- `MISTRAL_API_KEY`
- `OPENROUTER_API_KEY`

Ces secrets doivent rester dans GitHub Secrets ou dans l’environnement de build sécurisé.
