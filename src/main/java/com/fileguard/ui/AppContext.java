package com.fileguard.ui;

import com.fileguard.db.Database;
import com.fileguard.service.ScanService;
import javafx.application.HostServices;

/** Shared objects that every page needs, passed around as one parameter. */
public record AppContext(ScanService scanService, Database database, ThemeManager themeManager, HostServices hostServices) {
}
