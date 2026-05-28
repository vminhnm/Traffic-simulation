#### STRUCTURE
src
│
├── app
│   ├── MainApp
│   ├── SimulationApplication
│   └── AppConfig
│
├── core
│   ├── simulation
│   ├── model
│   ├── vehicle
│   ├── road
│   ├── intersection
│   ├── trafficlight
│   ├── controller
│   ├── driver
│   ├── rule
│   └── event
│
├── graphics
│   ├── renderer
│   ├── sprite
│   ├── camera
│   └── ui
│
├── sound
│   ├── SoundType
│   
│
├── data
│   ├── statistics
│   ├── export
│   └── report
│
└── util
├── Vector2D
├── Direction
├── MathUtils
└── IdGenerator

1. MÔ TẢ DỰ ÁN

* Smart City Traffic Simulation là ứng dụng mô phỏng giao thông đô thị bằng Java,
cho phép quan sát cách các phương tiện di chuyển qua nhiều loại nút giao thông như:
- Ngã ba
- Ngã tư
- Ngã năm
- Mạng lưới giao thông lớn gồm nhiều tuyến đường và nhiều nút giao

* Hệ thống hỗ trợ gồm nhiều loại phương tiện như: Ô tô, xe máy, xe cứu thương,...

* Các phương tiện cần tuân thủ:
- Dừng khi đèn đỏ
- Vượt xe an toàn
- Nhường đường cho xe ưu tiên
- Giữ khoảng cách an toàn với các xe lưu thông

* Các phương tiện có thể:
- Vượt xe một cách an toàn
- Di chuyển theo phong cách khác nhau
- Phát âm thanh phù hợp

* Ứng dụng có hai chế độ hiển thị:
- Basic Mode: Phương tiện là hình chữ nhật đơn giản, có tên xe.
- Graphics Mode: Phương tiện dùng ảnh sprite, xoay theo hướng di chuyển, có hiệu ứng đèn

2. Ý TƯỞNG THIẾT KẾ

* Ý tưởng chính là chia thành nhiều tầng độc lập:
- Simulation Core: Xử lý logic giao thông, xe, đường, đèn, va chạm.
- Rendering Layer: Phụ trách vẽ trạng thái mô phỏng ra màn hình.
- UI Layer: Cho người dùng chọn chế độ, chỉnh lưu lượng, đổi đèn, đổi bản đồ.
- Sound Layer: Phát âm thanh xe, còi, xi-nhan, còi hú. 
- Data Layer: Thu thập thống kê và xuất dữ liệu báo cáo.
# Đảm bảo TrafficController hoạt động chính xác khi thêm các loại xe mới vào
# DriverBehavior quyết định đến cách lái, có thể thay đổi dễ dàng

3. VAI TRÒ CỦA CÁC PACKAGE
# app: 
- chứa phần khởi động chương trình
- vai trò:
    + Khởi tạo ứng dụng
    + Load cấu hình ban đầu
    + Tao thế giới mô phỏng
    + Tạo giao diện
    + Băt đầu vòng lặp khi thực hiện mô phỏng
- Các class:
    + AppConfig
    + MainApp
    + SimulationAppllication
* nhớ thêm tên class, interface, enum,... nếu như có tạo thêm vào phần này

# core.simulation: 
- Trái tim của hệ thống
- Vai trò:
  + quản lí toàn bộ các trạng thái mô phỏng
  + Cập nhật xe, dèn đường, va chạm
  + Điều phối các thành phần logic
- Các class:
  + SimulationEngine
  + SimulationWorld
  + SimulationWorldFactory
* nhớ thêm tên class, interface, enum,... nếu như có tạo thêm vào phần này

# core.vehicle:
- chứa các loại phương tiện
- Vai trò:
    + Lưu trạng thái xe
    + Lưu vị trí tốc độ kích thước
    + Gắn hành vi lái xe
    + Gắn âm thanh
    + Cập nhật chuyển động
- Các class:
    + PriorityVehicle
    + Vehicle
    + VehicleType
