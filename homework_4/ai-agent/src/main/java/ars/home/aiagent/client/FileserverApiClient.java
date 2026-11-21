package ars.home.aiagent.client;

import ars.home.aiagent.client.dto.BookFileDto;
import ars.home.aiagent.client.dto.BookListDto;
import ars.home.aiagent.client.dto.FileOperationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class FileserverApiClient {

    private static final Logger log = LoggerFactory.getLogger(FileserverApiClient.class);

    private final String baseUrl;
    private final RestClient restClient;

    @Autowired
    public FileserverApiClient(@Value("${fileserver.base-url:http://localhost:8080}") String baseUrl) {
        this(RestClient.builder(), baseUrl);
    }

    FileserverApiClient(RestClient.Builder builder, String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.restClient = builder.baseUrl(this.baseUrl).build();
    }

    public BookListDto getAll() {
        return get("getAll", URI.create("/files"), BookListDto.class, null);
    }

    public BookListDto getByAuthor(String author) {
        URI uri = UriComponentsBuilder.fromPath("/getByAuthor")
                .queryParam("author", author)
                .build()
                .encode()
                .toUri();
        return get("getByAuthor", uri, BookListDto.class, null);
    }

    public BookFileDto getByName(String name) {
        URI uri = UriComponentsBuilder.fromPath("/file")
                .queryParam("name", name)
                .build()
                .encode()
                .toUri();
        return get("getByName", uri, BookFileDto.class, null);
    }

    public FileOperationResultDto archive(List<String> names) {
        return post("archive", "/files/archive", names);
    }

    public FileOperationResultDto restore(List<String> names) {
        return post("restore", "/archive/restore", names);
    }

    private <T> T get(String operation, URI uri, Class<T> type, Object requestBody) {
        ResponseEntity<T> response = restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        (request, httpResponse) -> handleError(operation, "GET", uri.toString(), requestBody, httpResponse))
                .toEntity(type);

        logCall(operation, "GET", uri.toString(), requestBody, response.getStatusCode(), response.getBody());
        return requireBody(operation, response.getBody());
    }

    private FileOperationResultDto post(String operation, String path, List<String> names) {
        ResponseEntity<FileOperationResultDto> response = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(names)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        (request, httpResponse) -> handleError(operation, "POST", path, names, httpResponse))
                .toEntity(FileOperationResultDto.class);

        logCall(operation, "POST", path, names, response.getStatusCode(), response.getBody());
        return requireBody(operation, response.getBody());
    }

    private void handleError(String operation, String method, String uri, Object requestBody,
                             ClientHttpResponse response) throws IOException {
        String body = readBody(response);
        logCall(operation, method, uri, requestBody, response.getStatusCode(), body);
        throw new FileserverApiException(response.getStatusCode(), body);
    }

    private void logCall(String operation, String method, String uri, Object requestBody,
                         HttpStatusCode status, Object result) {
        log.debug("FILESERVER_CALL operation={} method={} url={} requestBody={} status={} result={}",
                operation, method, baseUrl + uri, requestBody, status.value(), result);
    }

    private String readBody(ClientHttpResponse response) throws IOException {
        if (response.getBody() == null) {
            return "";
        }
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private <T> T requireBody(String operation, T body) {
        if (body == null) {
            throw new IllegalStateException("Empty response body for operation " + operation);
        }
        return body;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
