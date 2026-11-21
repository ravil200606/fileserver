package ars.home.aiagent.assistant;

import java.util.function.Supplier;

public class FixedContractBookAgent implements BookAgent {

    private final BookAgent delegate;
    private final Supplier<String> lastToolResult;
    private final Runnable clearToolResult;

    public FixedContractBookAgent(BookAgent delegate,
                                  Supplier<String> lastToolResult,
                                  Runnable clearToolResult) {
        this.delegate = delegate;
        this.lastToolResult = lastToolResult;
        this.clearToolResult = clearToolResult;
    }

    @Override
    public String chat(String userMessage) {
        clearToolResult.run();

        String modelAnswer = delegate.chat(userMessage);
        String toolResult = lastToolResult.get();

        clearToolResult.run();
        return toolResult == null ? modelAnswer : toolResult;
    }
}
