package com.fileguard;

import javafx.application.Application;

/**
 * Plain entry point that does not extend {@link Application}.
 * Starting JavaFX through a separate class lets the app run from IntelliJ
 * (or a plain "java" command) with JavaFX on the normal classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(FileGuardApp.class, args);
    }
}
