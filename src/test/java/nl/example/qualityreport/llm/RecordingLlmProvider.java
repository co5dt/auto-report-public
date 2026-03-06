package nl.example.qualityreport.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test double that returns canned responses and records all calls.
 * Shared across test classes to eliminate duplicate local copies.
 */
public class RecordingLlmProvider implements LlmProvider {

    private final Deque<String> responses;
    private final List<String> systemPrompts = new ArrayList<>();
    private final List<String> userMessages = new ArrayList<>();

    public RecordingLlmProvider(List<String> responses) {
        this.responses = new ArrayDeque<>(responses);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        systemPrompts.add(systemPrompt);
        userMessages.add(userMessage);
        if (responses.isEmpty()) {
            throw new IllegalStateException("No response left in test provider queue");
        }
        return responses.removeFirst();
    }

    public int callCount() {
        return systemPrompts.size();
    }

    public List<String> systemPrompts() {
        return systemPrompts;
    }

    public List<String> userMessages() {
        return userMessages;
    }
}
