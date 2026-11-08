package fr.photosorter.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "photosorter.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideImageDao(db: AppDatabase) = db.imageDao()
    @Provides fun provideLabelDao(db: AppDatabase) = db.labelDao()
    @Provides fun provideImageLabelDao(db: AppDatabase) = db.imageLabelDao()
    @Provides fun provideImageQueryDao(db: AppDatabase) = db.imageQueryDao()
}
