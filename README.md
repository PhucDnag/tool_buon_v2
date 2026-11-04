# Tool PhucHso — Hệ thống Auto Đi Buôn Hiệp Sĩ Online (HSO)

Bản mod hoàn thiện chức năng **Auto Đi Buôn** (thương nhân) dành cho **Hiệp Sĩ Online v403** chạy trên nền **MicroEmulator**.
Toàn bộ mã gốc của `KnightOnline_v403.jar` được bảo toàn và chèn các điểm can thiệp bytecode qua ASM (`tools/PatchClient.java`).

---

## 1. Khởi chạy & Quản lý nhiều tài khoản (Multi-Box Manager)

### A. Phần mềm Quản lý Tài khoản & Mở nhiều bản (`AccountManager.bat` / `AccountManager.ps1` / `AccountManager.exe`)
Nhấp đúp chuột vào **`AccountManager.bat`** (hoặc chạy `.\Start-Manager.ps1`) để mở giao diện quản lý đồ họa trực quan:

* **Giao diện basic**: nền sáng, bảng tài khoản gọn, nút Mở game (F5), mở các tài khoản đã tích, dừng game và xếp cửa sổ (F9). Nhấp đúp một dòng để sửa tài khoản. Nhân bản, khởi động lại, dừng tất cả và thư mục dữ liệu nằm trong **Thao tác khác** hoặc menu chuột phải.
* Các lối mở `.bat` / `.ps1` đều dùng `AccountManager.exe`. Chạy `build-manager.ps1` để biên dịch lại sau khi sửa mã nguồn C#.
* Game mở không kèm terminal, ưu tiên `javaw.exe` và dùng `CreateNoWindow`. Vùng hiển thị game mặc định **300 × 300**, chưa tính thanh tiêu đề và menu của Windows.

* **Giao diện Cấu hình 2 Tab chuyên biệt**:
  * **Tab 1: 👤 Tài khoản & Đăng nhập**: Tên gợi nhớ, Tài khoản game, Mật khẩu (ẩn/hiện), Máy chủ (Chọn danh sách), **Chọn nhân vật (Nhân vật 1, 2, 3 hoặc Tự chọn)** — *không cần phải gõ tên nhân vật thủ công*.
  * **Tab 2: ⚙ Cài đặt Đi Buôn & Tính năng**:
    * Bãi mua buôn: *Cổng Địa Ngục*, *Cửa Biển*, *Vách Đá Cheo Leo*.
    * Loại đá mua: *Kim Cương*, *Rubi*, *Saphia*.
    * Thú buôn: *Bò vàng (24v)*, *Lạc đà vàng (12v)*, *Lừa vàng (6v)*.
    * Đổi thú dưới: *30% HP*, *50% HP*, *70% HP*, *90% HP*.
    * Các tính năng tự động: *Tự bật buôn khi vào game*, *Tự kết nối lại khi mất mạng*, *Tự cưỡi thú*, *Tự khởi động lại khi crash (Watchdog)*.
    * Ghi chú cấu hình tự do.
* **Tự động chọn nhân vật & Báo lỗi thông minh trong game**:
  * Khi vào game, mod tự động nhận diện và vào đúng nhân vật đã chọn (Nhân vật 1, 2, hoặc 3).
  * Nếu tài khoản chưa có nhân vật ở vị trí đó, game sẽ hiển thị thông báo hộp thoại: *"Tài khoản không có nhân vật X"* để người dùng dễ dàng chuyển đổi hoặc tạo nhân vật.
