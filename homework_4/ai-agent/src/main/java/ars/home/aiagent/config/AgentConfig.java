package ars.home.aiagent.config;

import ars.home.aiagent.assistant.BookAgent;
import ars.home.aiagent.assistant.FixedContractBookAgent;
import ars.home.aiagent.tool.FileserverTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    private final ThreadLocal<String> lastToolResult = new ThreadLocal<>();

    @Bean
    BookAgent bookAgent(ChatModel chatModel, FileserverTool fileserverTool) {
        BookAgent delegate = AiServices.builder(BookAgent.class)
                .chatModel(chatModel)
                .tools(fileserverTool)
                .beforeToolExecution(before -> {
                    lastToolResult.remove();
                    log.debug("LLM_TOOL_REQUEST name={} arguments={}",
                            before.request().name(), before.request().arguments());
                })
                .afterToolExecution(execution -> {
                    String result = String.valueOf(execution.result());
                    lastToolResult.set(result);
                    log.debug("LLM_TOOL_RESULT name={} result={}",
                            execution.request().name(), result);
                })
                .build();

        return new FixedContractBookAgent(delegate, lastToolResult::get, lastToolResult::remove);
    }
}
