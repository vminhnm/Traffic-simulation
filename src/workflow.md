# Workflow tổng thể khi ứng dụng chạy
1. Người dùng mở ứng dụng.

2. MainApp khởi động UI.

3. SimulationApplication load AppConfig.

4. SimulationWorldFactory tạo scenario mặc định, ví dụ ngã tư.

5. Hệ thống tạo:
    - RoadNetwork
    - Road
    - Lane
    - Intersection
    - TrafficLight
    - TrafficController
    - Vehicle

6. MainView tạo giao diện:
    - Canvas mô phỏng
    - ControlPanel
    - Nút chọn Basic/Graphics
    - Slider lưu lượng
    - Nút Auto/Manual Light

7. SimulationEngine bắt đầu vòng lặp.

8. Mỗi frame:
    - TrafficController cập nhật đèn.
    - Vehicle hỏi DriverBehavior để lấy DrivingDecision.
    - DriverBehavior hỏi TrafficRuleEvaluator.
    - Vehicle thực hiện quyết định.
    - CollisionSystem kiểm tra va chạm.
    - StatisticsCollector lưu dữ liệu.
    - Renderer vẽ trạng thái mới.
    - SoundManager phát âm thanh nếu cần.

9. Người dùng có thể:
    - Đổi chế độ hiển thị.
    - Tăng/giảm lưu lượng xe.
    - Chuyển Auto/Manual.
    - Click đèn để đổi màu.
    - Chuyển scenario ngã ba/ngã tư/ngã năm/network.

10. Khi kết thúc:
- Export dữ liệu CSV.
- Sinh biểu đồ cho báo cáo.

# Workflow xử lý một xe thường

Ví dụ Car dùng NormalDriver.

1. SimulationWorld gọi car.update(deltaTime).

2. Car gọi:
   NormalDriver.decide(car, world)

3. NormalDriver kiểm tra:
    - Có đèn đỏ trước mặt không?
    - Có xe phía trước quá gần không?
    - Có xe cứu thương/cứu hỏa phía sau không?
    - Có thể tiếp tục đi không?

4. Nếu đèn đỏ:
   return STOP

5. Nếu quá gần xe trước:
   return BRAKE

6. Nếu có xe ưu tiên:
   return YIELD

7. Nếu an toàn:
   return ACCELERATE

8. Car nhận DrivingDecision.

9. Car cập nhật tốc độ, vị trí.

10. Renderer vẽ Car ở vị trí mới.

# Workflow xử lý xe ưu tiên

Ví dụ Ambulance.

1. SimulationWorld gọi ambulance.update(deltaTime).

2. Ambulance dùng EmergencyDriver.

3. EmergencyDriver kiểm tra:
    - Có xe chắn trước không?
    - Có làn trống để vượt không?
    - Có thể đi qua giao lộ an toàn không?

4. Nếu có thể vượt:
   return CHANGE_LANE_LEFT hoặc CHANGE_LANE_RIGHT

5. Nếu đường trống:
   return ACCELERATE

6. Xe thường xung quanh dùng PriorityVehiclePolicy phát hiện Ambulance.

7. Xe thường:
    - Giảm tốc
    - Dạt sang
    - Tạo khoảng trống

8. SoundManager phát AMBULANCE_SIREN.

9. SpriteRenderer bật hiệu ứng đèn nháy.

# Workflow đèn giao thông
    * Auto Mode
1. TrafficController dùng AutomaticLightControlStrategy.

2. Mỗi frame:
   light.update(deltaTime)

3. remainingTime giảm dần.

4. Khi remainingTime = 0:
   RED -> GREEN
   GREEN -> YELLOW
   YELLOW -> RED

5. Renderer cập nhật màu đèn.

    *  Manual Mode
1. TrafficController dùng ManualLightControlStrategy.

2. Đèn không tự đổi màu.

3. Người dùng click vào đèn.

4. UI gọi:
   trafficController.onLightClicked(light)

5. Strategy gọi:
   light.switchManually()

6. Renderer vẽ màu mới.

# Workflow chuyển chế độ hiển thị
1. Người dùng chọn Basic Mode.

2. SimulationCanvas dùng BasicRenderer.

3. Xe được vẽ bằng hình chữ nhật và label.

4. Người dùng chọn Graphics Mode.

5. SimulationCanvas đổi sang SpriteRenderer.

6. SpriteRenderer lấy ảnh từ SpriteManager.

7. Ảnh xe được xoay theo hướng di chuyển.

8. Nếu xe là Ambulance hoặc FireTruck:
   Renderer thêm hiệu ứng đèn nháy.

# Workflow zoom
1. Người dùng chọn ngã tư.

2. AutoZoomPolicy nhận scenario SINGLE_INTERSECTION.

3. Camera zoom lớn hơn.

4. Xe và đèn hiển thị to hơn.

5. Người dùng chọn mạng lưới lớn.

6. AutoZoomPolicy nhận scenario LARGE_NETWORK.

7. Camera zoom nhỏ hơn.

8. Xe, đường, đèn hiển thị nhỏ lại để nhìn toàn vùng.

## có thể thêm chi tiết vào workflow