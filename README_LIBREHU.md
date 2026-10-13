# Open Headunit LibreHU

Fork d'[Open Headunit](https://github.com/andreknieriem/headunit-revived) (ex-Headunit Revived, de Michael Reid
puis Andre Rinas), recepteur Android Auto en appli Android, pour les autoradios LibreHU (AC8257 / UJC201).
Licence inchangee : **AGPL-3.0** (voir `LICENSE` et `COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`) ; le code de ce fork
est publie avec ses APK.

## Ce que change le fork
- **Paquet `org.librehu.headunit`** (« Open Headunit LibreHU ») : s'installe a cote d'Open Headunit. Le namespace du
  code et les actions d'automatisation (`com.andrerinas.openheadunit.ACTION_*`) ne changent pas.
- **Signe avec la cle LibreHU** (`keystore/librehu.jks`, ou les secrets `LIBREHU_*` de la CI), comme toutes les
  applis LibreHU : LibreHU-service et LibreHU Launcher lui font confiance par signature.
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

## Integration LibreHU
- **LibreHU-service** (onglet Android Auto) : etat de la session (`com.andrerinas.headunitrevived.SESSION_STATE`),
  mode jour / nuit selon les phares (`ACTION_SET_NIGHT_MODE`), connexion USB, deconnexion, affichage, reglages ;
  marche aussi avec Open Headunit d'origine (sans la configuration).
- **LibreHU Launcher** : carte Android Auto sur le tableau de bord (etat, afficher, deconnecter) et prochaine
  manoeuvre de la navigation.

## Suivre Open Headunit
```
git remote add upstream https://github.com/andreknieriem/headunit-revived.git   # deja fait dans ce clone
git fetch upstream && git merge upstream/main
```
Les changements du fork sont petits et commentes « LibreHU fork » pour garder les fusions simples.
