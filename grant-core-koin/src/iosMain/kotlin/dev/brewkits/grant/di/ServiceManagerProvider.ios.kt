package dev.brewkits.grant.di

import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.ServiceManager
import org.koin.core.scope.Scope

internal actual fun Scope.createGrantServiceManager(): ServiceManager = ServiceFactory.createServiceManager()
