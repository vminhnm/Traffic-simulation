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
import javafx.scene.transform.Scale;
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
    private Stage     primaryStage;
    private Canvas    canvas;
    private Scale     canvasScale;
    private Label     lblTime, lblFPS;
    private Label     statSpawned, statFinished, statCrashed, statCollisions, statThroughput, statAvgTravel;
    private Button    btnStartPause;
    private TextArea  logArea;
    private VBox      lightStatusBox;
    private ComboBox<String> cbDir;

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
        this.primaryStage = stage;
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
        stage.setResizable(true);
        stage.setMinWidth(CANVAS_W + 310);
        stage.setMinHeight(CANVAS_H + 56);
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
        //for (int i = 0; i < 4; i++) spawnAt("car", makeFiveWayPath(i), 0);
        spawnAt("car", makeFiveWayPath(0), 0);
        spawnAt("car", makeFiveWayPath(2), 0);
        spawnAt("bus", makeFiveWayPath(3), 0);
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

        Button btnFs = new Button("⛶ Toàn màn hình");
        btnFs.setStyle("-fx-background-color:#1e3a5f;-fx-text-fill:#e2e8f0;-fx-font-size:12;" +
                       "-fx-cursor:hand;-fx-background-radius:6;-fx-padding:5 12;");
        btnFs.setOnMouseEntered(e -> btnFs.setOpacity(0.8));
        btnFs.setOnMouseExited (e -> btnFs.setOpacity(1.0));
        btnFs.setOnAction(e -> {
            boolean fs = !primaryStage.isFullScreen();
            primaryStage.setFullScreen(fs);
            btnFs.setText(fs ? "✕ Thoát toàn màn hình" : "⛶ Toàn màn hình");
        });

        hb.getChildren().addAll(title, sub, sp, lblFPS, btnFs);
        return hb;
    }

    private StackPane buildCanvasArea() {
        canvas = new Canvas(CANVAS_W, CANVAS_H);
        canvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY()));

        canvasScale = new Scale(1.0, 1.0, 0, 0);
        canvas.getTransforms().add(canvasScale);

        // TOP_LEFT alignment so translate is the sole positioning mechanism
        StackPane sp = new StackPane(canvas);
        sp.setAlignment(Pos.TOP_LEFT);
        sp.setStyle("-fx-background-color: #0d1b2a;");

        javafx.beans.value.ChangeListener<Number> sizeListener = (obs, oldVal, newVal) -> {
            double availW = sp.getWidth();
            double availH = sp.getHeight();
            if (availW <= 0 || availH <= 0) return;
            double s = Math.min(availW / CANVAS_W, availH / CANVAS_H);
            canvasScale.setX(s);
            canvasScale.setY(s);
            // Centre: (available - scaled_size) / 2
            canvas.setTranslateX((availW - CANVAS_W * s) / 2.0);
            canvas.setTranslateY((availH - CANVAS_H * s) / 2.0);
        };
        sp.widthProperty() .addListener(sizeListener);
        sp.heightProperty().addListener(sizeListener);

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
                if (cbDir != null) rebuildDirCombo(cbDir);
                SoundManager.stopAll();
                resetSimulation();
                SoundManager.loop(SoundType.TRAFFIC_AMBIENCE);
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
        cbDir = new ComboBox<>();
        rebuildDirCombo(cbDir);

        ComboBox<String> cbDriver = new ComboBox<>();
        cbDriver.getItems().addAll("👍 Normal","😤 Aggressive","🚨 Emergency");
        cbDriver.setValue("👍 Normal");
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
            case FOUR_WAY -> cb.getItems().addAll(
                "⬇ Bắc→Nam",
                "⬆ Nam→Bắc",
                "⬅ Đông→Tây",
                "➡ Tây→Đông",
                "↳ Bắc→Đông (rẽ phải)",
                "↱ Nam→Tây (rẽ phải)",
                "↱ Đông→Nam (rẽ phải)",
                "↳ Tây→Bắc (rẽ phải)");
            case THREE_WAY -> cb.getItems().addAll(
                "⬇ Bắc→Nam (thẳng)",
                "⬆ Nam→Bắc (thẳng)",
                "↱ Tây→Bắc (rẽ phải)",
                "↳ Tây→Nam (rẽ trái)");
            case FIVE_WAY -> cb.getItems().addAll(
                "⬇ Bắc→Nam",
                "⬆ Nam→Bắc",
                "⬅ Đông→Tây",
                "➡ Tây→Đông",
                "↙ Đông-Bắc→Tây (qua ngã 5)",
                "↳ Bắc→Đông-Bắc (rẽ phải)",
                "↱ Đông→Nam (rẽ phải)",
                "↳ Tây→Bắc (rẽ phải)");
            case GRID -> cb.getItems().addAll(
                "⬇ Cột trái",
                "⬆ Cột giữa",
                "⬇ Cột phải",
                "➡ Hàng trên",
                "⬅ Hàng giữa",
                "➡ Hàng dưới");
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
        // Coloured edge highlights — match the side where each light stands
        // lightNS đứng ở cạnh PHẢI đường dọc → highlight cạnh phải
        g.setFill(Color.web("#3b82f6", 0.55)); // NS (blue) — right edge of N/S road
        g.fillRect(cx + ROAD_HALF - 4, 0, 4, cy - ROAD_HALF);        // N-road right edge
        g.fillRect(cx + ROAD_HALF - 4, cy + ROAD_HALF, 4, CANVAS_H); // S-road right edge
        // lightEW đứng ở cạnh DƯỚI đường ngang → highlight cạnh dưới
        g.setFill(Color.web("#f59e0b", 0.55)); // EW (amber) — bottom edge of W/E road
        g.fillRect(0, cy + ROAD_HALF - 4, cx - ROAD_HALF, 4);        // W-road bottom edge
        g.fillRect(cx + ROAD_HALF, cy + ROAD_HALF - 4, CANVAS_W, 4); // E-road bottom edge
        drawLight(g, cx+ROAD_HALF+10, cy-ROAD_HALF-52, lightNS, "N ↓"); // N
        drawLight(g, cx-ROAD_HALF-22, cy+ROAD_HALF+6,  lightNS, "S ↑"); // S
        drawLight(g, cx+ROAD_HALF+10, cy+ROAD_HALF+6,  lightEW, "E ←"); // E
        drawLight(g, cx-ROAD_HALF-22, cy-ROAD_HALF-52, lightEW, "W →"); // W
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
        // Road highlights — match side where each light stands
        Color nsColor = lightGroupColor(lightNS);
        Color ewColor = lightGroupColor(lightEW);
        // lightNS đứng bên PHẢI đường dọc → highlight cạnh phải
        g.setFill(nsColor.deriveColor(0,1,1,0.55));
        g.fillRect(cx + ROAD_HALF - 4, 0, 4, cy - ROAD_HALF);        // N-road right edge
        g.fillRect(cx + ROAD_HALF - 4, cy + ROAD_HALF, 4, CANVAS_H); // S-road right edge
        // lightEW đứng bên TRÁI đường ngang → highlight cạnh dưới (đèn ở góc trên-trái)
        g.setFill(ewColor.deriveColor(0,1,1,0.55));
        g.fillRect(0, cy + ROAD_HALF - 4, cx - ROAD_HALF, 4);        // W-road bottom edge
        // lights
        drawLight(g, cx+ROAD_HALF+10,  cy-ROAD_HALF-52, lightNS, "N ↓");
        drawLight(g, cx-ROAD_HALF-22, cy+ROAD_HALF+6, lightNS, "S ↑");
        drawLight(g, cx-ROAD_HALF-22, cy-ROAD_HALF-52, lightEW, "W →");
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
        
        // Road highlights — match side where each light stands
        Color nsC = lightGroupColor(lightNS);
        Color ewC = lightGroupColor(lightEW);
        Color neC = lightGroupColor(lightNE);
        // lightNS: bên PHẢI đường dọc
        g.setFill(nsC.deriveColor(0,1,1,0.55));
        g.fillRect(cx + ROAD_HALF - 4, 0, 4, cy - ROAD_HALF);
        g.fillRect(cx + ROAD_HALF - 4, cy + ROAD_HALF, 4, CANVAS_H);
        // lightEW: cạnh DƯỚI đường ngang
        g.setFill(ewC.deriveColor(0,1,1,0.55));
        g.fillRect(0, cy + ROAD_HALF - 4, cx - ROAD_HALF, 4);
        g.fillRect(cx + ROAD_HALF, cy + ROAD_HALF - 4, CANVAS_W, 4);
        // lightNE: highlight cạnh TRÊN arm (local y = -ROAD_HALF, vẽ strip dày 4px vào trong)
        // Đèn NE ở góc trên-phải → highlight cạnh phải của arm diagonal
        double neHighlightLen = armLen * 0.7;
        g.setFill(neC.deriveColor(0,1,1,0.55));
        g.save();
        g.translate(cx, cy);
        g.rotate(-45);
        g.fillRect(ROAD_HALF+65, -ROAD_HALF, neHighlightLen, 4); // cạnh phải arm (local x > ROAD_HALF, y=-ROAD_HALF)
        g.restore();

        drawLight(g, cx+ROAD_HALF+10, cy-ROAD_HALF-52, lightNS, "N ↓");
        drawLight(g, cx-ROAD_HALF-22, cy+ROAD_HALF+6,  lightNS, "S ↑");
        drawLight(g, cx+ROAD_HALF+10, cy+ROAD_HALF+6,  lightEW, "E ←");
        drawLight(g, cx-ROAD_HALF-22, cy-ROAD_HALF-52, lightEW, "W →");
        drawLight(g, cx+ROAD_HALF+10, cy-ROAD_HALF-149, lightNE, "NE ↙");
    
        g.setFill(Color.web("#e2e8f0",0.4)); g.setFont(Font.font("Segoe UI",13));
        g.fillText("5-WAY INTERSECTION", cx-65, CANVAS_H-18);
    }

    // ── Grid network (2×2 blocks = 3×3 intersections) ─────────────
    private void renderGrid(GraphicsContext g) {
        drawGrassGrid(g);
        double gapX = CANVAS_W / 4.0;
        double gapY = CANVAS_H / 4.0;
        double rh = ROAD_HALF * 0.7;
        // 3 vertical roads
        for (int col = 0; col < 3; col++) {
            double x = gapX * (col+1);
            fillRoad(g, x-rh, 0, rh*2, CANVAS_H);
        }
        // 3 horizontal roads
        for (int row = 0; row < 3; row++) {
            double y = gapY * (row+1);
            fillRoad(g, 0, y-rh, CANVAS_W, rh*2);
        }
        // intersection boxes — 2 lights per intersection
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                double x = gapX*(col+1), y = gapY*(row+1);
                g.setFill(Color.web("#4b5563"));
                g.fillRect(x-rh, y-rh, rh*2, rh*2);
                // NS light: top-right corner of intersection
                SimpleTrafficLight nsLight = ((col+row)%2==0) ? lightNS : lightEW;
                // EW light: bottom-left corner of intersection (opposite phase)
                SimpleTrafficLight ewLight = ((col+row)%2==0) ? lightEW : lightNS;
                // Draw a thin coloured stripe on the road edges to match each light
                Color nsAccent = lightGroupColor(nsLight);
                Color ewAccent = lightGroupColor(ewLight);
                // NS stripe: RIGHT edge of vertical road — nơi đèn NS đứng (x + rh)
                g.setFill(nsAccent.deriveColor(0,1,1,0.55));
                g.fillRect(x + rh - 4, y - rh - 18, 4, rh * 2 + 18);
                // EW stripe: BOTTOM edge of horizontal road — nơi đèn EW đứng (y + rh)
                g.setFill(ewAccent.deriveColor(0,1,1,0.55));
                g.fillRect(x - rh - 18, y + rh - 4, rh * 2 + 18, 4);
                drawLight(g, x + rh + 2,   y - rh - 32, nsLight, "↕"); // NS (vertical traffic)
                drawLight(g, x - rh - 18,  y + rh + 4,  ewLight, "↔"); // EW (horizontal traffic)
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

    // ── Light group accent colours ──────────────────────────────────
    // NS = blue, EW = amber, NE = purple
    private Color lightGroupColor(SimpleTrafficLight light) {
        if (light == lightNS) return Color.web("#3b82f6");
        if (light == lightEW) return Color.web("#f59e0b");
        if (light == lightNE) return Color.web("#a855f7");
        if (light == lightNW) return Color.web("#10b981");
        if (light == lightSE) return Color.web("#ec4899");
        return Color.web("#94a3b8");
    }

    private String lightGroupLabel(SimpleTrafficLight light) {
        if (light == lightNS) return "NS";
        if (light == lightEW) return "EW";
        if (light == lightNE) return "NE";
        if (light == lightNW) return "NW";
        if (light == lightSE) return "SE";
        return "?";
    }

    /** Draw a traffic light with direction label and coloured pole. */
    private void drawLight(GraphicsContext g, double x, double y, SimpleTrafficLight light) {
        drawLight(g, x, y, light, null);
    }

    /** Draw a traffic light with an explicit directional label override (e.g. "N", "EW"). */
    private void drawLight(GraphicsContext g, double x, double y, SimpleTrafficLight light, String dirLabel) {
        lightClickTargets.add(new LightClickTarget(x, y, 15, 42, light));
        Color accent = lightGroupColor(light);

        // ── Pole (coloured accent) ────────────────────────────────
        g.setFill(accent.deriveColor(0, 1, 0.45, 1));
        g.fillRect(x + 5, y + 42, 5, 10); // pole stub below body

        // ── Light body with coloured border ───────────────────────
        // Outer glow ring
        g.setFill(accent.deriveColor(0, 1, 1, 0.18));
        g.fillRoundRect(x - 2, y - 2, 19, 46, 6, 6);

        // Dark body
        g.setFill(Color.web("#111827"));
        g.fillRoundRect(x, y, 15, 42, 4, 4);

        // Coloured left-edge stripe (group indicator)
        g.setFill(accent);
        g.fillRect(x, y, 3, 42);

        // Lights
        LightColor color = light.getColor();
        boolean r = color == LightColor.RED, yl = color == LightColor.YELLOW, gr = color == LightColor.GREEN;
        Glow glow = new Glow(0.9);
        g.setFill(r  ? Color.web("#ef4444") : Color.web("#7f1d1d"));
        if (r)  g.setEffect(glow); g.fillOval(x + 3, y + 3,  10, 10); g.setEffect(null);
        g.setFill(yl ? Color.web("#fbbf24") : Color.web("#78350f"));
        if (yl) g.setEffect(glow); g.fillOval(x + 3, y + 16, 10, 10); g.setEffect(null);
        g.setFill(gr ? Color.web("#34d399") : Color.web("#064e3b"));
        if (gr) g.setEffect(glow); g.fillOval(x + 3, y + 29, 10, 10); g.setEffect(null);

        // ── Direction label ───────────────────────────────────────
        String lbl = dirLabel != null ? dirLabel : lightGroupLabel(light);
        g.setFill(accent);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        // Centre the text under the body (body is 15px wide)
        double lblW = lbl.length() * 5.5;
        g.fillText(lbl, x + 7 - lblW / 2, y + 55);

        // ── Lane indicators (Lane 0 = Right/phải, Lane 1 = Left/trái) ──
        // Vietnamese convention: Lane 0 on bottom/right, Lane 1 on top/left
        // Lane 0 indicator (bottom) — teal/right lane color
        g.setFill(Color.web("#06b6d4", 0.8)); // Cyan for right lane
        g.fillRect(x - 1, y + 40, 3, 4);
        // Lane 1 indicator (top) — orange/left lane color
        g.setFill(Color.web("#f97316", 0.8)); // Orange for left lane
        g.fillRect(x + 13, y - 2, 3, 4);

        // ── Countdown ─────────────────────────────────────────────
        if (shouldDrawCountdown(light)) {
            g.setFill(Color.web("#f8fafc"));
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            g.fillText(String.format("%.0f", Math.ceil(light.getRemainingTime())), x - 2, y + 65);
        }
    }

    private boolean shouldDrawCountdown(SimpleTrafficLight light) {
        return switch (countdownMode) {
            case ALWAYS -> true;
            case LAST_10_SECONDS -> light.getRemainingTime() <= 10;
            case HIDDEN -> false;
        };
    }


    private void drawVehicle(GraphicsContext g, core.vehicle.RenderableState s) {
        double x = s.getPosition().x, y = s.getPosition().y;
        g.save();
        g.translate(x, y);
        // BASIC mode: rotate canvas to travel direction so the rectangle faces correctly.
        // GRAPHICS mode: no canvas rotation — the directional sprite already faces the right way.
        if (renderMode == RenderMode.BASIC) {
            g.rotate(Math.toDegrees(s.getRotation()));
        }
        double scale = currentMode == ScenarioMode.GRID ? 0.72 : 1.08;
        g.scale(scale, scale);

        if (s.isCrashed()) {
            var explosionStream = TrafficSimulationUI.class.getResourceAsStream("/assets/sprites/explosion.png");
            Image explosion = explosionStream == null ? null : new Image(explosionStream);
            if (explosion != null) {
                double sz = Math.max(s.getLength(), s.getWidth());
                g.drawImage(explosion, -sz/2, -sz/2, sz, sz);
            } else {
                g.setFill(Color.web("#374151"));
                g.fillRoundRect(-s.getLength()/2, -s.getWidth()/2, s.getLength(), s.getWidth(), 4, 4);
            }
            g.restore(); return;
        }

        // Siren glow
        if (s.isPriority() && s.isSirenFlash()) {
            g.setFill(Color.web("#fef3c7", 0.25));
            g.fillOval(-s.getLength(), -s.getLength(), s.getLength()*2, s.getLength()*2);
        }

        if (renderMode == RenderMode.BASIC) {
            // ── BASIC mode: use physical (hitbox) size for the rectangle ──
            double bL = s.getPhysicalLength();
            double bW = s.getPhysicalWidth();
            java.awt.Color ac = s.getBodyColor();
            g.setFill(Color.rgb(ac.getRed(), ac.getGreen(), ac.getBlue()));
            g.fillRoundRect(-bL/2, -bW/2, bL, bW, 5, 5);

            java.awt.Color rc = s.getRoofColor();
            g.setFill(Color.rgb(rc.getRed(), rc.getGreen(), rc.getBlue(), 0.85));
            g.fillRoundRect(-bL*0.26, -bW*0.30, bL*0.52, bW*0.60, 3, 3);

            g.setFill(Color.web("#fef9c3", 0.9));
            g.fillOval( bL/2 - 5, -bW/2,     5, 4);
            g.fillOval( bL/2 - 5,  bW/2 - 4, 5, 4);

            g.setFill(Color.WHITE);
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
            g.fillText(s.getBasicLabel(), -bL*0.22, 3);

        } else {
            // ── GRAPHICS mode: pick the correct directional sprite based on rotation ──
            Image sprite = SpriteLoader.get(getDirectionalSpriteKey(s.getSpriteKey(), s.getRotation()));
            if (sprite != null) {
                // Use the vehicle's render length, preserve sprite aspect ratio for height
                double drawW = s.getLength();
                double ratio = sprite.getHeight() / sprite.getWidth(); // e.g. 100/100 = 1.0
                double drawH = drawW * ratio;
                g.drawImage(sprite, -drawW/2, -drawH/2, drawW, drawH);
            } else {
                java.awt.Color ac = s.getBodyColor();
                g.setFill(Color.rgb(ac.getRed(), ac.getGreen(), ac.getBlue()));
                g.fillRoundRect(-s.getLength()/2, -s.getWidth()/2, s.getLength(), s.getWidth(), 5, 5);
            }
        }

        // ── Overlay indicators — use physical size for correct positioning ───
        double oL = s.getPhysicalLength();
        double oW = s.getPhysicalWidth();

        if (s.isYielding()) {
            g.setStroke(Color.ORANGE); g.setLineWidth(2);
            g.strokeRoundRect(-oL/2-2, -oW/2-2, oL+4, oW+4, 5, 5);
        }

        // Đèn hậu đỏ (phía sau xe)
        if (s.isStopped() && !s.isYielding()) {
            g.setFill(Color.web("#ef4444", 0.8));
            g.fillRect(-oL/2 - 2, -oW/2, 4, 4);
            g.fillRect(-oL/2 - 2,  oW/2 - 4, 4, 4);
        }

        // Đèn nháy xe ưu tiên
        if (s.isPriority()) {
            g.setFill(s.isSirenFlash() ? Color.web("#ef4444") : Color.web("#3b82f6"));
            g.fillRect(-oL/2+2, -oW/2-5, 7, 4);
        }

        g.restore();
    }

    /** Returns the _EAST variant of a sprite key (used as the 0° base for rotation). */
    private RenderAssetKey getEastSpriteKey(RenderAssetKey base) {
        String baseName = base.name().replaceAll(
            "_(EAST|NORTH|SOUTH|WEST|NORTHEAST|NORTHWEST|SOUTHEAST|SOUTHWEST|TOP)$", "");
        try {
            return RenderAssetKey.valueOf(baseName + "_EAST");
        } catch (IllegalArgumentException e) {
            return base;
        }
    }

    /**
     * Returns the directional sprite key that best matches the given rotation angle (radians).
     * Rotation 0 = EAST, PI/2 = SOUTH, PI = WEST, -PI/2 = NORTH (JavaFX screen coords: y increases downward).
     */
    private RenderAssetKey getDirectionalSpriteKey(RenderAssetKey base, double rotation) {
        String baseName = base.name().replaceAll(
            "_(EAST|NORTH|SOUTH|WEST|NORTHEAST|NORTHWEST|SOUTHEAST|SOUTHWEST|TOP)$", "");
        // Normalise angle to [0, 2π)
        double angle = rotation % (2 * Math.PI);
        if (angle < 0) angle += 2 * Math.PI;
        // 8-direction quantisation: each sector is 45° wide
        // 0=EAST, 45=SOUTHEAST, 90=SOUTH, 135=SOUTHWEST, 180=WEST, 225=NORTHWEST, 270=NORTH, 315=NORTHEAST
        String[] dirs = { "EAST", "SOUTHEAST", "SOUTH", "SOUTHWEST", "WEST", "NORTHWEST", "NORTH", "NORTHEAST" };
        int sector = (int) Math.round(angle / (Math.PI / 4)) % 8;
        String suffix = dirs[sector];
        try {
            return RenderAssetKey.valueOf(baseName + "_" + suffix);
        } catch (IllegalArgumentException e) {
            // Fallback to EAST if the directional variant doesn't exist
            return getEastSpriteKey(base);
        }
    }

    private void drawHitbox(GraphicsContext g, Vehicle v) {
        // Use the same render position as toRenderableState() — accounts for lateral offset
        Vector2D rightVector = new Vector2D(Math.cos(v.getRotation() + Math.PI/2), Math.sin(v.getRotation() + Math.PI/2));
        Vector2D renderPos = v.getPosition().add(rightVector.multiply(v.getLateralOffset()));
        double x = renderPos.x, y = renderPos.y;
        g.save();
        g.translate(x, y);
        g.rotate(Math.toDegrees(v.getRotation()));
        double scale = currentMode == ScenarioMode.GRID ? 0.72 : 1.08;
        // Scale hitbox to match visual scale
        double hw = v.getLength() * scale / 2;
        double hh = v.getWidth() * scale / 2;
        g.setStroke(Color.web("#00ff00", 0.7));
        g.setLineWidth(1.5);
        g.strokeRect(-hw, -hh, hw * 2, hh * 2);
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
                    lightRow("⬆⬇ Bắc–Nam:", lightNS),
                    lightRow("⬅➡ Đông–Tây:", lightEW));
            }
            case THREE_WAY -> {
                lightStatusBox.getChildren().addAll(
                    lightRow("⬇ Bắc–Nam:", lightNS),
                    lightRow("➡ Tây–Đông:", lightEW));
            }
            case FIVE_WAY -> {
                lightStatusBox.getChildren().addAll(
                    lightRow("⬆⬇ Bắc–Nam:", lightNS),
                    lightRow("⬅➡ Đông–Tây:", lightEW),
                    lightRow("↗ Đông-Bắc:", lightNE));
            }
            case GRID -> {
                lightStatusBox.getChildren().addAll(
                    lightRow("↕ Dọc (NS) chẵn:", lightNS),
                    lightRow("↔ Ngang (EW) chẵn:", lightEW),
                    lightRow("↕ Dọc (NS) lẻ:", lightEW),
                    lightRow("↔ Ngang (EW) lẻ:", lightNS));
            }
        }
    }

    private HBox lightRow(String label, SimpleTrafficLight light) {
        Color accent = lightGroupColor(light);
        // Coloured square badge matching the in-canvas light stripe
        javafx.scene.shape.Rectangle badge = new javafx.scene.shape.Rectangle(10, 10);
        badge.setFill(accent);
        badge.setArcWidth(3); badge.setArcHeight(3);

        Label k = new Label(label);
        k.setTextFill(Color.web("#94a3b8")); k.setFont(Font.font("Segoe UI",12)); k.setMinWidth(118);
        Label v = new Label(); v.setFont(Font.font("Segoe UI",FontWeight.BOLD,12));
        switch (light.getColor()) {
            case RED    -> { v.setText("● ĐỎ");   v.setTextFill(Color.web("#ef4444")); }
            case YELLOW -> { v.setText("● VÀNG"); v.setTextFill(Color.web("#fbbf24")); }
            case GREEN  -> { v.setText("● XANH"); v.setTextFill(Color.web("#34d399")); }
        }
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox hb = new HBox(5, badge, k, spacer, v);
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    /** Legacy overload kept for any remaining call-sites. */
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
    private boolean firstSeedDone = false;
    private final java.util.Random spawnRandom = new java.util.Random();

    private void autoSpawn() {
        String type;
        VehiclePath path;
        if (!firstSeedDone) {
            // First auto-spawn after seed: still round-robin (deterministic)
            type = AUTO_TYPES[spawnRR++ % AUTO_TYPES.length];
            path = getPathByModeAndDir(dirRR++ % dirCountForMode());
            firstSeedDone = true;
        } else {
            // All subsequent spawns: fully random
            type = AUTO_TYPES[spawnRandom.nextInt(AUTO_TYPES.length)];
            path = getPathByModeAndDir(spawnRandom.nextInt(dirCountForMode()));
        }
        doSpawn(type, path, null);
    }

    private int dirCountForMode() {
        return switch (currentMode) {
            case THREE_WAY -> 4;
            case FOUR_WAY -> 8;  // 4 straight + 4 turns
            case FIVE_WAY -> 8;  // 5 straight + 3 turns
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

    // ── 4-way ─ Vietnamese right-hand traffic convention ────────
    // Lane 0 = phải (right/slower), Lane 1 = trái (left/faster)
    
    private VehiclePath makeNorthSouthPath(int lane) {
        // N→S (top to bottom): both lanes on left half (x < cx), right-hand traffic
        // lane 0 = right lane (closer to center divider), lane 1 = left lane (closer to road edge)
        double x = cx() - laneOffset(lane);
        return new VehiclePath("ns"+lane, List.of(
            new Vector2D(x,-20), new Vector2D(x, cy()-ROAD_HALF-10),
            new Vector2D(x, cy()), new Vector2D(x, CANVAS_H+20)), 1,"light-NS","N","S");
    }
    private VehiclePath makeSouthNorthPath(int lane) {
        // S→N (bottom to top): both lanes on right half (x > cx), right-hand traffic
        // lane 0 = right lane (closer to center divider), lane 1 = left lane (closer to road edge)
        double x = cx() + laneOffset(lane);
        return new VehiclePath("sn"+lane, List.of(
            new Vector2D(x,CANVAS_H+20), new Vector2D(x, cy()+ROAD_HALF+10),
            new Vector2D(x, cy()), new Vector2D(x,-20)), 1,"light-NS","S","N");
    }
    private VehiclePath makeEastWestPath(int lane) {
        // E→W (right to left): both lanes on top half (y < cy), right-hand traffic
        // lane 0 = right lane (closer to center divider), lane 1 = left lane (closer to road edge)
        double y = cy() - laneOffset(lane);
        return new VehiclePath("ew"+lane, List.of(
            new Vector2D(CANVAS_W+20,y), new Vector2D(cx()+ROAD_HALF+10,y),
            new Vector2D(cx(),y), new Vector2D(-20,y)), 1,"light-EW","E","W");
    }
    private VehiclePath makeWestEastPath(int lane) {
        // W→E (left to right): both lanes on bottom half (y > cy), right-hand traffic
        // lane 0 = right lane (closer to center divider), lane 1 = left lane (closer to road edge)
        double y = cy() + laneOffset(lane);
        return new VehiclePath("we"+lane, List.of(
            new Vector2D(-20,y), new Vector2D(cx()-ROAD_HALF-10,y),
            new Vector2D(cx(),y), new Vector2D(CANVAS_W+20,y)), 1,"light-EW","W","E");
    }

    // ── 4-way turn paths (Vietnamese right-turn convention) ─────
    // idx 0=N→E, 1=S→W, 2=E→S, 3=W→N (all right turns)
    // Vietnamese convention: turns stay on right side of approach lane
    private VehiclePath makeFourWayTurnPath(int idx) {
        double cx = cx(), cy = cy();
        double near = ROAD_HALF - 8;
        double lw = laneOffset(0);
        return switch (idx) {
            case 0 -> // N→E: approach on right lane (x = cx-lw), exit on bottom of E-road (y = cy+lw)
                new VehiclePath("n-e-turn", List.of(
                    new Vector2D(cx - lw, -20),
                    new Vector2D(cx - lw, cy - near),
                    new Vector2D(cx - lw, cy + lw),   // curve stays in approach lane x, then shifts
                    new Vector2D(cx + near, cy + lw),
                    new Vector2D(CANVAS_W + 20, cy + lw)), 1, "light-NS", "N", "E");
            case 1 -> // S→W: approach on right lane (x = cx+lw), exit on top of W-road (y = cy-lw)
                new VehiclePath("s-w-turn", List.of(
                    new Vector2D(cx + lw, CANVAS_H + 20),
                    new Vector2D(cx + lw, cy + near),
                    new Vector2D(cx + lw, cy - lw),   // curve stays in approach lane x
                    new Vector2D(cx - near, cy - lw),
                    new Vector2D(-20, cy - lw)), 1, "light-NS", "S", "W");
            case 2 -> // E→S: approach on right lane (y = cy-lw), exit on left of S-road (x = cx-lw, southbound)
                new VehiclePath("e-s-turn", List.of(
                    new Vector2D(CANVAS_W + 20, cy - lw),
                    new Vector2D(cx + near, cy - lw),
                    new Vector2D(cx - lw, cy - lw),   // curve crosses intersection to southbound lane
                    new Vector2D(cx - lw, cy + near),
                    new Vector2D(cx - lw, CANVAS_H + 20)), 1, "light-EW", "E", "S");
            default -> // W→N: approach on right lane (y = cy+lw), exit on right of N-road (x = cx+lw, northbound)
                new VehiclePath("w-n-turn", List.of(
                    new Vector2D(-20, cy + lw),
                    new Vector2D(cx - near, cy + lw),
                    new Vector2D(cx + lw, cy + lw),   // curve crosses intersection to northbound lane
                    new Vector2D(cx + lw, cy - near),
                    new Vector2D(cx + lw, -20)), 1, "light-EW", "W", "N");
        };
    }


    private VehiclePath makeThreeWayPath(int idx) {
        double cx=cx(), cy=cy(), lw=laneOffset(0);
        return switch(idx%4) {
            case 0 -> {
                // N→S: standard right-hand lane (x < cx)
                double x = cx - laneOffset(0);
                yield new VehiclePath("3w-ns", List.of(
                    new Vector2D(x,-20), new Vector2D(x,cy-ROAD_HALF-10),
                    new Vector2D(x,cy+ROAD_HALF+10), new Vector2D(x,CANVAS_H+20)), 1,"light-NS","N","S");
            }
            case 1 -> {
                // S→N: standard right-hand lane (x > cx)
                double x = cx + laneOffset(0);
                yield new VehiclePath("3w-sn", List.of(
                    new Vector2D(x,CANVAS_H+20), new Vector2D(x,cy+ROAD_HALF+10),
                    new Vector2D(x,cy-ROAD_HALF-10), new Vector2D(x,-20)), 1,"light-NS","S","N");
            }
            case 2 -> new VehiclePath("3w-wn", List.of(
                new Vector2D(-20,cy+lw), new Vector2D(cx-ROAD_HALF-10,cy+lw),
                new Vector2D(cx-lw,cy+lw), new Vector2D(cx-lw,cy-ROAD_HALF-20),
                new Vector2D(cx-lw,-20)), 1,"light-EW","W","N");
            default -> new VehiclePath("3w-ws", List.of(
                // W→S: enter from left at y=cy+lw (right lane going E), turn down to S at x=cx+lw
                new Vector2D(-20,cy+lw), new Vector2D(cx-ROAD_HALF-10,cy+lw),
                new Vector2D(cx+lw,cy+lw), new Vector2D(cx+lw,cy+ROAD_HALF+20),
                new Vector2D(cx+lw,CANVAS_H+20)), 1,"light-EW","W","S");
        };
    }

    // ── 5-way (Vietnamese traffic convention) ──────────────────────
    private VehiclePath makeFiveWayPath(int idx) {
        double cx=cx(), cy=cy(), lw=LANE_W/2.0;
        return switch(idx%5) {
            case 0 -> makeNorthSouthPath(nextLane());
            case 1 -> makeSouthNorthPath(nextLane());
            case 2 -> makeEastWestPath(nextLane());
            case 3 -> makeWestEastPath(nextLane());
            default -> {
                // NE diagonal path — xe đến từ góc NE, đi vào giao lộ rồi ra hướng Tây
                // Tính toán đúng waypoints nằm trên arm xoay -45°, làn phải VN
                double cos45 = Math.sqrt(2) / 2;
                double laneOff = LANE_W / 2.0;
                // Perpendicular phải của xe đi hướng SW (góc 135°): offset về SE
                double prX = cos45 * laneOff, prY = cos45 * laneOff;
                // Điểm trên arm tại distance d từ center giao lộ + lane offset
                // world = (cx + cos45*d + prX, cy - cos45*d + prY)
                yield new VehiclePath("ne-diag", List.of(
                    new Vector2D(cx + cos45*370 + prX, cy - cos45*370 + prY), // entry
                    new Vector2D(cx + cos45*200 + prX, cy - cos45*200 + prY),
                    new Vector2D(cx + cos45*80  + prX, cy - cos45*80  + prY),
                    new Vector2D(cx - ROAD_HALF - 10, cy - laneOffset(0)),     // rẽ ra đường ngang, làn phải E→W
                    new Vector2D(-20, cy - laneOffset(0))),                     // exit West
                    1, "light-NE", "NE", "W");
            }
        };
    }

    // ── Grid paths (Vietnamese traffic convention) ─────────────────
    private VehiclePath makeGridPath(int idx) {
        double gx = CANVAS_W/4.0, gy = CANVAS_H/4.0;
        double lane = LANE_W / 2.0;
        return switch(idx%6) {
            case 0 -> new VehiclePath("grid-v1", List.of(
                new Vector2D(gx-lane,-20), new Vector2D(gx-lane,gy-16),
                new Vector2D(gx-lane,gy+16), new Vector2D(gx-lane,gy*2-16),
                new Vector2D(gx-lane,gy*2+16), new Vector2D(gx-lane,CANVAS_H+20)), 1,"light-NS","N1","S1");
            case 1 -> new VehiclePath("grid-v2-up", List.of(
                new Vector2D(gx*2+lane,CANVAS_H+20), new Vector2D(gx*2+lane,gy*3+16),
                new Vector2D(gx*2+lane,gy*3-16), new Vector2D(gx*2+lane,gy*2+16),
                new Vector2D(gx*2+lane,gy*2-16), new Vector2D(gx*2+lane,gy+16),
                new Vector2D(gx*2+lane,gy-16), new Vector2D(gx*2+lane,-20)), 1,"light-EW","S2","N2");
            case 2 -> new VehiclePath("grid-v3", List.of(
                new Vector2D(gx*3-lane,-20), new Vector2D(gx*3-lane,gy-16),
                new Vector2D(gx*3-lane,gy+16), new Vector2D(gx*3-lane,gy*2-16),
                new Vector2D(gx*3-lane,gy*2+16), new Vector2D(gx*3-lane,CANVAS_H+20)), 1,"light-NS","N3","S3");
            case 3 -> new VehiclePath("grid-h1", List.of(
                new Vector2D(-20,gy+lane), new Vector2D(gx-16,gy+lane),
                new Vector2D(gx+16,gy+lane), new Vector2D(gx*2-16,gy+lane),
                new Vector2D(gx*2+16,gy+lane), new Vector2D(CANVAS_W+20,gy+lane)), 1,"light-EW","W1","E1");
            case 4 -> new VehiclePath("grid-h2-left", List.of(
                new Vector2D(CANVAS_W+20,gy*2-lane), new Vector2D(gx*3+16,gy*2-lane),
                new Vector2D(gx*3-16,gy*2-lane), new Vector2D(gx*2+16,gy*2-lane),
                new Vector2D(gx*2-16,gy*2-lane), new Vector2D(gx-16,gy*2-lane),
                new Vector2D(-20,gy*2-lane)), 1,"light-NS","E2","W2");
            default -> new VehiclePath("grid-h3", List.of(
                new Vector2D(-20,gy*3+lane), new Vector2D(gx-16,gy*3+lane),
                new Vector2D(gx+16,gy*3+lane), new Vector2D(gx*2-16,gy*3+lane),
                new Vector2D(gx*2+16,gy*3+lane), new Vector2D(CANVAS_W+20,gy*3+lane)), 1,"light-EW","W3","E3");
        };
    }

    private VehiclePath getPathByModeAndDir(int idx) {
        return switch (currentMode) {
            case FOUR_WAY -> switch(idx%8) {
                case 0 -> makeNorthSouthPath(nextLane());
                case 1 -> makeSouthNorthPath(nextLane());
                case 2 -> makeEastWestPath(nextLane());
                case 3 -> makeWestEastPath(nextLane());
                case 4 -> makeFourWayTurnPath(0); // N->E (right turn)
                case 5 -> makeFourWayTurnPath(1); // S->W (right turn)
                case 6 -> makeFourWayTurnPath(2); // E->S (right turn)
                default-> makeFourWayTurnPath(3); // W->N (right turn)
            };
            case THREE_WAY -> makeThreeWayPath(idx%4);
            case FIVE_WAY  -> switch(idx%8) {
                case 0 -> makeNorthSouthPath(nextLane());
                case 1 -> makeSouthNorthPath(nextLane());
                case 2 -> makeEastWestPath(nextLane());
                case 3 -> makeWestEastPath(nextLane());
                case 4 -> makeFiveWayPath(4); // NE diagonal
                case 5 -> makeFourWayTurnPath(0); // N->E
                case 6 -> makeFourWayTurnPath(2); // E->S
                default-> makeFourWayTurnPath(3); // W->N
            };
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
        simTime=0; spawnTimer=0; spawnRR=0; dirRR=0; laneRR=0; firstSeedDone=false;
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
        cb.setStyle("-fx-background-color:#1e3a5f;-fx-text-fill:#e2e8f0;-fx-font-size:12;-fx-background-radius:4;");
        cb.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-text-fill:#e2e8f0;-fx-background-color:#1e3a5f;-fx-font-size:12;-fx-padding:5 8;");
            }
        });
        cb.setButtonCell(new javafx.scene.control.ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-text-fill:#e2e8f0;-fx-background-color:transparent;-fx-font-size:12;");
            }
        });
    }
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