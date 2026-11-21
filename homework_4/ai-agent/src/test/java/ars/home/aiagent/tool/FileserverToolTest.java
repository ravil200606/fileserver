package ars.home.aiagent.tool;

import ars.home.aiagent.client.FileserverApiClient;
import ars.home.aiagent.client.FileserverApiException;
import ars.home.aiagent.client.dto.BookFileDto;
import ars.home.aiagent.client.dto.BookListDto;
import ars.home.aiagent.client.dto.FileOperationResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileserverToolTest {

    private FileserverApiClient api;
    private FileserverTool tool;

    @BeforeEach
    void setUp() {
        api = Mockito.mock(FileserverApiClient.class);
        tool = new FileserverTool(api, new ToolResponseFormatter());
    }

    @Test
    void getAllDispatchesToGetAll() {
        when(api.getAll()).thenReturn(new BookListDto(List.of(), 5L));

        String result = tool.execute(Operation.getAll, null, null, null);

        assertThat(result).startsWith("Status: success\nAction: getAll\nData: {\"files\":[]");
        verify(api).getAll();
    }

    @Test
    void getByAuthorDispatchesWithAuthor() {
        when(api.getByAuthor("Tolstoy")).thenReturn(new BookListDto(List.of(), 5L));

        String result = tool.execute(Operation.getByAuthor, "Tolstoy", null, null);

        assertThat(result).contains("Action: getByAuthor");
        verify(api).getByAuthor("Tolstoy");
    }

    @Test
    void getByAuthorRequiresAuthor() {
        String result = tool.execute(Operation.getByAuthor, null, null, null);

        assertThat(result).isEqualTo("""
                Status: error
                Action: getByAuthor
                Data: null
                Errors: author is required for getByAuthor
                """.stripTrailing());
        verifyNoInteractions(api);
    }

    @Test
    void getByNameDispatchesForEachName() {
        when(api.getByName("Book.zip")).thenReturn(new BookFileDto("Book.zip", "I. Ivanov", "Book", 1L, 2L));
        when(api.getByName("Other.zip")).thenThrow(new FileserverApiException(HttpStatus.NOT_FOUND, "Book not found"));

        String result = tool.execute(Operation.getByName, null, null, List.of("Book.zip", "Other.zip"));

        assertThat(result).startsWith("Status: success\nAction: getByName\nData: [");
        assertThat(result).contains("HTTP 404");
        verify(api).getByName("Book.zip");
        verify(api).getByName("Other.zip");
    }

    @Test
    void getByNameRequiresName() {
        String result = tool.execute(Operation.getByName, null, null, List.of());

        assertThat(result).contains("Status: error", "name or names is required for getByName");
        verifyNoInteractions(api);
    }

    @Test
    void archiveDispatchesWithNames() {
        when(api.archive(List.of("Book.zip"))).thenReturn(new FileOperationResultDto(1, 0));

        String result = tool.execute(Operation.archive, null, "Book.zip", null);

        assertThat(result).isEqualTo("""
                Status: success
                Action: archive
                Data: {"succeeded":1,"failed":0}
                Errors: null
                """.stripTrailing());
        verify(api).archive(List.of("Book.zip"));
    }

    @Test
    void restoreDispatchesWithNames() {
        when(api.restore(List.of("Book.zip.arch"))).thenReturn(new FileOperationResultDto(1, 0));

        String result = tool.execute(Operation.restore, null, null, List.of("Book.zip.arch"));

        assertThat(result).contains("Action: restore", "\"succeeded\":1");
        verify(api).restore(List.of("Book.zip.arch"));
    }

    @Test
    void archiveRequiresName() {
        String result = tool.execute(Operation.archive, null, null, null);

        assertThat(result).contains("Status: error", "name or names is required for archive");
        verifyNoInteractions(api);
    }

    @Test
    void toolConvertsApiExceptionToErrorContract() {
        when(api.getByAuthor("Tolstoy")).thenThrow(new FileserverApiException(HttpStatus.INTERNAL_SERVER_ERROR, "boom"));

        String result = tool.execute(Operation.getByAuthor, "Tolstoy", null, null);

        assertThat(result).isEqualTo("""
                Status: error
                Action: getByAuthor
                Data: null
                Errors: HTTP 500: boom
                """.stripTrailing());
    }
}
