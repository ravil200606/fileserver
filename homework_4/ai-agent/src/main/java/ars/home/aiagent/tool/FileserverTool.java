package ars.home.aiagent.tool;

import ars.home.aiagent.client.FileserverApiClient;
import ars.home.aiagent.client.FileserverApiException;
import ars.home.aiagent.client.dto.BookFileDto;
import ars.home.aiagent.client.dto.FileOperationResultDto;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FileserverTool {

    private static final Logger log = LoggerFactory.getLogger(FileserverTool.class);

    private final FileserverApiClient api;
    private final ToolResponseFormatter formatter;

    public FileserverTool(FileserverApiClient api, ToolResponseFormatter formatter) {
        this.api = api;
        this.formatter = formatter;
    }

    @Tool(
            name = "fileserver_api",
            value = "Single gateway to the fileserver REST API. "
                    + "Supported operations: getAll, getByAuthor, getByName, archive, restore. "
                    + "Use 'author' only for getByAuthor. Use 'name' or 'names' for "
                    + "getByName, archive and restore."
    )
    public String execute(
            @P("Operation: getAll | getByAuthor | getByName | archive | restore")
            Operation operation,

            @P(value = "Author surname. Required only for getByAuthor.", required = false)
            String author,

            @P(value = "Single exact file name. Used by getByName, archive, restore.", required = false)
            String name,

            @P(value = "List of exact file names. Used by getByName, archive, restore.", required = false)
            List<String> names
    ) {
        log.debug("TOOL_CALL_START operation={} author={} name={} names={}", operation, author, name, names);

        if (operation == null) {
            return formatter.error(null, "operation is required");
        }

        try {
            return switch (operation) {
                case getAll -> formatter.success(operation, api.getAll());
                case getByAuthor -> getByAuthor(operation, author);
                case getByName -> getByName(operation, name, names);
                case archive -> mutate(operation, true, name, names);
                case restore -> mutate(operation, false, name, names);
            };
        } catch (FileserverApiException e) {
            return formatter.error(operation, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Tool execution failed for operation {}", operation, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return formatter.error(operation, message);
        }
    }

    private String getByAuthor(Operation operation, String author) {
        if (author == null || author.isBlank()) {
            return formatter.error(operation, "author is required for getByAuthor");
        }
        return formatter.success(operation, api.getByAuthor(author.trim()));
    }

    private String getByName(Operation operation, String name, List<String> names) {
        List<String> effectiveNames = effectiveNames(name, names);
        if (effectiveNames.isEmpty()) {
            return formatter.error(operation, "name or names is required for getByName");
        }

        List<BookFileDto> found = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String fileName : effectiveNames) {
            try {
                found.add(api.getByName(fileName));
            } catch (FileserverApiException e) {
                errors.add(fileName + " -> " + e.getMessage());
            }
        }

        if (found.isEmpty()) {
            return formatter.error(operation, String.join("; ", errors));
        }

        Object data = effectiveNames.size() == 1 && found.size() == 1 ? found.get(0) : found;
        if (errors.isEmpty()) {
            return formatter.success(operation, data);
        }
        return formatter.successWithErrors(operation, data, String.join("; ", errors));
    }

    private String mutate(Operation operation, boolean archive, String name, List<String> names) {
        List<String> effectiveNames = effectiveNames(name, names);
        if (effectiveNames.isEmpty()) {
            return formatter.error(operation, "name or names is required for " + operation.name());
        }

        FileOperationResultDto result = archive
                ? api.archive(effectiveNames)
                : api.restore(effectiveNames);

        return formatter.success(operation, result);
    }

    private static List<String> effectiveNames(String name, List<String> names) {
        if (names != null && !names.isEmpty()) {
            return names.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .toList();
        }
        if (name != null && !name.isBlank()) {
            return List.of(name.trim());
        }
        return List.of();
    }
}
