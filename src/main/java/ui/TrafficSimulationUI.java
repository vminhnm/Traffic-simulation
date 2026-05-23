package ui;

import core.driver.AggressiveDriver;
import core.driver.EmergencyDriver;
import core.driver.NormalDriver;
import core.road.VehiclePath;
import core.simulation.SimulationEngine;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.trafficlight.LightTiming;
import core.trafficlight.TrafficLight;
import core.vehicle.*;
import util.Vector2D;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Giao diện JavaFX cho hệ thống mô phỏng giao thông.
 * Tích hợp trực tiếp với SimulationEngine, SimulationWorld, Vehicle, TrafficLight.
 */
public class TrafficSimulationUI extends Application {

    // ── Kích thước ────────────────────────────────────────────────────
    private static final int CANVAS_W = 860;
    private static final int CANVAS_H = 660;

    // ── Center của giao lộ ────────────────────────────────────────────
    private static final double CX = CANVAS_W / 2.0;
    private static final double CY = CANVAS_H / 2.0;
    private static final double ROAD_HALF = 55;   // nửa chiều rộng đường
    private static final double LANE_W    = 25;   // chiều rộng 1 làn

    // ── Simulation core ───────────────────────────────────────────────
    private SimulationWorld world;
    private SimulationEngine engine;
    private AnimationTimer gameLoop;
    private long lastNano = 0;

    // ── UI components ──────────────────────────────────────────────────
    private Canvas canvas;
    private Label  lblVehicleCount;
    private Label  lblTime;
    private Label  lblFPS;
    private Label  lblLightNorth;
    private Label  lblLightSouth;
    private Label  lblLightEast;
    private Label  lblLightWest;
    private Slider sldSpeed;
    private Button btnStartPause;
    private TextArea logArea;

    // ── Stats ──────────────────────────────────────────────────────────
    private double simTime = 0;
    private double simSpeedMul = 1.0;
    private final AtomicInteger totalSpawned = new AtomicInteger(0);
    private final AtomicInteger totalFinished = new AtomicInteger(0);
    private final AtomicInteger totalCrashed = new AtomicInteger(0);

    // ── Traffic lights (4-way) ─────────────────────────────────────────
    private SimpleTrafficLight lightNS; // North-South green
    private SimpleTrafficLight lightEW; // East-West green

    // ── Spawn control ──────────────────────────────────────────────────
    private double spawnTimer = 0;
    private double spawnInterval = 3.0;
    private final String[] VEHICLE_TYPES = {"car", "car", "car", "motorbike", "bus", "truck", "ambulance", "bicycle"};
    private int spawnRoundRobin = 0;
    private int spawnDirectionRR = 0;

    // ── FPS counter ────────────────────────────────────────────────────
    private long frameCount = 0;
    private double fpsTimer = 0;
    private double currentFPS = 0;

