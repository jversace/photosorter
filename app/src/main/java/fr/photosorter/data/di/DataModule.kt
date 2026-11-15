package fr.photosorter.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.photosorter.data.moderation.PreFilter
import fr.photosorter.data.scanner.DefaultStorageRootProvider
import fr.photosorter.data.scanner.MediaMetadataReader
import fr.photosorter.data.scanner.MimeTypeDetector
import fr.photosorter.data.scanner.StorageRootProvider
import javax.inject.Singleton

/**
 * Fournit les collaborateurs "data" qui ne sont pas annotés @Inject :
 * - un alias de [Context] non qualifié (utilisé par ScanEngine et AdultClassifier),
 * - la factory [MediaMetadataReader],
 * - [PreFilter], [MimeTypeDetector], [StorageRootProvider].
 *
 * Seul module fournissant ces bindings (ScannerModule supprimé : doublon).
 * Aucun constructeur de classe n'est modifié : on complète uniquement le graphe Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Alias non qualifié de [Context] : permet d'injecter `Context` sans
     * @ApplicationContext dans les classes data existantes (ScanEngine,
     * AdultClassifier) sans modifier leurs constructeurs.
     */
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideMediaMetadataReader(
        @ApplicationContext context: Context
    ): MediaMetadataReader = MediaMetadataReader(context.contentResolver)

    /**
     * Factory utilisée par [fr.photosorter.data.scanner.ScanEngine] :
     * `metadataReaderFactory: (Context) -> MediaMetadataReader`.
     * @JvmSuppressWildcards évite le conflit de variance Kotlin côté Hilt.
     */
    @Provides
    @Singleton
    fun provideMediaMetadataReaderFactory(): @JvmSuppressWildcards (Context) -> MediaMetadataReader =
        { context -> MediaMetadataReader(context.contentResolver) }

    @Provides
    @Singleton
    fun providePreFilter(): PreFilter = PreFilter()

    @Provides
    @Singleton
    fun provideMimeTypeDetector(): MimeTypeDetector = MimeTypeDetector()

    @Provides
    @Singleton
    fun provideStorageRootProvider(): StorageRootProvider = DefaultStorageRootProvider()
}
