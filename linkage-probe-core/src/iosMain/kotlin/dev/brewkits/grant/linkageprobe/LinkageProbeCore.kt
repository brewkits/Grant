package dev.brewkits.grant.linkageprobe

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantFactory
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType

/**
 * A grant-core-only app: notifications plus the Location Services check. Neither may pull in
 * CoreLocation, Photos or AVFoundation. Before 3.0.0, both did, through `handlerFor()` and
 * `PlatformServiceDelegate`.
 *
 * Keep these public and calling into Grant. They are the framework's exported surface, and
 * scripts/check-ios-linkage.sh's CONTROL (UserNotifications present) proves they survived DCE.
 */
public object LinkageProbeCore {
    private val grants = GrantFactory.create()
    private val services = ServiceFactory.createServiceManager()

    public suspend fun notificationStatus(): GrantStatus = grants.checkStatus(AppGrant.NOTIFICATION)

    public suspend fun requestNotification(): GrantStatus = grants.request(AppGrant.NOTIFICATION)

    public suspend fun locationServices(): ServiceStatus =
        services.checkServiceStatus(ServiceType.LOCATION_GPS)
}
