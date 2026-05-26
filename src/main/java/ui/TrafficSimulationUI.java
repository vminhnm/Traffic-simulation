package ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import core.driver.AggressiveDriver;
import core.driver.EmergencyDriver;
import core.driver.NormalDriver;
import core.road.VehiclePath;
import core.simulation.CollisionEvent;
import core.simulation.CollisionManager;
import core.simulation.SimulationEngine;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.trafficlight.LightTiming;
import core.trafficlight.TrafficLight;
import core.vehicle.Vehicle;
import core.vehicle.VehicleFactory;
import graphics.renderer.RenderMode;
import graphics.sprite.RenderAssetKey;
import graphics.sprite.SpriteLoader;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import sound.SoundManager;
import sound.SoundType;
import util.Vector2D;

public class TrafficSimulationUI extends Application {

    // ── Canvas size ────────────────────────────────────────────────
    private static final int CANVAS_W = 820;
    private static final int CANVAS_H = 620;

    // ── Road geometry constants ────────────────────────────────────
    private static final double ROAD_HALF = 50.0;
    private static final double LANE_W    = 22.0;
    private static final double COLLISION_COOLDOWN = 2.0; // seconds before a vehicle can collide
    private static final double CRASH_DISPLAY_SECONDS = 1.2;

    // ── Scenario modes ─────────────────────────────────────────────
    private enum ScenarioMode { FOUR_WAY, THREE_WAY, FIVE_WAY, GRID }
    private enum CountdownMode { ALWAYS, LAST_10_SECONDS, HIDDEN }
    private ScenarioMode currentMode = ScenarioMode.FOUR_WAY;
    private RenderMode renderMode = RenderMode.BASIC;
    private CountdownMode countdownMode = CountdownMode.ALWAYS;
    private boolean autoLights = true;

    // ── Simulation core ────────────────────────────────────────────
    private SimulationWorld  world;
    private SimulationEngine engine;
    private final CollisionManager collisionManager = new CollisionManager(COLLISION_COOLDOWN);
    private AnimationTimer   gameLoop;
    private long             lastNano = 0;

    // ── Traffic lights ─────────────────────────────────────────────
    private SimpleTrafficLight lightNS, lightEW, lightNE, lightNW, lightSE;

    // ── UI refs ────────────────────────────────────────────────────
    private Canvas    canvas;
    private Label     lblTime, lblFPS;
    private Label     statSpawned, statFinished, statCrashed, statCollisions, statThroughput, statAvgTravel;
    private Button    btnStartPause;
    private TextArea  logArea;
    private VBox      lightStatusBox;

    // ── Stats ──────────────────────────────────────────────────────
    private double          simTime      = 0;
    private double          simSpeedMul  = 1.0;
    private final AtomicInteger totalSpawned  = new AtomicInteger(0);
    private final AtomicInteger totalFinished = new AtomicInteger(0);
    private final AtomicInteger totalCrashed  = new AtomicInteger(0);
    private final AtomicInteger totalCollisions = new AtomicInteger(0);
    private double totalTravelTime = 0;
    private final Map<String, Double> spawnTimes = new HashMap<>();

    // ── Spawn ──────────────────────────────────────────────────────
    private double   spawnTimer    = 0;
    private double   spawnInterval = 3.5;
    private int      spawnRR       = 0;
    private int      dirRR         = 0;
    private int      laneRR        = 0;
    private static final String[] AUTO_TYPES =
        {"car","car","car","car","motorbike","motorbike","bus","truck","bicycle","car","car","car"};

    // ── FPS ────────────────────────────────────────────────────────
    private long   frameCount = 0;
    private double fpsTimer   = 0;
    private double currentFPS = 0;

    // ── Collision cooldown: prevent instant re-crash after spawn ───
    private final Map<String, Double> crashedDisplayTimers = new HashMap<>();
    private final List<CollisionEffect> collisionEffects = new ArrayList<>();
    private final List<LightClickTarget> lightClickTargets = new ArrayList<>();

    // ── Debug visualization ────────────────────────────────────────
    private boolean showHitboxes = false;

