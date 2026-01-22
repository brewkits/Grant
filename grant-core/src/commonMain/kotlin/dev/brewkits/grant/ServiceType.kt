package dev.brewkits.grant

/**
 * System services that need to be enabled for grants to work properly.
 *
 * Even when a grant is GRANTED, the underlying system service might be disabled,
 * preventing the feature from working.
 *
 * **Example**: Location grant is GRANTED, but GPS is turned off.
 */
enum class ServiceType {
    /**
     * Location/GPS service
     * - Android: LocationManager.isProviderEnabled()
     * - iOS: CLLocationManager.locationServicesEnabled()
     */
    LOCATION_GPS,

    /**
     * Bluetooth service
     * - Android: BluetoothAdapter.isEnabled()
     * - iOS: CBCentralManager.state == .poweredOn
     */
    BLUETOOTH,

    /**
     * Wi-Fi service (optional)
     * - Android: WifiManager.isWifiEnabled()
     * - iOS: Network framework
     */
    WIFI,

    /**
     * NFC service (Android only)
     * - Android: NfcAdapter.isEnabled()
     * - iOS: N/A
     */
    NFC,

    /**
     * Camera hardware availability
     * - Android: Camera availability check
     * - iOS: AVCaptureDevice availability
     */
    CAMERA_HARDWARE
}
