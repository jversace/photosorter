package fr.photosorter.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.photosorter.data.repository.ImageRepositoryImpl
import fr.photosorter.data.repository.LabelRepositoryImpl
import fr.photosorter.data.repository.ScanRepositoryImpl
import fr.photosorter.domain.repository.ImageRepository
import fr.photosorter.domain.repository.LabelRepository
import fr.photosorter.domain.repository.ScanRepository
import javax.inject.Singleton

/** Lie les interfaces de domaine à leurs implémentations data. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds
    @Singleton
    abstract fun bindLabelRepository(impl: LabelRepositoryImpl): LabelRepository

    @Binds
    @Singleton
    abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository
}
