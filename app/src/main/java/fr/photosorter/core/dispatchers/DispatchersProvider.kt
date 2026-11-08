package fr.photosorter.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Fournisseur de dispatchers injectable (testabilité).
 * L'implémentation par défaut est liée dans le module Hilt (core.di).
 */
interface DispatchersProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}
