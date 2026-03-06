package nl.example.qualityreport.llm;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Test double that returns canned responses in FIFO order.
 * Throws if more calls are made than responses queued, ensuring tests
 * fail loudly on unexpected interactions.
 */
public class StubLlmProvider implements LlmProvider {

    private final Deque<String> responses;
    private int callCount;

    public StubLlmProvider(String... responses) {
        this.responses = new ArrayDeque<>(Arrays.asList(responses));
    }

    public StubLlmProvider(List<String> responses) {
        this.responses = new ArrayDeque<>(responses);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (responses.isEmpty()) {
            throw new IllegalStateException(
                    "StubLlmProvider exhausted: no more canned responses (call #" + (callCount + 1) + ")");
        }
        callCount++;
        return responses.poll();
    }

    public int getCallCount() {
        return callCount;
    }

    public boolean isExhausted() {
        return responses.isEmpty();
    }
}
