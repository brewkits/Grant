#include <windows.h>
#include <roapi.h>
#include <shellapi.h>
#include <winrt/Windows.Devices.Enumeration.h>

namespace {
// JNA may enter on a fresh JVM thread or one whose COM apartment belongs to the host UI.
// Balance only our own successful initialization; do not change the host's apartment.
class Apartment final {
public:
    Apartment() : result_(RoInitialize(RO_INIT_MULTITHREADED)) {}
    ~Apartment() { if (SUCCEEDED(result_)) RoUninitialize(); }
    bool usable() const { return SUCCEEDED(result_) || result_ == RPC_E_CHANGED_MODE; }
    Apartment(const Apartment&) = delete;
    Apartment& operator=(const Apartment&) = delete;
private:
    HRESULT result_;
};
}

extern "C" __declspec(dllexport) int grant_windows_bridge_version() noexcept { return 1; }

// 0 = camera, 1 = microphone. Returns Windows DeviceAccessStatus (0..3), or -1 on failure.
// No registry heuristics, media capture, user prompt, or out-of-process identity is involved.
extern "C" __declspec(dllexport) int grant_windows_access_status(int permission) noexcept {
    if (permission != 0 && permission != 1) return -1;
    Apartment apartment;
    if (!apartment.usable()) return -1;
    try {
        using namespace winrt::Windows::Devices::Enumeration;
        const auto device = permission == 0 ? DeviceClass::VideoCapture : DeviceClass::AudioCapture;
        return static_cast<int>(DeviceAccessInformation::CreateFromDeviceClass(device).CurrentStatus());
    } catch (const winrt::hresult_error& failure) {
        return failure.code().value;
    } catch (...) {
        // Exceptions must never cross the C ABI into JNA. Unavailable is not Allowed.
        return -1;
    }
}

extern "C" __declspec(dllexport) int grant_windows_open_settings(int permission) noexcept {
    const wchar_t* uri = nullptr;
    if (permission == 0) uri = L"ms-settings:privacy-webcam";
    if (permission == 1) uri = L"ms-settings:privacy-microphone";
    if (uri == nullptr) return 0;
    const auto result = ShellExecuteW(nullptr, L"open", uri, nullptr, nullptr, SW_SHOWNORMAL);
    return reinterpret_cast<INT_PTR>(result) > 32 ? 1 : 0;
}
