package ars.home.fileserver.service;

import ars.home.fileserver.dto.BookFileDto;
import ars.home.fileserver.dto.FileOperationResultDto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Работа с каталогом книг: список файлов, архивирование, восстановление и удаление,
 * а также поиск файла для скачивания.
 */
@Slf4j
@Service
public class BookService {

    /**
     * Расширение, которое добавляется к имени файла при архивировании.
     * Файлы с этим расширением на вкладке «Книги» не отображаются.
     */
    public static final String ARCHIVE_EXTENSION = ".arch";

    private final Path booksDir;
    @Getter
    private final long refreshIntervalSeconds;

    public BookService(@Value("${fileserver.books-dir}") String booksDir,
                       @Value("${fileserver.refresh-interval-seconds}") long refreshIntervalSeconds) {
        this.booksDir = expandUserHome(booksDir).toAbsolutePath().normalize();
        this.refreshIntervalSeconds = Math.max(1, refreshIntervalSeconds);
        log.info("Books directory: {}, refresh interval: {}s", this.booksDir, this.refreshIntervalSeconds);
    }

    public List<BookFileDto> listBooks() {
        return listFiles(false);
    }

    /**
     * Возвращает файлы, перенесённые в архив (с расширением {@link #ARCHIVE_EXTENSION}).
     */
    public List<BookFileDto> listArchivedBooks() {
        return listFiles(true);
    }

    private List<BookFileDto> listFiles(boolean archived) {
        if (!Files.isDirectory(booksDir)) {
            log.debug("Books directory {} does not exist or is not a directory", booksDir);
            return List.of();
        }

        try (Stream<Path> paths = Files.list(booksDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isArchived(path.getFileName().toString()) == archived)
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BookFileDto::author, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(BookFileDto::title, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(BookFileDto::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list books directory {}", booksDir, e);
            return List.of();
        }
    }

    /**
     * Архивирует книги: к имени каждого файла добавляется расширение {@link #ARCHIVE_EXTENSION}.
     *
     * @param names имена файлов, полученные из списка книг
     * @return количество успешно обработанных и неудачных файлов
     */
    public FileOperationResultDto archiveBooks(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new FileOperationResultDto(0, 0);
        }

        int archived = 0;
        int failed = 0;

        for (String name : new LinkedHashSet<>(names)) {
            if (name == null || name.isBlank() || isArchived(name)) {
                failed++;
                continue;
            }

            Optional<Path> book = findBook(name);
            if (book.isEmpty()) {
                log.warn("Cannot archive unknown book: {}", name);
                failed++;
                continue;
            }

            Path source = book.get();
            Path target = source.resolveSibling(source.getFileName().toString() + ARCHIVE_EXTENSION);
            try {
                Files.move(source, target);
                archived++;
            } catch (IOException e) {
                log.warn("Failed to archive {}", source, e);
                failed++;
            }
        }

        log.info("Archived books: {}, failed: {}", archived, failed);
        return new FileOperationResultDto(archived, failed);
    }

    /**
     * Восстанавливает архивированные книги: удаляет расширение {@link #ARCHIVE_EXTENSION}
     * из имени каждого файла.
     *
     * @param names имена архивных файлов
     * @return количество успешно обработанных и неудачных файлов
     */
    public FileOperationResultDto restoreBooks(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new FileOperationResultDto(0, 0);
        }

        int restored = 0;
        int failed = 0;

        for (String name : new LinkedHashSet<>(names)) {
            Optional<Path> book = findArchivedBook(name);
            if (book.isEmpty()) {
                log.warn("Cannot restore unknown archive file: {}", name);
                failed++;
                continue;
            }

            Path source = book.get();
            String fileName = source.getFileName().toString();
            String targetName = fileName.substring(0, fileName.length() - ARCHIVE_EXTENSION.length());
            try {
                Files.move(source, source.resolveSibling(targetName));
                restored++;
            } catch (IOException e) {
                log.warn("Failed to restore {}", source, e);
                failed++;
            }
        }

        log.info("Restored books: {}, failed: {}", restored, failed);
        return new FileOperationResultDto(restored, failed);
    }

