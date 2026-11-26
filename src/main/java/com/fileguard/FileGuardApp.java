package com.fileguard;

import com.fileguard.client.BackendClient;
import com.fileguard.db.Database;
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
 *  2. connect to the backend on localhost; if none is running, start one inside this process,
 *  3. show the main window.
 */
public class FileGuardApp extends Application {

    private BackendServer embeddedBackend;
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

        backend = connectBackend();
        backend.info("APP", "FileGuard started" + (embeddedBackend != null ? " (backend started by the app)" : ""));

        ThemeManager themeManager = new ThemeManager();
        themeManager.themeProperty().addListener((obs, old, now) ->
                backend.info("SETTINGS", "Theme changed to " + now.name().toLowerCase()));
        AppContext ctx = new AppContext(new ScanService(database, backend), database, themeManager,
                getHostServices(), backend);
        MainWindow mainWindow = new MainWindow(ctx);

        Scene scene = new Scene(mainWindow, 1280, 800);
        themeManager.register(scene);

        stage.setTitle("FileGuard");
        stage.setMinWidth(1040);
        stage.setMinHeight(660);
        try (InputStream icon = getClass().getResourceAsStream("/com/fileguard/icon.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        } catch (Exception ignored) {
            // The window icon is optional.
        }
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Uses an already running backend (for example "FileGuard Backend.exe") if there is one,
     * otherwise starts the backend inside the app. If neither works, the app still runs
     * and queues its logs until a backend becomes available.
     */
    private BackendClient connectBackend() {
        int port = Integer.getInteger("fileguard.backend.port", BackendServer.DEFAULT_PORT);
        BackendClient client = BackendClient.forPort(port);
        if (!client.checkHealth()) {
            try {
                embeddedBackend = BackendServer.start(port, BackendServer.defaultDbFile());
                client.checkHealth();
            } catch (Exception e) {
                System.err.println("FileGuard backend could not be started on port " + port + ": " + e.getMessage());
            }
        }
        client.startMonitoring();
        return client;
    }

    @Override
    public void stop() {
        if (backend != null) {
            backend.info("APP", "FileGuard closed");
            try {
                Thread.sleep(300); // give the last async log a moment to reach the backend
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            backend.close();
        }
        if (embeddedBackend != null) {
            embeddedBackend.stop();
        }
    }
}