* **Cô lập dữ liệu RMS độc lập 100%**:
  * Mỗi tài khoản chạy với thư mục RMS độc lập tại `%USERPROFILE%\.microemulator\<MãHồSơ>\`.
  * Không bao giờ bị đè tài khoản đăng nhập hay trùng cấu hình buôn giữa các nick.
  * 1-click nút `📁 Thư mục RMS` để truy cập ngay thư mục dữ liệu riêng của nick đang chọn.
* **Thao tác mở chơi nhiều bản tiện lợi**:
  * **▶▶ Mở tất cả (đã chọn)**: Mở tuần tự tất cả các nick có dấu tích (cách nhau 2 giây chống giật lag CPU).
  * **▶ Mở bản chọn (F5)**: Mở 1 nick đang chọn.
  * **🔄 Khởi động lại**: Khởi động lại nhanh bản game đang chọn.
  * **■ Dừng bản chọn / ⏹ Dừng tất cả**: Dừng nhanh tiến trình game.
* **⊞ Sắp xếp cửa sổ tự động (F9 - Tile Grid)**:
  * Tự động chia màn hình và xếp các cửa sổ game ngay ngắn dạng lưới (1x2, 2x2, 2x3, 2x4...).
  * Tự động khôi phục cửa sổ thu nhỏ và đưa lên trước màn hình để dễ dàng quan sát mọi nick cùng lúc.
* **Tự động đổi tên cửa sổ game**:
  * Tự động gắn nhãn tiêu đề cửa sổ theo tên tài khoản: `Knight Online [MãID] - Tên gợi nhớ (NV X)` giúp dễ dàng phân biệt trên thanh Taskbar.
* **Tự động mở lại khi văng/crash (Crash Watchdog)**:
  * Tích chọn `Watchdog` cho từng nick: nếu nick bị văng hoặc crash bất ngờ, hệ thống sẽ tự động khởi động lại sau 5 giây.

---

### Cấu hình tool riêng từng tài khoản

Chọn dòng tài khoản → **Cấu hình tool**. Hộp cấu hình gồm các tab **Tài khoản**, **Auto buôn**, **Di chuyển & hiển thị**, **Hỗ trợ nhân vật**. Có thể đặt bãi mua, đá, thú buôn, ngưỡng đổi thú, tự đổi bãi, thú cưỡi, HUD, tự đánh, tự nhặt đồ, tự dùng bình và ngưỡng HP/MP riêng cho từng tài khoản.

Bấm **Lưu tài khoản**, sau đó dừng/mở lại tab game đó để áp dụng; cấu hình không thay đổi các tab đang chạy khác. Bình HP/MP dùng các ô phím tắt hiện có của nhân vật. Auto buôn vẫn bật nhặt hàng trong chuyến buôn kể cả khi tắt tự nhặt đồ thông thường.

### B. Mở game đơn lẻ với giám sát tiến trình ngoài
Sử dụng script `Start-Trade.ps1` để tự động mở MicroEmulator và tự động khởi động lại nếu tiến trình bị tắt/crash:
```powershell
.\Start-Trade.ps1
```
* **Cửa sổ hiển thị**: Game hiển thị bình thường, tiến trình Java không tạo terminal riêng; vùng chơi mặc định 300 × 300.
* **Tự mở lại sau crash**: Bộ giám sát bên ngoài sẽ tự khởi động lại game với thuật toán backoff (từ 10s đến tối đa 300s).
* **Tiếp tục buôn sau đăng nhập**: Khi bật **Tự kết nối lại** và **Auto buôn** trong game, tool sẽ lưu trạng thái vào RMS và tự động tiếp tục chuyến buôn ngay khi nhân vật đăng nhập lại thành công.

---

## 2. Các chức năng đã hoàn thiện

### A. Chu trình Đi Buôn tự động khép kín (Full Trade Loop)
1. **Di chuyển đến bãi mua**:
   - 3 bãi mua: **Cổng Địa Ngục** (Map 52), **Cửa Biển** (Map 19), **Vách Đá Cheo Leo** (Map 39).
   - Tự động luân phiên giữa 3 bãi nếu hết hàng; nếu cả 3 bãi hết hàng sẽ quay về Cổng Địa Ngục và backoff chờ hồi hàng.
   - Hỗ trợ sử dụng **Đá Dịch Chuyển** để di chuyển nhanh đến thị trấn gần nhất (Làng Sói Trắng, Thành Phố Cảng, Thành Phố Kho Báu), tự động chuyển sang đi bộ nếu hết đá hoặc không có tuyến phù hợp.
2. **Hỏi tồn kho NPC**:
   - Tự động tìm NPC **Dylan** / **Thương nhân** tại bãi mua và chat kiểm tra tồn kho đá quý.
   - Phân tích chính xác số lượng từng loại đá: **Kim Cương**, **Rubi**, **Saphia** (bỏ qua ngọc trai bang hội).
   - Hỗ trợ đổi đá thay thế linh hoạt theo ưu tiên cấu hình.
3. **Thả thú chở hàng**:
   - Chỉ gọi thú sau khi đã kiểm tra chắc chắn NPC còn hàng.
   - Nhận diện đúng item thú vàng trong hành trang: **Lừa vàng** (max 6 viên), **Lạc đà vàng** (max 12 viên), **Bò vàng / Bò rừng vàng** (max 24 viên); loại bỏ hoàn toàn các loại thú trắng/thú cướp.
4. **Mua đá quý**:
   - Tương thích cả giao diện Shop vật phẩm (`TabShopNew`) lẫn Menu động (`Dynamic_Menu`).
   - Tự động xử lý hộp thoại xác nhận mua (chấp nhận các nút: *OK*, *Đồng ý*, *Xác nhận*, *Có*, *Chấp nhận*).
   - Đọc số hàng thực tế trên thú qua nhiều mẫu cú pháp (`đang chở`, `số hàng`, `số lượng`, `sức chở`, `hàng: X`, `X/Y`).
   - Tự động phát hiện thú đầy tải theo thông báo máy chủ hoặc theo ngưỡng sức chứa tối đa của thú (6/12/24).
5. **Hộ tống thú về Graham (Map 8 - Thung Lũng Kỳ Bí)**:
   - Tốc độ di chuyển của nhân vật tự động điều chỉnh theo tốc độ thực tế của thú buôn (`vMax`), không chạy quá xa làm mất thú.
   - Khoảng cách an toàn tối đa 130 pixel: nếu nhân vật vượt quá 130px sẽ tự động dừng lại và quay về phía thú.
   - Đường đi an toàn qua địa hình hiểm trở tại **Vách Đá Cheo Leo** (Map 39) bằng hệ thống điểm mốc (waypoints) chống kẹt vách đá.
   - Khi chuyển map, tool chờ thú xuất hiện ở map mới. Nếu sau 2 giây không thấy thú, nhân vật tự động lùi về map trước tìm kiếm trong 20 giây. Nếu thú bị tiêu diệt, tool reset chuyến và quay về bãi mua.
6. **Thay thú khi HP yếu**:
   - Nếu HP thú giảm xuống dưới ngưỡng cài đặt (30%, 50%, 70%, 90%), tool tiến hành cất thú và gọi thú mới.
   - Bắt buộc kiểm chứng số hàng trên thú mới khớp với số hàng trước đó trước khi tiếp tục hành trình; nếu không khớp sẽ tạm dừng để bảo vệ tài sản của người chơi.
7. **Bán hàng tại NPC Graham**:
   - Khi đến Map 8 và đứng gần Graham, tool tự động mở menu giao tiếp và chọn lệnh bán (*Bán hàng*, *Bán đá quý*, *Giao hàng*).
   - Tự động xác nhận hộp thoại bán hàng.
   - Xác nhận bán thành công khi nhận thông báo máy chủ và thú biến mất; tự động quay lại bãi mua để bắt đầu chuyến tiếp theo.

### B. Mạng lưới bản đồ tích hợp (Map Network & Pathfinding)
- Tích hợp sẵn topology của **hơn 80 map** trong Hiệp Sĩ Online kết nối 4 vùng lớn về Map 8 (Thung Lũng Kỳ Bí).
- Thuật toán BFS tìm đường ngắn nhất liên map; khi nhân vật vào map thực tế, tọa độ cổng chuyển map (`vecPointChange`) sẽ được cập nhật chính xác từng pixel và lưu vào RMS `phuc_routes_v1`.
- Thuật toán tìm đường tránh vật cản 4 hướng (Cardinal BFS) qua ma trận collision map của máy chủ.

### C. Khung giờ & An toàn
- Tự động tạm dừng hoạt động trong khung giờ buôn bang hội: **10h00 – 16h00 (UTC+7)**, tự động tiếp tục khi hết giờ cấm.
- Cơ chế hồi sinh: tự động chờ delay 9–11 giây (tránh bị phát hiện bot) trước khi gửi lệnh hồi sinh tại chỗ; nếu sau 6 giây vẫn chưa hồi sinh được (hết ngọc hồi sinh), tool tự động gọi lệnh *Về làng* để tiếp tục hoạt động.

### D. Giao diện HUD trực quan
- Hiển thị tên map và ID map hiện tại.
- Trạng thái Đánh quái, HP/MP tự động, và Tự nhặt đồ.
- Chi tiết tiến trình Auto buôn và hành động hiện thời.
- Thông tin thú buôn: Loại thú, HP/MaxHP, Số hàng trên thú / Sức chứa tối đa (vd: `Hàng: 6/6`).
- Trạng thái Thú nuôi (Pet): Tên pet và HP pet.
- Bãi mua, loại đá, ngưỡng đổi thú và delay hồi sinh.
- Tồn kho NPC gần nhất.
- Đường chỉ hướng màu vàng và vòng tròn cam định vị thú chở hàng.

---

## 3. Hệ thống Menu Tool PhucHso (Phân cấp trực quan, dễ thao tác)

Menu trong game được chia thành các nhóm chuyên biệt, mỗi danh mục gọn nhẹ (chỉ từ 5 đến 7 mục) giúp người dùng thao tác nhanh chóng trên cả màn hình điện thoại lẫn giả lập PC mà không cần cuộn trang phức tạp:

### Menu chính (`Menu` -> `Tool PhucHso`)
1. **Auto buôn: [BẬT / TẮT]**: Kích hoạt hoặc tắt chu trình đi buôn hoàn toàn tự động.
2. **Cài đặt Đi Buôn >**: Mở menu chuyên cấu hình chuyến buôn.
3. **Di chuyển nhanh >**: Mở menu chọn điểm đến và chế độ di chuyển.
4. **Hỗ trợ nhân vật >**: Mở menu bật/tắt tự đánh, nhặt đồ, bơm máu/mana, tự kết nối lại, thú cưỡi.
5. **Giao diện & Tiện ích >**: Mở menu cấu hình HUD, đường chỉ hướng, đổi tên, ghi dữ liệu chẩn đoán.
6. **Dừng mọi di chuyển**: Dừng ngay lập tức lộ trình đang đi của nhân vật.

---

### Menu con: Cài đặt Đi Buôn
* **Thú buôn**: Chuyển đổi giữa *Lừa vàng* (6 viên), *Lạc đà vàng* (12 viên), *Bò vàng* (24 viên).
* **Loại đá**: Chuyển đổi ưu tiên giữa *Kim Cương*, *Rubi*, *Saphia*.
* **Bãi mua**: Chuyển đổi giữa *Cổng Địa Ngục* (Map 52), *Cửa Biển* (Map 19), *Vách Đá Cheo Leo* (Map 39).
* **Đổi thú dưới**: Ngưỡng HP thú cần đổi mới (*30%*, *50%*, *70%*, *90%*).
* **Delay hồi sinh**: Được thiết lập mặc định cố định an toàn **9s–11s** ngẫu nhiên (chống phát hiện bot) ngầm định, lược bỏ khỏi menu để tối giản và trực quan nhất.
* **< Quay lại**: Quay về menu chính.
*(Mẹo: Khi bấm đổi các tùy chọn, menu tự động cập nhật ngay trên màn hình mà không bị thoát ra ngoài).*

---

### Menu con: Di chuyển nhanh
* **Đến Cổng Địa Ngục / Đến Cửa Biển / Đến Vách Đá Cheo Leo**: 1-click di chuyển ngay đến các bãi mua buôn.
* **Đến Graham (Thung Lũng)**: 1-click di chuyển về NPC bán hàng Graham tại Map 8.
* **Đến Làng Sói Trắng / TP Kho Báu / TP Cảng**: 1-click di chuyển về các thị trấn chính.
* **Di chuyển**: Chuyển đổi giữa *Ưu tiên dịch chuyển* (dùng Đá Dịch Chuyển khi thích hợp) và *Chỉ chạy bộ*.
* **Dừng di chuyển**: Hủy lộ trình hiện tại.
* **< Quay lại**: Quay về menu chính.

---

### Menu con: Hỗ trợ nhân vật
* **Tự kết nối lại**: Tự động kết nối lại khi mất mạng và tự động chọn nhân vật đang buôn.
* **Tự cưỡi thú**: Tự động dùng thú cưỡi khi nhân vật không dẫn thú buôn.
* **Chọn thú cưỡi**: Tự động quét và chỉ giữ lại những thú cưỡi có tên bắt đầu bằng **Ngựa**, **Heo**, **Lân** (hoặc **Kỳ lân**) trong hành trang (ví dụ: *Ngựa trắng, Ngựa xích thố, Ngựa nâu, Ngựa đen, Ngựa chiến giáp, Heo rừng, Kỳ lân...*). Loại bỏ triệt để 100% trang bị (Mề đay xạ thủ...), nguyên liệu (Cành xương rồng...), và các vật phẩm rác khác.
* **Tự đánh**: Bật / Tắt chế độ tự đánh quái bản gốc của game.
* **Tự hồi HP/MP**: Bật / Tắt cơ chế tự động dùng bình hồi phục.
* **Tự nhặt đồ**: Bật / Tắt cơ chế tự động nhặt vật phẩm rơi.
* **< Quay lại**: Quay về menu chính.

---

### Menu con: Giao diện & Tiện ích
* **HUD thông tin**: Bật / Tắt bảng thông số trực quan trên góc màn hình.
* **Đường chỉ thú buôn**: Bật / Tắt tia chỉ đường và vòng tròn định vị thú chở hàng.
* **Tên giả PhucHso**: Đổi tên hiển thị của nhân vật trên màn hình thành `PhucHso`.
* **Ghi dữ liệu NPC/map**: Ghi nhận log chi tiết tọa độ cổng và đoạn chat NPC.
* **Xem dữ liệu NPC gần nhất**: Mở hộp thoại xem log đã ghi.
* **< Quay lại**: Quay về menu chính.

---

## 4. Cấu trúc mã nguồn

* `src/autotrade/ClientMod.java`: Điểm điều phối hook trong game, giao diện menu Tool PhucHso, vẽ HUD, xử lý RMS và reconnect.
* `src/autotrade/LiveTrade.java`: Adapter tương tác với giao thức client/server (chat, menu, dialog, shop, di chuyển, dùng đá dịch chuyển).
* `src/autotrade/TradeEngine.java`: Máy trạng thái (state machine) độc lập điều phối toàn bộ chu trình buôn bán.
* `src/autotrade/TradeSignals.java`: Bộ quy tắc nhận diện chuỗi văn bản (thú, đá, hàng hóa, tình trạng đầy/hết, nút xác nhận).
* `src/autotrade/TradeStock.java`: Bộ phân tích ngữ nghĩa câu chat tồn kho NPC tiếng Việt.
* `src/autotrade/TradeConfig.java`: Cấu hình người chơi, sức chứa, khung giờ cấm và hằng số map.
* `src/autotrade/MapGraph.java`: Đồ thị mạng lưới liên map và bộ tìm đường BFS có hướng.
* `src/autotrade/Navigator.java`: Dẫn đường nhân vật, hộ tống thú buôn, xử lý địa hình vách đá.
* `src/autotrade/GridPath.java`: Tìm đường tránh vật cản theo ô tile trên từng map.
* `tools/PatchClient.java`: Công cụ chèn bytecode ASM tự động vào JAR game.

---

## 4. Biên dịch & Kiểm thử

Yêu cầu môi trường có Java (khuyến nghị Java 8):
```powershell
# Biên dịch mã nguồn và đóng gói JAR đã patch
.\build.ps1

