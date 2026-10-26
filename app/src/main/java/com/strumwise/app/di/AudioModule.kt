package com.strumwise.app.di

import com.strumwise.audio.GuitarEngine
import com.strumwise.audio.OboeGuitarEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    @Singleton
    fun oboeEngine(): OboeGuitarEngine = OboeGuitarEngine()

    @Provides
    @Singleton
    fun guitarEngine(engine: OboeGuitarEngine): GuitarEngine = engine
}
