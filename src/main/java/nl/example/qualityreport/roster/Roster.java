package nl.example.qualityreport.roster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import nl.example.qualityreport.model.TeamMember;

public class Roster {

    private final Map<String, TeamMember> membersByEmail;

    private Roster(Map<String, TeamMember> membersByEmail) {
        this.membersByEmail = membersByEmail;
    }

    public static Roster load(Path rosterPath) throws IOException {
        if (!rosterPath.toFile().exists()) {
            throw new IOException("Roster file not found: " + rosterPath.toAbsolutePath()
                    + "\nHint: use --roster <path> to specify the team roster JSON file.");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(rosterPath.toFile());
        } catch (Exception e) {
            throw new IOException("Failed to parse roster JSON at " + rosterPath + ": " + e.getMessage(), e);
        }

        JsonNode teamsNode = root.get("teams");
        if (teamsNode == null || !teamsNode.isObject()) {
            throw new IOException("Invalid roster: missing or non-object 'teams' field in " + rosterPath);
        }

        Map<String, TeamMember> members = new HashMap<>();
        var teamNames = teamsNode.fieldNames();
        while (teamNames.hasNext()) {
            String teamName = teamNames.next();
            JsonNode teamNode = teamsNode.get(teamName);
            JsonNode membersNode = teamNode.get("members");
            if (membersNode == null || !membersNode.isArray()) {
                continue;
            }
            for (JsonNode memberNode : membersNode) {
                String name = textOrDefault(memberNode, "name", "unknown");
                String role = textOrDefault(memberNode, "role", "unknown");

                List<String> rawAliases = collectAliases(memberNode);
                if (rawAliases.isEmpty()) {
                    continue;
                }

                Set<String> normalizedSet = new LinkedHashSet<>();
                for (String raw : rawAliases) {
                    normalizedSet.add(normalizeEmail(raw));
                }
                List<String> aliases = new ArrayList<>(normalizedSet);

                String primaryEmail = aliases.getFirst();

                TeamMember member = new TeamMember(name, primaryEmail, List.copyOf(aliases), role, teamName);

                for (String alias : aliases) {
                    TeamMember existing = members.get(alias);
                    if (existing != null && existing != member) {
                        throw new IOException("Duplicate alias '%s' in roster: already assigned to '%s' (%s), "
                                + "cannot assign to '%s' (%s) in %s"
                                        .formatted(
                                                alias, existing.name(), existing.team(), name, teamName, rosterPath));
                    }
                    members.put(alias, member);
                }
            }
        }

        return new Roster(members);
    }

    private static List<String> collectAliases(JsonNode memberNode) {
        List<String> result = new ArrayList<>();

        String singleEmail = textOrDefault(memberNode, "email", null);
        if (singleEmail != null) {
            result.add(singleEmail);
        }

        JsonNode emailsNode = memberNode.get("emails");
        if (emailsNode != null && emailsNode.isArray()) {
            for (JsonNode e : emailsNode) {
                String addr = e.asText(null);
                if (addr != null && !addr.isBlank()) {
                    result.add(addr);
                }
            }
        }

        return result;
    }

    public Optional<TeamMember> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(membersByEmail.get(normalizeEmail(email)));
    }

    public int memberCount() {
        return (int) membersByEmail.values().stream().distinct().count();
    }

    public int teamCount() {
        return (int) membersByEmail.values().stream()
                .distinct()
                .map(TeamMember::team)
                .distinct()
                .count();
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase();
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return defaultValue;
        }
        return child.asText(defaultValue);
    }
}
