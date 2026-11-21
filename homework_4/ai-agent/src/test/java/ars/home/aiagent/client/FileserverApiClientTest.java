package ars.home.aiagent.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class FileserverApiClientTest {

    private MockRestServiceServer server;
    private FileserverApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FileserverApiClient(builder, "http://localhost:8080");
    }

    @Test
    void getAllCallsFiles() {
        server.expect(requestTo("http://localhost:8080/files"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"files":[{"name":"Book.zip","author":"Ivanov I.","title":"Book","size":123,"modified":1700000000000}],"refreshIntervalSeconds":5}
                        """, MediaType.APPLICATION_JSON));

        var result = client.getAll();

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).name()).isEqualTo("Book.zip");
        assertThat(result.refreshIntervalSeconds()).isEqualTo(5L);
        server.verify();
    }

    @Test
    void getByAuthorCallsGetByAuthorWithQueryParam() {
        server.expect(requestTo("http://localhost:8080/getByAuthor?author=Tolstoy"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"files\":[],\"refreshIntervalSeconds\":5}", MediaType.APPLICATION_JSON));

        var result = client.getByAuthor("Tolstoy");

        assertThat(result.files()).isEmpty();
        server.verify();
    }

    @Test
    void getByNameCallsFileWithQueryParam() {
        server.expect(requestTo("http://localhost:8080/file?name=Book.zip"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"name\":\"Book.zip\",\"author\":\"Ivanov I.\",\"title\":\"Book\",\"size\":123,\"modified\":1700000000000}", MediaType.APPLICATION_JSON));

        var result = client.getByName("Book.zip");

        assertThat(result.title()).isEqualTo("Book");
        server.verify();
    }

    @Test
    void archivePostsJsonArray() {
        server.expect(requestTo("http://localhost:8080/files/archive"))
                .andExpect(method(POST))
                .andExpect(content().json("[\"Book.zip\"]"))
                .andRespond(withSuccess("{\"succeeded\":1,\"failed\":0}", MediaType.APPLICATION_JSON));

        var result = client.archive(List.of("Book.zip"));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        server.verify();
    }

    @Test
    void restorePostsJsonArray() {
        server.expect(requestTo("http://localhost:8080/archive/restore"))
                .andExpect(method(POST))
                .andExpect(content().json("[\"Book.zip.arch\"]"))
                .andRespond(withSuccess("{\"succeeded\":1,\"failed\":0}", MediaType.APPLICATION_JSON));

        var result = client.restore(List.of("Book.zip.arch"));

        assertThat(result.succeeded()).isEqualTo(1);
        server.verify();
    }

    @Test
    void getByNameThrowsOn404() {
        server.expect(requestTo("http://localhost:8080/file?name=missing.zip"))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND).body("Book not found").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.getByName("missing.zip"))
                .isInstanceOf(FileserverApiException.class)
                .hasMessageContaining("HTTP 404");

        server.verify();
    }
}
