# Cahier des charges — PhotoSorter

## 1. Présentation du projet

**PhotoSorter** est une application mobile Android permettant de scanner l'ensemble des fichiers du téléphone afin d'identifier, visualiser, trier et libeller les photos. Elle offre à l'utilisateur une vue claire de sa photothèque et des outils pour la gérer efficacement.

---

## 2. Objectifs

- Identifier tous les fichiers images présents sur le téléphone (hors fichiers système)
- Présenter ces fichiers de manière lisible, triable et filtrable
- Permettre de taguer/libeller les photos avec des libellés personnalisés
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
- Par libellé

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

### 4.5 Actions sur les fichiers (Module : IHM)
- **Sélection multiple** via case à cocher ou appui long
- Actions disponibles sur la sélection :
  - Supprimer (avec confirmation)
  - Déplacer vers un dossier
  - Copier vers un dossier
  - Partager (intent Android natif)
  - Assigner / retirer un libellé

---

## 5. Contraintes techniques

| Paramètre | Valeur |
|---|---|
| Plateforme | Android |
| Version minimale | Android 8.0 (API 26) |
| Fonctionnement | Local (pas de synchronisation cloud) |
| Stockage des libellés | Base de données locale (Room / SQLite) |
| Permissions requises | `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (selon API), `WRITE_EXTERNAL_STORAGE` (pour déplacement/suppression) |
| Langue de l'interface | Français |

---

## 6. Contraintes UX / Design

- Interface épurée, adaptée au grand public
- Pas de compte utilisateur requis
- Aucune donnée transmise à l'extérieur du téléphone
- Respect des guidelines Material Design Android

---

## 7. Hors périmètre (v1)

- Synchronisation cloud
- Reconnaissance faciale ou IA de classification
- Édition de photos
- Gestion de vidéos

---

## 8. Livrables attendus

- Application Android (APK / AAB)
- Code source versionné sur le dépôt Git
- Documentation technique de base
