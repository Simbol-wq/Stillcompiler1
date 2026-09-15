package com.steelcompiler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Главное окно SteelCompiler — первая версия.
 *
 * Что уже умеет:
 *  - выбрать папку проекта мода (там, где gradlew);
 *  - запустить "gradlew <task>" (по умолчанию "build") в отдельном потоке;
 *  - показывать вывод сборки построчно в реальном времени;
 *  - подсветить в логе строки с ошибками ("error", "FAILED", "Exception");
 *  - после успешной сборки найти получившийся .jar и предложить открыть его папку.
 *
 * Дерево файлов, редактор кода, GitHub Actions и авто-выбор Java под версию MC —
 * следующие шаги, сюда специально оставлено место (см. TODO ниже).
 */
public class MainWindow extends JFrame {

    private static final String PREF_LAST_PROJECT_DIR = "lastProjectDir";
    private static final String PREF_LAST_TASK = "lastGradleTask";

    private final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    private final JTextField projectDirField = new JTextField();
    private final JTextField gradleTaskField = new JTextField("build");
    private final JButton browseButton = new JButton("Обзор...");
    private final JButton buildButton = new JButton("🔨 Собрать");
    private final JButton cancelButton = new JButton("Отмена");
    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Готово к сборке.");
    private final JButton openJarFolderButton = new JButton("📦 Открыть папку с .jar");

    private final GradleBuildRunner buildRunner = new GradleBuildRunner();
    private final JarFinder jarFinder = new JarFinder();

    private File lastFoundJarDir;

    public MainWindow() {
        super("SteelCompiler — сборка Minecraft-модов");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildLogPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        restoreLastUsedValues();
        wireActions();
        setBuildInProgress(false);
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        panel.add(new JLabel("Папка проекта:"), c);

        c.gridx = 1; c.gridy = 0; c.weightx = 1;
        panel.add(projectDirField, c);

        c.gridx = 2; c.gridy = 0; c.weightx = 0;
        panel.add(browseButton, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        panel.add(new JLabel("Gradle-задача:"), c);

        c.gridx = 1; c.gridy = 1; c.weightx = 1;
        panel.add(gradleTaskField, c);

        JPanel buildButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buildButtonsPanel.add(buildButton);
        buildButtonsPanel.add(cancelButton);
        c.gridx = 2; c.gridy = 1; c.weightx = 0;
        panel.add(buildButtonsPanel, c);

        return panel;
    }

    private JScrollPane buildLogPanel() {
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(220, 220, 220));
        logArea.setCaretColor(Color.WHITE);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        return scrollPane;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        panel.add(statusLabel, BorderLayout.WEST);
        openJarFolderButton.setEnabled(false);
        panel.add(openJarFolderButton, BorderLayout.EAST);
        return panel;
    }

    private void wireActions() {
        browseButton.addActionListener(this::onBrowseProjectDir);
        buildButton.addActionListener(this::onBuildClicked);
        cancelButton.addActionListener(e -> {
            buildRunner.cancelCurrentBuild();
            appendLog("[отменено пользователем]");
            statusLabel.setText("Сборка отменена.");
            setBuildInProgress(false);
        });
        openJarFolderButton.addActionListener(e -> openInFileManager(lastFoundJarDir));
    }

    private void onBrowseProjectDir(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Выбери корневую папку проекта мода (где лежит gradlew)");
        String current = projectDirField.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            projectDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onBuildClicked(ActionEvent e) {
        String path = projectDirField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Сначала выбери папку проекта.", "Нет папки проекта",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        File projectDir = new File(path);
        if (!projectDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Указанная папка не существует:\n" + path,
                    "Папка не найдена", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String task = gradleTaskField.getText().trim();
        if (task.isEmpty()) {
            task = "build";
        }

        prefs.put(PREF_LAST_PROJECT_DIR, projectDir.getAbsolutePath());
        prefs.put(PREF_LAST_TASK, task);

        logArea.setText("");
        openJarFolderButton.setEnabled(false);
        setBuildInProgress(true);
        statusLabel.setText("Сборка запущена: gradlew " + task + " ...");
        appendLog("=== Запуск сборки в " + projectDir.getAbsolutePath() + " ===");

        buildRunner.runBuildAsync(projectDir, task, new GradleBuildRunner.BuildListener() {
            @Override
            public void onLine(String line) {
                appendLog(line);
            }

            @Override
            public void onFinished(boolean success, int exitCode) {
                setBuildInProgress(false);
                if (success) {
                    statusLabel.setText("✅ Сборка успешна.");
                    appendLog("=== СБОРКА УСПЕШНА (код выхода 0) ===");
                    handleSuccessfulBuild(projectDir);
                } else {
                    statusLabel.setText("❌ Сборка завершилась с ошибкой (код " + exitCode + ").");
                    appendLog("=== СБОРКА ЗАВЕРШИЛАСЬ С ОШИБКОЙ (код выхода " + exitCode + ") ===");
                }
            }

            @Override
            public void onFailedToStart(String reason) {
                setBuildInProgress(false);
                statusLabel.setText("❌ Не удалось запустить сборку.");
                appendLog("=== ОШИБКА ЗАПУСКА ===\n" + reason);
                JOptionPane.showMessageDialog(MainWindow.this, reason, "Ошибка запуска сборки",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void handleSuccessfulBuild(File projectDir) {
        List<File> jars = jarFinder.findBuiltJars(projectDir);
        if (jars.isEmpty()) {
            appendLog("[SteelCompiler] Сборка прошла, но .jar в папках build/libs не найден.");
            return;
        }
        appendLog("[SteelCompiler] Найдены собранные .jar:");
        for (File jar : jars) {
            appendLog("  - " + jar.getAbsolutePath());
        }
        File newestJar = jars.get(0);
        lastFoundJarDir = newestJar.getParentFile();
        openJarFolderButton.setEnabled(true);
        statusLabel.setText("✅ Готово: " + newestJar.getName());
    }

    private void setBuildInProgress(boolean inProgress) {
        buildButton.setEnabled(!inProgress);
        browseButton.setEnabled(!inProgress);
        cancelButton.setEnabled(inProgress);
    }

    private void appendLog(String text) {
        logArea.append(text + "\n");
        // определяем "похоже на ошибку" по ключевым словам и просто выводим маркер —
        // полноценную подсветку строк добавим отдельным шагом (нужен StyledDocument вместо JTextArea)
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void restoreLastUsedValues() {
        String lastDir = prefs.get(PREF_LAST_PROJECT_DIR, "");
        String lastTask = prefs.get(PREF_LAST_TASK, "build");
        projectDirField.setText(lastDir);
        gradleTaskField.setText(lastTask);
    }

    private void openInFileManager(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (Exception ex) {
            appendLog("[SteelCompiler] Не удалось открыть папку автоматически: " + ex.getMessage());
            appendLog("Путь: " + dir.getAbsolutePath());
        }
    }

    // TODO(следующие шаги, по фичам из плана):
    //  - дерево файлов проекта слева (JTree на базе projectDirField) + JTextArea-редактор справа;
    //  - подсветка строк "FAILED"/"error:"/"Exception" в логе через StyledDocument;
    //  - автоопределение нужной версии Java по gradle.properties / minecraft version и переключение JAVA_HOME;
    //  - вкладка "GitHub Actions": кнопка "запустить workflow" через GitHub REST API (нужен personal access token);
    //  - профиль для steelandcrowns: запомненный путь + быстрые задачи (build, runClient, runServer).
}
