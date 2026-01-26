# 📱 Grant Demo App - Hướng Dẫn Sử Dụng

## Tình trạng hiện tại

### ✅ **Grant Implementation** - ĐANG HOẠT ĐỘNG
- Custom implementation với full permission support
- Có thể test system grant dialog, rationale dialog và settings dialog
- `openSettings()` hoạt động bình thường
- Hỗ trợ cả Android và iOS

---

## Cách Test Demo App (Android)

### 1. Mở App
```bash
adb shell am start -n dev.brewkits.grant.demo/dev.brewkits.grant.demo.MainActivity
```

### 2. Test Grant Flow

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
   [Grant Permission] [Cancel]
```

**Bước 3: Deny lần 2 → Settings Dialog (Tùy chỉnh) xuất hiện**
```
5. Click "Grant Permission" trong Rationale Dialog
6. ✅ System Camera Grant Dialog xuất hiện LẦN 2. Chọn "Don't allow" lần nữa.
7. ✅ Settings Dialog tùy chỉnh xuất hiện:
   "Camera access is disabled. Enable it in Settings > Permissions > Camera"
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

---

## Reset để Test Lại

Click **"Reset All Results"** button ở dưới cùng để:
- Clear tất cả grant states
- Reset request counts
- Test lại từ đầu

---

## Troubleshooting

### "Không thấy dialog"
→ Đảm bảo app có permission để hiển thị overlay (nếu cần).
→ Click button và làm theo hướng dẫn của hệ thống/dialog tùy chỉnh.

### "Click Open Settings không làm gì"
→ Settings ĐÃ mở! Kiểm tra các ứng dụng gần đây (recent apps).
→ Vuốt để thấy Settings app đang chạy ngầm.

---

## Tóm tắt

✅ **Grant Implementation** đã hoạt động trên cả Android và iOS!
✅ Test flow đầy đủ: system dialog → rationale → settings
✅ Production-ready với error handling đầy đủ

**Test ngay:** Click button để thấy toàn bộ flow với dialog hệ thống và dialog tùy chỉnh!
