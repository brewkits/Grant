package dev.brewkits.grant

/**
 * The types of hardware or system services that can be monitored.
 */
public enum class ServiceType {
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
     * - **iOS**: not readable. iOS exposes no public API for the Wi-Fi radio state, so this always
     *   reports [ServiceStatus.ENABLED]. That is optimistic, not a measurement: it keeps
     *   Wi-Fi-gated flows from being permanently blocked (an `UNKNOWN` would never count as
     *   ready). Handle connectivity failures in your own network layer.
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
