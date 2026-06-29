# Firebase setup BetValue Analyzer

Le code Android est branché sur le projet Firebase `betvalue-analyzer` via `app/google-services.json`.

État attendu :

- Authentication anonyme : active.
- Cloud Firestore : base `(default)` créée en `eur3 (Europe)`.
- Règles Firestore : lecture Android autorisée sur `/shared_results`, `/cloud_results` et `/cloud_diagnostics`.
- GitHub Actions : écriture serveur sur `/cloud_results/{eventId}` et `/cloud_diagnostics/current` via compte de service.

Collections :

- `/shared_results/{eventId}` : résultats collaboratifs envoyés par les téléphones.
- `/cloud_results/{eventId}` : résultats préparés automatiquement par GitHub Actions.
- `/cloud_diagnostics/current` : dernière exécution du job GitHub Actions.

Pour que le cloud collaboratif fonctionne réellement sur les téléphones :

1. Dans Firebase Console, ouvrir le projet `betvalue-analyzer`.
2. Authentication > Sign-in method > activer `Anonymous`.
3. Firestore Database > créer/activer la base Firestore en mode production.
4. Déployer les règles locales :

```powershell
firebase deploy --only firestore:rules --project betvalue-analyzer
```

Si la commande `firebase` n’existe pas :

```powershell
npm install -g firebase-tools
firebase login
firebase deploy --only firestore:rules --project betvalue-analyzer
```

## GitHub Actions gratuit, sans Cloud Functions

Le dossier `cloud-job/` contient le job Node.js lancé par `.github/workflows/cloud-sports-job.yml`.

Il tourne 4 fois par jour via GitHub Actions :

- matin ;
- midi ;
- fin d’après-midi ;
- soir.

Secrets GitHub à créer dans `Settings > Secrets and variables > Actions` :

- `FIREBASE_PROJECT_ID` : `betvalue-analyzer`
- `FIREBASE_SERVICE_ACCOUNT_JSON` : JSON complet du compte de service Firebase, idéalement encodé en base64.

Le secret `FIREBASE_SERVICE_ACCOUNT_JSON` ne doit jamais être commité dans le code.

Pour générer ce JSON :

1. Firebase Console > Project settings > Service accounts.
2. Cliquer sur `Generate new private key`.
3. Copier le JSON dans GitHub Secret, ou l’encoder en base64 puis coller le résultat.

Vérification après un run GitHub Actions :

- `cloud_diagnostics/current.status` doit valoir `success`.
- `cloud_diagnostics/current.eventsFound` doit être supérieur à 0.
- `cloud_diagnostics/current.resultsWritten` doit être supérieur à 0.
- `cloud_diagnostics/current.eventsBySport` permet de voir sport par sport ce qui est remonté.

Firebase reste compatible avec le plan gratuit Spark : pas de Cloud Functions, pas de serveur, pas besoin de PC allumé. Attention seulement aux quotas gratuits Firestore si le volume devient énorme.

Diagnostic rapide depuis ce dossier :

```powershell
powershell -ExecutionPolicy Bypass -File tools\Test-FirebaseCloud.ps1
```

L’application reste tolérante aux pannes : si GitHub Actions, Auth, Firestore ou App Check ne répondent pas, elle continue avec ses données locales et écrit l’erreur dans Réglages > Cloud collaboratif.
