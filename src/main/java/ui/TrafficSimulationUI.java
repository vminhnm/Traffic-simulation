package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import core.driver.AggressiveDriver;
import core.driver.EmergencyDriver;
import core.driver.NormalDriver;
import core.road.VehiclePath;
import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationEngine;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.trafficlight.LightTiming;
import core.trafficlight.TrafficLight;
import core.vehicle.Vehicle;
import core.vehicle.VehicleFactory;
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
import util.Vector2D;

public class TrafficSimulationUI extends Application {

    // ── Canvas size ────────────────────────────────────────────────
    private static final int CANVAS_W = 820;
    private static final int CANVAS_H = 620;

    // ── Road geometry constants ────────────────────────────────────
    private static final double ROAD_HALF = 50.0;
    private static final double LANE_W    = 22.0;

    // ── Scenario modes ─────────────────────────────────────────────
    private enum ScenarioMode { FOUR_WAY, THREE_WAY, FIVE_WAY, GRID }
    private ScenarioMode currentMode = ScenarioMode.FOUR_WAY;

    // ── Simulation core ────────────────────────────────────────────
    private SimulationWorld  world;
    private SimulationEngine engine;
    private final TrafficRuleEvaluator rules = new TrafficRuleEvaluator();
    private AnimationTimer   gameLoop;
    private long             lastNano = 0;

    // ── Traffic lights ─────────────────────────────────────────────
    private SimpleTrafficLight lightNS, lightEW, lightNE, lightNW, lightSE;

    // ── UI refs ────────────────────────────────────────────────────
    private Canvas    canvas;
    private Label     lblTime, lblFPS;
    private Label     statSpawned, statFinished, statCrashed;
    private Button    btnStartPause;
    private TextArea  logArea;
    private VBox      lightStatusBox;

    // ── Stats ──────────────────────────────────────────────────────
    private double          simTime      = 0;
    private double          simSpeedMul  = 1.0;
    private final AtomicInteger totalSpawned  = new AtomicInteger(0);
    private final AtomicInteger totalFinished = new AtomicInteger(0);
    private final AtomicInteger totalCrashed  = new AtomicInteger(0);

    // ── Spawn ──────────────────────────────────────────────────────
    private double   spawnTimer    = 0;
    private double   spawnInterval = 3.5;
    private int      spawnRR       = 0;
    private int      dirRR         = 0;
    private static final String[] AUTO_TYPES =
        {"car","car","car","car","motorbike","motorbike","bus","truck","bicycle","car","car","car"};

    // ── FPS ────────────────────────────────────────────────────────
    private long   frameCount = 0;
    private double fpsTimer   = 0;
    private double currentFPS = 0;

    // ── Collision cooldown: prevent instant re-crash after spawn ───
    private final java.util.Map<String,Double> collisionCooldown = new java.util.HashMap<>();
    private static final double COLLISION_COOLDOWN = 2.0; // seconds before a vehicle can collide

    // ── Debug visualization ────────────────────────────────────────
    private boolean showHitboxes = false;

    @Override
    public void start(Stage stage) {
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
        log("✅ Mô phỏng khởi động — chế độ: " + modeName(currentMode));
    }

