# PhotoSorter v1.0.0 - Guide d'installation

applicationId: fr.photosorter | minSdk: 26 (Android 8.0) | versionName: 1.0.0

## 1. Prerequis

Generation : Android Studio Hedgehog 2023.1+, JDK 17, Android SDK API 34, Gradle wrapper inclus.
Installation : smartphone Android 8.0+ (API 26+), fichier app-release.apk.

## 1bis. Wrapper Gradle (bug 2086c768)

Le depot fournit `gradlew` (Unix), `gradlew.bat` (Windows) et
`gradle/wrapper/gradle-wrapper.properties` (Gradle **8.9**, requis par AGP 8.5.2).

⚠️ Le binaire `gradle/wrapper/gradle-wrapper.jar` (~60 Ko) ne peut pas etre committe
en texte par les agents. Il doit etre genere **une seule fois** depuis un poste avec Gradle
installe, puis committe :

    gradle wrapper --gradle-version 8.9 --distribution-type bin

Sans ce `.jar`, `./gradlew` echoue avec
`ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`.

## 2. Creer un keystore de release

keytool -genkeypair -alias photosorter -keyalg RSA -keysize 2048 -validity 10000 -keystore release.keystore -storetype PKCS12

Conservez ce fichier : indispensable pour toutes les mises a jour.

## 3. Configurer la signature

Copiez keystore.properties.example en keystore.properties (non versionne) :

storeFile=release.keystore
storePassword=MOT_DE_PASSE_KEYSTORE
keyAlias=photosorter
keyPassword=MOT_DE_PASSE_CLE

Ne commitez jamais keystore.properties ni release.keystore (exclus par .gitignore).

## 4. Generer l'APK signe

./gradlew assembleRelease

Sortie : app/build/outputs/apk/release/app-release.apk

## 5. Generer l'AAB (Play Store uniquement)

./gradlew bundleRelease

Sortie : app/build/outputs/bundle/release/app-release.aab
L'AAB ne s'installe pas directement en sideloading.

## 6. Verifier la signature

$ANDROID_SDK/build-tools/34.0.0/apksigner verify --verbose app-release.apk
$ANDROID_SDK/build-tools/34.0.0/aapt dump badging app-release.apk | grep -E "package|versionCode|versionName|minSdk"
$ANDROID_SDK/build-tools/34.0.0/aapt dump permissions app-release.apk | grep INTERNET
unzip -l app-release.apk | grep tflite

Resultats attendus :
- Signature v2 : Verifies
- package name='fr.photosorter' versionCode='1' versionName='1.0.0' minSdkVersion:'26'
- Aucune permission INTERNET
- assets/model.tflite et assets/labels.txt presents

## 7. Installer en sideloading (sans cable)

1. Transferez app-release.apk sur le smartphone (cable USB, Bluetooth, e-mail...)
2. Autorisez les sources inconnues :
   Android 8.0-8.1 : Parametres > Securite > Sources inconnues
   Android 9+ : Parametres > Installer des applis inconnues > autoriser l'appli utilisee
3. Ouvrez l'APK depuis le gestionnaire de fichiers et confirmez l'installation
4. Si Play Protect affiche un avertissement, choisissez Installer quand meme

## 8. Installer via ADB (cable USB)

1. Mode developpeur : Parametres > A propos > appuyer 7 fois sur Numero de build
2. Debogage USB : Parametres > Options developpeur > Debogage USB
3. Connectez le cable et executez :
   adb devices
   adb install -r app-release.apk
Message attendu : Success

## 9. Premiers pas

1. Accordez les permissions au premier lancement :
   Android 13+ : READ_MEDIA_IMAGES
   Android 8-12 : READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
2. Appuyez sur Lancer un scan pour indexer toutes les photos
3. Parcourez la galerie (liste ou grille), triez, filtrez, visualisez en plein ecran
4. Assignez des libelles, utilisez le filtre adulte pour le contenu sensible

## 10. Mise a jour

Incrementez versionCode dans app/build.gradle.kts, regenerez l'APK et installez avec :
adb install -r app-release.apk

## 11. Desinstallation

adb uninstall fr.photosorter
Ou via Parametres > Applications > PhotoSorter > Desinstaller.

## 12. Depannage

- APK refuse par Play Protect : choisir Installer quand meme
- Permission refusee au scan : Parametres > Applications > PhotoSorter > Permissions
- APK non signe : verifier que keystore.properties est present et correct
- Modele TFLite absent : verifier app/assets/model.tflite et labels.txt dans le projet
- Build echoue : verifier JDK 17 (java -version) et Android SDK installe
- ./gradlew: ClassNotFoundException GradleWrapperMain : le gradle-wrapper.jar est absent (voir section 1bis)
