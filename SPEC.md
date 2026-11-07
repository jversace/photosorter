# Spécifications PhotoSorter

> Basées sur le cahier des charges initial (voir `docs/cahier-des-charges.md`)

## Modules

### Scanneur de fichiers images
- Scan manuel déclenché par l'utilisateur
- Parcours récursif du stockage interne et carte SD
- Détection par extension et type MIME
- Exclusion des répertoires système Android (`/Android/data`, `/Android/obb`, assets d'apps)
- Progression affichée en temps réel
- Cache du résultat de scan
- Détection de contenu adulte : pré-filtre screenshots/documents (sans IA) + modèle TFLite embarqué local
- Label "adulte" assigné automatiquement, révisable par l'utilisateur
- Aucun envoi de données à l'extérieur

### IHM
- Liste des fichiers en 2 modes : compact (nom, format, taille, date, libellés) et grille de vignettes
- Tri : nom, date, taille, format, libellé (ordre croissant/décroissant)
- Filtres : format, plage de dates, taille minimale, libellé (dont filtre "adulte")
- Visualisation plein écran avec zoom, navigation précédent/suivant, métadonnées EXIF
- Sélection multiple (appui long ou case à cocher)
- Actions : supprimer (avec confirmation), déplacer, copier, partager, assigner/retirer un libellé
- Système de libellés personnalisés (créer, renommer, supprimer) stockés en base locale
- Label "adulte" : libellé système, badge discret sur vignettes

## Contraintes techniques
- Android 8.0 minimum (API 26)
- 100% local, aucune donnée transmise à l'extérieur
- Permissions : `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`
- Langue : Français
- Design : Material Design Android
- Stockage libellés : Room / SQLite
- Modèle détection adulte : TensorFlow Lite embarqué (~5-20 Mo), compatible NNAPI

## Formats supportés
JPEG, PNG, HEIC/HEIF, WebP, GIF, BMP, RAW (CR2, DNG, NEF) — RAW sans prévisualisation garantie

## Hors périmètre v1
- Cloud, reconnaissance faciale, édition, vidéos