    @Override
    public void start(Stage stage) {
        stage.setTitle("🚦 Traffic Simulation — Mô phỏng Giao thông");

        // ── Build world ────────────────────────────────────────────────
        initWorld();

        // ── Build UI ───────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        root.setTop(buildHeader());
        root.setCenter(buildCanvasArea());
        root.setRight(buildControlPanel());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, CANVAS_W + 320, CANVAS_H + 100);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        // ── Start loop ─────────────────────────────────────────────────
        startGameLoop();
        engine.start();
        log("✅ Mô phỏng khởi động. Hãy nhấn Tạm dừng để dừng.");
    }

    // ════════════════════════════════════════════════════════════════════
    //  World Initialization
    // ════════════════════════════════════════════════════════════════════

    private void initWorld() {
        world  = new SimulationWorld();
        engine = new SimulationEngine(world);

        // Tạo 4 đèn giao thông
        lightNS = new SimpleTrafficLight("light-NS", LightColor.GREEN,
                new LightTiming(20, 3, 20));
        lightEW = new SimpleTrafficLight("light-EW", LightColor.RED,
                new LightTiming(20, 3, 20));
        lightEW.forceRed();   // bắt đầu: NS xanh, EW đỏ

        world.registerTrafficLight(lightNS);
        world.registerTrafficLight(lightEW);

        // Tạo vài xe ban đầu
        spawnVehicle("car",      makeNorthSouthPath());
        spawnVehicle("bus",      makeEastWestPath());
        spawnVehicle("car",      makeSouthNorthPath());
        spawnVehicle("ambulance",makeWestEastPath());
    }

    // ════════════════════════════════════════════════════════════════════
    //  UI Builders
    // ════════════════════════════════════════════════════════════════════

    private HBox buildHeader() {
        HBox hb = new HBox(12);
        hb.setPadding(new Insets(14, 20, 10, 20));
        hb.setAlignment(Pos.CENTER_LEFT);
        hb.setStyle("-fx-background-color: #16213e;");

        Label title = new Label("🚦 Traffic Simulation");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#e2e8f0"));

        Label sub = new Label("Mô phỏng Giao thông Thông minh");
        sub.setFont(Font.font("Segoe UI", 13));
        sub.setTextFill(Color.web("#94a3b8"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblFPS = styledLabel("FPS: --", "#64ffda");

        hb.getChildren().addAll(title, sub, spacer, lblFPS);
        return hb;
    }

    private StackPane buildCanvasArea() {
        canvas = new Canvas(CANVAS_W, CANVAS_H);
        StackPane sp = new StackPane(canvas);
        sp.setStyle("-fx-background-color: #0f3460;");
        return sp;
    }

    private VBox buildControlPanel() {
        VBox panel = new VBox(14);
        panel.setPadding(new Insets(16, 16, 16, 12));
        panel.setPrefWidth(305);
        panel.setStyle("-fx-background-color: #16213e; -fx-border-color: #0f3460; -fx-border-width: 0 0 0 2;");

        panel.getChildren().addAll(
            sectionLabel("📊 Thống kê"),
            buildStatsBox(),
            new Separator(),
            sectionLabel("🚦 Đèn Giao thông"),
            buildLightStatusBox(),
            new Separator(),
            sectionLabel("🎛️ Điều khiển"),
            buildControlsBox(),
            new Separator(),
            sectionLabel("➕ Thêm Phương tiện"),
            buildSpawnBox(),
            new Separator(),
            sectionLabel("📝 Nhật ký"),
            buildLogBox()
        );
        return panel;
    }

    private HBox buildStatusBar() {
        HBox hb = new HBox(20);
        hb.setPadding(new Insets(6, 20, 6, 20));
        hb.setAlignment(Pos.CENTER_LEFT);
        hb.setStyle("-fx-background-color: #0f3460;");

        lblTime = styledLabel("⏱ Thời gian: 0.0s", "#94a3b8");
        lblVehicleCount = styledLabel("🚗 Xe: 0", "#94a3b8");
        Label credit = new Label("Traffic Simulation v1.0 — JavaFX UI");
        credit.setTextFill(Color.web("#475569"));
        credit.setFont(Font.font("Segoe UI", 11));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        hb.getChildren().addAll(lblTime, lblVehicleCount, spacer, credit);
        return hb;
    }

    private VBox buildStatsBox() {
        VBox box = new VBox(6);

        Label lSpawned  = createStatRow("Đã tạo:");
        Label lFinished = createStatRow("Đã qua giao lộ:");
        Label lCrashed  = createStatRow("Va chạm:");

        // refresh trong game loop
        this.lblVehicleCount = lSpawned;

        box.getChildren().addAll(
            makeStatLine("🚗 Đã tạo:", lSpawned),
            makeStatLine("✅ Qua giao lộ:", lFinished),
            makeStatLine("💥 Va chạm:", lCrashed)
        );

        // Store references
        this.statFinished = lFinished;
        this.statCrashed  = lCrashed;

        return box;
    }

    private Label statFinished, statCrashed;

    private HBox makeStatLine(String label, Label value) {
        Label lbl = new Label(label);
        lbl.setTextFill(Color.web("#94a3b8"));
        lbl.setFont(Font.font("Segoe UI", 12));
        lbl.setMinWidth(130);
        value.setTextFill(Color.web("#64ffda"));
        value.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        HBox hb = new HBox(6, lbl, value);
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    private VBox buildLightStatusBox() {
        VBox box = new VBox(6);
        lblLightNorth = lightIndicator("Bắc–Nam:");
        lblLightSouth = lightIndicator("Nam–Bắc:");
        lblLightEast  = lightIndicator("Đông–Tây:");
        lblLightWest  = lightIndicator("Tây–Đông:");

        box.getChildren().addAll(
            makeLightRow("🔴 Bắc–Nam:", lblLightNorth),
            makeLightRow("🔴 Nam–Bắc:", lblLightSouth),
            makeLightRow("🔴 Đông–Tây:", lblLightEast),
            makeLightRow("🔴 Tây–Đông:", lblLightWest)
        );
        return box;
    }

    private HBox makeLightRow(String dir, Label indicator) {
        Label d = new Label(dir);
        d.setTextFill(Color.web("#94a3b8"));
        d.setFont(Font.font("Segoe UI", 12));
        d.setMinWidth(110);
        HBox hb = new HBox(6, d, indicator);
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    private VBox buildControlsBox() {
        VBox box = new VBox(10);

        // Start/Pause
        btnStartPause = new Button("⏸ Tạm dừng");
        styleButton(btnStartPause, "#3b82f6");
        btnStartPause.setMaxWidth(Double.MAX_VALUE);
        btnStartPause.setOnAction(e -> togglePause());

        // Reset
        Button btnReset = new Button("🔄 Đặt lại");
        styleButton(btnReset, "#6366f1");
        btnReset.setMaxWidth(Double.MAX_VALUE);
        btnReset.setOnAction(e -> resetSimulation());

        // Speed control
        Label spdLabel = new Label("⚡ Tốc độ mô phỏng: 1.0×");
        spdLabel.setTextFill(Color.web("#94a3b8"));
        spdLabel.setFont(Font.font("Segoe UI", 12));

        sldSpeed = new Slider(0.1, 3.0, 1.0);
        sldSpeed.setShowTickMarks(true);
        sldSpeed.setMajorTickUnit(0.5);
        sldSpeed.setStyle("-fx-control-inner-background: #1e3a5f;");
        sldSpeed.valueProperty().addListener((obs, ov, nv) -> {
            simSpeedMul = nv.doubleValue();
            spdLabel.setText(String.format("⚡ Tốc độ mô phỏng: %.1f×", simSpeedMul));
        });

        // Spawn interval
        Label spawnLabel = new Label("🕐 Khoảng cách sinh xe: 3.0s");
        spawnLabel.setTextFill(Color.web("#94a3b8"));
        spawnLabel.setFont(Font.font("Segoe UI", 12));

        Slider sldSpawn = new Slider(0.5, 8.0, 3.0);
        sldSpawn.setShowTickMarks(true);
        sldSpawn.setMajorTickUnit(1.0);
        sldSpawn.setStyle("-fx-control-inner-background: #1e3a5f;");
        sldSpawn.valueProperty().addListener((obs, ov, nv) -> {
            spawnInterval = nv.doubleValue();
            spawnLabel.setText(String.format("🕐 Khoảng cách sinh xe: %.1fs", spawnInterval));
        });

        // Manual light switch
        Button btnSwitchLight = new Button("🚦 Đổi đèn thủ công");
        styleButton(btnSwitchLight, "#0ea5e9");
        btnSwitchLight.setMaxWidth(Double.MAX_VALUE);
        btnSwitchLight.setOnAction(e -> {
            lightNS.switchManually();
            lightEW.switchManually();
            log("🚦 Đổi đèn thủ công.");
        });

        box.getChildren().addAll(
            btnStartPause, btnReset,
            spdLabel, sldSpeed,
            spawnLabel, sldSpawn,
            btnSwitchLight
        );
        return box;
    }

    private VBox buildSpawnBox() {
        VBox box = new VBox(8);

        String[] types = {"car", "motorbike", "bus", "truck", "bicycle", "ambulance", "firetruck"};
        String[] labels = {"🚗 Ô tô", "🏍 Mô tô", "🚌 Xe buýt", "🚚 Xe tải", "🚲 Xe đạp", "🚑 Cứu thương", "🚒 Cứu hỏa"};
        String[] fromLabels = {"Bắc→Nam", "Nam→Bắc", "Đông→Tây", "Tây→Đông"};

        ComboBox<String> cbType = new ComboBox<>();
        cbType.getItems().addAll(labels);
        cbType.setValue(labels[0]);
        styleCombo(cbType);

        ComboBox<String> cbFrom = new ComboBox<>();
        cbFrom.getItems().addAll(fromLabels);
        cbFrom.setValue(fromLabels[0]);
        styleCombo(cbFrom);

        ComboBox<String> cbDriver = new ComboBox<>();
        cbDriver.getItems().addAll("Normal", "Aggressive", "Emergency");
        cbDriver.setValue("Normal");
        styleCombo(cbDriver);

        Button btnSpawn = new Button("➕ Thêm xe");
        styleButton(btnSpawn, "#10b981");
        btnSpawn.setMaxWidth(Double.MAX_VALUE);
        btnSpawn.setOnAction(e -> {
            String type = types[cbType.getSelectionModel().getSelectedIndex()];
            int dirIdx  = cbFrom.getSelectionModel().getSelectedIndex();
            String drv  = cbDriver.getValue();
            VehiclePath path = getPathByDirection(dirIdx);
            var behavior = switch (drv) {
                case "Aggressive" -> new AggressiveDriver();
                case "Emergency"  -> new EmergencyDriver();
                default           -> new NormalDriver();
            };
            spawnVehicleWithBehavior(type, path, behavior);
        });

        box.getChildren().addAll(
            labelSmall("Loại xe:"), cbType,
            labelSmall("Hướng đi:"), cbFrom,
            labelSmall("Kiểu lái:"), cbDriver,
            btnSpawn
        );
        return box;
    }

    private ScrollPane buildLogBox() {
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(130);
        logArea.setStyle(
            "-fx-control-inner-background: #0f3460;" +
            "-fx-text-fill: #94a3b8;" +
            "-fx-font-family: 'Consolas'; -fx-font-size: 10;"
        );
        ScrollPane sp = new ScrollPane(logArea);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #0f3460; -fx-border-color: transparent;");
        return sp;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Game Loop
    // ════════════════════════════════════════════════════════════════════

    private void startGameLoop() {
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNano == 0) { lastNano = now; return; }

                double rawDelta = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;
                double delta = Math.min(rawDelta, 0.05) * simSpeedMul;

                // FPS
                frameCount++;
                fpsTimer += rawDelta;
                if (fpsTimer >= 1.0) {
                    currentFPS = frameCount / fpsTimer;
                    frameCount = 0;
                    fpsTimer = 0;
                }

                if (engine.isRunning()) {
                    simTime += delta;
                    engine.update(delta);

                    // Spawn logic
                    spawnTimer += delta;
                    if (spawnTimer >= spawnInterval) {
                        spawnTimer = 0;
                        autoSpawn();
                    }

                    // Remove finished / crashed
                    cleanupVehicles();
                }

                render();
                updateUI();
            }
        };
        gameLoop.start();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Rendering
    // ════════════════════════════════════════════════════════════════════

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();

        // ── Background ─────────────────────────────────────────────────
        g.setFill(Color.web("#1a2332"));
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);

        // ── Grass / Sidewalk blocks ────────────────────────────────────
        g.setFill(Color.web("#1e3a2f"));
        // 4 corner blocks
        g.fillRect(0, 0, CX - ROAD_HALF, CY - ROAD_HALF);
        g.fillRect(CX + ROAD_HALF, 0, CANVAS_W, CY - ROAD_HALF);
        g.fillRect(0, CY + ROAD_HALF, CX - ROAD_HALF, CANVAS_H);
        g.fillRect(CX + ROAD_HALF, CY + ROAD_HALF, CANVAS_W, CANVAS_H);

        // ── Roads ──────────────────────────────────────────────────────
        g.setFill(Color.web("#374151"));
        // Vertical road
        g.fillRect(CX - ROAD_HALF, 0, ROAD_HALF * 2, CANVAS_H);
        // Horizontal road
        g.fillRect(0, CY - ROAD_HALF, CANVAS_W, ROAD_HALF * 2);

        // ── Intersection box ───────────────────────────────────────────
        g.setFill(Color.web("#4b5563"));
        g.fillRect(CX - ROAD_HALF, CY - ROAD_HALF, ROAD_HALF * 2, ROAD_HALF * 2);

        // ── Lane markings ──────────────────────────────────────────────
        drawLaneMarkings(g);

        // ── Zebra crossings ────────────────────────────────────────────
        drawZebraCrossings(g);

        // ── Traffic lights ─────────────────────────────────────────────
        drawTrafficLights(g);

        // ── Vehicles ───────────────────────────────────────────────────
        for (Vehicle v : world.getVehicles()) {
            drawVehicle(g, v.toRenderableState());
        }

        // ── Overlay info ───────────────────────────────────────────────
        drawOverlay(g);
    }

    private void drawLaneMarkings(GraphicsContext g) {
        g.setStroke(Color.web("#facc15", 0.7));
        g.setLineWidth(2);
        double dashOn = 20, dashOff = 15;

        // Center line - vertical road
        g.setLineDashes(dashOn, dashOff);
        g.strokeLine(CX, 0, CX, CY - ROAD_HALF);
        g.strokeLine(CX, CY + ROAD_HALF, CX, CANVAS_H);

        // Center line - horizontal road
        g.strokeLine(0, CY, CX - ROAD_HALF, CY);
        g.strokeLine(CX + ROAD_HALF, CY, CANVAS_W, CY);
        g.setLineDashes(null);

        // Edge lines
        g.setStroke(Color.web("#e2e8f0", 0.5));
        g.setLineWidth(1.5);
        // vertical road edges
        g.strokeLine(CX - ROAD_HALF, 0, CX - ROAD_HALF, CY - ROAD_HALF);
        g.strokeLine(CX + ROAD_HALF, 0, CX + ROAD_HALF, CY - ROAD_HALF);
        g.strokeLine(CX - ROAD_HALF, CY + ROAD_HALF, CX - ROAD_HALF, CANVAS_H);
        g.strokeLine(CX + ROAD_HALF, CY + ROAD_HALF, CX + ROAD_HALF, CANVAS_H);
        // horizontal road edges
        g.strokeLine(0, CY - ROAD_HALF, CX - ROAD_HALF, CY - ROAD_HALF);
        g.strokeLine(0, CY + ROAD_HALF, CX - ROAD_HALF, CY + ROAD_HALF);
        g.strokeLine(CX + ROAD_HALF, CY - ROAD_HALF, CANVAS_W, CY - ROAD_HALF);
        g.strokeLine(CX + ROAD_HALF, CY + ROAD_HALF, CANVAS_W, CY + ROAD_HALF);
    }

    private void drawZebraCrossings(GraphicsContext g) {
        g.setFill(Color.web("#e2e8f0", 0.3));
        int stripes = 5;
        double sw = ROAD_HALF * 2 / stripes;

        // Top crossing
        double ty = CY - ROAD_HALF - 18;
        for (int i = 0; i < stripes; i++) {
            g.fillRect(CX - ROAD_HALF + i * sw + 1, ty, sw - 2, 14);
        }
        // Bottom crossing
        double by = CY + ROAD_HALF + 4;
        for (int i = 0; i < stripes; i++) {
            g.fillRect(CX - ROAD_HALF + i * sw + 1, by, sw - 2, 14);
        }
        // Left crossing
        double lx = CX - ROAD_HALF - 18;
        for (int i = 0; i < stripes; i++) {
            g.fillRect(lx, CY - ROAD_HALF + i * sw + 1, 14, sw - 2);
        }
        // Right crossing
        double rx = CX + ROAD_HALF + 4;
        for (int i = 0; i < stripes; i++) {
            g.fillRect(rx, CY - ROAD_HALF + i * sw + 1, 14, sw - 2);
        }
    }

    private void drawTrafficLights(GraphicsContext g) {
        LightColor nsColor = lightNS.getColor();
        LightColor ewColor = lightEW.getColor();

        // North light (right side of road going down)
        drawLight(g, CX + ROAD_HALF + 6, CY - ROAD_HALF - 32, nsColor);
        // South light
        drawLight(g, CX - ROAD_HALF - 22, CY + ROAD_HALF + 6, nsColor);
        // East light
        drawLight(g, CX + ROAD_HALF + 6, CY + 8, ewColor);
        // West light
        drawLight(g, CX - ROAD_HALF - 22, CY - ROAD_HALF - 32, ewColor);
    }

    private void drawLight(GraphicsContext g, double x, double y, LightColor color) {
        // Housing
        g.setFill(Color.web("#1f2937"));
        g.fillRoundRect(x, y, 16, 44, 4, 4);

        // Red
        boolean redOn = (color == LightColor.RED);
        g.setFill(redOn ? Color.web("#ef4444") : Color.web("#7f1d1d"));
        if (redOn) { g.setEffect(new javafx.scene.effect.Glow(0.8)); }
        g.fillOval(x + 2, y + 2, 12, 12);
        g.setEffect(null);

        // Yellow
        boolean yelOn = (color == LightColor.YELLOW);
        g.setFill(yelOn ? Color.web("#fbbf24") : Color.web("#78350f"));
        if (yelOn) { g.setEffect(new javafx.scene.effect.Glow(0.8)); }
        g.fillOval(x + 2, y + 16, 12, 12);
        g.setEffect(null);

        // Green
        boolean grnOn = (color == LightColor.GREEN);
        g.setFill(grnOn ? Color.web("#34d399") : Color.web("#064e3b"));
        if (grnOn) { g.setEffect(new javafx.scene.effect.Glow(0.8)); }
        g.fillOval(x + 2, y + 30, 12, 12);
        g.setEffect(null);
    }

    private void drawVehicle(GraphicsContext g, core.vehicle.RenderableState state) {
        double x  = state.getPosition().x;
        double y  = state.getPosition().y;
        double rot = state.getRotation();
        double len = state.getLength();
        double wid = state.getWidth();

        g.save();
        g.translate(x, y);
        g.rotate(Math.toDegrees(rot));

        // Crashed = grayscale + shake
        if (state.isCrashed()) {
            g.setFill(Color.web("#6b7280"));
            g.fillRoundRect(-len / 2, -wid / 2, len, wid, 4, 4);
            g.setFill(Color.RED);
            g.setFont(Font.font(10));
            g.fillText("💥", -6, 4);
            g.restore();
            return;
        }

        // Siren flash background
        if (state.isPriority() && state.isSirenFlash()) {
            g.setFill(Color.web("#fef3c7", 0.3));
            g.fillOval(-len, -len, len * 2, len * 2);
        }

        // Body
        java.awt.Color ac = state.getBodyColor();
        Color body = Color.rgb(ac.getRed(), ac.getGreen(), ac.getBlue());
        g.setFill(body);
        g.fillRoundRect(-len / 2, -wid / 2, len, wid, 6, 6);

        // Roof
        java.awt.Color rc = state.getRoofColor();
        Color roof = Color.rgb(rc.getRed(), rc.getGreen(), rc.getBlue());
        g.setFill(roof);
        double roofLen = len * 0.5;
        double roofWid = wid * 0.7;
        g.fillRoundRect(-roofLen / 2, -roofWid / 2, roofLen, roofWid, 3, 3);

        // Headlights
        g.setFill(Color.web("#fef9c3", 0.9));
        g.fillOval(len / 2 - 5, -wid / 2, 5, 4);
        g.fillOval(len / 2 - 5, wid / 2 - 4, 5, 4);

        // Yielding indicator
        if (state.isYielding()) {
            g.setStroke(Color.ORANGE);
            g.setLineWidth(2);
            g.strokeRoundRect(-len / 2 - 2, -wid / 2 - 2, len + 4, wid + 4, 6, 6);
        }

        // Stopped indicator
        if (state.isStopped() && !state.isYielding()) {
            g.setFill(Color.web("#ef4444", 0.7));
            g.fillRect(-len / 2 - 4, -2, 4, 4);
        }

        // Priority siren
        if (state.isPriority()) {
            Color sc = state.isSirenFlash() ? Color.web("#ef4444") : Color.web("#3b82f6");
            g.setFill(sc);
            g.fillRect(-len / 2 + 2, -wid / 2 - 5, 8, 5);
        }

        // Label
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
        g.fillText(state.getBasicLabel(), -len / 4, 3);

        g.restore();
    }

    private void drawOverlay(GraphicsContext g) {
        // Paused overlay
        if (!engine.isRunning()) {
            g.setFill(Color.web("#000000", 0.45));
            g.fillRect(0, 0, CANVAS_W, CANVAS_H);
            g.setFill(Color.web("#f8fafc"));
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
            g.fillText("⏸ TẠM DỪNG", CX - 110, CY + 12);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  UI Updates
    // ════════════════════════════════════════════════════════════════════

    private void updateUI() {
        lblFPS.setText(String.format("FPS: %.0f", currentFPS));
        lblTime.setText(String.format("⏱ Thời gian: %.1fs", simTime));
        lblVehicleCount.setText(String.valueOf(totalSpawned.get()));
        statFinished.setText(String.valueOf(totalFinished.get()));
        statCrashed.setText(String.valueOf(totalCrashed.get()));

        // Light indicators
        updateLightLabel(lblLightNorth, lightNS.getColor());
        updateLightLabel(lblLightSouth, lightNS.getColor());
        updateLightLabel(lblLightEast,  lightEW.getColor());
        updateLightLabel(lblLightWest,  lightEW.getColor());
    }

    private void updateLightLabel(Label lbl, LightColor color) {
        switch (color) {
            case RED    -> { lbl.setText("● ĐỎ");   lbl.setTextFill(Color.web("#ef4444")); }
            case YELLOW -> { lbl.setText("● VÀNG"); lbl.setTextFill(Color.web("#fbbf24")); }
            case GREEN  -> { lbl.setText("● XANH"); lbl.setTextFill(Color.web("#34d399")); }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Vehicle Spawning
    // ════════════════════════════════════════════════════════════════════

    private void autoSpawn() {
        String type = VEHICLE_TYPES[spawnRoundRobin % VEHICLE_TYPES.length];
        spawnRoundRobin++;
        VehiclePath path = getPathByDirection(spawnDirectionRR % 4);
        spawnDirectionRR++;
        spawnVehicle(type, path);
    }

    private void spawnVehicle(String type, VehiclePath path) {
        spawnVehicleWithBehavior(type, path, null);
    }

    private void spawnVehicleWithBehavior(String type, VehiclePath path, core.driver.DriverBehavior beh) {
        try {
            Vehicle v;
            if (beh == null) {
                v = VehicleFactory.create(type, path);
            } else {
                v = VehicleFactory.create(type, path, beh);
            }
            world.addVehicle(v);
            totalSpawned.incrementAndGet();
            log("🚗 Tạo xe: " + type + " [" + v.getId() + "] " + path.getEntryArm() + "→" + path.getExitArm());
        } catch (Exception ex) {
            log("⚠ Lỗi tạo xe: " + ex.getMessage());
        }
    }

    private void cleanupVehicles() {
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle v : world.getVehicles()) {
            if (v.isFinished()) {
                toRemove.add(v);
                totalFinished.incrementAndGet();
            } else if (v.isCrashed()) {
                toRemove.add(v);
                totalCrashed.incrementAndGet();
                log("💥 Va chạm: " + v.getId());
            }
        }
        for (Vehicle v : toRemove) world.removeVehicle(v);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Paths — 4 hướng qua giao lộ
    // ════════════════════════════════════════════════════════════════════

    private VehiclePath makeNorthSouthPath() {
        double laneOffset = LANE_W / 2.0;
        List<Vector2D> wps = List.of(
            new Vector2D(CX + laneOffset, -20),
            new Vector2D(CX + laneOffset, CY - ROAD_HALF - 20),  // stop line
            new Vector2D(CX + laneOffset, CY),
            new Vector2D(CX + laneOffset, CANVAS_H + 20)
        );
        return new VehiclePath("ns", wps, 1, "light-NS", "N", "S");
    }

    private VehiclePath makeSouthNorthPath() {
        double laneOffset = LANE_W / 2.0;
        List<Vector2D> wps = List.of(
            new Vector2D(CX - laneOffset, CANVAS_H + 20),
            new Vector2D(CX - laneOffset, CY + ROAD_HALF + 20),  // stop line
            new Vector2D(CX - laneOffset, CY),
            new Vector2D(CX - laneOffset, -20)
        );
        return new VehiclePath("sn", wps, 1, "light-NS", "S", "N");
    }

    private VehiclePath makeEastWestPath() {
        double laneOffset = LANE_W / 2.0;
        List<Vector2D> wps = List.of(
            new Vector2D(CANVAS_W + 20, CY + laneOffset),
            new Vector2D(CX + ROAD_HALF + 20, CY + laneOffset),  // stop line
            new Vector2D(CX, CY + laneOffset),
            new Vector2D(-20, CY + laneOffset)
        );
        return new VehiclePath("ew", wps, 1, "light-EW", "E", "W");
    }

    private VehiclePath makeWestEastPath() {
        double laneOffset = LANE_W / 2.0;
        List<Vector2D> wps = List.of(
            new Vector2D(-20, CY - laneOffset),
            new Vector2D(CX - ROAD_HALF - 20, CY - laneOffset),  // stop line
            new Vector2D(CX, CY - laneOffset),
            new Vector2D(CANVAS_W + 20, CY - laneOffset)
        );
        return new VehiclePath("we", wps, 1, "light-EW", "W", "E");
    }

    private VehiclePath getPathByDirection(int idx) {
        return switch (idx % 4) {
            case 0 -> makeNorthSouthPath();
            case 1 -> makeSouthNorthPath();
            case 2 -> makeEastWestPath();
            default -> makeWestEastPath();
        };
    }

    // ════════════════════════════════════════════════════════════════════
    //  Controls
    // ════════════════════════════════════════════════════════════════════

    private void togglePause() {
        if (engine.isRunning()) {
            engine.pause();
            btnStartPause.setText("▶ Tiếp tục");
            log("⏸ Đã tạm dừng.");
        } else {
            engine.resume();
            lastNano = 0;
            btnStartPause.setText("⏸ Tạm dừng");
            log("▶ Tiếp tục chạy.");
        }
    }

    private void resetSimulation() {
        engine.pause();
        initWorld();
        simTime = 0;
        spawnTimer = 0;
        totalSpawned.set(0);
        totalFinished.set(0);
        totalCrashed.set(0);
        spawnRoundRobin = 0;
        spawnDirectionRR = 0;
        engine = new SimulationEngine(world);
        engine.start();
        lastNano = 0;
        btnStartPause.setText("⏸ Tạm dừng");
        log("🔄 Đã đặt lại mô phỏng.");
    }

    // ════════════════════════════════════════════════════════════════════
    //  Logging
    // ════════════════════════════════════════════════════════════════════

    private void log(String msg) {
        String line = String.format("[%.1fs] %s%n", simTime, msg);
        Platform.runLater(() -> {
            logArea.appendText(line);
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  UI Helpers
    // ════════════════════════════════════════════════════════════════════

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        l.setTextFill(Color.web("#e2e8f0"));
        return l;
    }

    private Label styledLabel(String text, String color) {
        Label l = new Label(text);
        l.setTextFill(Color.web(color));
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        return l;
    }

    private Label createStatRow(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#64ffda"));
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        return l;
    }

    private Label lightIndicator(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        return l;
    }

    private Label labelSmall(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#94a3b8"));
        l.setFont(Font.font("Segoe UI", 11));
        return l;
    }

    private void styleButton(Button btn, String hex) {
        btn.setStyle(
            "-fx-background-color: " + hex + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 12;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 6;"
        );
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
    }

    private void styleCombo(ComboBox<String> cb) {
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setStyle(
            "-fx-background-color: #1e3a5f;" +
            "-fx-text-fill: #e2e8f0;" +
            "-fx-font-size: 12;" +
            "-fx-background-radius: 4;"
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  SimpleTrafficLight — concrete implementation
    // ════════════════════════════════════════════════════════════════════

    static class SimpleTrafficLight extends TrafficLight {
        private final LightTiming timing;

        SimpleTrafficLight(String id, LightColor startColor, LightTiming timing) {
            this.id = id;
            this.currentColor = startColor;
            this.timing = timing;
            this.remainingTime = switch (startColor) {
                case GREEN  -> timing.getGreenDuration();
                case YELLOW -> timing.getYellowDuration();
                case RED    -> timing.getRedDuration();
            };
        }

        void forceRed() {
            this.currentColor = LightColor.RED;
            this.remainingTime = timing.getRedDuration();
        }

        @Override
        protected void switchToNextColor() {
            currentColor = switch (currentColor) {
                case GREEN  -> { remainingTime = timing.getYellowDuration(); yield LightColor.YELLOW; }
                case YELLOW -> { remainingTime = timing.getRedDuration();    yield LightColor.RED;    }
                case RED    -> { remainingTime = timing.getGreenDuration();  yield LightColor.GREEN;  }
            };
        }

        @Override
        public boolean shouldShowCountdown() { return true; }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Entry point
    // ════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        launch(args);
    }
}
