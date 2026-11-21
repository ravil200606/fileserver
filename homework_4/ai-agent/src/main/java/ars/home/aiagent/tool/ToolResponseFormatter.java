package ars.home.aiagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ToolResponseFormatter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String success(Operation operation, Object data) {
        return format(operation, "success", data, null);
    }

    public String successWithErrors(Operation operation, Object data, String errors) {
        return format(operation, "success", data, errors);
    }

    public String error(Operation operation, String errors) {
        return format(operation, "error", null, errors);
    }

    private String format(Operation operation, String status, Object data, String errors) {
        return "Status: " + status + "\n"
                + "Action: " + (operation == null ? "null" : operation.name()) + "\n"
                + "Data: " + toJson(data) + "\n"
                + "Errors: " + (errors == null || errors.isBlank() ? "null" : errors);
    }

    private String toJson(Object data) {
        if (data == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return String.valueOf(data);
        }
    }
}
