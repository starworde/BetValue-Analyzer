# BetValue cloud job

Ce dossier contient le job gratuit prévu pour GitHub Actions.

Il ne remplace pas le calcul local Android : il prépare une base commune dans Firestore pour que l’application puisse lire des résultats déjà calculés quand ils sont disponibles.

Collections Firestore utilisées :

- `/cloud_results/{eventId}` : résultats préparés automatiquement par GitHub Actions ;
- `/cloud_diagnostics/current` : état du dernier job ;
- `/shared_results/{eventId}` : résultats collaboratifs envoyés par les téléphones Android.

Secrets GitHub nécessaires :

- `FIREBASE_SERVICE_ACCOUNT_JSON` : JSON complet d’un compte de service Firebase encodé en base64 ou collé tel quel.
- `FIREBASE_PROJECT_ID` : identifiant du projet Firebase, par exemple `betvalue-analyzer`.

Le job utilise uniquement GitHub Actions + Firebase Firestore. Il n’utilise pas Firebase Cloud Functions, ne demande pas Blaze, ne demande pas de serveur et ne nécessite pas de PC allumé.

Commande GitHub Actions :

```bash
npm install --omit=dev
npm start
```

Diagnostic attendu après une exécution :

```text
cloud_diagnostics/current.status = success
cloud_diagnostics/current.eventsFound > 0
cloud_diagnostics/current.resultsWritten > 0
cloud_diagnostics/current.eventsBySport.volleyball >= 0
```

Le diagnostic contient aussi `eventsBySport` et `resultsBySport` pour vérifier que les sports secondaires comme volley-ball, cyclisme, cricket, handball, hockey sur gazon, fléchettes ou snooker ne sont pas silencieusement oubliés.

`sportsWithoutEvents` liste les sports configurés qui n’ont rien remonté pendant cette exécution. C’est utile pour différencier un vrai calendrier vide d’un problème de source publique.
