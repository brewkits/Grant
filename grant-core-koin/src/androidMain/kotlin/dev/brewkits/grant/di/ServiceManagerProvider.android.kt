package dev.brewkits.grant.di

import android.content.Context
import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.ServiceManager
import org.koin.core.scope.Scope

internal actual fun Scope.createGrantServiceManager(): ServiceManager {
    // Idempotent: init() only stores context.applicationContext.
    ServiceFactory.init(get<Context>())
    return ServiceFactory.createServiceManager()
}
