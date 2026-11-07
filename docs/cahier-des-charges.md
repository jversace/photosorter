# Cahier des charges — PhotoSorter

## 1. Présentation du projet

**PhotoSorter** est une application mobile Android permettant de scanner l'ensemble des fichiers du téléphone afin d'identifier, visualiser, trier et libeller les photos. Elle offre à l'utilisateur une vue claire de sa photothèque et des outils pour la gérer efficacement.

---

## 2. Objectifs

- Identifier tous les fichiers images présents sur le téléphone (hors fichiers système)
- Présenter ces fichiers de manière lisible, triable et filtrable
- Permettre de taguer/libeller les photos avec des libellés personnalisés
- Détecter automatiquement les images à caractère adulte et les labelliser
- Permettre des actions simples sur les fichiers sélectionnés
- Offrir une interface intuitive, adaptée au grand public

---

## 3. Périmètre

### 3.1 Stockage couvert
- Stockage interne du téléphone
- Carte SD externe (si présente)
- Tous les dossiers, à l'exception des répertoires système Android (icônes, ressources d'applications, etc.)

### 3.2 Formats supportés
- JPEG / JPG
- PNG
- HEIC / HEIF
- WebP
- GIF (fichier statique)
- BMP
- RAW (CR2, DNG, NEF…) — support en lecture seule, sans prévisualisation garantie

---

## 4. Fonctionnalités

### 4.1 Scan des fichiers (Module : Scanneur de fichiers images)
- Lancement manuel du scan depuis l'interface
- Parcours récursif de l'arborescence du stockage interne et de la carte SD
- Détection des fichiers images par extension et/ou type MIME
- Exclusion des répertoires système Android (ex : `/Android/data`, `/Android/obb`, dossiers d'assets d'apps)
- Affichage de la progression du scan en temps réel
- Résultat mis en cache pour accès rapide sans nouveau scan

### 4.2 Liste des fichiers (Module : IHM)

#### Mode sans prévisualisation
- Liste compacte avec : nom du fichier, format, taille, date, libellés associés

#### Mode avec prévisualisation
- Grille de vignettes (thumbnails) générées à la volée

#### Tri disponible
| Critère | Ordre |
|---|---|
| Nom | A→Z / Z→A |
| Date | Plus récent / Plus ancien |
| Taille | Plus grand / Plus petit |
| Format | Regroupement par extension |
| Libellé | Regroupement par libellé |

#### Filtres
- Par format de fichier
- Par plage de dates
- Par taille minimale (ex : > 5 Mo)
- Par libellé (dont filtre spécifique sur le label "adulte")

### 4.3 Visualisation plein écran (Module : IHM)
- Affichage plein écran d'une photo sélectionnée
- Zoom et défilement tactile
- Navigation vers la photo précédente / suivante
- Affichage des métadonnées EXIF (date, résolution, appareil, coordonnées GPS si disponibles)
- Affichage des libellés associés

### 4.4 Système de libellés (Module : IHM)
- L'utilisateur peut créer ses propres libellés (ex : "Vacances", "À garder", "À supprimer")
- Un ou plusieurs libellés peuvent être assignés à une photo
- Les libellés sont stockés dans une base de données locale à l'application (aucune modification des fichiers originaux)
- Gestion des libellés : créer, renommer, supprimer
- Le libellé **"adulte"** est un libellé système, assigné automatiquement par le moteur de détection (voir 4.6), révisable manuellement par l'utilisateur

### 4.5 Actions sur les fichiers (Module : IHM)
- **Sélection multiple** via case à cocher ou appui long
- Actions disponibles sur la sélection :
  - Supprimer (avec confirmation)
  - Déplacer vers un dossier
  - Copier vers un dossier
  - Partager (intent Android natif)
  - Assigner / retirer un libellé

### 4.6 Détection de contenu adulte (Module : Scanneur de fichiers images)

L'application intègre un moteur de détection automatique des images à caractère sexuel, érotique ou pornographique, fonctionnant **entièrement en local**, sans aucun envoi de données à l'extérieur.

#### Approche hybride en deux étapes :

**Étape 1 — Pré-filtre léger (sans IA)**
- Identification et exclusion des images manifestement safe : screenshots et documents (détection d'éléments d'interface, texte dense, etc.)
- Ces images sont écartées de l'analyse IA sans être soumises au modèle
- Objectif : réduire le volume soumis au modèle et alléger la consommation CPU/batterie

**Étape 2 — Classification IA locale (TensorFlow Lite)**
- Les images non écartées par le pré-filtre sont analysées par un modèle TFLite embarqué
- Le modèle est exécuté entièrement sur l'appareil (aucun appel réseau)
- Seuil de classification conservateur pour limiter les faux positifs
- Les images classifiées comme adultes reçoivent automatiquement le label **"adulte"**

#### Révision manuelle
- L'utilisateur peut retirer le label "adulte" d'une image (faux positif)
- L'utilisateur peut ajouter manuellement le label "adulte" à une image

#### Contraintes
- Aucune donnée (image ou métadonnée) n'est transmise à l'extérieur de l'appareil
- Le modèle TFLite est embarqué dans l'application (~5-20 Mo)
- Compatible Android 8.0 (API 26) via NNAPI / TFLite
- La détection s'effectue lors du scan, en arrière-plan

---

## 5. Contraintes techniques

| Paramètre | Valeur |
|---|---|
| Plateforme | Android |
| Version minimale | Android 8.0 (API 26) |
| Fonctionnement | Local uniquement (pas de synchronisation cloud, pas d'appel réseau) |
| Stockage des libellés | Base de données locale (Room / SQLite) |
| Modèle de détection adulte | TensorFlow Lite embarqué |
| Permissions requises | `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (selon API), `WRITE_EXTERNAL_STORAGE` (pour déplacement/suppression) |
| Langue de l'interface | Français |

---

## 6. Contraintes UX / Design

- Interface épurée, adaptée au grand public
- Pas de compte utilisateur requis
- Aucune donnée transmise à l'extérieur du téléphone
- Respect des guidelines Material Design Android
- Le label "adulte" est discret mais visible (badge sur la vignette, mention dans la liste)

---

## 7. Hors périmètre (v1)

- Synchronisation cloud
- Reconnaissance faciale
- Édition de photos
- Gestion de vidéos

---

## 8. Livrables attendus

- Application Android (APK / AAB)
- Code source versionné sur le dépôt Git
- Documentation technique de base
