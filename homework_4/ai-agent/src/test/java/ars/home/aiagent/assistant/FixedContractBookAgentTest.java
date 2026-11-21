package ars.home.aiagent.assistant;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FixedContractBookAgentTest {

    @Test
    void returnsToolResultWhenToolWasCalled() {
        AtomicReference<String> toolResult = new AtomicReference<>();
        BookAgent delegate = message -> {
            toolResult.set("Status: success\nAction: getAll\nData: {}\nErrors: null");
            return "model answer that violates the contract";
        };

        BookAgent agent = new FixedContractBookAgent(
                delegate,
                toolResult::get,
                () -> toolResult.set(null)
        );

        assertThat(agent.chat("покажи все книги"))
                .isEqualTo("Status: success\nAction: getAll\nData: {}\nErrors: null");
        assertThat(toolResult.get()).isNull();
    }

    @Test
    void returnsModelAnswerWhenToolWasNotCalled() {
        AtomicReference<String> toolResult = new AtomicReference<>();
        BookAgent delegate = message -> "Я оператор fileserver API.";

        BookAgent agent = new FixedContractBookAgent(
                delegate,
                toolResult::get,
                () -> toolResult.set(null)
        );

        assertThat(agent.chat("какая погода?")).isEqualTo("Я оператор fileserver API.");
    }
}
