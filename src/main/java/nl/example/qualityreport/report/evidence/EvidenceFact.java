package nl.example.qualityreport.report.evidence;

/**
 * A single deterministically extracted fact from the change context.
 *
 * @param id          unique identifier within the bundle (e.g. "ticket-0", "class-2")
 * @param type        category of the fact
 * @param value       the exact string to match in the narrative (case-insensitive)
 * @param source      where it was extracted from (e.g. "jira.tickets", "diff:path/Foo.java")
 * @param mustMention whether the narrative is required to cite this fact
 */
public record EvidenceFact(String id, FactType type, String value, String source, boolean mustMention) {

    public enum FactType {
        TICKET,
        CLASS_NAME,
        MIGRATION,
        METHOD,
        ANNOTATION_LITERAL,
        API_PATH,
        CONFIG_KEY,
        CONSTANT_FIELD,
        DOMAIN_TERM
    }
}