* nhớ thêm tên class, interface, enum,... nếu như có tạo thêm vào phần này

# core.driver:
- Chứa bộ não để lái xe:
- Vai trò:
    + Quyết định xe nên đi, dừng, giảm tốc, vượt, đổi làn hay nhường đường
    + Cho phép thay đổi phong cách lái mà không sửa Vehicle
- Các class:
    + DriverBehavior
    + DrivingAction
    + DrivingDecision
* nhớ thêm tên class, interface, enum,... nếu như có tạo thêm vào phần này

# core.road:
- Chứa hệ thống đường đi:
- Vai trò:
    + Mô tả đường
    + Mô tả làn xe
    + Mô tả tuyến đường xe sẽ đi
    + Liên kết các nút giao thông trong mạng lưới lớn
- Các class:
    + Lane
    + LaneType
    + Road
    + RoadNetwork
    + Route
* nhớ thêm tên class, interface, enum,... nếu như có tạo thêm vào phần này

# core.intersection:
- Chứa các loại ngã rẽ:
- Vai trò:
    + Mô tả ngã ba, ngã tư, ngã năm
    + Quản lý các đường nối vào nút giao
    + Quản lý các đèn giao thông thuộc nút giao
- Các class:
    + Intersection
    + IntersectionType
* nhớ thêm tên class, interface, enum,... nếu như có tạo thêm vào phần này

# core.trafficlight:
- Chứa hê thống đèn giao thông: 
- Vai trò: 
    + Quản lý màu đèn
    + Quản lý thời gian đèn
    + Hỗ trợ nhiều kiểu hiển thị đếm giây
- Các class:
    + LightColor
    + LightTiming
    + TrafficLight
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# core.controller:
- Chứa bộ điều khiển đèn giao thông:
- Vai trò:
    + Điều khiển đèn tự động
    + Cho phép người dùng click đổi đèn ở chế độ thủ công
    + Không phụ thuộc trực tiếp vào loại xe cụ thể
- Các class:
    + LightControllerStrategy
    + TrafficController
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# core.rule:
- Chứa các luật giao thông:
- Vai trò:
    + Kiểm tra xe có phải dừng đèn đỏ không
    + Kiểm tra khoảng cách an toàn
    + Kiểm tra có được vượt không
    + Kiểm tra có phải nhường đường cho xe ưu tiên không
- Các class:
    + OvertakingPolicy
    + PriorityVehiclePolicy
    + SafeDistancePolicy
    + TrafficRuleEvaluator
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# graphics.renderer:
- Chứa logic vẽ:
- Vai trò: 
    + Vẽ Basic Mode
    + Vẽ Graphics Mode
    + Cho phép thay renderer mà không sửa core simulation
- Các class:
    + Renderer
    + RendererMode
*  nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# graphics.camera:
- Quản lí zoom và chuyển đổi tọa độ
- Vai trò:
    + Khi mô phỏng một ngã rẽ, zoom lớn hơn
    + Khi mô phỏng mạng lưới lớn, zoom nhỏ hơn
    + Chuyển tọa độ thế giới sang tọa độ màn hình
- Các class:
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# graphics.ui:
- Chứa giao diện người dùng
- Vai trò:
    + Canvas hiển thị mô phỏng
    + Panel điều khiển lưu lượng xe
    + Chọn chế độ Basic/Graphics
    + Chọn Auto/Manual traffic light
    + Chọn loại bản đồ
- Các class:
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# sound:
- Chứa âm thanh
- Vai trò:
    + Phát âm thanh động cơ
    + Phát xi-nhan
    + Phát còi xin vượt
    + Phát còi hú xe ưu tiên
- Các class:
    + SoundType
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# data.statistics:
- Chứa thống kê
- Vai trò: 
    + Đếm số lượng xe
    + Tính tốc độ trung bình
    + Tính thời gian chờ
    + Ghi nhận va chạm
- Các class:
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# data.export:
- Chứa xuất dữ liệu:
- Vai trò: 
    + Xuất CSV
    + Xuất dữ liệu phục vụ biểu đồ báo cáo
