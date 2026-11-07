---

````markdown
# Architecture technique — PhotoSorter

> Auteur : Zoé (Architecte). Référence pour Léna (back-end) et Théo (front-end). Version 1.0.

## 1. Vue d'ensemble

Application Android **100 % locale, hors-ligne**. Scanne le stockage à la recherche d'images, permet parcours/tri/filtre/actions. Aucune donnée ne quitte l'appareil.

Modules :
- **Scanneur de fichiers images** : parcours du stockage, identification des images, métadonnées, cache, classification locale (label « adulte »).
- **IHM** : liste (compact/grille), tri, filtres, plein écran, sélection multiple, actions, libellés.

Architecture en couches : `ui/` ← ViewModel ← `domain/` ← `data/`. Communication entre modules **uniquement via le domaine** (interfaces + modèles).

## 2. Stack

| Domaine | Choix |
|---|---|
| Langage | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target/Compile | 34 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Async | Coroutines + Flow |
| Persistance | Room (SQLite) |
| Images | Coil |
| EXIF | AndroidX ExifInterface |
| Détection adulte | TensorFlow Lite + modèle NSFW embarqué (NNAPI si dispo) |
| Pré-filtre | ML Kit Text Recognition on-device |
| DI | Hilt |
| Tests | JUnit4, Turbine, MockK, Robolectric |

Aucune dépendance réseau ; pas de permission INTERNET.

## 3. Structure

```
app/src/main/java/fr/photosorter/
  PhotosorterApp.kt, MainActivity.kt
  core/ (dispatchers, result/AppResult, ext)
  domain/
    model/ (ImageItem, ImageFormat, Label, ScanProgress, SortCriteria, FilterState)
    repository/ (ImageRepository, LabelRepository, ScanRepository)
    usecase/
  data/
    db/ (Room: AppDatabase, DAO, entities)
    repository/ (implémentations)
    scanner/ (ScanEngine, ScannerFileWalker, MimeTypeDetector)
    media/ (MediaStoreQuery, ThumbnailSource)
    moderation/ (PreFilter, AdultClassifier, ModerationPipeline)
  ui/
    theme/, navigation/
    gallery/, detail/, labels/, components/
```

## 4. Flux de données

### Scan
IHM → ViewModel.StartScan → ScanRepository → ScanEngine (Dispatchers.IO) :
1. MediaStoreQuery (interne + SD)
2. parcours récursif
3. exclusions /Android/data, /Android/obb, assets système
4. MIME (extension + type)
5. métadonnées (taille, date, format, dimensions)
6. PreFilter (texte/screenshot) puis AdultClassifier (TFLite) si non exclu
7. écriture par lots dans Room
→ ScanProgress émis via Flow → ViewModel StateFlow (progression temps réel)

### Affichage / tri / filtre
IHM → observeImages(filter, sort) → DAO Room (WHERE + ORDER BY) → StateFlow<List<ImageItem>>. Tri/filtres poussés en SQL.

### Actions
IHM → UseCase → Repository (MediaStore/File + Room) → AppResult ; UI rafraîchie par le Flow Room.

## 5. Modèles du domaine

```kotlin
data class ImageItem(
    val id: Long, val uri: String, val name: String, val format: ImageFormat,
    val sizeBytes: Long, val dateTaken: Long?, val dateModified: Long,
    val width: Int, val height: Int, val labels: Set<String>,
    val isAdult: Boolean, val thumbnailHint: String?
)
enum class ImageFormat { JPEG, PNG, HEIC, WEBP, GIF, BMP, RAW }
data class Label(val name: String, val isSystem: Boolean, val createdAt: Long)
sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Running(val done: Int, val total: Int, val current: String) : ScanProgress
    data class Completed(val count: Int, val durationMs: Long) : ScanProgress
    data class Error(val message: String) : ScanProgress
}
data class SortCriteria(val field: SortField, val ascending: Boolean)
enum class SortField { NAME, DATE, SIZE, FORMAT, LABEL }
data class FilterState(
    val formats: Set<ImageFormat> = emptySet(),
    val dateFrom: Long? = null, val dateTo: Long? = null,
    val minSizeBytes: Long? = null, val labels: Set<String> = emptySet(),
    val includeAdult: Boolean = true
)
```

## 6. Interfaces entre modules

