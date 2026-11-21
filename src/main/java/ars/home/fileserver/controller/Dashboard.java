package ars.home.fileserver.controller;

import ars.home.fileserver.dto.BookFileDto;
import ars.home.fileserver.dto.BookListDto;
import ars.home.fileserver.dto.FileOperationResultDto;
import ars.home.fileserver.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class Dashboard {

    private final BookService bookService;

    @GetMapping(value = "/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public BookListDto listFiles() {
        return new BookListDto(bookService.listBooks(), bookService.getRefreshIntervalSeconds());
    }

    @GetMapping(value = "/getByAuthor", produces = MediaType.APPLICATION_JSON_VALUE)
    public BookListDto listFilesByAuthor(@RequestParam("author") String author) {
        return new BookListDto(bookService.listBooksByAuthor(author), bookService.getRefreshIntervalSeconds());
    }

    @PostMapping(value = "/files/archive",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public FileOperationResultDto archiveFiles(@RequestBody List<String> names) {
        return bookService.archiveBooks(names);
    }

    @GetMapping(value = "/archive", produces = MediaType.APPLICATION_JSON_VALUE)
    public BookListDto listArchivedFiles() {
        return new BookListDto(bookService.listArchivedBooks(), bookService.getRefreshIntervalSeconds());
    }

    @PostMapping(value = "/archive/restore",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public FileOperationResultDto restoreFiles(@RequestBody List<String> names) {
        return bookService.restoreBooks(names);
    }

    @PostMapping(value = "/archive/delete",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public FileOperationResultDto deleteFiles(@RequestBody List<String> names) {
        return bookService.deleteBooks(names);
    }

    @GetMapping(value = "/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public BookFileDto getFile(@RequestParam("name") String name) {
        return bookService.getBook(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }

    @GetMapping(value = "/file/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> downloadFile(@RequestParam("name") String name) throws IOException {
        Path path = bookService.findBook(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));

        FileSystemResource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(path.getFileName().toString(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }
}
