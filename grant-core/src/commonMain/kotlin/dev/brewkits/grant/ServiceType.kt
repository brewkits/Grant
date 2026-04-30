package dev.brewkits.grant

/**
 * The types of hardware or system services that can be monitored.
 */
enum class ServiceType {
    /**
     * System location and GPS services.
     *
     * - **Android**: Checked via `LocationManager.isProviderEnabled()`.
     * - **iOS**: Checked via `CLLocationManager.locationServicesEnabled()`.
     */
    LOCATION_GPS,

    /**
     * Bluetooth radio status.
     *
     * - **Android**: Checked via `BluetoothAdapter.isEnabled()`.
     * - **iOS**: Checked via `CBCentralManager.state`.
     */
    BLUETOOTH,

    /**
     * Wi-Fi radio status.
     *
     * - **Android**: Checked via `WifiManager.isWifiEnabled()`.
     * - **iOS**: Limited detection via Network framework.
     */
    WIFI,

    /**
     * Near Field Communication (NFC).
     *
     * - **Android**: Checked via `NfcAdapter.isEnabled()`.
     * - **iOS**: No-op (NFC is managed automatically by the OS).
     */
    NFC,

    /**
     * Availability of the camera hardware.
     *
     * - **Android**: Checks if at least one camera is available on the device.
     * - **iOS**: Checks `AVCaptureDevice` discovery sessions.
     */
    CAMERA_HARDWARE,

    /**
     * Health data services (Apple Health, Health Connect).
     *
     * - **Android**: Checked via Health Connect settings intent resolution.
     * - **iOS**: Checked via `HKHealthStore.isHealthDataAvailable()`.
     */
    HEALTH
}
