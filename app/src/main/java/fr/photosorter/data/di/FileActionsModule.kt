package fr.photosorter.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.photosorter.data.repository.FileActions
import javax.inject.Singleton

/** Fournit [FileActions] (actions fichiers scoped storage + fallback). */
@Module
@InstallIn(SingletonComponent::class)
object FileActionsModule {

    @Provides
    @Singleton
    fun provideFileActions(@ApplicationContext context: Context): FileActions = FileActions(context)
}
