package com.steelcompiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ищет собранные .jar файлы в стандартных выходных папках Gradle (build/libs)
 * по всему дереву проекта — на случай мультимодульной сборки (например, отдельный
 * модуль для steelandcrowns).
 */
public class JarFinder {

    /** @return список найденных jar-файлов, отсортированный по времени изменения (новые первыми) */
    public List<File> findBuiltJars(File projectDir) {
        List<File> result = new ArrayList<>();
        Path root = projectDir.toPath();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    // не лезем в служебные и кэш-папки — там могут быть чужие/промежуточные jar-ы
                    if (name.equals(".gradle") || name.equals(".git") || name.equals("node_modules")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path parent = file.getParent();
                    if (parent != null && parent.getFileName() != null
                            && parent.getFileName().toString().equals("libs")
                            && file.toString().endsWith(".jar")) {
                        result.add(file.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // если что-то пошло не так при обходе — просто вернём то, что успели найти
        }
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }
}
