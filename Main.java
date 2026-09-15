package com.steelcompiler;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Точка входа SteelCompiler.
 * Первая версия: выбор папки проекта мода, кнопка "Собрать" (запускает gradlew build),
 * окно логов сборки в реальном времени, поиск собранного .jar после успешной сборки.
 */
public class Main {
    public static void main(String[] args) {
        // Используем системный Look&Feel, чтобы окно не выглядело как Java 1.0
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // если не получилось — остаёмся на дефолтном Metal, не критично
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