    // ══════════════════════════════════════════════════════════════
    //  World init
    // ══════════════════════════════════════════════════════════════
    private void initWorld() {
        world  = new SimulationWorld();
        engine = new SimulationEngine(world);
        collisionCooldown.clear();

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
    }
    private void seedFiveWay() {
        for (int i = 0; i < 3; i++) spawnAt("car", makeFiveWayPath(i), 0);
        spawnAt("ambulance", makeFiveWayPath(3), 0);
    }
    private void seedGrid() {
        spawnAt("car",  makeGridPath(0), 0);
        spawnAt("bus",  makeGridPath(1), 0);
        spawnAt("car",  makeGridPath(2), 0);
        spawnAt("truck",makeGridPath(3), 0);
        spawnAt("ambulance", makeGridPath(4), 0);
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
        panel.getChildren().addAll(
            statRow("🚗 Tổng xe đã tạo:", statSpawned),
            statRow("✅ Qua giao lộ:", statFinished),
            statRow("💥 Va chạm:", statCrashed)
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

        Button btnToggleHitbox = new Button("📦 Ẩn Hitbox");
        styleBtn(btnToggleHitbox, "#8b5cf6");
        btnToggleHitbox.setMaxWidth(Double.MAX_VALUE);
        btnToggleHitbox.setOnAction(e -> {
            showHitboxes = !showHitboxes;
            btnToggleHitbox.setText(showHitboxes ? "📦 Hiện Hitbox" : "📦 Ẩn Hitbox");
            log(showHitboxes ? "📦 Hiện hitbox" : "📦 Ẩn hitbox");
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

        panel.getChildren().addAll(btnStartPause, btnReset, btnSwitchLight, btnToggleHitbox,
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
            case THREE_WAY -> cb.getItems().addAll("⬇ Bắc→Nam","⬆ Nam→Bắc","➡ Tây→Nam");
            case FIVE_WAY -> cb.getItems().addAll("⬇ Bắc→Nam","⬆ Nam→Bắc","⬅ Đông→Tây","➡ Tây→Đông","↗ Đông-Bắc");
            case GRID -> cb.getItems().addAll("Ô lưới 1","Ô lưới 2","Ô lưới 3","Ô lưới 4","Ô lưới 5");
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
                    // advance cooldown timers
                    collisionCooldown.replaceAll((k,v) -> v - delta);
                    collisionCooldown.entrySet().removeIf(e -> e.getValue() <= 0);
                    checkCollisionsManually(); // our safer collision check
                    cleanupVehicles();
                }
                render();
                updateUI();
            }
        };
        gameLoop.start();
    }

    // ══════════════════════════════════════════════════════════════
    //  Collision detection (replace the one in Vehicle/RULES)
    //  We do it here to respect cooldown so spawning vehicles don't
    //  immediately crash into each other.
    // ══════════════════════════════════════════════════════════════
    private void checkCollisionsManually() {
        List<Vehicle> vehicles = new ArrayList<>(world.getVehicles());
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle a = vehicles.get(i);
            if (a.isCrashed() || a.isFinished()) continue;
            // skip if in cooldown
            if (collisionCooldown.containsKey(a.getId())) continue;

            for (int j = i+1; j < vehicles.size(); j++) {
                Vehicle b = vehicles.get(j);
                if (b.isCrashed() || b.isFinished()) continue;
                if (collisionCooldown.containsKey(b.getId())) continue;

                if (rules.isColliding(a, b)) {
                    // Ambulance/Firetruck should NOT be destroyed by normal traffic
                    // They push through — only flag the normal vehicle
                    if (a.isPriorityVehicle() && !b.isPriorityVehicle()) {
                        b.setCrashed();
                        log("💥 Va chạm: " + b.getId() + " ← bị xe ưu tiên " + a.getId() + " đẩy");
                    } else if (b.isPriorityVehicle() && !a.isPriorityVehicle()) {
                        a.setCrashed();
                        log("💥 Va chạm: " + a.getId() + " ← bị xe ưu tiên " + b.getId() + " đẩy");
                    } else if (!a.isPriorityVehicle() && !b.isPriorityVehicle()) {
                        a.setCrashed(); b.setCrashed();
                        log("💥 Va chạm: " + a.getId() + " ↔ " + b.getId());
                    }
                    // two priority vehicles: neither crashes (they yield to each other)
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════
    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
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
        drawLight(g, cx+ROAD_HALF+4,  cy-ROAD_HALF-36, lightNS.getColor()); // N
        drawLight(g, cx-ROAD_HALF-20, cy+ROAD_HALF+4,  lightNS.getColor()); // S
        drawLight(g, cx+ROAD_HALF+4,  cy+6,            lightEW.getColor()); // E
        drawLight(g, cx-ROAD_HALF-20, cy-ROAD_HALF-36, lightEW.getColor()); // W
    }

    // ── Three-way (T-junction): roads from N, S, W — no East arm ──
    private void renderThreeWay(GraphicsContext g) {
        double cx = CANVAS_W/2.0, cy = CANVAS_H/2.0;
        drawGrass(g);
        // vertical road (N→S, but only N half + junction)
        fillRoad(g, cx-ROAD_HALF, 0, ROAD_HALF*2, cy+ROAD_HALF);
        // horizontal road West arm only
        fillRoad(g, 0, cy-ROAD_HALF, cx+ROAD_HALF, ROAD_HALF*2);
        g.setFill(Color.web("#4b5563")); g.fillRect(cx-ROAD_HALF,cy-ROAD_HALF,ROAD_HALF*2,ROAD_HALF*2);
        drawLaneCenter(g, cx, 0, cx, cy-ROAD_HALF);
        drawLaneCenter(g, 0, cy, cx-ROAD_HALF, cy);
        // Right-side cap (east dead end)
        g.setFill(Color.web("#374151"));
        g.fillRect(cx+ROAD_HALF, cy-ROAD_HALF, 12, ROAD_HALF*2);
        // lights
        drawLight(g, cx+ROAD_HALF+4,  cy-ROAD_HALF-36, lightNS.getColor());
        drawLight(g, cx-ROAD_HALF-20, cy-ROAD_HALF-36, lightEW.getColor());
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
        // 5th arm: diagonal NE
        double armLen = 200;
        g.save();
        g.translate(cx+ROAD_HALF, cy-ROAD_HALF);
        g.rotate(-45);
        fillRoad(g, 0, -ROAD_HALF, armLen, ROAD_HALF*2);
        g.restore();
        g.setFill(Color.web("#4b5563"));
        g.fillOval(cx-ROAD_HALF*1.3, cy-ROAD_HALF*1.3, ROAD_HALF*2.6, ROAD_HALF*2.6);
        drawLaneCenter(g, cx, 0, cx, cy-ROAD_HALF);
        drawLaneCenter(g, cx, cy+ROAD_HALF, cx, CANVAS_H);
        drawLaneCenter(g, 0, cy, cx-ROAD_HALF, cy);
        drawLaneCenter(g, cx+ROAD_HALF, cy, CANVAS_W, cy);
        drawLight(g, cx+ROAD_HALF+4,  cy-ROAD_HALF-36, lightNS.getColor());
        drawLight(g, cx-ROAD_HALF-20, cy+ROAD_HALF+4,  lightNS.getColor());
        drawLight(g, cx+ROAD_HALF+4,  cy+6,            lightEW.getColor());
        drawLight(g, cx-ROAD_HALF-20, cy-ROAD_HALF-36, lightEW.getColor());
        drawLight(g, cx+ROAD_HALF+50, cy-ROAD_HALF-80, lightNE.getColor());
        g.setFill(Color.web("#e2e8f0",0.4)); g.setFont(Font.font("Segoe UI",13));
        g.fillText("5-WAY INTERSECTION", cx-65, CANVAS_H-18);
    }

    // ── Grid network (2×2 blocks = 3×3 intersections) ─────────────
    private void renderGrid(GraphicsContext g) {
        drawGrassGrid(g);
        double gapX = CANVAS_W / 3.0;
        double gapY = CANVAS_H / 3.0;
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
                drawLight(g, x+ROAD_HALF*0.7+2, y-ROAD_HALF*0.7-28, lt.getColor());
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

    private void drawLight(GraphicsContext g, double x, double y, LightColor color) {
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

    private void drawVehicle(GraphicsContext g, core.vehicle.RenderableState s) {
        double x=s.getPosition().x, y=s.getPosition().y;
        g.save();
        g.translate(x,y);
        g.rotate(Math.toDegrees(s.getRotation()));

        if (s.isCrashed()) {
            g.setFill(Color.web("#374151")); g.fillRoundRect(-s.getLength()/2,-s.getWidth()/2,s.getLength(),s.getWidth(),4,4);
            g.setFill(Color.web("#ef4444")); g.setFont(Font.font(10)); g.fillText("✕",-4,4);
            g.restore(); return;
        }

        // Siren glow
        if (s.isPriority() && s.isSirenFlash()) {
            g.setFill(Color.web("#fef3c7",0.25)); g.fillOval(-s.getLength(),-s.getLength(),s.getLength()*2,s.getLength()*2);
        }

        java.awt.Color ac = s.getBodyColor();
        g.setFill(Color.rgb(ac.getRed(),ac.getGreen(),ac.getBlue()));
        g.fillRoundRect(-s.getLength()/2,-s.getWidth()/2,s.getLength(),s.getWidth(),5,5);

        java.awt.Color rc = s.getRoofColor();
        g.setFill(Color.rgb(rc.getRed(),rc.getGreen(),rc.getBlue()));
        g.fillRoundRect(-s.getLength()*0.26,-s.getWidth()*0.36,s.getLength()*0.52,s.getWidth()*0.72,3,3);

        // Headlights
        g.setFill(Color.web("#fef9c3",0.9));
        g.fillOval(s.getLength()/2-5,-s.getWidth()/2,5,4);
        g.fillOval(s.getLength()/2-5,s.getWidth()/2-4,5,4);

        if (s.isYielding()) { g.setStroke(Color.ORANGE); g.setLineWidth(2);
            g.strokeRoundRect(-s.getLength()/2-2,-s.getWidth()/2-2,s.getLength()+4,s.getWidth()+4,5,5); }
        if (s.isStopped() && !s.isYielding()) {
            g.setFill(Color.web("#ef4444",0.8)); g.fillRect(-s.getLength()/2-5,-2,4,4); }
        if (s.isPriority()) {
            g.setFill(s.isSirenFlash() ? Color.web("#ef4444") : Color.web("#3b82f6"));
            g.fillRect(-s.getLength()/2+2,-s.getWidth()/2-5,7,4); }

        g.setFill(Color.WHITE); g.setFont(Font.font("Segoe UI",FontWeight.BOLD,7));
        g.fillText(s.getBasicLabel(),-s.getLength()*0.22,3);
        g.restore();
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

    // ══════════════════════════════════════════════════════════════
    //  UI updates
    // ══════════════════════════════════════════════════════════════
    private void updateUI() {
        lblFPS.setText(String.format("FPS: %.0f", currentFPS));
        lblTime.setText(String.format("⏱ %.1fs", simTime));
        statSpawned.setText(String.valueOf(totalSpawned.get()));
        statFinished.setText(String.valueOf(totalFinished.get()));
        statCrashed.setText(String.valueOf(totalCrashed.get()));
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
        return switch (currentMode) { case THREE_WAY -> 3; case FIVE_WAY -> 5; default -> 4; };
    }

    private void spawnAt(String type, VehiclePath path, double extraCooldown) {
        doSpawn(type, path, null);
    }

    private void doSpawn(String type, VehiclePath path, core.driver.DriverBehavior beh) {
        try {
            Vehicle v = beh == null ? VehicleFactory.create(type, path)
                                    : VehicleFactory.create(type, path, beh);
            world.addVehicle(v);
            // Give every newly spawned vehicle a cooldown so it can't immediately crash
            collisionCooldown.put(v.getId(), COLLISION_COOLDOWN);
            totalSpawned.incrementAndGet();
            log("🚗 " + type + " [" + v.getId() + "] " + path.getEntryArm() + "→" + path.getExitArm());
        } catch (Exception ex) {
            log("⚠ Lỗi: " + ex.getMessage());
        }
    }

    private void cleanupVehicles() {
        List<Vehicle> rm = new ArrayList<>();
        for (Vehicle v : world.getVehicles()) {
            if (v.isFinished()) { rm.add(v); totalFinished.incrementAndGet(); }
            else if (v.isCrashed()) { rm.add(v); totalCrashed.incrementAndGet(); }
        }
        rm.forEach(world::removeVehicle);
    }

    // ══════════════════════════════════════════════════════════════
    //  Path factories
    // ══════════════════════════════════════════════════════════════
    private double cx() { return CANVAS_W/2.0; }
    private double cy() { return CANVAS_H/2.0; }

    // ── 4-way ─────────────────────────────────────────────────────
    private VehiclePath makeNorthSouthPath(int lane) {
        double x = cx() + LANE_W + lane*LANE_W;
        return new VehiclePath("ns"+lane, List.of(
            new Vector2D(x,-20), new Vector2D(x, cy()-ROAD_HALF-10),
            new Vector2D(x, cy()), new Vector2D(x, CANVAS_H+20)), 1,"light-NS","N","S");
    }
    private VehiclePath makeSouthNorthPath(int lane) {
        double x = cx() - LANE_W - lane*LANE_W;
        return new VehiclePath("sn"+lane, List.of(
            new Vector2D(x,CANVAS_H+20), new Vector2D(x, cy()+ROAD_HALF+10),
            new Vector2D(x, cy()), new Vector2D(x,-20)), 1,"light-NS","S","N");
    }
    private VehiclePath makeEastWestPath(int lane) {
        double y = cy() + LANE_W + lane*LANE_W;
        return new VehiclePath("ew"+lane, List.of(
            new Vector2D(CANVAS_W+20,y), new Vector2D(cx()+ROAD_HALF+10,y),
            new Vector2D(cx(),y), new Vector2D(-20,y)), 1,"light-EW","E","W");
    }
    private VehiclePath makeWestEastPath(int lane) {
        double y = cy() - LANE_W - lane*LANE_W;
        return new VehiclePath("we"+lane, List.of(
            new Vector2D(-20,y), new Vector2D(cx()-ROAD_HALF-10,y),
            new Vector2D(cx(),y), new Vector2D(CANVAS_W+20,y)), 1,"light-EW","W","E");
    }

    // ── 3-way ─────────────────────────────────────────────────────
    private VehiclePath makeThreeWayPath(int idx) {
        double cx=cx(), cy=cy(), lw=LANE_W/2.0;
        return switch(idx) {
            case 0 -> new VehiclePath("3w-ns", List.of(
                new Vector2D(cx+lw,-20), new Vector2D(cx+lw,cy-ROAD_HALF-10),
                new Vector2D(cx+lw,cy+ROAD_HALF-10), new Vector2D(cx+lw,cy+ROAD_HALF+40)), 1,"light-NS","N","S");
            default -> new VehiclePath("3w-wn", List.of(
                new Vector2D(-20,cy-lw), new Vector2D(cx-ROAD_HALF-10,cy-lw),
                new Vector2D(cx,cy-lw), new Vector2D(cx,cy-ROAD_HALF-20),
                new Vector2D(cx,-20)), 1,"light-EW","W","N");
        };
    }

    // ── 5-way ─────────────────────────────────────────────────────
    private VehiclePath makeFiveWayPath(int idx) {
        double cx=cx(), cy=cy(), lw=LANE_W/2.0;
        return switch(idx%5) {
            case 0 -> makeNorthSouthPath(0);
            case 1 -> makeSouthNorthPath(0);
            case 2 -> makeEastWestPath(0);
            case 3 -> makeWestEastPath(0);
            default -> new VehiclePath("ne-diag", List.of(
                new Vector2D(CANVAS_W+20,-20), new Vector2D(cx+ROAD_HALF+60,cy-ROAD_HALF-60),
                new Vector2D(cx+ROAD_HALF,cy-ROAD_HALF),
                new Vector2D(cx,cy), new Vector2D(-20,cy+lw)), 1,"light-NE","NE","SW");
        };
    }

    // ── Grid paths ────────────────────────────────────────────────
    private VehiclePath makeGridPath(int idx) {
        double gx = CANVAS_W/3.0, gy = CANVAS_H/3.0;
        return switch(idx%5) {
            // vertical downward through col 1
            case 0 -> new VehiclePath("grid-v1", List.of(
                new Vector2D(gx,-20), new Vector2D(gx,gy-16),
                new Vector2D(gx,gy+16), new Vector2D(gx,gy*2-16),
                new Vector2D(gx,gy*2+16), new Vector2D(gx,CANVAS_H+20)), 1,"light-NS","N1","S1");
            // vertical downward through col 2
            case 1 -> new VehiclePath("grid-v2", List.of(
                new Vector2D(gx*2,-20), new Vector2D(gx*2,gy*2-16),
                new Vector2D(gx*2,gy*2+16), new Vector2D(gx*2,CANVAS_H+20)), 1,"light-EW","N2","S2");
            // vertical downward col 3
            case 2 -> new VehiclePath("grid-v3", List.of(
                new Vector2D(gx*3,-20), new Vector2D(gx*3,gy-16),
                new Vector2D(gx*3,gy+16), new Vector2D(gx*3,CANVAS_H+20)), 1,"light-NS","N3","S3");
            // horizontal right row 1
            case 3 -> new VehiclePath("grid-h1", List.of(
                new Vector2D(-20,gy), new Vector2D(gx-16,gy),
                new Vector2D(gx+16,gy), new Vector2D(gx*2-16,gy),
                new Vector2D(gx*2+16,gy), new Vector2D(CANVAS_W+20,gy)), 1,"light-EW","W1","E1");
            // horizontal right row 2
            default -> new VehiclePath("grid-h2", List.of(
                new Vector2D(-20,gy*2), new Vector2D(gx-16,gy*2),
                new Vector2D(gx*2+16,gy*2), new Vector2D(CANVAS_W+20,gy*2)), 1,"light-NS","W2","E2");
        };
    }

    private VehiclePath getPathByModeAndDir(int idx) {
        return switch (currentMode) {
            case FOUR_WAY -> switch(idx%4) {
                case 0->makeNorthSouthPath(0); case 1->makeSouthNorthPath(0);
                case 2->makeEastWestPath(0); default->makeWestEastPath(0);};
            case THREE_WAY -> makeThreeWayPath(idx%3);
            case FIVE_WAY  -> makeFiveWayPath(idx%5);
            case GRID      -> makeGridPath(idx%5);
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  Controls
    // ══════════════════════════════════════════════════════════════
    private void togglePause() {
        if (engine.isRunning()) { engine.pause(); btnStartPause.setText("▶ Tiếp tục"); log("⏸ Tạm dừng."); }
        else { engine.resume(); lastNano=0; btnStartPause.setText("⏸ Tạm dừng"); log("▶ Tiếp tục."); }
    }

    private void resetSimulation() {
        if (engine!=null) engine.pause();
        simTime=0; spawnTimer=0; spawnRR=0; dirRR=0;
        totalSpawned.set(0); totalFinished.set(0); totalCrashed.set(0);
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
        SimpleTrafficLight(String id, LightColor start, LightTiming t) {
            this.id=id; this.t=t; this.currentColor=start;
            this.remainingTime = switch(start){case GREEN->t.getGreenDuration();case YELLOW->t.getYellowDuration();case RED->t.getRedDuration();};}
        void forceRed() { currentColor=LightColor.RED; remainingTime=t.getRedDuration(); }
        @Override protected void switchToNextColor() {
            currentColor = switch(currentColor) {
                case GREEN  -> { remainingTime=t.getYellowDuration(); yield LightColor.YELLOW; }
                case YELLOW -> { remainingTime=t.getRedDuration();    yield LightColor.RED;    }
                case RED    -> { remainingTime=t.getGreenDuration();  yield LightColor.GREEN;  }
            };}
        @Override public boolean shouldShowCountdown() { return true; }
    }

    public static void main(String[] args) { launch(args); }
}
