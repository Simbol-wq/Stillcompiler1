package com.steelcompiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Запускает "./gradlew build" (или gradlew.bat на Windows) в указанной папке проекта
 * и построчно отдаёт вывод через колбэк. Сам процесс выполняется в отдельном потоке,
 * чтобы не блокировать GUI.
 */
public class GradleBuildRunner {

    public interface BuildListener {
        /** Вызывается на каждую новую строку лога сборки (уже в потоке Swing/EDT). */
        void onLine(String line);

        /** Вызывается один раз по завершении сборки (в потоке Swing/EDT). */
        void onFinished(boolean success, int exitCode);

        /** Вызывается, если процесс вообще не удалось запустить (например, gradlew не найден). */
        void onFailedToStart(String reason);
    }

    private Process currentProcess;

    /**
     * @param projectDir папка проекта мода (там, где лежит gradlew / gradlew.bat)
     * @param gradleTask задача Gradle, например "build" или "build --offline"
     * @param listener   колбэки для UI
     */
    public void runBuildAsync(File projectDir, String gradleTask, BuildListener listener) {
        Thread worker = new Thread(() -> runBuildBlocking(projectDir, gradleTask, listener), "gradle-build-thread");
        worker.setDaemon(true);
        worker.start();
    }

    private void runBuildBlocking(File projectDir, String gradleTask, BuildListener listener) {
        File wrapperScript = resolveWrapperScript(projectDir);
        if (wrapperScript == null) {
            notify(() -> listener.onFailedToStart(
                    "Не найден gradlew/gradlew.bat в папке проекта: " + projectDir.getAbsolutePath()
                            + "\nУбедись, что выбрана корневая папка мода (там же, где build.gradle)."));
            return;
        }

        List<String> command = buildCommand(wrapperScript, gradleTask);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir);
        pb.redirectErrorStream(true); // сливаем stderr в тот же поток, что и stdout — проще читать один лог

        try {
            currentProcess = pb.start();
        } catch (IOException e) {
            notify(() -> listener.onFailedToStart("Не удалось запустить процесс сборки: " + e.getMessage()));
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String finalLine = line;
                notify(() -> listener.onLine(finalLine));
            }
        } catch (IOException e) {
            notify(() -> listener.onLine("[ошибка чтения вывода сборки] " + e.getMessage()));
        }

        int exitCode;
        try {
            exitCode = currentProcess.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exitCode = -1;
        }

        boolean success = exitCode == 0;
        int finalExitCode = exitCode;
        notify(() -> listener.onFinished(success, finalExitCode));
    }

    /** Принудительно останавливает текущую сборку, если она идёт. */
    public void cancelCurrentBuild() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
        }
    }

    private File resolveWrapperScript(File projectDir) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        File script = isWindows
                ? new File(projectDir, "gradlew.bat")
                : new File(projectDir, "gradlew");
        return script.isFile() ? script : null;
    }

    private List<String> buildCommand(File wrapperScript, String gradleTask) {
        List<String> command = new ArrayList<>();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            command.add(wrapperScript.getAbsolutePath());
        } else {
            // на Unix нужно явно дать право на выполнение и вызвать через путь
            wrapperScript.setExecutable(true);
            command.add(wrapperScript.getAbsolutePath());
        }
        // gradleTask может содержать несколько слов/флагов, например "build --stacktrace"
        for (String part : gradleTask.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                command.add(part);
            }
        }
        command.add("--console=plain"); // без ANSI-прогрессбаров, которые плохо смотрятся в JTextArea
        return command;
    }

    private void notify(Runnable r) {
        javax.swing.SwingUtilities.invokeLater(r);
    }
}
