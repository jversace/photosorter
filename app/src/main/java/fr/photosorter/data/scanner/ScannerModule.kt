package fr.photosorter.data.scanner

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {

    @Provides @Singleton
    fun provideMimeTypeDetector(): MimeTypeDetector = MimeTypeDetector()

    @Provides @Singleton
    fun provideStorageRootProvider(): StorageRootProvider = DefaultStorageRootProvider()
}
