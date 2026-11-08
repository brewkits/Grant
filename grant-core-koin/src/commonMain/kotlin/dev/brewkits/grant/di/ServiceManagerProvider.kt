package dev.brewkits.grant.di

import dev.brewkits.grant.ServiceManager
import org.koin.core.scope.Scope

/**
 * Builds the [ServiceManager] for [grantModule].
 *
 * Platform-specific because Android's `ServiceFactory` needs a `Context` that only the Koin graph
 * has: calling `ServiceFactory.createServiceManager()` directly threw "ServiceFactory not
 * initialized" for an app that used exactly the documented setup (`androidContext(...)` +
 * `grantModule` + `grantPlatformModule`), because nothing on that path ever called
 * `ServiceFactory.init(context)`.
 */
internal expect fun Scope.createGrantServiceManager(): ServiceManager
