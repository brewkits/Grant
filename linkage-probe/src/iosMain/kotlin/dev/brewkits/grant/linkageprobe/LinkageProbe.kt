package dev.brewkits.grant.linkageprobe

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantFactory
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType

/**
 * Everything a camera-only app does with Grant (Issue #91's reporter): a GrantManager used only
 * for CAMERA and a ServiceManager used only for CAMERA_HARDWARE.
 *
 * These functions MUST stay public and MUST call into Grant. They are this framework's exported
 * surface. If nothing here reached Grant, Kotlin/Native would strip all of it and every
 * "framework absent" check in scripts/check-ios-linkage.sh would pass vacuously. The script's
 * positive control (AVCaptureDevice must be present) is what catches that.
 *
 * Do not add any other AppGrant or ServiceType here. That would hide exactly the leak the
 * check exists to measure.
 */
public object LinkageProbe {
    private val grants = GrantFactory.create()
    private val services = ServiceFactory.createServiceManager()

    public suspend fun cameraStatus(): GrantStatus = grants.checkStatus(AppGrant.CAMERA)

    public suspend fun requestCamera(): GrantStatus = grants.request(AppGrant.CAMERA)

    public suspend fun cameraHardware(): ServiceStatus =
        services.checkServiceStatus(ServiceType.CAMERA_HARDWARE)
}