- Các class:
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

# util:
- Chứa các hàm phương thức hỗ trợ
- Vai trò:
- Các class:
    + Direction
    + IdGenerator
    + MathUtils
    + Vector2D
* nhớ thêm tên class, interface, enum,... nếu như có tạo vào phần này

### Vai trò các class quan trọng
#SimulationEngine
- Là bộ máy chạy mô phỏng.
- Nhiệm vụ:
    + Start
    + Pause
    + Resume
    + Update simulation theo deltaTime

# SimulationWorld
- Là nơi chứa toàn bộ trạng thái mô phỏng.
- Nhiệm vụ:

    + Danh sách xe
    + Mạng lưới đường
    + Danh sách controller
    + Hệ thống va chạm
    + Bộ thống kê

* Workflow trong SimulationWorld.update(): 
Cập nhật đèn
-> Cập nhật xe
-> Kiểm tra va chạm
-> Thu thập thống kê

# Vehicle
- Là class trừu tượng cho mọi phương tiện.
- Nhiệm vụ:
    + Lưu vị trí
    + Lưu tốc độ
    + Lưu hướng xoay
    + Lưu kích thước
    + Gắn DriverBehavior
    + Gắn Route
    + Di chuyển theo quyết định của DriverBehavior
# VehicleProfile
- Nhiệm vụ:
  + cấu hình cho phương tiện như là tên hiển thị, âm thanh, ...

# PriorityVehicle
- Class cha cho xe ưu tiên.
- Vai trò:
    + Cho biết xe này là xe ưu tiên
    + Có còi hú
    + Có thể yêu cầu xe khác nhường đường

# DriverBehavior
- Là interface định nghĩa bộ não lái xe.
- Vai trò:
    + Tách quyết định lái ra khỏi Vehicle
    + Cho phép thay đổi phong cách lái dễ dàng

# DrivingDecision
- Là kết quả quyết định của driver.
- Ví dụ:
    + ACCELERATE
    + BRAKE
    + STOP
    + CHANGE_LANE_LEFT
    + CHANGE_LANE_RIGHT
    + YIELD
* Vehicle chỉ cần đọc quyết định này và thực thi.

# RoadNetwork
- Vai trò:
    + Quản lý toàn bộ đường và ngã rẽ
    + Cho phép mở rộng từ một ngã tư sang mạng lưới lớn
    + Tìm đường, tìm làn, tìm đèn liên quan đến xe

# Road
- Vai trò:
    + Mô tả một đoạn đường
    + Có điểm bắt đầu
    + Có điểm kết thúc
    + Có nhiều làn
    + Có giới hạn tốc độ

# Lane
- Vai trò:
    + Mô tả làn xe
    + Chứa các điểm tọa độ tạo thành đường đi
    + Cho biết xe đang ở vị trí nào trên làn

# Route
- Vai trò:
    + Định nghĩa hành trình của xe
    + Xe đi từ Lane A sang Lane B rồi Lane C
    + Hỗ trợ mô phỏng mạng đường lớn

# Intersection
- Class cha cho mọi loại nút giao.
- Vai trò:
    + Lưu vị trí trung tâm nút giao
    + Lưu các đường kết nối
    + Lưu các đèn giao thông
    + ThreeWayIntersection

# TrafficLight
- Class cha cho các loại đèn.
- Vai trò:
    + Lưu màu hiện tại
    + Lưu thời gian còn lại
    + Chuyển màu
    + Cho phép đổi thủ công

# TrafficController
- Vai trò:
    + Điều khiển cụm đèn tại một intersection
    + Không phụ thuộc vào loại xe cụ thể
    + Không xử lý vẽ
    + Không xử lý âm thanh

# LightControlStrategy
- Interface cho chế độ điều khiển đèn.
- Vai trò:
    + Tách auto mode và manual mode
    + Cho phép đổi chiến lược điều khiển đèn dễ dàng

