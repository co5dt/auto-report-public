package nl.example.qualityreport.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record RiskAssessment(
        RiskLevel finalRisk,
        int roundReached,
        ConsensusType consensusType,
        List<AgentVote> agentVotes,
        String minorityOpinion) {
    public RiskAssessment {
        Objects.requireNonNull(finalRisk, "finalRisk is required");
        Objects.requireNonNull(consensusType, "consensusType is required");
        Objects.requireNonNull(agentVotes, "agentVotes is required");
        if (roundReached < 1) {
            throw new IllegalArgumentException("roundReached must be >= 1");
        }
        if (agentVotes.isEmpty()) {
            throw new IllegalArgumentException("agentVotes cannot be empty");
        }
        minorityOpinion = minorityOpinion == null ? "" : minorityOpinion;
        agentVotes = List.copyOf(agentVotes);
    }

    public static RiskAssessment fromConsensus(List<AgentVote> votes, int round) {
        if (votes == null || votes.isEmpty()) {
            throw new IllegalArgumentException("Votes cannot be empty for consensus result");
        }
        RiskLevel first = votes.getFirst().vote();
        boolean unanimous = votes.stream().allMatch(v -> v.vote() == first);
        if (!unanimous) {
            throw new IllegalArgumentException("Consensus result requires unanimous votes");
        }
        return new RiskAssessment(first, round, ConsensusType.UNANIMOUS, votes, "");
    }

    public static RiskAssessment fromHighestVote(List<AgentVote> votes, int round) {
        if (votes == null || votes.isEmpty()) {
            throw new IllegalArgumentException("Votes cannot be empty for highest-vote result");
        }
        RiskLevel highest = votes.stream().map(AgentVote::vote).reduce(RiskLevel.LOW, RiskLevel::max);

        String minority = votes.stream()
                .filter(v -> v.vote().ordinal() < highest.ordinal())
                .map(v -> v.agent() + ": " + v.reasoning())
                .collect(Collectors.joining(" | "));

        return new RiskAssessment(highest, round, ConsensusType.HIGHEST, votes, minority);
    }

    public enum ConsensusType {
        UNANIMOUS,
        HIGHEST
    }
}
