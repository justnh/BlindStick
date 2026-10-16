# Gậy Thông Minh – App Android (Kotlin + Jetpack Compose + Firebase)

App đi kèm firmware `GayThongMinh_Moi2.ino` (ESP32-S3). Có 2 vai trò: **người dùng gậy** và **người thân**.
Bản đồ: OpenStreetMap. Giao diện chỉ có màu sáng (xanh da trời / cam / xanh lá), không có nền đen.

## 1. Cấu hình 1 lần (bắt buộc, khoảng 10 phút)

App cần 2 dịch vụ miễn phí của Google. File `app/google-services.json` trong project hiện là **file giả** để Android Studio sync được – bạn phải thay bằng file thật.

### a) Firebase (đăng nhập Google + cơ sở dữ liệu realtime)
1. Vào https://console.firebase.google.com → **Add project** (gói Spark miễn phí là đủ).
2. **Add app → Android**, Package name: `com.gaythongminh.app`.
   SHA-1: mở Android Studio → tab **Gradle** → `GayThongMinh > Tasks > android > signingReport` (nằm trong `app > Tasks > android`) → copy dòng **SHA1** của variant `debug`, dán vào Firebase.
3. Tải `google-services.json` → **chép đè** lên `app/google-services.json`.
4. **Build → Authentication → Sign-in method → Google → Enable.**
5. **Build → Firestore Database → Create database** (chọn vùng gần Việt Nam, vd. `asia-southeast1`).
6. Tab **Rules** của Firestore: dán toàn bộ nội dung file `firestore.rules` ở thư mục gốc project → **Publish**.
   (Rules này cũng là "backend đối chiếu email + mã liên kết": người thân không thể liên kết nếu sai mã.)
7. Sau khi bật Google Sign-in, tải lại `google-services.json` lần nữa (để có đủ OAuth client) rồi chép đè.

### b) Bản đồ
Bản đồ dùng **OpenStreetMap** (thư viện osmdroid): **không cần API key, không cần thẻ, không cần tài khoản thanh toán**. Chỉ cần điện thoại có internet để tải bản đồ.

## 2. Build ra file APK
1. Android Studio (bản mới, JDK 17) → **File → Open** → chọn thư mục `GayThongMinhApp`. Chờ Gradle Sync xong.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Bấm **locate** ở thông báo hiện ra, hoặc lấy file tại `app/build/outputs/apk/debug/app-debug.apk`.
4. Chép sang điện thoại Android (8.0 trở lên), bật "Cài ứng dụng từ nguồn không xác định" và cài.
   Cần cài **Google Play services** và **Speech Services by Google + gói giọng tiếng Việt** để app đọc to được.

## 3. Dữ liệu và đồng bộ thời gian thực (Cloud Firestore)

| Đường dẫn | Nội dung |
|---|---|
| `users/{uid}` | email, tên, vai trò, mã liên kết |
| `codes/{mã 6 ký tự}` | uid + email chủ mã (để tra khi liên kết) |
| `links/{caneUid_relativeUid}` | liên kết: email 2 bên, thời gian liên kết |
| `cane_status/{caneUid}` | `status` (SAFE/FALLEN), vị trí, `locAt`, `bleConnected`, lần ngã gần nhất |
| `cane_status/{caneUid}/falls/*` | nhật ký các lần ngã (thời gian + tọa độ) |

Màn hình người thân dùng *snapshot listener* nên trạng thái / vị trí / nhật ký cập nhật ngay khi người dùng gậy ghi lên. Khi mất mạng, Firestore tự lưu hàng đợi và đẩy lên khi có mạng lại; vị trí cuối cùng + thời gian vẫn nằm trong `cane_status`, người thân thấy banner "Mất kết nối… vị trí cuối lúc …" nếu quá 30 giây không có vị trí mới.

## 4. App nói chuyện với ESP32 thế nào (đúng theo firmware)

BLE tên `GayThongMinh`, Nordic UART (service `6e400001-…`, RX `6e400002-…` app ghi, TX `6e400003-…` gậy báo).

| Gậy gửi lên app | App làm |
|---|---|
| `FALL` | hiện cảnh báo đỏ, đọc to, đẩy GPS mới nhất cho ESP32 |
| `CANCEL` | tắt cảnh báo, đọc "đã xác nhận an toàn" |
| `SENDING_ALERT` (hết 10 s không bấm nút GPIO 40) | lấy GPS, ghi **BỊ NGÃ** + nhật ký lên Firestore → người thân nhận thông báo cùng lúc ESP32 gửi mail |
| `ALERT_SENT` / `ALERT_FAILED` | đọc to kết quả gửi mail |

| App gửi xuống gậy | |
|---|---|
| `GPS,lat,lon` mỗi ~5 giây | ESP32 dùng để điền toạ độ + link Google Maps vào email EmailJS (template `template_mncbl42`) |

Email cảnh báo do **ESP32 tự gửi qua EmailJS** (service/template/public key đã nằm sẵn trong firmware), nên email người thân vẫn cấu hình ở trang `192.168.4.1` như cũ. App chỉ lo phần thông báo trong app + trạng thái + vị trí.

## 5. Luồng màn hình
- **Đăng nhập / Đăng ký bằng Google** → chọn **người dùng gậy** hoặc **người thân**.
- Người dùng gậy: Trạng thái Bluetooth + Google Map; menu 3 sọc: Màn hình chính / Tài khoản đã liên kết (mã 6 ký tự, danh sách người thân, hủy liên kết có hỏi lại) / Đăng xuất. Mọi thông báo được đọc to.
- Người thân: tab Trạng thái (AN TOÀN / BỊ NGÃ, nhật ký, nút "Tôi xác nhận người thân an toàn") và tab Vị trí Google Map; menu: Tài khoản đã liên kết (thêm bằng email + mã, hủy liên kết có hỏi lại) / Đăng xuất.

## 6. Lưu ý
- Lần đầu mở app hãy cấp đủ quyền: Vị trí, Bluetooth, Thông báo. Nên tắt "tối ưu pin" cho app để dịch vụ nền không bị hệ thống tắt.
- Người thân cần để app mở ít nhất một lần sau khi đăng nhập để dịch vụ nền theo dõi chạy; thông báo ngã sẽ kêu chuông + rung.
- Bản này chưa tự khởi động lại sau khi khởi động lại điện thoại: mở app một lần là dịch vụ chạy lại.
