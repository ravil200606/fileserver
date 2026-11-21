package ars.home.aiagent.cli;

import ars.home.aiagent.assistant.BookAgent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AgentCli implements CommandLineRunner {

    private final BookAgent bookAgent;

    public AgentCli(BookAgent bookAgent) {
        this.bookAgent = bookAgent;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            System.out.println("Usage: java -jar ai-agent.jar \"покажи все книги\"");
            return;
        }

        String request = String.join(" ", args);
        System.out.println(bookAgent.chat(request));
    }
}
