package com.fileguard;

import com.fileguard.db.Database;
import com.fileguard.service.ScanService;
import com.fileguard.ui.AppContext;
import com.fileguard.ui.MainWindow;
import com.fileguard.ui.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/** JavaFX application class: opens the database and shows the main window. */
public class FileGuardApp extends Application {

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

        ThemeManager themeManager = new ThemeManager();
        MainWindow mainWindow = new MainWindow(new AppContext(new ScanService(database), database, themeManager, getHostServices()));

        Scene scene = new Scene(mainWindow, 1240, 780);
        themeManager.register(scene);

        stage.setTitle("FileGuard");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }
}