# TrafficRuleEvaluator
- Vai trò:
    + Là nơi tập trung kiểm tra luật giao thông
    + DriverBehavior gọi class này để ra quyết định

* Nó trả lời các câu hỏi:
    + Xe có cần dừng vì đèn đỏ không?
    + Xe có quá gần xe phía trước không?
    + Xe có cần nhường xe ưu tiên không?
    + Xe có thể vượt an toàn không?

# Renderer
- Interface vẽ.
- Vai trò:
    + Tách logic vẽ khỏi logic mô phỏng
    + Cho phép thay BasicRenderer bằng SpriteRenderer

# Camera
- Vai trò:
    + Quản lý zoom
    + Quản lý offset
    + Chuyển world coordinate sang screen coordinate

# SoundManager
- Vai trò:
    + Load âm thanh
    + Phát âm thanh
    + Dừng âm thanh
    + Quản lý âm lượng

# StatisticsCollector
- Vai trò:
    + Thu thập dữ liệu trong quá trình chạy
    + Hỗ trợ làm báo cáo cuối project
### Vai trò các interface
# Movable: 
Dành cho object có thể di chuyển.

# DriverBehavior
Dành cho các kiểu lái

# LightControlStrategy
Dành cho các kiểu điều khiển đèn.
* VD: 
Automatic
Manual

# Renderer
Dành cho các kiểu hiển thị đồ họa khác nhau

# SafeDistancePolicy
Dành cho thuật toán giữ khoảng cách.

# OvertakingPolicy
Dành cho thuật toán vượt xe.

# PriorityVehiclePolicy
Dành cho logic nhường xe ưu tiên.

### Vai trò các enum


* Có thể dùng cho:

Chọn sprite
Chọn âm thanh
Thống kê số lượng từng loại xe
Hiển thị label ở Basic Mode

# LightColor: Màu đèn giao thông.

RED,
YELLOW,
GREEN

* Có thể dùng cho:

TrafficLight
Renderer
TrafficRuleEvaluator

# RenderMode: Chế độ vẽ.

BASIC,
GRAPHICS

* Dùng cho:

ControlPanel
RendererFactory
SimulationCanvas

# DrivingAction: Hành động lái xe.

ACCELERATE,
BRAKE,
STOP,
CHANGE_LANE_LEFT,
CHANGE_LANE_RIGHT,
YIELD

* Có thể dùng trong DrivingDecision.

# IntersectionType: Loại nút giao.

THREE_WAY,
FOUR_WAY,
FIVE_WAY,

* Có thể dùng cho:

Scenario selector
AutoZoomPolicy
Statistics

# LaneType :Loại làn.

STRAIGHT,
LEFT_TURN,
RIGHT_TURN,
MIXED,
EMERGENCY

* Dùng cho:

Route
OvertakingPolicy
TrafficRuleEvaluator

# SoundType: Loại âm thanh.

ENGINE_CAR,
ENGINE_MOTORBIKE,
BICYCLE_BELL,
TURN_SIGNAL,
HORN,
AMBULANCE_SIREN,
FIRE_TRUCK_SIREN

* Dùng cho SoundManager.

## Tóm tắt tư duy thiết kế chuyên nghiệp


Vehicle chỉ giữ trạng thái và di chuyển.
DriverBehavior quyết định cách lái.
TrafficRuleEvaluator kiểm tra luật.
TrafficController chỉ điều khiển đèn.
Renderer chỉ vẽ.
SoundManager chỉ phát âm thanh.
StatisticsCollector chỉ thu dữ liệu.

Như vậy hệ thống:

Dễ mở rộng
Dễ test
Dễ debug
Dễ thay đổi GUI
Dễ thêm xe
Dễ thêm bản đồ
Dễ thêm luật giao thông
Dễ bảo trì theo SOLID

Nếu có đóng góp thêm vào thiết kế của dư án có thể thêm vào file này

* Đây là hướng thiết kế phù hợp cho một project Java mô phỏng giao thông đô thị có thể phát triển theo từng tuần và đủ cơ sở để viết báo cáo, UML, test case và thuyết trình.
