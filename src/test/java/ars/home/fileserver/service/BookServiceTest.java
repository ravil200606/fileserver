package ars.home.fileserver.service;

import ars.home.fileserver.dto.BookFileDto;
import ars.home.fileserver.dto.FileOperationResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookServiceTest {

    @TempDir
    Path tempDir;

    private Path booksDir;
    private Path outsideDir;
    private BookService service;

    @BeforeEach
    void setUp() throws IOException {
        booksDir = Files.createDirectories(tempDir.resolve("books"));
        outsideDir = Files.createDirectories(tempDir.resolve("outside"));
        service = new BookService(booksDir.toString(), 5);
    }

    @Test
    void listBooksReturnsOnlyRegularNonArchivedFilesDirectlyInBooksDirectory() throws IOException {
        writeFile(booksDir, "Ivanov_I-Book.zip", "book");
        writeFile(booksDir, "NoConvention.epub", "book");
        writeFile(booksDir, "Old.zip.arch", "archived");
        writeFile(booksDir, "nested/Nested.zip", "nested");
        Files.createDirectory(booksDir.resolve("Folder.arch"));

        List<String> names = namesOf(service.listBooks());

        assertEquals(2, names.size());
        assertTrue(names.contains("Ivanov_I-Book.zip"));
        assertTrue(names.contains("NoConvention.epub"));
        assertFalse(names.contains("Old.zip.arch"));
        assertFalse(names.contains("Nested.zip"));
        assertFalse(names.contains("Folder.arch"));
    }

    @Test
    void listBooksReturnsEmptyWhenBooksDirectoryDoesNotExist() {
        BookService missingDirectoryService =
                new BookService(tempDir.resolve("missing").toString(), 5);

        assertTrue(missingDirectoryService.listBooks().isEmpty());
        assertTrue(missingDirectoryService.listArchivedBooks().isEmpty());
    }

    @Test
    void listBooksReportsFileSizeAndModificationTime() throws IOException {
        Path book = writeFile(booksDir, "Ivanov_I-Book.zip", "12345");

        BookFileDto dto = service.listBooks().get(0);

        assertEquals(5, dto.size());
        assertEquals(Files.getLastModifiedTime(book).toMillis(), dto.modified());
    }

    @Test
    void listBooksSortsByAuthorTitleAndNameCaseInsensitively() throws IOException {
        writeFile(booksDir, "Zeta.zip", "book");
        writeFile(booksDir, "alpha.zip", "book");
        writeFile(booksDir, "Ivanov_I-Zeta.zip", "book");
        writeFile(booksDir, "Ivanov_I-Alpha.zip", "book");
        writeFile(booksDir, "Petrov_P-Any.zip", "book");

        assertEquals(
                List.of(
                        "alpha.zip",
                        "Zeta.zip",
                        "Ivanov_I-Alpha.zip",
                        "Ivanov_I-Zeta.zip",
                        "Petrov_P-Any.zip"),
                namesOf(service.listBooks()));
    }

    @Test
    void listArchivedBooksReturnsOnlyArchivedRegularFiles() throws IOException {
        writeFile(booksDir, "Old.zip.arch", "archived");
        writeFile(booksDir, "Upper.zip.ARCH", "archived");
        writeFile(booksDir, "Book.zip", "book");
        Files.createDirectory(booksDir.resolve("Folder.arch"));

        Set<String> archivedNames = new HashSet<>(namesOf(service.listArchivedBooks()));

        assertEquals(Set.of("Old.zip.arch", "Upper.zip.ARCH"), archivedNames);
        assertEquals(List.of("Book.zip"), namesOf(service.listBooks()));
    }

    @Test
    void listArchivedBooksDerivesMetadataAfterRemovingArchiveSuffix() throws IOException {
        writeFile(booksDir, "Толстой_Л_Н-Анна Каренина.zip.arch", "book");

        BookFileDto archived = service.listArchivedBooks().get(0);

        assertEquals("Толстой_Л_Н-Анна Каренина.zip.arch", archived.name());
        assertEquals("Толстой Л. Н.", archived.author());
        assertEquals("Анна Каренина", archived.title());
    }

    @Test
    void convertsConventionalFilenameToAuthorAndTitle() throws IOException {
        writeFile(booksDir, "Толстой_Л_Н-Анна Каренина.zip", "book");

        BookFileDto book = service.listBooks().get(0);

        assertEquals("Толстой_Л_Н-Анна Каренина.zip", book.name());
        assertEquals("Толстой Л. Н.", book.author());
        assertEquals("Анна Каренина", book.title());
    }

    @Test
    void uppercasesAuthorInitialsFromConventionalFilename() throws IOException {
        writeFile(booksDir, "Ivanov_i_p-Book.zip", "book");

        BookFileDto book = service.listBooks().get(0);

        assertEquals("Ivanov I. P.", book.author());
        assertEquals("Book", book.title());
    }

    @Test
    void treatsOnlyFirstHyphenAsAuthorTitleSeparator() throws IOException {
        writeFile(booksDir, "Ivanov_I-War-and-Peace.zip", "book");

        BookFileDto book = service.listBooks().get(0);

        assertEquals("Ivanov I.", book.author());
        assertEquals("War-and-Peace", book.title());
    }

    @Test
    void fallsBackToFileStemWhenFilenameDoesNotFollowConvention() throws IOException {
        writeFile(booksDir, "NoConventionFile", "book");
        writeFile(booksDir, "book.name.with.dots.zip", "book");
        writeFile(booksDir, "-Title.zip", "book");

        List<BookFileDto> books = service.listBooks();

        BookFileDto noExtension = byName(books, "NoConventionFile");
        assertEquals("", noExtension.author());
        assertEquals("NoConventionFile", noExtension.title());

        BookFileDto multipleDots = byName(books, "book.name.with.dots.zip");
        assertEquals("", multipleDots.author());
        assertEquals("book.name.with.dots", multipleDots.title());

        BookFileDto leadingHyphen = byName(books, "-Title.zip");
        assertEquals("", leadingHyphen.author());
        assertEquals("Title", leadingHyphen.title());
    }

    @Test
    void fallsBackToFullStemWhenTitleAfterHyphenIsEmpty() throws IOException {
        writeFile(booksDir, "Ivanov_I-.zip", "book");

        BookFileDto book = service.listBooks().get(0);

        assertEquals("Ivanov I.", book.author());
        assertEquals("Ivanov_I-", book.title());
    }

    @Test
    void detectsArchiveSuffixCaseInsensitively() {
        assertTrue(BookService.isArchived("book.zip.arch"));
        assertTrue(BookService.isArchived("book.zip.ARCH"));
        assertTrue(BookService.isArchived(".arch"));
        assertFalse(BookService.isArchived("book.zip"));
        assertFalse(BookService.isArchived("book.arch.zip"));
        assertFalse(BookService.isArchived(null));
    }

    @Test
    void findBookRejectsNamesThatResolveOutsideBooksDirectory() throws IOException {
        Path outsideBook = writeFile(outsideDir, "secret.zip", "secret");

        assertTrue(service.findBook("../outside/secret.zip").isEmpty());
        assertTrue(service.findBook(outsideBook.toAbsolutePath().toString()).isEmpty());
        assertTrue(service.findBook("../../etc/passwd").isEmpty());
    }

    @Test
    void findBookRejectsNestedPathsDirectoriesMissingFilesAndBlankNames() throws IOException {
        writeFile(booksDir, "book.zip", "book");
        writeFile(booksDir, "sub/nested.zip", "nested");
        Files.createDirectory(booksDir.resolve("folder.zip"));

        assertTrue(service.findBook("sub/nested.zip").isEmpty());
        assertTrue(service.findBook("folder.zip").isEmpty());
        assertTrue(service.findBook("missing.zip").isEmpty());
        assertTrue(service.findBook("").isEmpty());
        assertTrue(service.findBook("   ").isEmpty());
        assertTrue(service.findBook(null).isEmpty());
        assertEquals(booksDir.resolve("book.zip"), service.findBook("book.zip").orElseThrow());
    }

    @Test
    void getBookUsesSamePathRestrictionsAsFindBook() throws IOException {
        writeFile(booksDir, "Ivanov_I-Book.zip", "book");
        writeFile(outsideDir, "secret.zip", "secret");

        assertTrue(service.getBook("../outside/secret.zip").isEmpty());

        BookFileDto book = service.getBook("Ivanov_I-Book.zip").orElseThrow();
        assertEquals("Ivanov I.", book.author());
        assertEquals("Book", book.title());
    }

    @Test
    void archiveBooksRenamesBookByAppendingArchiveSuffix() throws IOException {
        Path book = writeFile(booksDir, "Ivanov_I-Book.zip", "content");

        FileOperationResultDto result = service.archiveBooks(List.of("Ivanov_I-Book.zip"));

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(book));
        Path archived = booksDir.resolve("Ivanov_I-Book.zip.arch");
        assertTrue(Files.isRegularFile(archived));
        assertEquals("content", readFile(archived));
        assertTrue(service.listBooks().isEmpty());
        assertEquals(List.of("Ivanov_I-Book.zip.arch"), namesOf(service.listArchivedBooks()));
    }

    @Test
    void archiveBooksProcessesEachDistinctNameAndCountsFailures() throws IOException {
        Path first = writeFile(booksDir, "A.zip", "a");
        Path second = writeFile(booksDir, "B.zip", "b");

        FileOperationResultDto result = service.archiveBooks(
                List.of("A.zip", "A.zip", "B.zip", "missing.zip"));

        assertEquals(2, result.succeeded());
        assertEquals(1, result.failed());
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
        assertTrue(Files.isRegularFile(booksDir.resolve("A.zip.arch")));
        assertTrue(Files.isRegularFile(booksDir.resolve("B.zip.arch")));
    }

    @Test
    void archiveBooksProcessesDuplicateNamesOnlyOnce() throws IOException {
        Path book = writeFile(booksDir, "Book.zip", "content");

        FileOperationResultDto result = service.archiveBooks(
                List.of("Book.zip", "Book.zip", "Book.zip"));

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(book));
        assertEquals(List.of("Book.zip.arch"), namesOf(service.listArchivedBooks()));
        assertEquals("content", readFile(booksDir.resolve("Book.zip.arch")));
    }

    @Test
    void archiveBooksRejectsArchivedBlankNullAndUnknownNames() throws IOException {
        Path archived = writeFile(booksDir, "Book.zip.arch", "archived");

        FileOperationResultDto result = service.archiveBooks(
                Arrays.asList("Book.zip.arch", null, "   ", "missing.zip"));

        assertEquals(0, result.succeeded());
        assertEquals(4, result.failed());
        assertTrue(Files.isRegularFile(archived));
    }

    @Test
    void archiveBooksDoesNotTouchFilesOutsideBooksDirectory() throws IOException {
        Path outsideBook = writeFile(outsideDir, "secret.zip", "secret");

        FileOperationResultDto result = service.archiveBooks(List.of("../outside/secret.zip"));

        assertEquals(0, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals("secret", readFile(outsideBook));
        assertFalse(Files.exists(outsideDir.resolve("secret.zip.arch")));
    }

    @Test
    void archiveBooksFailsWhenArchiveTargetAlreadyExists() throws IOException {
        Path source = writeFile(booksDir, "Book.zip", "new");
        Path target = writeFile(booksDir, "Book.zip.arch", "old");

        FileOperationResultDto result = service.archiveBooks(List.of("Book.zip"));

        assertEquals(0, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals("new", readFile(source));
        assertEquals("old", readFile(target));
    }

    @Test
    void restoreBooksRemovesArchiveSuffixAndPreservesContent() throws IOException {
        Path archived = writeFile(booksDir, "Ivanov_I-Book.zip.arch", "content");

        FileOperationResultDto result = service.restoreBooks(List.of("Ivanov_I-Book.zip.arch"));

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(archived));
        Path restored = booksDir.resolve("Ivanov_I-Book.zip");
        assertTrue(Files.isRegularFile(restored));
        assertEquals("content", readFile(restored));
        assertEquals(List.of("Ivanov_I-Book.zip"), namesOf(service.listBooks()));
        assertTrue(service.listArchivedBooks().isEmpty());
    }

    @Test
    void restoreBooksProcessesDuplicateNamesOnlyOnce() throws IOException {
        Path archived = writeFile(booksDir, "Book.zip.arch", "content");

        FileOperationResultDto result = service.restoreBooks(
                List.of("Book.zip.arch", "Book.zip.arch", "Book.zip.arch"));

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(archived));
        assertEquals("content", readFile(booksDir.resolve("Book.zip")));
        assertTrue(service.listArchivedBooks().isEmpty());
    }

    @Test
    void restoreBooksRefusesNonArchivedFiles() throws IOException {
        Path book = writeFile(booksDir, "Book.zip", "content");

        FileOperationResultDto result = service.restoreBooks(List.of("Book.zip"));

        assertEquals(0, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals("content", readFile(book));
        assertFalse(Files.exists(booksDir.resolve("Book.zip.arch")));
    }

    @Test
    void restoreBooksRejectsUnknownBlankAndNullNames() {
        FileOperationResultDto result = service.restoreBooks(
                Arrays.asList(null, "   ", "missing.zip.arch"));

        assertEquals(0, result.succeeded());
        assertEquals(3, result.failed());
    }

    @Test
    void restoreBooksDoesNotTouchFilesOutsideBooksDirectory() throws IOException {
        Path outsideArchive = writeFile(outsideDir, "secret.zip.arch", "secret");

        FileOperationResultDto result = service.restoreBooks(List.of("../outside/secret.zip.arch"));

        assertEquals(0, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals("secret", readFile(outsideArchive));
        assertFalse(Files.exists(outsideDir.resolve("secret.zip")));
    }

    @Test
    void restoreBooksFailsWhenRestoredTargetAlreadyExists() throws IOException {
        Path source = writeFile(booksDir, "Book.zip.arch", "archived");
        Path target = writeFile(booksDir, "Book.zip", "existing");

        FileOperationResultDto result = service.restoreBooks(List.of("Book.zip.arch"));

        assertEquals(0, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals("archived", readFile(source));
        assertEquals("existing", readFile(target));
    }

    @Test
    void deleteBooksDeletesArchivedRegularFiles() throws IOException {
        Path archived = writeFile(booksDir, "Book.zip.arch", "archived");

        FileOperationResultDto result = service.deleteBooks(List.of("Book.zip.arch"));

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(archived));
        assertTrue(service.listArchivedBooks().isEmpty());
    }

    @Test
    void deleteBooksProcessesDuplicateNamesOnlyOnce() throws IOException {
        Path archived = writeFile(booksDir, "Book.zip.arch", "archived");

        FileOperationResultDto result = service.deleteBooks(
                List.of("Book.zip.arch", "Book.zip.arch", "Book.zip.arch"));

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(archived));
        assertTrue(service.listArchivedBooks().isEmpty());
    }

    @Test
    void deleteBooksRefusesNonArchivedFiles() throws IOException {
        Path book = writeFile(booksDir, "Book.zip", "book");

        FileOperationResultDto result = service.deleteBooks(List.of("Book.zip"));

        assertEquals(0, result.succeeded());
        assertEquals(1, result.failed());
        assertTrue(Files.isRegularFile(book));
    }

    @Test
    void deleteBooksDeletesOnlyArchivedFilesInMixedRequest() throws IOException {
        Path archived = writeFile(booksDir, "Archived.zip.arch", "archived");
        Path book = writeFile(booksDir, "Book.zip", "book");

        FileOperationResultDto result = service.deleteBooks(
                List.of("Archived.zip.arch", "Book.zip"));

        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        assertFalse(Files.exists(archived));
        assertTrue(Files.isRegularFile(book));
    }

    @Test
    void deleteBooksRejectsUnknownBlankNullAndDirectoryNames() throws IOException {
        Path directory = Files.createDirectory(booksDir.resolve("Folder.arch"));

        FileOperationResultDto result = service.deleteBooks(
                Arrays.asList(null, "   ", "missing.zip.arch", "Folder.arch"));

        assertEquals(0, result.succeeded());
        assertEquals(4, result.failed());
        assertTrue(Files.isDirectory(directory));
    }

    @Test
    void deleteBooksDoesNotTouchFilesOutsideBooksDirectory() throws IOException {
        Path outsideArchive = writeFile(outsideDir, "secret.zip.arch", "secret");

        FileOperationResultDto result = service.deleteBooks(List.of("../outside/secret.zip.arch"));

        assertEquals(0, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals("secret", readFile(outsideArchive));
    }

    @Test
    void listBooksByAuthorReturnsBooksByMatchingSurname() throws IOException {
        writeFile(booksDir, "Ivanov_I-Book One.zip", "book");
        writeFile(booksDir, "Ivanov_P-Book Two.zip", "book");
        writeFile(booksDir, "Petrov_P-Another.zip", "book");

        List<String> names = namesOf(service.listBooksByAuthor("Ivanov"));

        assertEquals(2, names.size());
        assertTrue(names.contains("Ivanov_I-Book One.zip"));
        assertTrue(names.contains("Ivanov_P-Book Two.zip"));
        assertFalse(names.contains("Petrov_P-Another.zip"));
    }

    @Test
    void listBooksByAuthorMatchesSurnameCaseInsensitively() throws IOException {
        writeFile(booksDir, "Толстой_Л_Н-Анна Каренина.zip", "book");

        assertFalse(service.listBooksByAuthor("толстой").isEmpty());
        assertFalse(service.listBooksByAuthor("ТОЛСТОЙ").isEmpty());
        assertFalse(service.listBooksByAuthor("Толстой").isEmpty());
    }

    @Test
    void listBooksByAuthorReturnsEmptyForUnknownSurname() throws IOException {
        writeFile(booksDir, "Ivanov_I-Book.zip", "book");

        assertTrue(service.listBooksByAuthor("Petrov").isEmpty());
    }

    @Test
    void listBooksByAuthorReturnsEmptyForNullOrBlankSurname() throws IOException {
        writeFile(booksDir, "Ivanov_I-Book.zip", "book");

        assertTrue(service.listBooksByAuthor(null).isEmpty());
        assertTrue(service.listBooksByAuthor("").isEmpty());
        assertTrue(service.listBooksByAuthor("   ").isEmpty());
    }

    @Test
    void listBooksByAuthorExcludesArchivedBooks() throws IOException {
        writeFile(booksDir, "Ivanov_I-Book.zip", "book");
        writeFile(booksDir, "Ivanov_I-Old.zip.arch", "archived");

        List<String> names = namesOf(service.listBooksByAuthor("Ivanov"));

        assertEquals(1, names.size());
        assertTrue(names.contains("Ivanov_I-Book.zip"));
        assertFalse(names.contains("Ivanov_I-Old.zip.arch"));
    }

    @Test
    void listBooksByAuthorExcludesBooksWithoutAuthor() throws IOException {
        writeFile(booksDir, "NoConvention.zip", "book");
        writeFile(booksDir, "Ivanov_I-Book.zip", "book");

        List<String> names = namesOf(service.listBooksByAuthor("Ivanov"));

        assertEquals(1, names.size());
        assertFalse(names.contains("NoConvention.zip"));
    }

    @Test
    void operationsReturnZeroCountsForNullOrEmptyNameLists() {
        assertEquals(new FileOperationResultDto(0, 0), service.archiveBooks(null));
        assertEquals(new FileOperationResultDto(0, 0), service.archiveBooks(List.of()));
        assertEquals(new FileOperationResultDto(0, 0), service.restoreBooks(null));
        assertEquals(new FileOperationResultDto(0, 0), service.restoreBooks(List.of()));
        assertEquals(new FileOperationResultDto(0, 0), service.deleteBooks(null));
        assertEquals(new FileOperationResultDto(0, 0), service.deleteBooks(List.of()));
    }

    private static Path writeFile(Path directory, String fileName, String content) throws IOException {
        Path file = directory.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String readFile(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static List<String> namesOf(List<BookFileDto> files) {
        return files.stream().map(BookFileDto::name).toList();
    }

    private static BookFileDto byName(List<BookFileDto> files, String name) {
        return files.stream()
                .filter(file -> file.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
