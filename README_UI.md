# 🚦 Traffic Simulation — Giao diện JavaFX

## Cấu trúc giao diện

```
src/main/java/ui/
└── TrafficSimulationUI.java   ← Entry point JavaFX
```

## Yêu cầu

- **Java 21+** (có sẵn trong project)
- **Maven 3.8+**
- **JavaFX 21** (tự động tải qua Maven)

---

## ▶ Chạy ứng dụng

### Cách 1 — Maven JavaFX Plugin (khuyên dùng)
```bash
cd Traffic-simulation-test1
mvn clean compile
mvn javafx:run
```

### Cách 2 — IntelliJ IDEA
1. Mở project trong IntelliJ
2. Cấu hình Run → `ui.TrafficSimulationUI`
3. Thêm VM Options:
   ```
   --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.graphics
   ```
4. Nhấn Run

### Cách 3 — Command line với JavaFX SDK riêng
```bash
# Tải JavaFX SDK từ https://gluonhq.com/products/javafx/
export PATH_TO_FX=/path/to/javafx-sdk-21/lib
mvn clean package -DskipTests
java --module-path $PATH_TO_FX \
     --add-modules javafx.controls,javafx.graphics \
     -cp target/traffic-simulation-1.0.jar \
     ui.TrafficSimulationUI
```

---

## 🖥 Tính năng giao diện

### Canvas Mô phỏng (860×660)
- **Giao lộ 4 chiều** với làn xe, vạch kẻ đường, vạch dừng zebra
- **Đèn giao thông** phát sáng (đỏ/vàng/xanh) tại 4 góc giao lộ
- **Xe chạy thực thời gian** theo VehiclePath & DriverBehavior
- Hiển thị trạng thái: dừng (đèn đỏ), nhường đường (yielding), va chạm (💥)
- **Xe ưu tiên** (Ambulance, FireTruck) có đèn siren nhấp nháy

### Panel Điều khiển (bên phải)
| Tính năng | Mô tả |
|-----------|-------|
| 📊 Thống kê | Tổng xe đã tạo, qua giao lộ, va chạm |
| 🚦 Đèn giao thông | Trạng thái thực của 4 hướng |
| ⏸/▶ Tạm dừng | Dừng/tiếp tục engine |
| 🔄 Đặt lại | Reset toàn bộ mô phỏng |
| ⚡ Tốc độ | Slider 0.1× → 3.0× (nhân delta time) |
| 🕐 Khoảng cách sinh xe | Tần suất tự động sinh xe (0.5s → 8s) |
| 🚦 Đổi đèn thủ công | Trigger switchManually() ngay lập tức |
| ➕ Thêm xe | Chọn loại xe + hướng + kiểu lái rồi thêm |
| 📝 Nhật ký | Log theo thời gian thực |

### Loại xe hỗ trợ
`car` · `motorbike` · `bicycle` · `bus` · `truck` · `ambulance` · `firetruck`

### Kiểu lái
- **Normal** — tuân thủ đèn, giữ khoảng cách
- **Aggressive** — vượt xe, ít tuân thủ
- **Emergency** — bỏ qua đèn đỏ, ưu tiên tốc độ

---

## 🏗 Kiến trúc tích hợp

```
TrafficSimulationUI
    ├── SimulationEngine.update(delta)     ← gọi mỗi frame
    ├── SimulationWorld                    ← chứa vehicles + lights
    │   ├── Vehicle.update()              ← DriverBehavior.decide()
    │   └── TrafficLight.update()         ← tự chuyển pha
    ├── VehicleFactory.create()            ← tạo xe theo typeKey
    └── Vehicle.toRenderableState()        ← snapshot cho Canvas
```

> **Giao diện KHÔNG thay đổi bất kỳ logic core nào** —
> chỉ gọi API đã có: `engine.update()`, `world.addVehicle()`,
> `v.toRenderableState()`, `light.getColor()`.
