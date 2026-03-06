package nl.example.qualityreport.model;

import java.time.Instant;

public record CommitInfo(
        String hash,
        String author,
        String email,
        String role,
        String team,
        String message,
        Instant date,
        int filesChanged,
        int insertions,
        int deletions,
        String repoName) {

    public CommitInfo(
            String hash,
            String author,
            String email,
            String role,
            String team,
            String message,
            Instant date,
            int filesChanged,
            int insertions,
            int deletions) {
        this(hash, author, email, role, team, message, date, filesChanged, insertions, deletions, "");
    }
}
