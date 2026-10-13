# Open Headunit LibreHU

Fork d'[Open Headunit](https://github.com/andreknieriem/headunit-revived) (ex-Headunit Revived, de Michael Reid
puis Andre Rinas), recepteur Android Auto en appli Android, pour les autoradios LibreHU (AC8257 / UJC201).
Licence inchangee : **AGPL-3.0** (voir `LICENSE` et `COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`) ; le code de ce fork
est publie avec ses APK.

## Ce que change le fork
- **Paquet `org.librehu.headunit`** (« Open Headunit LibreHU ») : s'installe a cote d'Open Headunit. Le namespace du
  code et les actions d'automatisation (`com.andrerinas.openheadunit.ACTION_*`) ne changent pas.
- **Nom et icone** : affiche « Android Auto » sous l'icone (role de l'appli sur l'autoradio). L'icone est
  originale (fleche de navigation Material, Apache-2.0, sur fond vert), avec un glyphe blanc
  (`org.librehu.themed_icon`) que LibreHU Launcher colore avec l'accent du theme. Le logo Android Auto de Google n'est
  pas repris : marque reservee (voir « Marques » ci-dessous). Pour mettre une autre image sur son autoradio : appui
  long sur l'appli dans LibreHU Launcher → « Changer l'icone ».
- **Signe avec la cle LibreHU** (la meme que toutes les applis LibreHU) : LibreHU-service et LibreHU Launcher lui
  font confiance par signature. Les `*.jks` sont ignores par git ici : la CI prend le secret `LIBREHU_KEYSTORE_B64`, ou
  a defaut telecharge `keystore/librehu.jks` de LibreHU-service ; elle refuse de publier un APK non signe
  (`apksigner verify`), qu'Android refuserait (« Failed to collect certificates »).
- **`LibreHuAutomationReceiver`** : les memes commandes que `AutomationReceiver`, reservees aux applis signees avec
  la cle LibreHU (permission signature `org.librehu.headunit.permission.CONFIGURE`), configuration comprise sans le
  reglage « Autoriser la configuration externe » (qui reste la barriere pour toutes les autres applis).
- **Navigation virage par virage** : `NAVIGATION_UPDATE` est envoye avec la permission signature
  `org.librehu.headunit.permission.NAVIGATION_UPDATE`, declaree par ce paquet (celle d'Open Headunit,
  `com.andrerinas.headunitrevived.permission.NAVIGATION_UPDATE`, n'est declaree par aucune appli : personne ne
  recevait ces diffusions). LibreHU Launcher l'affiche sur le tableau de bord.
- **CI** (`.github/workflows/librehu.yml`) : saveur `github` en release, publiee en pre-release continue
  `latest-<branche>` avec `release.txt` (LibreHU Apps) et en compilations numerotees ; les workflows d'Open Headunit
  propres a son depot (revue Gemini, issues inactives, APK debug) sont retires, `android-ci.yml` garde.

## Installation
Depuis **LibreHU Apps** (catalogue : « Android Auto (Open Headunit LibreHU) »), ou l'APK de la pre-release
`latest-main` de ce depot.

## Integration LibreHU
- **Vehicule** (`librehu/LibreHuVehicleSync.kt`) : au demarrage et a chaque changement, le nom de la voiture, la
  marque, le modele, l'annee, le cote conducteur et l'adresse Bluetooth de l'autoradio (qu'Android cache aux
  applis) viennent de LibreHU-service (`content://org.librehu.service.vehicle/info`, permission signature
  `org.librehu.permission.VEHICLE`) et remplissent les reglages vehicule d'Open Headunit (envoyes au telephone a la
  connexion). Une valeur vide laisse celle d'Open Headunit.
- **Theme du launcher** (`librehu/LibreHuTheme.kt`) : clair / sombre et couleur d'accent de LibreHU Launcher
  (`content://org.librehu.launcher.theme/theme` et diffusion `org.librehu.action.THEME_CHANGED`), suivis en direct.
  L'accent du launcher (un de ses 8 : bleu, sarcelle, vert, jaune, orange, rouge, rose, violet, en variante claire ou
  sombre) remplace le sarcelle d'Open Headunit : boutons, interrupteurs, dialogues, barres de progression. Les styles
  et les mises en page passent par l'attribut `?attr/librehuAccent` (et `?attr/librehuOnAccent` pour le texte pose
  dessus), et un `ThemeOverlay.LibreHU.*` est pose sur chaque theme d'activite et de dialogue plein ecran. Le mode
  jour / nuit de l'appli devient celui du launcher ; le moteur d'Open Headunit (capteur de lumiere, lever du soleil…)
  est alors en pause. Option **Reglages → Mode sombre → « Suivre le launcher LibreHU »** : activee par defaut et
  visible seulement si le launcher est installe. Desactivee, les reglages de theme d'Open Headunit reviennent. La
  projection n'est pas recreee (seulement un changement de configuration), donc la video continue.
- **Pre-configuration LibreHU** (`librehu/LibreHuDefaults.kt`) : appliquee une fois, si LibreHU Launcher ou
  LibreHU-service est installe, et **seulement pour les reglages jamais choisis**. Un choix de l'utilisateur ou une
  sauvegarde importee reste.

  | Reglage | Valeur | Pourquoi |
  |---|---|---|
  | Demarrage au branchement USB | active | rien a ouvrir a la main en montant en voiture |
  | Connexion automatique si un seul appareil | activee | un seul telephone branche : rien a choisir |
  | Orientation | paysage fixe | dalle 1280x720 montee en paysage |
  | Lecture en cours d'Android Auto dans la session media | activee | titre, artiste et pochette dans le lecteur du launcher et pour les touches du volant |

  Ce qui reste hors de la pre-configuration :
  - la video (resolution, codec, rendu, densite), choisie par l'assistant de premier demarrage d'Open Headunit d'apres
    la taille de la dalle et les decodeurs H.264 / H.265 ;
  - le mode nuit envoye au telephone, qui peut suivre les phares (LibreHU-service) ;
  - l'avertissement de securite de l'assistant, qui n'est pas saute.
- **LibreHU-service** (onglet Android Auto) : etat de la session (`com.andrerinas.headunitrevived.SESSION_STATE`),
  mode jour / nuit selon les phares (`ACTION_SET_NIGHT_MODE`), connexion USB, deconnexion, affichage, reglages ;
  marche aussi avec Open Headunit d'origine (sans la configuration).
- **LibreHU Launcher** : carte Android Auto sur le tableau de bord (etat, afficher, deconnecter) et prochaine
  manoeuvre de la navigation.

## Marques
Android Auto est une marque de Google LLC. Ce fork n'est ni affilie a Google ni certifie par Google ; le nom
« Android Auto » sous l'icone decrit seulement le protocole que l'appli recoit. Les directives de marque de Google
reservent l'usage du nom et du logo Android Auto aux produits approuves
([Brand guidelines](https://developer.android.com/distribute/marketing-tools/brand-guidelines)) : le logo n'est donc
pas inclus dans ce depot public.

## Suivre Open Headunit
```
git remote add upstream https://github.com/andreknieriem/headunit-revived.git   # deja fait dans ce clone
git fetch upstream && git merge upstream/main
```
Les changements du fork sont petits et commentes « LibreHU fork » pour garder les fusions simples.
