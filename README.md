# BetValue Analyzer 2.1

Application Android qui récupère automatiquement les prochains événements sportifs et les cotes Betclic, puis classe les paris potentiels par probabilité de marché, value, confiance et risque.

> Réservé aux personnes majeures. Aucun gain n'est garanti. Les paris sportifs comportent un risque de perte d'argent.

## Fonctionnement automatique

1. L'utilisateur renseigne une seule fois une clé gratuite **The Odds API** dans les réglages.
2. L'application interroge le flux documenté des prochains événements.
3. Elle conserve uniquement les événements et issues pour lesquels le bookmaker `betclic_fr` expose une cote.
4. Elle retire la marge de chaque bookmaker comparable.
5. Elle calcule un consensus de marché indépendant de Betclic.
6. Elle compare ce consensus au seuil de rentabilité de la cote Betclic.
7. Elle affiche automatiquement les value bets potentielles, issues les plus probables, marchés équilibrés et paris à éviter.

Il n'y a aucun match, participant, marché ou cote à saisir manuellement dans le parcours principal.

The Odds API référence officiellement **Betclic (FR)** avec la clé `betclic_fr` dans ses [bookmakers européens](https://the-odds-api.com/sports-odds-data/bookmaker-apis.html#eu-bookmakers). Une clé personnelle peut être créée sur la [page d'accès du fournisseur](https://the-odds-api.com/#get-access).

## Fonctions incluses

- accueil automatique centré sur les prochains matchs ;
- retrait automatique des événements déjà commencés, réévalué chaque minute ;
- actualisation manuelle et périodique toutes les 6 heures avec WorkManager ;
- délai anti-double-clic d'une minute et enrichissement réduit automatiquement quand le quota devient faible ;
- cotes Betclic réelles lorsque le fournisseur les expose ;
- consensus sans marge basé sur jusqu'à neuf bookmakers comparables ;
- probabilité implicite, probabilité de consensus, edge et espérance mathématique ;
- score de confiance, catégorie et niveau de risque ;
- explication détaillée, points favorables et points de vigilance ;
- recherche par équipe ou sport ;
- ajout d'une prédiction au suivi en un toucher ;
- historique Room, résultats, taux de réussite et ROI théorique ;
- bankroll responsable et mode « analyse uniquement » ;
- catalogue multi-sports extensible ;
- aucune connexion au compte Betclic et aucun pari automatique.

## Première utilisation

1. Installer et ouvrir l'APK.
2. Confirmer la majorité et les avertissements de risque.
3. Depuis l'accueil, toucher **Configurer la source automatique**.
4. Toucher **Obtenir une clé gratuite** et créer une clé The Odds API.
5. Coller la clé dans l'application, puis toucher **Enregistrer et récupérer les matchs**.
6. Les prochains matchs et paris potentiels apparaissent automatiquement.

La clé API est chiffrée avec une clé AES non exportable de l'Android Keystore. Aucun identifiant Betclic n'est demandé ou stocké.

## Méthode de scoring

Pour chaque bookmaker, la probabilité équivalente à une cote est normalisée afin de retirer la marge :

```text
probabilité normalisée = (1 / cote de l'issue) / somme(1 / toutes les cotes du marché)
```

La probabilité estimée est ensuite construite à partir de la moyenne des bookmakers autres que Betclic, avec une réduction de confiance quand :

- peu de sources proposent le marché ;
- les bookmakers divergent ;
- les données sont anciennes ;
- la cote est élevée et donc plus volatile.

Une value potentielle existe lorsque la probabilité de consensus dépasse suffisamment la probabilité implicite Betclic et que l'espérance `probabilité × cote - 1` est positive.

## Limites transparentes

- Le flux `upcoming` du fournisseur retourne les événements les plus proches, pas l'intégralité du catalogue Betclic.
- La version 2 analyse d'abord le marché **vainqueur / h2h**. Les handicaps, totaux et props seront ajoutés ensuite.
- Betclic peut être absent temporairement de certains événements ou sports dans la réponse du fournisseur.
- Le consensus des cotes ne connaît pas à lui seul les blessures, compositions ou actualités. Ces sources devront être intégrées séparément et légalement.
- Une prédiction de marché n'est jamais une certitude sportive.

## Architecture

- `data/remote/` : client Retrofit de The Odds API ;
- `sync/` : actualisation périodique WorkManager ;
- `domain/AutomaticPredictionEngine.kt` : retrait de marge, consensus, value et scoring ;
- `data/local/` : Room pour les prédictions automatiques et le suivi ;
- `data/` : repositories et préférences DataStore ;
- `ui/` : écrans Jetpack Compose et design system sombre.

## Générer l'APK debug

Prérequis : JDK 17 et Android SDK 35.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Chemin Gradle :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Copie prête à installer :

```text
output/apk/BetValueAnalyzer-v3.apk
```

## Installer sur Android

1. Transférer `output/apk/BetValueAnalyzer-v3.apk` sur le téléphone.
2. Ouvrir le fichier.
3. Autoriser ponctuellement l'installation depuis cette source si Android le demande.
4. Installer et lancer **BetValue Analyzer**.

Avec ADB :

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "output\apk\BetValueAnalyzer-v3.apk"
```

## Version release signable

Le projet ne contient aucune clé privée. Construire d'abord l'APK release :

```powershell
.\gradlew.bat assembleRelease
```

Fichier obtenu :

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Créer ensuite une clé privée locale avec `keytool`, puis signer avec `apksigner`. Ne jamais committer la clé ou son mot de passe.

## Validation

- tests unitaires du moteur manuel et du moteur de consensus ;
- compilation de l'APK de test instrumenté ;
- lint Android ;
- APK debug signé et vérifiable ;
- build release minifié ;
- migration Room 1 → 2 préservant l'historique ;
- aucune clé API ni aucun secret inclus dans le dépôt.
