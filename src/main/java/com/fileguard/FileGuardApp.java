package com.fileguard;

import com.fileguard.client.BackendClient;
import com.fileguard.db.Database;
import com.fileguard.model.LogLevel;
import com.fileguard.server.BackendServer;
import com.fileguard.service.ScanService;
import com.fileguard.ui.AppContext;
import com.fileguard.ui.MainWindow;
import com.fileguard.ui.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * JavaFX application class.
 *
 * Start-up order:
 *  1. open the local SQLite database (baselines and scan history),
 *  2. show the main window,
 *  3. in the background: connect to the backend on localhost, or start one inside this
 *     process if none is running. Logs written before that finishes are queued.
 */
public class FileGuardApp extends Application {

    private static final double WINDOW_WIDTH = 1280;
    private static final double WINDOW_HEIGHT = 800;
    private static final double MIN_WIDTH = 1040;
    private static final double MIN_HEIGHT = 660;

    private volatile BackendServer embeddedBackend;
    private BackendClient backend;

    @Override
    public void start(Stage stage) {
        Database database;
        try {
            database = Database.openDefault();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "FileGuard could not open its local database.\n\n" + e.getMessage());
            alert.setHeaderText("Database error");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        int port = Integer.getInteger("fileguard.backend.port", BackendServer.DEFAULT_PORT);
        backend = BackendClient.forPort(port);
        backend.info("APP", AppInfo.NAME + " " + AppInfo.VERSION + " started"); // queued until connected

        ThemeManager themeManager = new ThemeManager();
        themeManager.themeProperty().addListener((obs, old, now) ->
                backend.info("SETTINGS", "Theme changed to " + now.name().toLowerCase()));
        AppContext ctx = new AppContext(new ScanService(database, backend), database, themeManager,
                getHostServices(), backend);

        Scene scene = new Scene(new MainWindow(ctx), WINDOW_WIDTH, WINDOW_HEIGHT);
        themeManager.register(scene);
        stage.setTitle(AppInfo.NAME);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        loadIcon(stage);
        stage.setScene(scene);
        stage.show();

        Thread connector = new Thread(() -> connectBackend(port), "fileguard-backend-connect");
        connector.setDaemon(true);
        connector.start();
    }

    /**
     * Uses an already running backend (for example "FileGuard Backend.exe") if there is one,
     * otherwise starts the backend inside the app. If neither works, the app still runs and
     * keeps its logs queued until a backend becomes available. Runs on a background thread.
     */
    private void connectBackend(int port) {
        if (!backend.checkHealth()) {
            try {
                embeddedBackend = BackendServer.start(port, BackendServer.defaultDbFile());
            } catch (Exception e) {
                System.err.println("FileGuard backend could not be started on port " + port + ": " + e.getMessage());
            }
        }
        backend.startMonitoring(); // first health check runs immediately and sends queued logs
    }

    private void loadIcon(Stage stage) {
        try (InputStream icon = getClass().getResourceAsStream("/com/fileguard/icon.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        } catch (Exception e) {
            System.err.println("Window icon could not be loaded: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (backend != null) {
            backend.logAndWait(LogLevel.INFO, "APP", AppInfo.NAME + " closed");
            backend.close();
        }
        if (embeddedBackend != null) {
            embeddedBackend.stop();
        }
    }
}