```kotlin
interface ImageRepository {
    fun observeImages(sort: SortCriteria, filter: FilterState): Flow<List<ImageItem>>
    suspend fun getImages(filter: FilterState): List<ImageItem>
    suspend fun delete(items: List<ImageItem>): AppResult<Unit>
    suspend fun move(items: List<ImageItem>, destUri: String): AppResult<Unit>
    suspend fun copy(items: List<ImageItem>, destUri: String): AppResult<Unit>
    suspend fun share(items: List<ImageItem>): AppResult<Unit>
}
interface LabelRepository {
    fun observeLabels(): Flow<List<Label>>
    suspend fun createLabel(name: String): AppResult<Label>
    suspend fun renameLabel(oldName: String, newName: String): AppResult<Unit>
    suspend fun deleteLabel(name: String): AppResult<Unit>
    suspend fun assign(items: List<ImageItem>, label: String): AppResult<Unit>
    suspend fun unassign(items: List<ImageItem>, label: String): AppResult<Unit>
}
interface ScanRepository {
    suspend fun runScan(): Flow<ScanProgress>
    fun lastScanSummary(): Flow<ScanProgress?>
}
```

Règle : l'IHM ne connaît que ces interfaces + modèles du domaine. Pas d'import croisé ui ↔ data.

## 7. Room

```
images (cache): id PK, uri, name, format, sizeBytes, dateTaken, dateModified,
                width, height, thumbnailHint, scanBatchId
labels: name PK, isSystem INTEGER, createdAt INTEGER
image_labels (n-n): imageId, labelName, PK(imageId,labelName), FK cascade
scan_state: id=1, lastScanAt, lastCount, lastDurationMs
```

Label « adulte » = libellé système (`isSystem=true`), non supprimable.

## 8. Classification contenu adulte (local, sans réseau)

1. PreFilter : exclure screenshots/documents (ML Kit text, densité texte, bords rectilignes). **Pas de colorimétrie/texture en exclusion** (faux négatifs). Erreur → passer à l'IA (conservateur).
2. AdultClassifier TFLite : bitmap redimensionné → score [0,1].
3. Seuil conservateur τ (label proposé si score ≥ τ), configurable.
4. Label révisable par l'utilisateur.
5. Cache : pas de recalcul si (id + dateModified) inchangé.

Optimisations : TFLite seulement sur non-exclus, batching, feature flag. Mesurer avant davantage de complexité.

## 9. Permissions

- API 33+ : `READ_MEDIA_IMAGES`
- API 26-32 : `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` (maxSdk 32)
- Pas de réseau.
- Actions via MediaStore (scoped storage) API 29+, fallback File API 26-28.

## 10. Contrats ViewModel

```kotlin
class GalleryViewModel(...) : ViewModel() {
    val uiState: StateFlow<GalleryUiState>
    fun setSort(c: SortCriteria); fun updateFilter(f: FilterState)
    fun toggleSelection(id: Long)
    fun applyLabel(n: String); fun clearLabel(n: String)
    fun deleteSelection(); fun shareSelection()
}
class ScanViewModel(repo: ScanRepository) : ViewModel() {
    val progress: StateFlow<ScanProgress>; fun startScan()
}
// GalleryUiState: mode (COMPACT|GRID), sort, filter, selectedIds, images, isLoading, error
```

## 11. Standards de code

- Aucune dépendance réseau ; pas d'API externe dans `data/`.
- Strings FR externalisés, pas de texte en dur dans Compose.
- contentDescription sur images/actions.
- Tests : use cases (JUnit), ViewModels (Turbine), DAO (in-memory Room), pipeline modération.

## 12. Découpage des tâches

### Léna (back-end)
1. Socle Gradle + Hilt + core + modèles domaine.
2. Room (entités, DAO tri/filtre, seed label adulte).
3. ScanEngine (MediaStore, récursif, exclusions, MIME, métadonnées).
4. Repositories image/label + use cases.
5. Modération (PreFilter, AdultClassifier TFLite, seuil, cache, intégration scan).
6. Actions fichiers (delete/move/copy/share + fallback).
7. Tests unitaires back-end.

### Théo (front-end)
1. Galerie (compact + grille, badges, sélection multiple).
2. Tri & filtres.
3. Plein écran (zoom, prev/next, EXIF).
4. Action bar / dialogues.
5. Gestion libellés + révision label adulte.
6. Écran scan / progression.
7. Thème Material 3 + accessibilité, strings FR.

Théo consomme les contrats §5/§6/§10 ; Léna livre progressivement (stubs/mocks côté UI).

## 13. Risques

- Performance TFLite → mesurer ; feature flag de désactivation.
- Taille APK (modèle 5-20 Mo) → R8, modèle séparable.
- RAW sans prévisualisation → placeholder.
- Scoped storage vs legacy → stratégie dédoublée.
- Progression temps réel sur milliers de fichiers sans bloquer l'UI.
````

---
