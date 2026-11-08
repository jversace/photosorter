package fr.photosorter.data.scanner

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

    @Provides @Singleton
    fun provideMimeTypeDetector(): MimeTypeDetector = MimeTypeDetector()

    @Provides @Singleton
    fun provideStorageRootProvider(): StorageRootProvider = DefaultStorageRootProvider()

    @Provides
    fun provideMetadataReaderFactory(@ApplicationContext context: Context): (Context) -> MediaMetadataReader =
        { ctx -> MediaMetadataReader(ctx.contentResolver) }
}
