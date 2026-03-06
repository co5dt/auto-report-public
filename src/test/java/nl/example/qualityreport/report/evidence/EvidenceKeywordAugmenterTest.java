package nl.example.qualityreport.report.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.report.evidence.EvidenceFact.FactType;
import org.junit.jupiter.api.Test;

class EvidenceKeywordAugmenterTest {

    @Test
    void dictionaryTermPromotedAsMustMention() {
        var baseline = new EvidenceBundle(
                List.of(new EvidenceFact("ticket-0", FactType.TICKET, "SEC-42", "jira.tickets", true)));

        var dict = dictOf("SQL injection", "PASPOORT");
        ChangeData changes = changes("Fix SQL injection in BsnLookup.java");
        JiraData jira = jira("Fix SQL injection vulnerability", "");

        EvidenceBundle result = EvidenceKeywordAugmenter.augment(baseline, dict, List.of(), changes, jira);

        assertThat(result.mustMentionValues()).contains("SEC-42", "SQL injection");
        assertThat(result.facts().stream()
                        .filter(f -> f.value().equals("SQL injection"))
                        .findFirst()
                        .orElseThrow()
                        .source())
                .isEqualTo("dictionary");
        assertThat(result.mustMentionValues()).doesNotContain("PASPOORT");
    }

    @Test
    void llmKeywordAnchoredBecomeMustMention() {
        var baseline = new EvidenceBundle(List.of());
        var dict = DomainKeywordDictionary.empty();
        ChangeData changes = changes("Implement BRP mutatie verwerking");
        JiraData jira = jira("BRP mutatie flow aanpassen", "");

        EvidenceBundle result = EvidenceKeywordAugmenter.augment(
                baseline, dict, List.of("BRP mutatie", "nonexistent term"), changes, jira);

        assertThat(result.mustMentionValues()).containsExactly("BRP mutatie");
        assertThat(result.facts().stream()
                        .filter(f -> f.value().equals("BRP mutatie"))
                        .findFirst()
                        .orElseThrow()
                        .source())
                .isEqualTo("llm-jira-extract+anchor");
    }

    @Test
    void llmKeywordNotAnchoredIsSkipped() {
        var baseline = new EvidenceBundle(List.of());
        ChangeData changes = changes("Simple refactor");
        JiraData jira = jira("Refactor code", "");

        EvidenceBundle result = EvidenceKeywordAugmenter.augment(
                baseline, DomainKeywordDictionary.empty(), List.of("BRP mutatie"), changes, jira);

        assertThat(result.facts()).isEmpty();
    }

    @Test
    void deduplicatesAgainstExistingFacts() {
        var baseline = new EvidenceBundle(List.of(new EvidenceFact(
                "literal-0", FactType.ANNOTATION_LITERAL, "SQL injection", "diff.annotation_values", true)));

        var dict = dictOf("SQL injection");
        ChangeData changes = changes("SQL injection fix");
        JiraData jira = jira("Fix SQL injection", "");

        EvidenceBundle result =
                EvidenceKeywordAugmenter.augment(baseline, dict, List.of("SQL injection"), changes, jira);

        long count = result.facts().stream()
                .filter(f -> DomainKeywordDictionary.normalize(f.value())
                        .equals(DomainKeywordDictionary.normalize("SQL injection")))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void preservesStableOrdering() {
        var baseline = new EvidenceBundle(List.of(
                new EvidenceFact("ticket-0", FactType.TICKET, "SEC-1", "jira.tickets", true),
                new EvidenceFact("class-1", FactType.CLASS_NAME, "FooService", "changedFiles", true)));

        var dict = dictOf("BSN");
        ChangeData changes = changes("Add BSN lookup");
        JiraData jira = jira("BSN lookup feature", "");

        EvidenceBundle result = EvidenceKeywordAugmenter.augment(baseline, dict, List.of(), changes, jira);

        assertThat(result.facts().get(0).id()).isEqualTo("ticket-0");
        assertThat(result.facts().get(1).id()).isEqualTo("class-1");
        assertThat(result.facts().get(2).type()).isEqualTo(FactType.DOMAIN_TERM);
    }

    @Test
    void handlesEmptyDictionaryAndEmptyLlmKeywords() {
        var baseline = new EvidenceBundle(
                List.of(new EvidenceFact("ticket-0", FactType.TICKET, "DEV-1", "jira.tickets", true)));

        EvidenceBundle result = EvidenceKeywordAugmenter.augment(
                baseline, DomainKeywordDictionary.empty(), List.of(), changes("Some change"), jira("Some title", ""));

        assertThat(result.facts()).hasSize(1);
    }

    @Test
    void handlesNullDictionaryAndNullKeywords() {
        var baseline = new EvidenceBundle(List.of());

        EvidenceBundle result =
                EvidenceKeywordAugmenter.augment(baseline, null, null, changes("Change"), jira("Title", ""));

        assertThat(result.facts()).isEmpty();
    }

    private static DomainKeywordDictionary dictOf(String... terms) {
        var sb = new StringBuilder("{\"terms\":[");
        for (int i = 0; i < terms.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(terms[i]).append("\"");
        }
        sb.append("]}");
        return DomainKeywordDictionary.parseJson(
                new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static ChangeData changes(String commitMessage) {
        return ChangeData.from(
                List.of(new CommitInfo(
                        "abc123", "dev", "dev@test.nl", "developer", "team-a", commitMessage, Instant.now(), 1, 10, 5)),
                "",
                List.of());
    }

    private static JiraData jira(String description, String acceptanceCriteria) {
        return new JiraData(List.of("TST-1"), description, acceptanceCriteria, null, null, null, true, "1.0");
    }
}