    @Override
    public void start(Stage stage) {
        SpriteLoader.preloadAll();
        SoundManager.preloadAll();
        stage.setTitle("🚦 Traffic Simulation — Mô phỏng Giao thông");
        initWorld();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.setTop(buildHeader());
        root.setCenter(buildCanvasArea());
        root.setRight(buildControlPanel());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, CANVAS_W + 310, CANVAS_H + 56);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        startGameLoop();
        engine.start();
        SoundManager.loop(SoundType.TRAFFIC_AMBIENCE);
        log("✅ Mô phỏng khởi động — chế độ: " + modeName(currentMode));
    }

    // ══════════════════════════════════════════════════════════════
    //  World init
    // ══════════════════════════════════════════════════════════════
    private void initWorld() {
        world  = new SimulationWorld();
        engine = new SimulationEngine(world);
        collisionManager.clear();
        crashedDisplayTimers.clear();
        collisionEffects.clear();

        lightNS = new SimpleTrafficLight("light-NS", LightColor.GREEN,  new LightTiming(18,3,18));
        lightEW = new SimpleTrafficLight("light-EW", LightColor.RED,    new LightTiming(18,3,18));
        lightNE = new SimpleTrafficLight("light-NE", LightColor.RED,    new LightTiming(18,3,18));
        lightNW = new SimpleTrafficLight("light-NW", LightColor.GREEN,  new LightTiming(18,3,18));
        lightSE = new SimpleTrafficLight("light-SE", LightColor.RED,    new LightTiming(18,3,18));

        lightEW.forceRed();
        lightNE.forceRed();
        lightSE.forceRed();

        world.registerTrafficLight(lightNS);
        world.registerTrafficLight(lightEW);
        world.registerTrafficLight(lightNE);
        world.registerTrafficLight(lightNW);
        world.registerTrafficLight(lightSE);
        setLightsAuto(autoLights);

        // Seed a few vehicles with staggered starting positions
        switch (currentMode) {
            case FOUR_WAY -> seedFourWay();
            case THREE_WAY -> seedThreeWay();
            case FIVE_WAY -> seedFiveWay();
            case GRID -> seedGrid();
        }
    }

    private void seedFourWay() {
        spawnAt("car",       makeNorthSouthPath(0), 0);
        spawnAt("bus",       makeEastWestPath(0),   0);
        spawnAt("car",       makeSouthNorthPath(0), 0);
        spawnAt("ambulance", makeWestEastPath(0),   0);
    }
    private void seedThreeWay() {
        spawnAt("car", makeThreeWayPath(0), 0);
        spawnAt("car", makeThreeWayPath(1), 0);
        spawnAt("bus", makeThreeWayPath(2), 0);
    }
    private void seedFiveWay() {
        for (int i = 0; i < 4; i++) spawnAt("car", makeFiveWayPath(i), 0);
        spawnAt("ambulance", makeFiveWayPath(4), 0);
    }
    private void seedGrid() {
        spawnAt("car", makeGridPath(0), 0);
        spawnAt("bus", makeGridPath(1), 0);
        spawnAt("car", makeGridPath(2), 0);
        spawnAt("truck", makeGridPath(3), 0);
        spawnAt("motorbike", makeGridPath(4), 0);
        spawnAt("ambulance", makeGridPath(5), 0);
    }

    // ══════════════════════════════════════════════════════════════
    //  UI Builders
    // ══════════════════════════════════════════════════════════════
    private HBox buildHeader() {
        HBox hb = new HBox(14);
        hb.setPadding(new Insets(12,20,10,20));
        hb.setAlignment(Pos.CENTER_LEFT);
        hb.setStyle("-fx-background-color: #16213e;");

        Label title = new Label("🚦 Traffic Simulation");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 21));
        title.setTextFill(Color.web("#e2e8f0"));

        Label sub = new Label("Mô phỏng Giao thông Thông minh");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#94a3b8"));

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        lblFPS = new Label("FPS: --");
        lblFPS.setFont(Font.font("Segoe UI",FontWeight.BOLD,13));
        lblFPS.setTextFill(Color.web("#64ffda"));

        hb.getChildren().addAll(title, sub, sp, lblFPS);
        return hb;
    }

    private StackPane buildCanvasArea() {
        canvas = new Canvas(CANVAS_W, CANVAS_H);
        canvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY()));
        StackPane sp = new StackPane(canvas);
        sp.setStyle("-fx-background-color: #0d1b2a;");
        return sp;
    }

    private ScrollPane buildControlPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(14,14,14,10));
        panel.setPrefWidth(305);
        panel.setStyle("-fx-background-color: #16213e;");

        // ── Stats ──────────────────────────────────────────────────
        panel.getChildren().add(sectionLbl("📊 Thống kê"));
        statSpawned  = valueLbl("0");
        statFinished = valueLbl("0");
        statCrashed  = valueLbl("0");
        statCollisions = valueLbl("0");
        statThroughput = valueLbl("0.0/min");
        statAvgTravel = valueLbl("--");
        panel.getChildren().addAll(
            statRow("🚗 Tổng xe đã tạo:", statSpawned),
            statRow("✅ Qua giao lộ:", statFinished),
            statRow("💥 Xe hỏng:", statCrashed),
            statRow("⚠ Số vụ va chạm:", statCollisions),
            statRow("📈 Lưu lượng:", statThroughput),
            statRow("⏱ TG đi TB:", statAvgTravel)
        );
        panel.getChildren().add(separator());

        // ── Scenario chooser ───────────────────────────────────────
        panel.getChildren().add(sectionLbl("🗺 Chọn Kịch bản"));
        ToggleGroup tg = new ToggleGroup();
        HBox scRow1 = new HBox(6);
        HBox scRow2 = new HBox(6);
        for (ScenarioMode m : ScenarioMode.values()) {
            ToggleButton tb = new ToggleButton(scenarioIcon(m) + " " + modeName(m));
            tb.setToggleGroup(tg);
            tb.setSelected(m == currentMode);
            tb.setStyle(toggleStyle(m == currentMode));
            tb.setOnAction(e -> {
                currentMode = m;
                for (javafx.scene.Node n : scRow1.getChildren()) styleToggle((ToggleButton)n, tg);
                for (javafx.scene.Node n : scRow2.getChildren()) styleToggle((ToggleButton)n, tg);
                resetSimulation();
            });
            tb.selectedProperty().addListener((obs,ov,nv) -> tb.setStyle(toggleStyle(nv)));
            if (m == ScenarioMode.FOUR_WAY || m == ScenarioMode.THREE_WAY) scRow1.getChildren().add(tb);
            else scRow2.getChildren().add(tb);
            HBox.setHgrow(tb, Priority.ALWAYS); tb.setMaxWidth(Double.MAX_VALUE);
        }
        panel.getChildren().addAll(scRow1, scRow2);
        panel.getChildren().add(separator());

        // ── Traffic lights ─────────────────────────────────────────
        panel.getChildren().add(sectionLbl("🚦 Trạng thái Đèn"));
        lightStatusBox = new VBox(5);
        panel.getChildren().add(lightStatusBox);
        panel.getChildren().add(separator());

        // ── Controls ───────────────────────────────────────────────
        panel.getChildren().add(sectionLbl("🎛️ Điều khiển"));

        btnStartPause = new Button("⏸ Tạm dừng");
        styleBtn(btnStartPause, "#3b82f6");
        btnStartPause.setMaxWidth(Double.MAX_VALUE);
        btnStartPause.setOnAction(e -> togglePause());

        Button btnReset = new Button("🔄 Đặt lại");
        styleBtn(btnReset, "#6366f1");
        btnReset.setMaxWidth(Double.MAX_VALUE);
        btnReset.setOnAction(e -> resetSimulation());

        Button btnSwitchLight = new Button("🚦 Đổi đèn thủ công");
        styleBtn(btnSwitchLight, "#0ea5e9");
        btnSwitchLight.setMaxWidth(Double.MAX_VALUE);
        btnSwitchLight.setOnAction(e -> {
            lightNS.switchManually(); lightEW.switchManually();
            lightNE.switchManually(); lightNW.switchManually(); lightSE.switchManually();
            log("🚦 Đổi đèn thủ công.");
        });

        ToggleButton btnAutoLights = new ToggleButton("🤖 Đèn tự động");
        btnAutoLights.setSelected(autoLights);
        btnAutoLights.setStyle(toggleStyle(true));
        btnAutoLights.setMaxWidth(Double.MAX_VALUE);
        btnAutoLights.selectedProperty().addListener((obs, oldVal, selected) -> {
            autoLights = selected;
            setLightsAuto(autoLights);
            btnAutoLights.setText(autoLights ? "🤖 Đèn tự động" : "👆 Đèn thủ công");
            btnAutoLights.setStyle(toggleStyle(selected));
            log(autoLights ? "🤖 Đèn tự động." : "👆 Đèn thủ công: click vào đèn để đổi màu.");
        });

        Button btnToggleHitbox = new Button("📦 Ẩn Hitbox");
        styleBtn(btnToggleHitbox, "#8b5cf6");
        btnToggleHitbox.setMaxWidth(Double.MAX_VALUE);
        btnToggleHitbox.setOnAction(e -> {
            showHitboxes = !showHitboxes;
            btnToggleHitbox.setText(showHitboxes ? "📦 Hiện Hitbox" : "📦 Ẩn Hitbox");
            log(showHitboxes ? "📦 Hiện hitbox" : "📦 Ẩn hitbox");
        });

        ToggleButton btnRenderMode = new ToggleButton("🎨 Basic");
        btnRenderMode.setSelected(false);
        btnRenderMode.setStyle(toggleStyle(false));
        btnRenderMode.setMaxWidth(Double.MAX_VALUE);
        btnRenderMode.selectedProperty().addListener((obs, oldVal, graphics) -> {
            renderMode = graphics ? RenderMode.GRAPHICS : RenderMode.BASIC;
            btnRenderMode.setText(graphics ? "🖼 Graphics" : "🎨 Basic");
            btnRenderMode.setStyle(toggleStyle(graphics));
            log("🎨 Chế độ hiển thị: " + renderMode);
        });

        ComboBox<String> cbCountdown = new ComboBox<>();
        cbCountdown.getItems().addAll("Đếm luôn", "Chỉ 10 giây cuối", "Không đếm");
        cbCountdown.setValue("Đếm luôn");
        styleCombo(cbCountdown);
        cbCountdown.setOnAction(e -> {
            countdownMode = switch (cbCountdown.getSelectionModel().getSelectedIndex()) {
                case 1 -> CountdownMode.LAST_10_SECONDS;
                case 2 -> CountdownMode.HIDDEN;
                default -> CountdownMode.ALWAYS;
            };
            log("⏱ Kiểu đếm đèn: " + cbCountdown.getValue());
        });

        Label spdLbl = smallLbl("⚡ Tốc độ mô phỏng: 1.0×");
        Slider sldSpd = new Slider(0.1, 3.0, 1.0);
        styleSlider(sldSpd);
        sldSpd.valueProperty().addListener((obs, oldVal, nv) -> {
            simSpeedMul = nv.doubleValue();
            spdLbl.setText(String.format("⚡ Tốc độ mô phỏng: %.1f×", simSpeedMul));
        });

        Label spawnLbl = smallLbl("🕐 Khoảng sinh xe: 3.5s");
        Slider sldSpawn = new Slider(0.5, 10.0, 3.5);
        styleSlider(sldSpawn);
        sldSpawn.valueProperty().addListener((obs, oldVal, nv) -> {
            spawnInterval = nv.doubleValue();
            spawnLbl.setText(String.format("🕐 Khoảng sinh xe: %.1fs", spawnInterval));
        });

        panel.getChildren().addAll(btnStartPause, btnReset, btnSwitchLight, btnAutoLights,
                                   btnRenderMode, btnToggleHitbox,
                                   smallLbl("Kiểu đếm đèn:"), cbCountdown,
                                   spdLbl, sldSpd, spawnLbl, sldSpawn);
        panel.getChildren().add(separator());

        // ── Manual spawn ───────────────────────────────────────────
        panel.getChildren().add(sectionLbl("➕ Thêm Phương tiện"));

        String[] typeLabels = {"🚗 Ô tô","🏍 Mô tô","🚌 Xe buýt","🚚 Xe tải",
                               "🚲 Xe đạp","🚑 Cứu thương","🚒 Cứu hỏa"};
        String[] typeKeys   = {"car","motorbike","bus","truck","bicycle","ambulance","firetruck"};

        ComboBox<String> cbType = new ComboBox<>();
        cbType.getItems().addAll(typeLabels); cbType.setValue(typeLabels[0]);
        styleCombo(cbType);

        // Direction options depend on mode — rebuilt on reset
        ComboBox<String> cbDir = new ComboBox<>();
        rebuildDirCombo(cbDir);

        ComboBox<String> cbDriver = new ComboBox<>();
        cbDriver.getItems().addAll("🧑 Normal","😤 Aggressive","🚨 Emergency");
        cbDriver.setValue("🧑 Normal");
        styleCombo(cbDriver);

        Button btnAdd = new Button("➕ Thêm xe ngay");
        styleBtn(btnAdd, "#10b981");
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        btnAdd.setOnAction(e -> {
            String typeKey = typeKeys[cbType.getSelectionModel().getSelectedIndex()];
            int dirIdx     = cbDir.getSelectionModel().getSelectedIndex();
            var path       = getPathByModeAndDir(dirIdx);
            var drv = switch (cbDriver.getSelectionModel().getSelectedIndex()) {
                case 1 -> new AggressiveDriver();
                case 2 -> new EmergencyDriver();
                default -> new NormalDriver();
            };
            doSpawn(typeKey, path, drv);
        });

        panel.getChildren().addAll(
            smallLbl("Loại xe:"), cbType,
            smallLbl("Hướng đi:"), cbDir,
            smallLbl("Kiểu lái:"), cbDriver,
            btnAdd
        );
        panel.getChildren().add(separator());
        // ── Âm thanh ──────────────────────────────────────────────
        panel.getChildren().add(sectionLbl("🔊 Âm thanh"));

        // Nút mute
        ToggleButton btnMute = new ToggleButton("🔊 Bật tiếng");
        btnMute.setSelected(false);
        btnMute.setStyle(toggleStyle(false));
        btnMute.setMaxWidth(Double.MAX_VALUE);
        btnMute.selectedProperty().addListener((obs, oldVal, muted) -> {
            SoundManager.setMuted(muted);
            btnMute.setText(muted ? "🔇 Tắt tiếng" : "🔊 Bật tiếng");
            btnMute.setStyle(toggleStyle(!muted));
            log(muted ? "🔇 Tắt tiếng." : "🔊 Bật tiếng.");
         });

        // Slider volume
        Label volLbl = smallLbl("🔉 Âm lượng: 70%");
        Slider sldVol = new Slider(0, 1.0, 0.7);
        styleSlider(sldVol);
        sldVol.valueProperty().addListener((obs, oldVal, nv) -> {
            SoundManager.setMasterVolume(nv.doubleValue());
            volLbl.setText(String.format("🔉 Âm lượng: %.0f%%", nv.doubleValue() * 100));
        });

        panel.getChildren().addAll(btnMute, volLbl, sldVol);
        panel.getChildren().add(separator());


        // ── Log ───────────────────────────────────────────────────
        panel.getChildren().add(sectionLbl("📝 Nhật ký"));
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setStyle("-fx-control-inner-background:#0f3460;-fx-text-fill:#94a3b8;" +
                         "-fx-font-family:Consolas;-fx-font-size:10;");
        panel.getChildren().add(logArea);

        ScrollPane sp = new ScrollPane(panel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background:#16213e;-fx-background-color:#16213e;" +
                    "-fx-border-color:#0f3460;-fx-border-width:0 0 0 2;");
        sp.setPrefWidth(310);
        return sp;
    }

    private void rebuildDirCombo(ComboBox<String> cb) {
        cb.getItems().clear();
        switch (currentMode) {
            case FOUR_WAY -> cb.getItems().addAll("⬇ Bắc→Nam","⬆ Nam→Bắc","⬅ Đông→Tây","➡ Tây→Đông");
            case THREE_WAY -> cb.getItems().addAll("⬇ Bắc→Nam","⬆ Nam→Bắc","↱ Tây→Bắc","↳ Tây→Nam");
            case FIVE_WAY -> cb.getItems().addAll("⬇ Bắc→Nam","⬆ Nam→Bắc","⬅ Đông→Tây","➡ Tây→Đông","↙ Đông-Bắc→Tây");
            case GRID -> cb.getItems().addAll("⬇ Cột trái","⬆ Cột giữa","⬇ Cột phải","➡ Hàng trên","⬅ Hàng giữa","➡ Hàng dưới");
        }
        cb.setValue(cb.getItems().get(0));
    }

    private HBox buildStatusBar() {
        HBox hb = new HBox(18);
        hb.setPadding(new Insets(5,20,5,20));
        hb.setAlignment(Pos.CENTER_LEFT);
        hb.setStyle("-fx-background-color:#0f3460;");
        lblTime = new Label("⏱ 0.0s");
        lblTime.setTextFill(Color.web("#94a3b8"));
        lblTime.setFont(Font.font("Segoe UI",12));
        Label cr = new Label("Traffic Simulation v2.0 — JavaFX UI");
        cr.setTextFill(Color.web("#475569"));
        cr.setFont(Font.font("Segoe UI",11));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        hb.getChildren().addAll(lblTime, sp, cr);
        return hb;
    }

    // ══════════════════════════════════════════════════════════════
    //  Game Loop
    // ══════════════════════════════════════════════════════════════
    private void startGameLoop() {
        gameLoop = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNano == 0) { lastNano = now; return; }
                double rawDelta = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;
                double delta = Math.min(rawDelta, 0.05) * simSpeedMul;

                frameCount++; fpsTimer += rawDelta;
                if (fpsTimer >= 1.0) {
                    currentFPS = frameCount / fpsTimer;
                    frameCount = 0; fpsTimer = 0;
                }

                if (engine.isRunning()) {
                    simTime += delta;
                    engine.update(delta);
                    spawnTimer += delta;
                    if (spawnTimer >= spawnInterval) { spawnTimer = 0; autoSpawn(); }
                    collisionManager.updateCooldowns(delta);
                    handleCollisionEvents(collisionManager.detectAndResolve(world));
                    updateCollisionEffects(delta);
                    cleanupVehicles(delta);
                }
                render();
                updateUI();
            }
        };
        gameLoop.start();
    }

    // ══════════════════════════════════════════════════════════════
    //  Collision event handling
    // ══════════════════════════════════════════════════════════════
    private void handleCollisionEvents(List<CollisionEvent> events) {
        for (CollisionEvent event : events) {
            totalCollisions.incrementAndGet();
            SoundManager.play(SoundType.CRASH);
            collisionEffects.add(new CollisionEffect(event.getPosition(), 0.9));

            Vehicle a = event.getFirst();
            Vehicle b = event.getSecond();
            switch (event.getType()) {
                case PRIORITY_PUSH -> {
                    Vehicle crashed = a.isPriorityVehicle() ? b : a;
                    Vehicle priority = a.isPriorityVehicle() ? a : b;
                    log("💥 Va chạm: " + crashed.getId() + " bị xe ưu tiên " + priority.getId() + " đẩy");
                }
                case NORMAL_CRASH -> log("💥 Va chạm: " + a.getId() + " ↔ " + b.getId());
                case PRIORITY_YIELD -> log("⚠ Hai xe ưu tiên gặp nhau: " + a.getId() + " ↔ " + b.getId());
            }
        }
    }

    private void updateCollisionEffects(double deltaTime) {
        for (CollisionEffect effect : collisionEffects) {
            effect.remainingSeconds -= deltaTime;
        }
        collisionEffects.removeIf(effect -> effect.remainingSeconds <= 0);
    }

    // ══════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════
    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        lightClickTargets.clear();
        g.setFill(Color.web("#1a2332")); g.fillRect(0,0,CANVAS_W,CANVAS_H);

        switch (currentMode) {
            case FOUR_WAY  -> renderFourWay(g);
            case THREE_WAY -> renderThreeWay(g);
            case FIVE_WAY  -> renderFiveWay(g);
            case GRID      -> renderGrid(g);
        }

        // Vehicles
        for (Vehicle v : world.getVehicles()) {
            drawVehicle(g, v.toRenderableState());
            if (showHitboxes) drawHitbox(g, v);
        }
        drawCollisionEffects(g);

        // Pause overlay
        if (!engine.isRunning()) {
            g.setFill(Color.web("#000",0.45));
            g.fillRect(0,0,CANVAS_W,CANVAS_H);
            g.setFill(Color.web("#f8fafc"));
            g.setFont(Font.font("Segoe UI",FontWeight.BOLD,36));
            g.fillText("⏸ TẠM DỪNG", CANVAS_W/2.0-110, CANVAS_H/2.0+12);
        }
    }

    // ── Four-way intersection ──────────────────────────────────────
    private void renderFourWay(GraphicsContext g) {
        double cx = CANVAS_W/2.0, cy = CANVAS_H/2.0;
        drawGrass(g);
        // vertical + horizontal roads
        fillRoad(g, cx-ROAD_HALF, 0, ROAD_HALF*2, CANVAS_H);
        fillRoad(g, 0, cy-ROAD_HALF, CANVAS_W, ROAD_HALF*2);
        // intersection box
        g.setFill(Color.web("#4b5563")); g.fillRect(cx-ROAD_HALF,cy-ROAD_HALF,ROAD_HALF*2,ROAD_HALF*2);
        drawLaneCenter(g, cx, 0, cx, cy-ROAD_HALF);
        drawLaneCenter(g, cx, cy+ROAD_HALF, cx, CANVAS_H);
        drawLaneCenter(g, 0, cy, cx-ROAD_HALF, cy);
        drawLaneCenter(g, cx+ROAD_HALF, cy, CANVAS_W, cy);
        drawRoadEdges4Way(g, cx, cy);
        drawZebra4Way(g, cx, cy);
        drawLight(g, cx+ROAD_HALF+10, cy-ROAD_HALF-48, lightNS); // N
        drawLight(g, cx-ROAD_HALF-22, cy+ROAD_HALF+6,  lightNS); // S
        drawLight(g, cx+ROAD_HALF+10, cy+ROAD_HALF+6,  lightEW); // E
        drawLight(g, cx-ROAD_HALF-22, cy-ROAD_HALF-48, lightEW); // W
    }

    // ── Three-way (T-junction): roads from N, S, W — no East arm ──
    private void renderThreeWay(GraphicsContext g) {
        double cx = CANVAS_W/2.0, cy = CANVAS_H/2.0;
        g.setFill(Color.web("#1e3a2f"));
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);
        fillRoad(g, cx-ROAD_HALF, 0, ROAD_HALF*2, CANVAS_H);
        fillRoad(g, 0, cy-ROAD_HALF, cx+ROAD_HALF, ROAD_HALF*2);
        g.setFill(Color.web("#4b5563")); g.fillRect(cx-ROAD_HALF,cy-ROAD_HALF,ROAD_HALF*2,ROAD_HALF*2);
        drawLaneCenter(g, cx, 0, cx, cy-ROAD_HALF);
        drawLaneCenter(g, cx, cy+ROAD_HALF, cx, CANVAS_H);
        drawLaneCenter(g, 0, cy, cx-ROAD_HALF, cy);
        // Right-side cap (east dead end)
        g.setFill(Color.web("#1e3a2f"));
        g.fillRect(cx+ROAD_HALF, cy-ROAD_HALF, 12, ROAD_HALF*2);
        g.setStroke(Color.web("#e2e8f0",0.4)); g.setLineWidth(1.2);
        g.strokeLine(cx-ROAD_HALF, 0, cx-ROAD_HALF, cy-ROAD_HALF);
        g.strokeLine(cx+ROAD_HALF, 0, cx+ROAD_HALF, cy-ROAD_HALF);
        g.strokeLine(cx-ROAD_HALF, cy+ROAD_HALF, cx-ROAD_HALF, CANVAS_H);
        g.strokeLine(cx+ROAD_HALF, cy+ROAD_HALF, cx+ROAD_HALF, CANVAS_H);
        g.strokeLine(0, cy-ROAD_HALF, cx-ROAD_HALF, cy-ROAD_HALF);
        g.strokeLine(0, cy+ROAD_HALF, cx-ROAD_HALF, cy+ROAD_HALF);
        // lights
        drawLight(g, cx+ROAD_HALF+10,  cy-ROAD_HALF-48, lightNS);
        drawLight(g, cx-ROAD_HALF-22, cy+ROAD_HALF+6, lightNS);
        drawLight(g, cx-ROAD_HALF-22, cy-ROAD_HALF-48, lightEW);
        g.setFill(Color.web("#e2e8f0",0.4)); g.setFont(Font.font("Segoe UI",13));
        g.fillText("T-JUNCTION", cx-40, CANVAS_H-18);
    }

    // ── Five-way intersection ──────────────────────────────────────
    private void renderFiveWay(GraphicsContext g) {
        double cx = CANVAS_W/2.0, cy = CANVAS_H/2.0;
        drawGrass(g);
        // main 4 arms
        fillRoad(g, cx-ROAD_HALF, 0, ROAD_HALF*2, CANVAS_H);
        fillRoad(g, 0, cy-ROAD_HALF, CANVAS_W, ROAD_HALF*2);
        // 5th arm: full diagonal NE road
        double armLen = 520;
        g.save();
        g.translate(cx, cy);
        g.rotate(-45);
        fillRoad(g, 0, -ROAD_HALF, armLen, ROAD_HALF*2);
        drawLaneCenter(g, 0, 0, armLen, 0);
        g.restore();
        g.setFill(Color.web("#4b5563"));
        g.fillOval(cx-ROAD_HALF*1.3, cy-ROAD_HALF*1.3, ROAD_HALF*2.6, ROAD_HALF*2.6);
        drawLaneCenter(g, cx, 0, cx, cy-ROAD_HALF);
        drawLaneCenter(g, cx, cy+ROAD_HALF, cx, CANVAS_H);
        drawLaneCenter(g, 0, cy, cx-ROAD_HALF, cy);
        drawLaneCenter(g, cx+ROAD_HALF, cy, CANVAS_W, cy);
        
        drawLight(g, cx+ROAD_HALF+10, cy-ROAD_HALF-48, lightNS);
        drawLight(g, cx-ROAD_HALF-22, cy+ROAD_HALF+6,  lightNS);
        drawLight(g, cx+ROAD_HALF+10, cy+ROAD_HALF+6,  lightEW);
        drawLight(g, cx-ROAD_HALF-22, cy-ROAD_HALF-48, lightEW);
        drawLight(g, cx+ROAD_HALF+10, cy-ROAD_HALF-145, lightNE);
    
        g.setFill(Color.web("#e2e8f0",0.4)); g.setFont(Font.font("Segoe UI",13));
        g.fillText("5-WAY INTERSECTION", cx-65, CANVAS_H-18);
    }

    // ── Grid network (2×2 blocks = 3×3 intersections) ─────────────
    private void renderGrid(GraphicsContext g) {
        drawGrassGrid(g);
        double gapX = CANVAS_W / 4.0;
        double gapY = CANVAS_H / 4.0;
        // 3 vertical roads
        for (int col = 0; col < 3; col++) {
            double x = gapX * (col+1);
            fillRoad(g, x-ROAD_HALF*0.7, 0, ROAD_HALF*1.4, CANVAS_H);
        }
        // 3 horizontal roads
        for (int row = 0; row < 3; row++) {
            double y = gapY * (row+1);
            fillRoad(g, 0, y-ROAD_HALF*0.7, CANVAS_W, ROAD_HALF*1.4);
        }
        // intersection boxes
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                double x = gapX*(col+1), y = gapY*(row+1);
                g.setFill(Color.web("#4b5563"));
                g.fillRect(x-ROAD_HALF*0.7, y-ROAD_HALF*0.7, ROAD_HALF*1.4, ROAD_HALF*1.4);
                // traffic lights at each intersection (alternate)
                SimpleTrafficLight lt = ((col+row)%2==0) ? lightNS : lightEW;
                drawLight(g, x+ROAD_HALF*0.7+2, y-ROAD_HALF*0.7-28, lt);
            }
        }
        // lane centers
        for (int col = 0; col < 3; col++) {
            double x = gapX*(col+1);
            drawLaneCenter(g, x, 0, x, CANVAS_H);
        }
        for (int row = 0; row < 3; row++) {
            double y = gapY*(row+1);
            drawLaneCenter(g, 0, y, CANVAS_W, y);
        }
        g.setFill(Color.web("#e2e8f0",0.4)); g.setFont(Font.font("Segoe UI",13));
        g.fillText("ROAD NETWORK (3×3 Grid)", 12, CANVAS_H-18);
    }

    // ── Drawing helpers ────────────────────────────────────────────
    private void drawGrass(GraphicsContext g) {
        g.setFill(Color.web("#1e3a2f"));
        double cx=CANVAS_W/2.0, cy=CANVAS_H/2.0;
        g.fillRect(0,0,cx-ROAD_HALF,cy-ROAD_HALF);
        g.fillRect(cx+ROAD_HALF,0,CANVAS_W,cy-ROAD_HALF);
        g.fillRect(0,cy+ROAD_HALF,cx-ROAD_HALF,CANVAS_H);
        g.fillRect(cx+ROAD_HALF,cy+ROAD_HALF,CANVAS_W,CANVAS_H);
    }

    private void drawGrassGrid(GraphicsContext g) {
        g.setFill(Color.web("#1e3a2f")); g.fillRect(0,0,CANVAS_W,CANVAS_H);
    }

    private void fillRoad(GraphicsContext g, double x, double y, double w, double h) {
        g.setFill(Color.web("#374151")); g.fillRect(x,y,w,h);
    }

    private void drawLaneCenter(GraphicsContext g, double x1, double y1, double x2, double y2) {
        g.setStroke(Color.web("#facc15",0.6)); g.setLineWidth(1.5);
        g.setLineDashes(18,12); g.strokeLine(x1,y1,x2,y2); g.setLineDashes(null);
    }

    private void drawRoadEdges4Way(GraphicsContext g, double cx, double cy) {
        g.setStroke(Color.web("#e2e8f0",0.4)); g.setLineWidth(1.2);
        g.strokeLine(cx-ROAD_HALF,0,cx-ROAD_HALF,cy-ROAD_HALF);
        g.strokeLine(cx+ROAD_HALF,0,cx+ROAD_HALF,cy-ROAD_HALF);
        g.strokeLine(cx-ROAD_HALF,cy+ROAD_HALF,cx-ROAD_HALF,CANVAS_H);
        g.strokeLine(cx+ROAD_HALF,cy+ROAD_HALF,cx+ROAD_HALF,CANVAS_H);
        g.strokeLine(0,cy-ROAD_HALF,cx-ROAD_HALF,cy-ROAD_HALF);
        g.strokeLine(0,cy+ROAD_HALF,cx-ROAD_HALF,cy+ROAD_HALF);
        g.strokeLine(cx+ROAD_HALF,cy-ROAD_HALF,CANVAS_W,cy-ROAD_HALF);
        g.strokeLine(cx+ROAD_HALF,cy+ROAD_HALF,CANVAS_W,cy+ROAD_HALF);
    }

    private void drawZebra4Way(GraphicsContext g, double cx, double cy) {
        g.setFill(Color.web("#e2e8f0",0.25));
        int n = 5; double sw = ROAD_HALF*2/n;
        for (int i=0;i<n;i++) {
            g.fillRect(cx-ROAD_HALF+i*sw+1, cy-ROAD_HALF-16, sw-2, 13);
            g.fillRect(cx-ROAD_HALF+i*sw+1, cy+ROAD_HALF+3,  sw-2, 13);
            g.fillRect(cx-ROAD_HALF-16, cy-ROAD_HALF+i*sw+1, 13, sw-2);
            g.fillRect(cx+ROAD_HALF+3,  cy-ROAD_HALF+i*sw+1, 13, sw-2);
        }
    }

    /*private void drawLight(GraphicsContext g, double x, double y, LightColor color) {
        drawLightBody(g, x, y, color);
    }*/

    private void drawLight(GraphicsContext g, double x, double y, SimpleTrafficLight light) {
        lightClickTargets.add(new LightClickTarget(x, y, 15, 42, light));
        drawLightBody(g, x, y, light.getColor());
        if (shouldDrawCountdown(light)) {
            g.setFill(Color.web("#f8fafc"));
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            g.fillText(String.format("%.0f", Math.ceil(light.getRemainingTime())), x - 2, y + 54);
        }
    }

    private void drawLightBody(GraphicsContext g, double x, double y, LightColor color) {
        g.setFill(Color.web("#1f2937")); g.fillRoundRect(x,y,15,42,4,4);
        boolean r = color==LightColor.RED, yl = color==LightColor.YELLOW, gr = color==LightColor.GREEN;
        Glow glow = new Glow(0.9);
        g.setFill(r ? Color.web("#ef4444") : Color.web("#7f1d1d"));
        if (r) g.setEffect(glow); g.fillOval(x+2,y+2,11,11); g.setEffect(null);
        g.setFill(yl ? Color.web("#fbbf24") : Color.web("#78350f"));
        if (yl) g.setEffect(glow); g.fillOval(x+2,y+15,11,11); g.setEffect(null);
        g.setFill(gr ? Color.web("#34d399") : Color.web("#064e3b"));
        if (gr) g.setEffect(glow); g.fillOval(x+2,y+28,11,11); g.setEffect(null);
    }

    private boolean shouldDrawCountdown(SimpleTrafficLight light) {
        return switch (countdownMode) {
            case ALWAYS -> true;
            case LAST_10_SECONDS -> light.getRemainingTime() <= 10;
            case HIDDEN -> false;
        };
    }


    private void drawVehicle(GraphicsContext g, core.vehicle.RenderableState s) {
        double x=s.getPosition().x, y=s.getPosition().y;
        g.save();
        g.translate(x,y);
       // g.rotate(Math.toDegrees(s.getRotation()));
        double scale = currentMode == ScenarioMode.GRID ? 0.72 : 1.08;
        g.scale(scale, scale);

        if (s.isCrashed()) {
            //gray square with red X
            /*g.setFill(Color.web("#374151")); g.fillRoundRect(-s.getLength()/2, -s.getWidth()/2, s.getLength(), s.getWidth(), 4, 4);
            g.setFill(Color.web("#ef4444")); 
            g.setFont(Font.font(10)); 
            g.fillText("X", -4, 4);*/
            
            // explosion.png
            var explosionStream = TrafficSimulationUI.class.getResourceAsStream("/assets/sprites/explosion.png");
            Image explosion = explosionStream == null ? null : new Image(explosionStream);
            if (explosion != null) {
                double drawW = s.getLength();
                double drawH = s.getWidth();
                g.drawImage(explosion, -drawW/2, -drawH/2, drawW, drawH);
            } else {
                // fallback: gray square
                g.setFill(Color.web("#374151"));
                g.fillRoundRect(-s.getLength()/2,-s.getWidth()/2,s.getLength(),s.getWidth(),4,4);
            }
            g.restore(); return;
        }

        // Siren glow
        if (s.isPriority() && s.isSirenFlash()) {
            g.setFill(Color.web("#fef3c7",0.25)); g.fillOval(-s.getLength(),-s.getLength(),s.getLength()*2,s.getLength()*2);
        }
        /* 
        java.awt.Color ac = s.getBodyColor();
        g.setFill(Color.rgb(ac.getRed(),ac.getGreen(),ac.getBlue()));
        g.fillRoundRect(-s.getLength()/2,-s.getWidth()/2,s.getLength(),s.getWidth(),5,5);

        if (renderMode == RenderMode.GRAPHICS) {
            java.awt.Color rc = s.getRoofColor();
            g.setFill(Color.rgb(rc.getRed(),rc.getGreen(),rc.getBlue()));
            g.fillRoundRect(-s.getLength()*0.26,-s.getWidth()*0.36,s.getLength()*0.52,s.getWidth()*0.72,3,3);

            g.setFill(Color.web("#dbeafe",0.85));
            g.fillRoundRect(s.getLength()*0.05,-s.getWidth()*0.28,s.getLength()*0.18,s.getWidth()*0.56,2,2);
            g.setFill(Color.web("#111827"));
            g.fillOval(-s.getLength()*0.24,-s.getWidth()*0.62,5,5);
            g.fillOval(s.getLength()*0.20,-s.getWidth()*0.62,5,5);
            g.fillOval(-s.getLength()*0.24,s.getWidth()*0.34,5,5);
            g.fillOval(s.getLength()*0.20,s.getWidth()*0.34,5,5);

            g.setFill(Color.web("#fef9c3",0.9));
            g.fillOval(s.getLength()/2-5,-s.getWidth()/2,5,4);
            g.fillOval(s.getLength()/2-5,s.getWidth()/2-4,5,4);
        }
        */
        //Image sprite = SpriteLoader.get(s.getSpriteKey());
        RenderAssetKey dirKey = getSpriteKey(s.getSpriteKey(), s.getRotation());
        Image sprite = SpriteLoader.get(dirKey);
        if (sprite != null) {
            double imgRatio = sprite.getHeight() / sprite.getWidth();
            double drawW = s.getLength();
            double drawH = drawW * imgRatio;
            g.drawImage(sprite, -drawW/2, -drawH/2, drawW, drawH);
        } else {
            // Fallback: vẽ hình chữ nhật nếu không có ảnh
            java.awt.Color ac = s.getBodyColor();
            g.setFill(Color.rgb(ac.getRed(),ac.getGreen(),ac.getBlue()));
            g.fillRoundRect(-s.getLength()/2,-s.getWidth()/2,s.getLength(),s.getWidth(),5,5);
        }
        
        if (s.isYielding()) { g.setStroke(Color.ORANGE); g.setLineWidth(2);
            g.strokeRoundRect(-s.getLength()/2-2,-s.getWidth()/2-2,s.getLength()+4,s.getWidth()+4,5,5); }
        
        // red square when stopped (except if yielding, which is a different state)
        if (s.isStopped() && !s.isYielding()) {
            g.setFill(Color.web("#ef4444", 0.8));
            double dirX = Math.cos(s.getRotation());
            double dirY = Math.sin(s.getRotation());
            
            double markerOffset = s.getLength() / 2.0 + 0.2;
            double markerX = -dirX * markerOffset;
            double markerY = -dirY * markerOffset;
            g.fillRect(markerX, markerY, 4, 4);
        }

        if (s.isPriority()) {
            g.setFill(s.isSirenFlash() ? Color.web("#ef4444") : Color.web("#3b82f6"));
            g.fillRect(-s.getLength()/2+2,-s.getWidth()/2-5,7,4); }
            /* 
        if (renderMode == RenderMode.BASIC) {
            g.setFill(Color.WHITE); g.setFont(Font.font("Segoe UI",FontWeight.BOLD,7));
            g.fillText(s.getBasicLabel(),-s.getLength()*0.22,3);
        }*/
        g.restore();
    }

    private RenderAssetKey getSpriteKey(RenderAssetKey base, double rotation) {
        double deg = Math.toDegrees(rotation) % 360;
        if (deg < 0) deg += 360;

        String dir;
        if      (deg >= 337.5 || deg < 22.5)  dir = "_EAST";
        else if (deg >= 22.5  && deg < 67.5)  dir = "_SOUTHEAST";
        else if (deg >= 67.5  && deg < 112.5) dir = "_SOUTH";
        else if (deg >= 112.5 && deg < 157.5) dir = "_SOUTHWEST";
        else if (deg >= 157.5 && deg < 202.5) dir = "_WEST";
        else if (deg >= 202.5 && deg < 247.5) dir = "_NORTHWEST";
        else if (deg >= 247.5 && deg < 292.5) dir = "_NORTH";
        else                                   dir = "_NORTHEAST";

        String baseName = base.name().replaceAll("_(EAST|NORTH|SOUTH|WEST|NORTHEAST|NORTHWEST|SOUTHEAST|SOUTHWEST|TOP)$", "");

        try {
            return RenderAssetKey.valueOf(baseName + dir);
        } catch (IllegalArgumentException e) {
            return base;
        }
    }

    private void drawHitbox(GraphicsContext g, Vehicle v) {
        Vector2D rightVector = new Vector2D(Math.cos(v.getRotation() + Math.PI/2), Math.sin(v.getRotation() + Math.PI/2));
        Vector2D renderPos = v.getPosition().add(rightVector.multiply(v.getLateralOffset()));
        double x = renderPos.x, y = renderPos.y;
        g.save();
        g.translate(x, y);
        g.rotate(Math.toDegrees(v.getRotation()));
        g.setStroke(Color.web("#00ff00", 0.7));
        g.setLineWidth(1.5);
        g.strokeRect(-v.getLength()/2, -v.getWidth()/2, v.getLength(), v.getWidth());
        g.restore();
    }

    private void drawCollisionEffects(GraphicsContext g) {
        for (CollisionEffect effect : collisionEffects) {
            double progress = effect.remainingSeconds / effect.initialSeconds;
            double radius = 28 * (1.0 - progress) + 8;
            Vector2D p = effect.position;

            g.setStroke(Color.web("#f97316", Math.max(0, progress)));
            g.setLineWidth(3);
            g.strokeOval(p.x - radius, p.y - radius, radius * 2, radius * 2);

            g.setFill(Color.web("#ef4444", Math.max(0, progress * 0.55)));
            g.fillOval(p.x - 5, p.y - 5, 10, 10);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  UI updates
    // ══════════════════════════════════════════════════════════════
    private void updateUI() {
        lblFPS.setText(String.format("FPS: %.0f", currentFPS));
        lblTime.setText(String.format("⏱ %.1fs", simTime));
        statSpawned.setText(String.valueOf(totalSpawned.get()));
        statFinished.setText(String.valueOf(totalFinished.get()));
        statCrashed.setText(String.valueOf(totalCrashed.get()));
        statCollisions.setText(String.valueOf(totalCollisions.get()));
        double minutes = Math.max(simTime / 60.0, 1.0 / 60.0);
        statThroughput.setText(String.format("%.1f/min", totalFinished.get() / minutes));
        statAvgTravel.setText(totalFinished.get() == 0
                ? "--"
                : String.format("%.1fs", totalTravelTime / totalFinished.get()));
        rebuildLightStatus();
    }

    private void rebuildLightStatus() {
        lightStatusBox.getChildren().clear();
        switch (currentMode) {
            case FOUR_WAY -> {
                lightStatusBox.getChildren().addAll(
                    lightRow("⬆⬇ Bắc–Nam:", lightNS.getColor()),
                    lightRow("⬅➡ Đông–Tây:", lightEW.getColor()));
            }
            case THREE_WAY -> {
                lightStatusBox.getChildren().addAll(
                    lightRow("⬇ Bắc–Nam:", lightNS.getColor()),
                    lightRow("➡ Tây–Đông:", lightEW.getColor()));
            }
            case FIVE_WAY -> {
                lightStatusBox.getChildren().addAll(
                    lightRow("⬆⬇ Bắc–Nam:", lightNS.getColor()),
                    lightRow("⬅➡ Đông–Tây:", lightEW.getColor()),
                    lightRow("↗ Đông-Bắc:", lightNE.getColor()));
            }
            case GRID -> {
                lightStatusBox.getChildren().addAll(
                    lightRow("Nhóm A (chẵn):", lightNS.getColor()),
                    lightRow("Nhóm B (lẻ):", lightEW.getColor()));
            }
        }
    }

    private HBox lightRow(String label, LightColor color) {
        Label k = new Label(label); k.setTextFill(Color.web("#94a3b8")); k.setFont(Font.font("Segoe UI",12)); k.setMinWidth(115);
        Label v = new Label(); v.setFont(Font.font("Segoe UI",FontWeight.BOLD,12));
        switch (color) {
            case RED    -> { v.setText("● ĐỎ");   v.setTextFill(Color.web("#ef4444")); }
            case YELLOW -> { v.setText("● VÀNG"); v.setTextFill(Color.web("#fbbf24")); }
            case GREEN  -> { v.setText("● XANH"); v.setTextFill(Color.web("#34d399")); }
        }
        HBox hb = new HBox(6,k,v); hb.setAlignment(Pos.CENTER_LEFT); return hb;
    }

    // ══════════════════════════════════════════════════════════════
    //  Spawning
    // ══════════════════════════════════════════════════════════════
    private void autoSpawn() {
        String type = AUTO_TYPES[spawnRR++ % AUTO_TYPES.length];
        VehiclePath path = getPathByModeAndDir(dirRR++ % dirCountForMode());
        doSpawn(type, path, null);
    }

    private int dirCountForMode() {
        return switch (currentMode) {
            case THREE_WAY, FOUR_WAY -> 4;
            case FIVE_WAY -> 5;
            case GRID -> 6;
        };
    }

    private void spawnAt(String type, VehiclePath path, double extraCooldown) {
        doSpawn(type, path, null);
    }

    private void doSpawn(String type, VehiclePath path, core.driver.DriverBehavior beh) {
        try {
            Vehicle v = beh == null ? VehicleFactory.create(type, path)
                                    : VehicleFactory.create(type, path, beh);
            world.addVehicle(v);
            collisionManager.startSpawnCooldown(v);
            spawnTimes.put(v.getId(), simTime);
            totalSpawned.incrementAndGet();
            log("🚗 " + type + " [" + v.getId() + "] " + path.getEntryArm() + "→" + path.getExitArm());
            switch (type) {
            case "bicycle"   -> SoundManager.play(SoundType.BICYCLE_BELL);
            case "ambulance" -> SoundManager.loop(SoundType.AMBULANCE_SIREN);
            case "firetruck" -> SoundManager.loop(SoundType.FIRE_TRUCK_SIREN);
        }

        } catch (Exception ex) {
            log("⚠ Lỗi: " + ex.getMessage());
        }
    }

    private void cleanupVehicles(double deltaTime) {
        List<Vehicle> rm = new ArrayList<>();
        for (Vehicle v : world.getVehicles()) {
            if (v.isFinished()) {
                rm.add(v);
                totalFinished.incrementAndGet();
                Double spawnedAt = spawnTimes.remove(v.getId());
                if (spawnedAt != null) totalTravelTime += Math.max(0, simTime - spawnedAt);
                 // ── Dừng siren khi xe ưu tiên finished ───────────
            if (v instanceof core.vehicle.Ambulance) {
                // Chỉ dừng nếu không còn xe cứu thương nào khác
                boolean stillHas = world.getVehicles().stream()
                    .filter(x -> x != v)
                    .anyMatch(x -> x instanceof core.vehicle.Ambulance);
                if (!stillHas) SoundManager.stop(SoundType.AMBULANCE_SIREN);
            }
            if (v instanceof core.vehicle.FireTruck) {
                boolean stillHas = world.getVehicles().stream()
                    .filter(x -> x != v)
                    .anyMatch(x -> x instanceof core.vehicle.FireTruck);
                if (!stillHas) SoundManager.stop(SoundType.FIRE_TRUCK_SIREN);
            }
            } else if (v.isCrashed()) {
                double remaining = crashedDisplayTimers.getOrDefault(v.getId(), CRASH_DISPLAY_SECONDS);
                remaining -= deltaTime;
                if (remaining <= 0) {
                    rm.add(v);
                    crashedDisplayTimers.remove(v.getId());
                    spawnTimes.remove(v.getId());
                    totalCrashed.incrementAndGet();
                } else {
                    crashedDisplayTimers.put(v.getId(), remaining);
                }
            }
        }
        rm.forEach(world::removeVehicle);
    }

    // ══════════════════════════════════════════════════════════════
    //  Path factories
    // ══════════════════════════════════════════════════════════════
    private double cx() { return CANVAS_W/2.0; }
    private double cy() { return CANVAS_H/2.0; }
    private int nextLane() { return laneRR++ % 2; }
    private double laneOffset(int lane) { return LANE_W / 2.0 + Math.min(Math.max(lane, 0), 1) * LANE_W; }

    // ── 4-way ─────────────────────────────────────────────────────
    private VehiclePath makeNorthSouthPath(int lane) {
        double x = cx() + laneOffset(lane);
        return new VehiclePath("ns"+lane, List.of(
            new Vector2D(x,-20), new Vector2D(x, cy()-ROAD_HALF-10),
            new Vector2D(x, cy()), new Vector2D(x, CANVAS_H+20)), 1,"light-NS","N","S");
    }
    private VehiclePath makeSouthNorthPath(int lane) {
        double x = cx() - laneOffset(lane);
        return new VehiclePath("sn"+lane, List.of(
            new Vector2D(x,CANVAS_H+20), new Vector2D(x, cy()+ROAD_HALF+10),
            new Vector2D(x, cy()), new Vector2D(x,-20)), 1,"light-NS","S","N");
    }
    private VehiclePath makeEastWestPath(int lane) {
        double y = cy() + laneOffset(lane);
        return new VehiclePath("ew"+lane, List.of(
            new Vector2D(CANVAS_W+20,y), new Vector2D(cx()+ROAD_HALF+10,y),
            new Vector2D(cx(),y), new Vector2D(-20,y)), 1,"light-EW","E","W");
    }
    private VehiclePath makeWestEastPath(int lane) {
        double y = cy() - laneOffset(lane);
        return new VehiclePath("we"+lane, List.of(
            new Vector2D(-20,y), new Vector2D(cx()-ROAD_HALF-10,y),
            new Vector2D(cx(),y), new Vector2D(CANVAS_W+20,y)), 1,"light-EW","W","E");
    }

    // ── 3-way ─────────────────────────────────────────────────────
    private VehiclePath makeThreeWayPath(int idx) {
        double cx=cx(), cy=cy(), lw=laneOffset(0);
        return switch(idx%4) {
            case 0 -> {
                double x = cx + laneOffset(nextLane());
                yield new VehiclePath("3w-ns", List.of(
                    new Vector2D(x,-20), new Vector2D(x,cy-ROAD_HALF-10),
                    new Vector2D(x,cy+ROAD_HALF+10), new Vector2D(x,CANVAS_H+20)), 1,"light-NS","N","S");
            }
            case 1 -> {
                double x = cx - laneOffset(nextLane());
                yield new VehiclePath("3w-sn", List.of(
                    new Vector2D(x,CANVAS_H+20), new Vector2D(x,cy+ROAD_HALF+10),
                    new Vector2D(x,cy-ROAD_HALF-10), new Vector2D(x,-20)), 1,"light-NS","S","N");
            }
            case 2 -> new VehiclePath("3w-wn", List.of(
                new Vector2D(-20,cy-lw), new Vector2D(cx-ROAD_HALF-10,cy-lw),
                new Vector2D(cx-lw,cy-lw), new Vector2D(cx-lw,cy-ROAD_HALF-20),
                new Vector2D(cx-lw,-20)), 1,"light-EW","W","N");
            default -> new VehiclePath("3w-ws", List.of(
                new Vector2D(-20,cy+lw), new Vector2D(cx-ROAD_HALF-10,cy+lw),
                new Vector2D(cx+lw,cy+lw), new Vector2D(cx+lw,cy+ROAD_HALF+20),
                new Vector2D(cx+lw,CANVAS_H+20)), 1,"light-EW","W","S");
        };
    }

    // ── 5-way ─────────────────────────────────────────────────────
    private VehiclePath makeFiveWayPath(int idx) {
        double cx=cx(), cy=cy(), lw=LANE_W/2.0;
        return switch(idx%5) {
            case 0 -> makeNorthSouthPath(nextLane());
            case 1 -> makeSouthNorthPath(nextLane());
            case 2 -> makeEastWestPath(nextLane());
            case 3 -> makeWestEastPath(nextLane());
            default -> new VehiclePath("ne-diag", List.of(
                new Vector2D(CANVAS_W+20,-20), new Vector2D(cx+ROAD_HALF+130,cy-ROAD_HALF-130),
                new Vector2D(cx+ROAD_HALF+35,cy-ROAD_HALF-35),
                new Vector2D(cx,cy+lw), new Vector2D(cx-ROAD_HALF-10,cy+lw),
                new Vector2D(-20,cy+lw)), 1,"light-NE","NE","W");
        };
    }

    // ── Grid paths ────────────────────────────────────────────────
    private VehiclePath makeGridPath(int idx) {
        double gx = CANVAS_W/4.0, gy = CANVAS_H/4.0;
        double lane = LANE_W / 2.0;
        return switch(idx%6) {
            case 0 -> new VehiclePath("grid-v1", List.of(
                new Vector2D(gx+lane,-20), new Vector2D(gx+lane,gy-16),
                new Vector2D(gx+lane,gy+16), new Vector2D(gx+lane,gy*2-16),
                new Vector2D(gx+lane,gy*2+16), new Vector2D(gx+lane,CANVAS_H+20)), 1,"light-NS","N1","S1");
            case 1 -> new VehiclePath("grid-v2-up", List.of(
                new Vector2D(gx*2-lane,CANVAS_H+20), new Vector2D(gx*2-lane,gy*3+16),
                new Vector2D(gx*2-lane,gy*3-16), new Vector2D(gx*2-lane,gy*2+16),
                new Vector2D(gx*2-lane,gy*2-16), new Vector2D(gx*2-lane,gy+16),
                new Vector2D(gx*2-lane,gy-16), new Vector2D(gx*2-lane,-20)), 1,"light-EW","S2","N2");
            case 2 -> new VehiclePath("grid-v3", List.of(
                new Vector2D(gx*3+lane,-20), new Vector2D(gx*3+lane,gy-16),
                new Vector2D(gx*3+lane,gy+16), new Vector2D(gx*3+lane,gy*2-16),
                new Vector2D(gx*3+lane,gy*2+16), new Vector2D(gx*3+lane,CANVAS_H+20)), 1,"light-NS","N3","S3");
            case 3 -> new VehiclePath("grid-h1", List.of(
                new Vector2D(-20,gy-lane), new Vector2D(gx-16,gy-lane),
                new Vector2D(gx+16,gy-lane), new Vector2D(gx*2-16,gy-lane),
                new Vector2D(gx*2+16,gy-lane), new Vector2D(CANVAS_W+20,gy-lane)), 1,"light-EW","W1","E1");
            case 4 -> new VehiclePath("grid-h2-left", List.of(
                new Vector2D(CANVAS_W+20,gy*2+lane), new Vector2D(gx*3+16,gy*2+lane),
                new Vector2D(gx*3-16,gy*2+lane), new Vector2D(gx*2+16,gy*2+lane),
                new Vector2D(gx*2-16,gy*2+lane), new Vector2D(gx-16,gy*2+lane),
                new Vector2D(-20,gy*2+lane)), 1,"light-NS","E2","W2");
            default -> new VehiclePath("grid-h3", List.of(
                new Vector2D(-20,gy*3-lane), new Vector2D(gx-16,gy*3-lane),
                new Vector2D(gx+16,gy*3-lane), new Vector2D(gx*2-16,gy*3-lane),
                new Vector2D(gx*2+16,gy*3-lane), new Vector2D(CANVAS_W+20,gy*3-lane)), 1,"light-EW","W3","E3");
        };
    }

    private VehiclePath getPathByModeAndDir(int idx) {
        return switch (currentMode) {
            case FOUR_WAY -> switch(idx%4) {
                case 0->makeNorthSouthPath(nextLane()); case 1->makeSouthNorthPath(nextLane());
                case 2->makeEastWestPath(nextLane()); default->makeWestEastPath(nextLane());};
            case THREE_WAY -> makeThreeWayPath(idx%4);
            case FIVE_WAY  -> makeFiveWayPath(idx%5);
            case GRID      -> makeGridPath(idx%6);
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  Controls
    // ══════════════════════════════════════════════════════════════
    private void togglePause() {
        if (engine.isRunning()) { engine.pause(); btnStartPause.setText("▶ Tiếp tục"); log("⏸ Tạm dừng.");
            SoundManager.pauseAll();
         }
        else { engine.resume(); lastNano=0; btnStartPause.setText("⏸ Tạm dừng"); log("▶ Tiếp tục."); 
            // Resume ambience
            SoundManager.loop(SoundType.TRAFFIC_AMBIENCE);
            // Resume siren nếu vẫn còn xe ưu tiên trên đường
            boolean hasAmbulance = world.getVehicles().stream()
                .anyMatch(v -> v instanceof core.vehicle.Ambulance);
            boolean hasFireTruck = world.getVehicles().stream()
                .anyMatch(v -> v instanceof core.vehicle.FireTruck);
            if (hasAmbulance) SoundManager.loop(SoundType.AMBULANCE_SIREN);
            if (hasFireTruck) SoundManager.loop(SoundType.FIRE_TRUCK_SIREN);
        }
    }

    private void handleCanvasClick(double x, double y) {
        if (autoLights) return;
        for (LightClickTarget target : lightClickTargets) {
            if (target.contains(x, y)) {
                target.light.switchManually();
                log("🚦 Click đổi đèn: " + target.light.getId());
                return;
            }
        }
    }

    private void setLightsAuto(boolean auto) {
        if (lightNS != null) lightNS.setAutoSwitch(auto);
        if (lightEW != null) lightEW.setAutoSwitch(auto);
        if (lightNE != null) lightNE.setAutoSwitch(auto);
        if (lightNW != null) lightNW.setAutoSwitch(auto);
        if (lightSE != null) lightSE.setAutoSwitch(auto);
    }

    private void resetSimulation() {
        if (engine!=null) engine.pause();
        simTime=0; spawnTimer=0; spawnRR=0; dirRR=0; laneRR=0;
        totalSpawned.set(0); totalFinished.set(0); totalCrashed.set(0); totalCollisions.set(0);
        totalTravelTime = 0;
        spawnTimes.clear();
        initWorld();
        engine = new SimulationEngine(world);
        engine.start(); lastNano=0;
        if (btnStartPause!=null) btnStartPause.setText("⏸ Tạm dừng");
        log("🔄 Đặt lại — chế độ: " + modeName(currentMode));
    }

    private void log(String msg) {
        String line = String.format("[%.1fs] %s%n", simTime, msg);
        Platform.runLater(() -> { if(logArea!=null){logArea.appendText(line); logArea.setScrollTop(Double.MAX_VALUE);}});
    }

    // ══════════════════════════════════════════════════════════════
    //  Style helpers
    // ══════════════════════════════════════════════════════════════
    private Label sectionLbl(String t) {
        Label l=new Label(t); l.setFont(Font.font("Segoe UI",FontWeight.BOLD,13)); l.setTextFill(Color.web("#e2e8f0")); return l;}
    private Label valueLbl(String t) {
        Label l=new Label(t); l.setFont(Font.font("Segoe UI",FontWeight.BOLD,12)); l.setTextFill(Color.web("#64ffda")); return l;}
    private Label smallLbl(String t) {
        Label l=new Label(t); l.setFont(Font.font("Segoe UI",11)); l.setTextFill(Color.web("#94a3b8")); return l;}
    private HBox statRow(String k, Label v) {
        Label kl=new Label(k); kl.setFont(Font.font("Segoe UI",12)); kl.setTextFill(Color.web("#94a3b8")); kl.setMinWidth(120);
        HBox h=new HBox(6,kl,v); h.setAlignment(Pos.CENTER_LEFT); return h;}
    private Separator separator() { Separator s=new Separator(); s.setStyle("-fx-background-color:#334155;"); return s;}
    private void styleBtn(Button b, String hex) {
        b.setStyle("-fx-background-color:"+hex+";-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:12;" +
                   "-fx-cursor:hand;-fx-background-radius:6;");
        b.setOnMouseEntered(e->b.setOpacity(0.82)); b.setOnMouseExited(e->b.setOpacity(1.0));}
    private void styleCombo(ComboBox<String> cb) {
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setStyle("-fx-background-color:#1e3a5f;-fx-text-fill:#e2e8f0;-fx-font-size:12;-fx-background-radius:4;");}
    private void styleSlider(Slider s) {
        s.setShowTickMarks(true); s.setMajorTickUnit(1); s.setStyle("-fx-control-inner-background:#1e3a5f;");}
    private String toggleStyle(boolean sel) {
        return sel ? "-fx-background-color:#3b82f6;-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:11;-fx-background-radius:5;-fx-cursor:hand;"
                   : "-fx-background-color:#1e3a5f;-fx-text-fill:#94a3b8;-fx-font-size:11;-fx-background-radius:5;-fx-cursor:hand;";}
    private void styleToggle(ToggleButton tb, ToggleGroup tg) { tb.setStyle(toggleStyle(tb.isSelected())); }
    private String modeName(ScenarioMode m) {
        return switch(m){case FOUR_WAY->"Ngã 4";case THREE_WAY->"Ngã 3";case FIVE_WAY->"Ngã 5";case GRID->"Mạng lưới";};}
    private String scenarioIcon(ScenarioMode m) {
        return switch(m){case FOUR_WAY->"➕";case THREE_WAY->"⊤";case FIVE_WAY->"✳";case GRID->"⊞";};}

    // ══════════════════════════════════════════════════════════════
    //  Simple TrafficLight impl
    // ══════════════════════════════════════════════════════════════
    static class SimpleTrafficLight extends TrafficLight {
        private final LightTiming t;
        private boolean autoSwitch = true;
        SimpleTrafficLight(String id, LightColor start, LightTiming t) {
            this.id=id; this.t=t; this.currentColor=start;
            this.remainingTime = switch(start){case GREEN->t.getGreenDuration();case YELLOW->t.getYellowDuration();case RED->t.getRedDuration();};}
        void forceRed() { currentColor=LightColor.RED; remainingTime=t.getRedDuration(); }
        void setAutoSwitch(boolean autoSwitch) { this.autoSwitch = autoSwitch; }
        double getRemainingTime() { return remainingTime; }
        @Override public void update(double deltaTime) {
            if (autoSwitch) super.update(deltaTime);
        }
        @Override protected void switchToNextColor() {
            currentColor = switch(currentColor) {
                case GREEN  -> { remainingTime=t.getYellowDuration(); yield LightColor.YELLOW; }
                case YELLOW -> { remainingTime=t.getRedDuration();    yield LightColor.RED;    }
                case RED    -> { remainingTime=t.getGreenDuration();  yield LightColor.GREEN;  }
            };}
        @Override public boolean shouldShowCountdown() { return true; }
    }

    private static final class LightClickTarget {
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final SimpleTrafficLight light;

        private LightClickTarget(double x, double y, double width, double height, SimpleTrafficLight light) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.light = light;
        }

        private boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    private static final class CollisionEffect {
        private final Vector2D position;
        private final double initialSeconds;
        private double remainingSeconds;

        private CollisionEffect(Vector2D position, double initialSeconds) {
            this.position = position;
            this.initialSeconds = initialSeconds;
            this.remainingSeconds = initialSeconds;
        }
    }

    public static void main(String[] args) { launch(args); }
}
