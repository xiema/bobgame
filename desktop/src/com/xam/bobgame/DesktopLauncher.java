package com.xam.bobgame;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.xam.bobgame.BoBGame;
import com.badlogic.gdx.backends.headless.HeadlessApplication;

import java.util.HashMap;
import java.util.Map;

// Please note that on macOS your application needs to be started with the -XstartOnFirstThread JVM argument
public class DesktopLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT);
		config.setForegroundFPS(60);
		config.setTitle("BoBGame");

		Map<String,String> runArgs = new HashMap<>();
		int i = 0;
		while (i < arg.length) {
			switch (arg[i]) {
				case "-h":
					runArgs.put("headless", "");
					break;
				case "-s":
					runArgs.put("server", "");
					break;
				case "-c":
					runArgs.put("client", "");
					break;
			}
			i++;
		}

		if (runArgs.containsKey("headless")) {
			new HeadlessApplication(new BoBGame(runArgs));
		}
		else {
			new Lwjgl3Application(new BoBGame(runArgs), config);
		}
	}
}