    /**
     * Удаляет архивированные книги из файловой системы.
     *
     * @param names имена архивных файлов
     * @return количество успешно обработанных и неудачных файлов
     */
    public FileOperationResultDto deleteBooks(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new FileOperationResultDto(0, 0);
        }

        int deleted = 0;
        int failed = 0;

        for (String name : new LinkedHashSet<>(names)) {
            Optional<Path> book = findArchivedBook(name);
            if (book.isEmpty()) {
                log.warn("Cannot delete unknown archive file: {}", name);
                failed++;
                continue;
            }

            try {
                Files.delete(book.get());
                deleted++;
            } catch (IOException e) {
                log.warn("Failed to delete {}", book.get(), e);
                failed++;
            }
        }

        log.info("Deleted archived books: {}, failed: {}", deleted, failed);
        return new FileOperationResultDto(deleted, failed);
    }

    /**
     * Проверяет, что файл уже перенесён в архив и не должен показываться в списке книг.
     */
    public static boolean isArchived(String fileName) {
        return fileName != null
                && fileName.toLowerCase(Locale.ROOT).endsWith(ARCHIVE_EXTENSION);
    }

    private Optional<Path> findArchivedBook(String name) {
        if (name == null || name.isBlank() || !isArchived(name)) {
            return Optional.empty();
        }
        return findBook(name);
    }

    private static String stripArchiveExtension(String fileName) {
        return isArchived(fileName)
                ? fileName.substring(0, fileName.length() - ARCHIVE_EXTENSION.length())
                : fileName;
    }

    /**
     * Возвращает файл только в том случае, если он находится непосредственно в каталоге книг.
     * Это защищает от выхода за пределы каталога через {@code ..} или абсолютные пути.
     */
    public Optional<Path> findBook(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        try {
            Path candidate = booksDir.resolve(name).normalize();
            if (!booksDir.equals(candidate.getParent()) || !Files.isRegularFile(candidate)) {
                return Optional.empty();
            }
            return Optional.of(candidate);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private BookFileDto toDto(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            String fileName = path.getFileName().toString();
            String baseName = stripExtension(stripArchiveExtension(fileName));

            String author = "";
            String title = baseName;
            int separatorIndex = baseName.indexOf('-');
            if (separatorIndex >= 0) {
                author = formatAuthor(baseName.substring(0, separatorIndex));
                title = baseName.substring(separatorIndex + 1).trim();
                if (title.isEmpty()) {
                    title = baseName;
                }
            }

            return new BookFileDto(
                    fileName,
                    author,
                    title,
                    attributes.size(),
                    attributes.lastModifiedTime().toMillis());
        } catch (IOException e) {
            log.warn("Failed to read attributes of {}", path, e);
            return null;
        }
    }

    /**
     * Имя файла имеет вид {@code Фамилия_Инициал_Инициал-Название книги.zip}.
     * Возвращает имя без расширения.
     */
    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * Преобразует часть имени до дефиса в вид {@code Фамилия И. И.}.
     */
    private static String formatAuthor(String rawAuthor) {
        String[] parts = rawAuthor.trim().split("_");
        if (parts.length == 0 || parts[0].isBlank()) {
            return "";
        }

        StringBuilder author = new StringBuilder(parts[0].trim());
        for (int i = 1; i < parts.length; i++) {
            String initial = parts[i].trim();
            if (!initial.isEmpty()) {
                author.append(' ').append(Character.toUpperCase(initial.charAt(0))).append('.');
            }
        }
        return author.toString();
    }

    private static Path expandUserHome(String value) {
        String path = value == null ? "" : value.trim();
        if (path.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home"), path.substring(2));
        }
        return Path.of(path);
    }
}
