package nl.example.qualityreport.model;

import java.util.List;

/**
 * @param email   primary email (always set; used for output compatibility)
 * @param aliases all normalized emails that resolve to this member (includes primary)
 */
public record TeamMember(String name, String email, List<String> aliases, String role, String team) {

    public TeamMember(String name, String email, String role, String team) {
        this(name, email, List.of(email), role, team);
    }
}
