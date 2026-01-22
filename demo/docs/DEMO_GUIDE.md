# 📱 Grant Demo App - Hướng Dẫn Sử Dụng

## Tình trạng hiện tại

### ✅ **Moko Implementation** - ĐANG HOẠT ĐỘNG (Android)
- Sử dụng thư viện Moko Grants (real grants)
- Có thể test system grant dialog, rationale dialog và settings dialog
- `openSettings()` hoạt động bình thường
- Hiện tại chỉ hoạt động trên Android, iOS đang được debug.

### ✅ **Custom Implementation** - ĐANG HOẠT ĐỘNG
- Mode simulation: deny → deny → grant
- Có thể test rationale dialog và settings dialog
- `openSettings()` hoạt động bình thường

---

## Cách Test Demo App (Android)

### 1. Mở App
```bash
adb shell am start -n dev.brewkits.grant.demo/dev.brewkits.grant.demo.MainActivity
```

### 2. Verify Mode
Ở đầu screen, kiểm tra:
- **Implementation Type**: "Moko (Recommended)" ✅
- **Simulation Mode**: "Real" ✅

### 3. Test Grant Flow (Moko - Real Grants)

#### Test Case: Request Camera

**Bước 1: Request lần đầu**
```
1. Click "Request Camera → Microphone"
2. ✅ System Camera Grant Dialog xuất hiện:
   "Allow GrantDemo to take pictures and record video?"
   [While using the app] [Only this time] [Don't allow]
```

**Bước 2: Deny lần 1 → Rationale Dialog (Tùy chỉnh) xuất hiện**
```
3. Chọn "Don't allow"
4. ✅ Rationale Dialog tùy chỉnh xuất hiện:
   "Camera is required to capture video for your recordings"
   [Grant Grant] [Cancel]
```

**Bước 3: Deny lần 2 → Settings Dialog (Tùy chỉnh) xuất hiện**
```
5. Click "Grant Grant" trong Rationale Dialog
6. ✅ System Camera Grant Dialog xuất hiện LẦN 2. Chọn "Don't allow" lần nữa.
7. ✅ Settings Dialog tùy chỉnh xuất hiện:
   "Camera access is disabled. Enable it in Settings > Grants > Camera"
   [Open Settings] [Cancel]
```

**Bước 4: Click Open Settings → Cài đặt ứng dụng mở ra**
```
8. Click "Open Settings"
9. ✅ Android Settings app mở ra (cụ thể là cài đặt ứng dụng GrantDemo)
10. Từ trong Settings, bạn cần TỰ CẤP QUYỀN Camera thủ công (bật công tắc)
11. Quay lại demo app
12. Click "Request Camera → Microphone" LẦN 3
13. ✅ Grant GRANTED! Success message hiện
```

### 4. Check Logs (Moko - Real Grants)
```bash
adb logcat | grep -E "Moko"
```

Expected output (ví dụ):
```
I/MokoGrants: Requesting grant: Camera
I/MokoGrants: Grant Camera status: Denied
I/MokoGrants: Opening app settings
```

---

## Simulation Modes (Chỉ dành cho Custom Implementation)

### 🔄 **Realistic**
- Lần 1: DENIED → Rationale dialog
- Lần 2: DENIED_ALWAYS → Settings dialog
- Lần 3+: GRANTED ✅
- **Best cho demo UI!**

### 🎯 **Real**
⚠️ Custom Implementation không support runtime request.
- Sẽ thấy log: "Runtime request not implemented"

### ✅ **Auto Grant**
- Grant ngay lập tức
- Quick testing

### ⚠️ **Soft Deny**
- Luôn show rationale dialog
- Test UI/UX của rationale

### 🚫 **Hard Deny**
- Luôn show settings dialog
- Test UI/UX của settings guide

---

## Reset để Test Lại

Click **"Reset All Results"** button ở dưới cùng để:
- Clear tất cả grant states
- Reset request counts
- Test lại từ đầu

---

## Khi nào dùng Custom vs Moko?

### Custom Implementation (Hiện tại)
✅ **Pros:**
- Simulation modes để test UI
- Không cần Activity callbacks phức tạp
- Tốt cho demo và learning

⚠️ **Cons:**
- Runtime requests không thực sự kích hoạt hệ thống
- Chỉ check được status, không request thật

### Moko Implementation (Production)
✅ **Pros:**
- Full runtime request support (kích hoạt system dialog)
- Xử lý tất cả edge cases
- Production-ready
- Cross-platform consistency

⚠️ **Cons:**
- Cần binding với Activity (Android) - đã xử lý
- Cần debug iOS (đang làm)

---

## Troubleshooting

### "Không thấy dialog"
→ Kiểm tra "Implementation Type" = "Moko (Recommended)" và "Simulation Mode" = "Real".
→ Click button và làm theo hướng dẫn của hệ thống/dialog tùy chỉnh.

### "Click Open Settings không làm gì"
→ Settings ĐÃ mở! Kiểm tra các ứng dụng gần đây (recent apps).
→ Vuốt để thấy Settings app đang chạy ngầm.

---

## Tóm tắt

✅ **Moko Implementation** đã hoạt động trên Android!
✅ **Custom Implementation** vẫn hoạt động để test UI.
⏳ **Moko iOS** đang debug.

**Test ngay trên Android:** Click button để thấy toàn bộ flow với dialog hệ thống và dialog tùy chỉnh!
