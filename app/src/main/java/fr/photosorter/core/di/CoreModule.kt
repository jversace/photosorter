package fr.photosorter.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.photosorter.core.dispatchers.DefaultDispatchersProvider
import fr.photosorter.core.dispatchers.DispatchersProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindDispatchersProvider(impl: DefaultDispatchersProvider): DispatchersProvider
}
