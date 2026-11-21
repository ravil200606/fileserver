package ars.home.aiagent.assistant;

import dev.langchain4j.service.SystemMessage;

public interface BookAgent {

    @SystemMessage(SystemPrompt.TEXT)
    String chat(String userMessage);
}
