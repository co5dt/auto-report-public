package nl.example.qualityreport.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import nl.example.qualityreport.config.AppConfig;
import nl.example.qualityreport.context.ContextBuilder;
import nl.example.qualityreport.llm.LlmProvider;
import nl.example.qualityreport.llm.LlmProviderException;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;

public class AgentDeliberation {

    static final int MAX_ROUNDS = 3;
    static final List<String> ALL_AGENTS = List.of("diff-analyst", "process-assessor", "evidence-checker");

    private final LlmProvider llm;
    private final ContextBuilder contextBuilder;
    private final Function<String, String> promptLoader;
    private final List<String> activeAgents;
    private final boolean toolLoopEnabled;
    private final ToolExecutor toolExecutor;

    public AgentDeliberation(LlmProvider llm) {
        this(llm, new ContextBuilder(), null, new AppConfig().agentCount(), false, null);
    }

    public AgentDeliberation(LlmProvider llm, int agentCount, boolean toolLoopEnabled, ToolExecutor toolExecutor) {
        this(llm, new ContextBuilder(), null, agentCount, toolLoopEnabled, toolExecutor);
    }

    AgentDeliberation(LlmProvider llm, ContextBuilder contextBuilder) {
        this(llm, contextBuilder, null, ALL_AGENTS.size(), false, null);
    }

    AgentDeliberation(LlmProvider llm, ContextBuilder contextBuilder, Function<String, String> promptLoader) {
        this(llm, contextBuilder, promptLoader, ALL_AGENTS.size(), false, null);
    }

    AgentDeliberation(
            LlmProvider llm, ContextBuilder contextBuilder, Function<String, String> promptLoader, int agentCount) {
        this(llm, contextBuilder, promptLoader, agentCount, false, null);
    }

    AgentDeliberation(
            LlmProvider llm,
            ContextBuilder contextBuilder,
            Function<String, String> promptLoader,
            int agentCount,
            boolean toolLoopEnabled,
            ToolExecutor toolExecutor) {
        this.llm = Objects.requireNonNull(llm, "llm is required");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder is required");
        this.promptLoader = promptLoader != null ? promptLoader : this::loadPromptFromResources;
        if (agentCount < 1 || agentCount > ALL_AGENTS.size()) {
            throw new IllegalArgumentException(
                    "agentCount must be between 1 and " + ALL_AGENTS.size() + ", got: " + agentCount);
        }
        this.activeAgents = ALL_AGENTS.subList(0, agentCount);
        this.toolLoopEnabled = toolLoopEnabled;
        this.toolExecutor = toolExecutor;
    }

    public RiskAssessment assess(ChangeData changes, JiraData jira) {
        String context = contextBuilder.build(changes, jira);
        List<AgentVote> votes = List.of();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            votes = runRound(context, votes, round);
            if (isUnanimous(votes)) {
                return RiskAssessment.fromConsensus(votes, round);
            }
        }

        // Pattern A: final disagreement resolves to the highest vote.
        return RiskAssessment.fromHighestVote(votes, MAX_ROUNDS);
    }

    private List<AgentVote> runRound(String context, List<AgentVote> priorVotes, int round) {
        var roundVotes = new ArrayList<AgentVote>(activeAgents.size());
        for (String agent : activeAgents) {
            String systemPrompt = promptLoader.apply(agent);
            String userMessage = buildUserMessage(context, priorVotes, round);

            AgentVote vote = callAgentWithOptionalToolLoop(agent, systemPrompt, userMessage, round);
            roundVotes.add(vote);
        }
        return List.copyOf(roundVotes);
    }

    static final int MAX_PARSE_RETRIES = 1;
    private static final String RETRY_NUDGE = "\n\nYour previous response was not valid JSON. "
            + "Respond with ONLY a JSON object: {\"vote\": \"LOW|MEDIUM|HIGH\", \"confidence\": 0.0-1.0, \"reasoning\": \"...\"}";

    private AgentVote callAgentWithOptionalToolLoop(String agent, String systemPrompt, String userMessage, int round) {
        String response = llm.chat(systemPrompt, userMessage);

        ResponseParser.ParsedResponse parsed;
        try {
            parsed = ResponseParser.parse(agent, response, toolLoopEnabled);
        } catch (IllegalArgumentException firstEx) {
            parsed = retryParse(agent, systemPrompt, userMessage, round, firstEx);
        }

        if (parsed instanceof ResponseParser.ParsedResponse.Vote v) {
            return v.vote();
        }

        if (parsed instanceof ResponseParser.ParsedResponse.ToolCall tc && toolExecutor != null) {
            ToolExecutor.ToolResult toolResult = toolExecutor.execute(tc.request());
            String enrichedMessage = userMessage + "\n\n" + toolResult.xml();
            String secondResponse = llm.chat(systemPrompt, enrichedMessage);

            try {
                String cleaned = ResponseParser.stripCodeFences(secondResponse);
                return ResponseParser.parseVote(agent, cleaned);
            } catch (IllegalArgumentException ex) {
                throw new LlmProviderException(
                        "Failed to parse vote after tool result for agent '" + agent + "' in round " + round + ": "
                                + ex.getMessage(),
                        ex);
            }
        }

        throw new LlmProviderException(
                "Tool loop enabled but no ToolExecutor configured for agent '" + agent + "' in round " + round);
    }

    private ResponseParser.ParsedResponse retryParse(
            String agent, String systemPrompt, String userMessage, int round, IllegalArgumentException firstEx) {
        for (int retry = 0; retry < MAX_PARSE_RETRIES; retry++) {
            String retryResponse = llm.chat(systemPrompt, userMessage + RETRY_NUDGE);
            try {
                return ResponseParser.parse(agent, retryResponse, toolLoopEnabled);
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new LlmProviderException(
                "Failed to parse vote for agent '" + agent + "' in round " + round + " after " + MAX_PARSE_RETRIES
                        + " retry(ies): " + firstEx.getMessage(),
                firstEx);
    }

    private String buildUserMessage(String context, List<AgentVote> priorVotes, int round) {
        if (round <= 1 || priorVotes == null || priorVotes.isEmpty()) {
            return context;
        }

        return context + "\n\n<prior_votes round=\""
                + (round - 1) + "\">\n" + formatVotes(priorVotes)
                + "\n</prior_votes>";
    }

    private String formatVotes(List<AgentVote> priorVotes) {
        return priorVotes.stream()
                .map(v -> "- " + v.agent() + " | vote="
                        + v.vote() + " | confidence="
                        + v.confidence() + " | reasoning="
                        + v.reasoning())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private boolean isUnanimous(List<AgentVote> votes) {
        if (votes == null || votes.isEmpty()) {
            return false;
        }
        var first = votes.getFirst().vote();
        return votes.stream().allMatch(v -> v.vote() == first);
    }

    private String loadPromptFromResources(String agentName) {
        String resourcePath = "/prompts/" + agentName + ".txt";
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource: " + resourcePath, e);
        }
    }
}