# Chạy toàn bộ 1075+ kiểm thử hành vi, mô phỏng chu trình và kiểm tra bytecode Java (-Xverify:all)
.\test.ps1
```
JAR hoàn chỉnh sau khi build nằm tại `dist/KnightOnline_PhucHso_v2.jar`.


### Tuyến dắt thú về bán đã xác nhận

Cửa Biển → Nghĩa Địa Cát → Hố Tử Thần → Sa Mạc → Khu phía Tây → Thành Phố Kho Báu → Rừng Chết → Hang Sói Quỷ → Cánh Đồng Sói → Thung Lũng Kỳ Bí.

Khi về bán, tool chọn cổng thật theo thứ tự trên, kể cả khi cache có đường ngắn hơn. Nếu không tìm thấy cổng bắt buộc, tool dừng và báo tên cổng còn thiếu. Nhịp mua giữ ở 4 giây/lần.


### Bố cục dự án sau khi dọn

- `src/`: mã nguồn tool Java, trình quản lí C# và icon ứng dụng.
- `tests/`: kiểm thử và lớp giả lập phục vụ kiểm thử.
- `tools/`: thư viện biên dịch, giả lập, công cụ patch và công cụ dịch ngược CFR. `microemulator.jar.bak` giữ bản giả lập gốc để đối chiếu/khôi phục.
- `KnightOnline_v403.jar`: đầu vào gốc bắt buộc của quá trình build; chứa cả class và tài nguyên game, không cần giải nén ra thư mục gốc.
- `dist/KnightOnline_PhucHso_v2.jar` và `AccountManager.exe`: bản chạy hiện tại.
- `build/`: kết quả trung gian tự sinh. Có thể xóa rồi chạy `build.ps1` trước `test.ps1` để tạo lại.
- `accounts.json`, `manager_config.json` (nếu có), `logs/`: dữ liệu cá nhân/cấu hình/log, không đưa vào Git.

Các thư mục dịch ngược chỉ dùng tham khảo, không tham gia build. Khi cần có thể tạo lại:

```powershell
java -jar tools/cfr-0.152.jar KnightOnline_v403.jar --outputdir recovered
java -jar tools/cfr-0.152.jar tools/microemulator.jar --outputdir tools/decompiled
```

### Tự nhận diện đường dẫn khi chuyển máy/VPS

Mở `AccountManager.exe` để tự tìm Java và các JAR. Đường dẫn còn hợp lệ được giữ lại; đường dẫn mất hiệu lực được tìm lại khi mở quản lí hoặc khởi chạy game. Có thể bấm **Tự nhận diện** trong Cài đặt để tìm lại toàn bộ.

Chép cả thư mục tool, gồm `tools` và `dist`. Java được tìm trong `runtime\bin\java.exe`, `jre\bin\java.exe`, `java\bin\java.exe`, JAVA_HOME/JRE_HOME, PATH và các thư mục cài đặt thông dụng. Máy chưa có Java cần cài Java hoặc chép một bộ Java portable đầy đủ vào `runtime`; tool không tự tải/cài Java.

JAR trong thư mục tool được lưu bằng đường dẫn tương đối. Khi chưa chọn bản game, quản lí chọn bản `KnightOnline_PhucHso*.jar` mới nhất theo thời gian sửa file, không chọn JAR game gốc chưa mod.


### Bản build chuẩn và bảo vệ mật khẩu

Chỉ dùng `AccountManager.exe` và `dist/KnightOnline_PhucHso_v2.jar`; build lại sẽ ghi đè tên cố định. Launcher phụ `tools/manager-launcher.jar` nhận cấu hình bằng stdin, không đưa mật khẩu vào dòng lệnh tiến trình. Khi file đang được sử dụng, đóng ứng dụng tương ứng rồi build lại, không tạo tên phiên bản mới.

Ô mật khẩu trong Sửa tài khoản luôn trống, không có nút xem. Để trống để giữ mật khẩu hiện có; nhập để thay mới. Khi quản lí nạp tài khoản cũ, mật khẩu rõ trong JSON được chuyển sang dữ liệu Windows DPAPI theo người dùng hiện tại. Chuyển sang máy hoặc tài khoản Windows khác cần nhập lại mật khẩu. Cơ chế này ngăn xem mật khẩu trong giao diện và file JSON; không bảo đảm bí mật trước quản trị viên hoặc chương trình chạy dưới cùng tài khoản Windows có thể đọc bộ nhớ/giải mã DPAPI.
# Quản lý nhiều cửa sổ và bộ nhớ

- **Giảm lag** mặc định bật: vào **Cấu hình tool → Di chuyển & hiển thị** trong manager hoặc **MENU TOOL → Giao diện & Tiện ích** trong game để bật/tắt. Ẩn người chơi khác, bỏ nền/đồ trang trí và hiệu ứng; giữ nhân vật của bạn, NPC, quái, vật phẩm và thú buôn. Ô đi được có viền vuông, vật cản để trống theo dữ liệu va chạm của map. Chỉ vẽ vùng đang nhìn thấy; không thay dữ liệu di chuyển hoặc nhịp auto. Thay đổi trong game áp dụng ngay; cấu hình manager áp dụng ở lần mở game sau.

- Khi Vách Đá Cheo Leo báo hết hàng (hoặc đã kiểm tra hết cả ba bãi), auto quay về Cổng Địa Ngục và kiểm tra hàng mỗi 60 giây cho đến khi mua được. Quy tắc Vách Đá áp dụng cả khi tắt đổi bãi. Thú còn hàng được đưa đi bán trước; thú rỗng được thu hồi trước khi quay về.
- Bản sửa ẩn cửa sổ dùng `ShowWindowAsync`, theo dõi cả cửa sổ phụ của game và ghi cài đặt nền ngoài luồng giao diện. Java đang treo vẫn có thể nhận lệnh ẩn chậm, nhưng manager không chờ nó xử lý lệnh.

- **Ẩn tất cả / Hiện tất cả**: ẩn cửa sổ game để chạy nền và hiện lại. Auto và kết nối vẫn hoạt động; phần vẽ hình khi ẩn được giới hạn tối đa 1 lần/giây. F5 trên dòng đang chạy cũng hiện lại game đó. Khi thoát manager mà giữ game, các cửa sổ sẽ được hiện lại.
- **Tắt tất cả game**: dừng mọi game do manager hiện tại quản lý. **Xếp cửa sổ (F9)** hiện và xếp các game sát nhau trên màn hình chứa manager.
- **Cài đặt → Thu gom RAM (phút)**: mặc định 10 phút, 0 để tắt, tối đa 120 phút. Áp dụng ngay cho các game đang mở bằng bản mới. Client yêu cầu Java thu gom đối tượng không còn dùng khi auto rảnh; không xóa dữ liệu game hoặc ép Windows đẩy RAM ra đĩa. Việc giảm RAM thực tế phụ thuộc JVM và tải game.
- **Vàng bán hàng**: cộng số vàng được ghi trong phản hồi bán hàng thành công của auto, lưu theo acc qua các lần mở manager. Tổng là tiền bán hàng, chưa trừ vốn, chỉ bắt đầu ghi nhận từ bản này. Chuyến bán thiếu số tiền rõ ràng được đếm ở mục “Chuyến chưa rõ số vàng”; không ước tính từ biến động ví. Thống kê được gửi về manager mỗi giây; tắt cưỡng bức có thể mất cập nhật cuối cùng.
