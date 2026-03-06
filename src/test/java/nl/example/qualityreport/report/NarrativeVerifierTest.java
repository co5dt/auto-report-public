package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nl.example.qualityreport.report.evidence.EvidenceBundle;
import nl.example.qualityreport.report.evidence.EvidenceFact;
import nl.example.qualityreport.report.evidence.EvidenceFact.FactType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NarrativeVerifierTest {

    @Nested
    class Verification {

        @Test
        void passesWhenAllMustMentionPresent() {
            var bundle = bundle(fact("PROJ-1234", true), fact("CacheConfig", true));

            var result = NarrativeVerifier.verify("De klasse CacheConfig is gewijzigd voor PROJ-1234.", bundle);

            assertThat(result.passed()).isTrue();
            assertThat(result.missingIdentifiers()).isEmpty();
            assertThat(result.presentIdentifiers()).containsExactlyInAnyOrder("PROJ-1234", "CacheConfig");
        }

        @Test
        void failsWhenMustMentionMissing() {
            var bundle = bundle(fact("PROJ-1234", true), fact("CacheConfig", true), fact("GbaLookupService", true));

            var result = NarrativeVerifier.verify("De klasse CacheConfig is gewijzigd voor PROJ-1234.", bundle);

            assertThat(result.passed()).isFalse();
            assertThat(result.missingIdentifiers()).containsExactly("GbaLookupService");
        }

        @Test
        void ignoresOptionalFacts() {
            var bundle = new EvidenceBundle(List.of(
                    new EvidenceFact("t-0", FactType.TICKET, "PROJ-1", "jira", true),
                    new EvidenceFact("m-0", FactType.METHOD, "doWork", "diff", false)));

            var result = NarrativeVerifier.verify("PROJ-1 betreft een wijziging.", bundle);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void caseInsensitiveMatching() {
            var bundle = bundle(fact("CacheConfig", true));

            var result = NarrativeVerifier.verify("De cacheconfig klasse is aangepast.", bundle);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void emptyBundlePasses() {
            var result = NarrativeVerifier.verify("Some text.", new EvidenceBundle(List.of()));
            assertThat(result.passed()).isTrue();
        }

        @Test
        void nullBundlePasses() {
            var result = NarrativeVerifier.verify("Some text.", null);
            assertThat(result.passed()).isTrue();
        }

        @Test
        void coveragePercentCalculation() {
            var bundle = bundle(fact("FooService", true), fact("BarRepository", true), fact("BazController", true));

            var result = NarrativeVerifier.verify("De FooService en BarRepository zijn aangepast.", bundle);

            assertThat(result.coveragePercent()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.1));
        }
    }

    @Nested
    class RepairPrompt {

        @Test
        void containsMissingIdentifiers() {
            String prompt = NarrativeVerifier.buildRepairPrompt(
                    "De caching is aangepast.", List.of("GbaLookupService", "gba-persons"));

            assertThat(prompt).contains("GbaLookupService");
            assertThat(prompt).contains("gba-persons");
            assertThat(prompt).contains("De caching is aangepast.");
        }

        @Test
        void usesInsertOnlyLanguage() {
            String prompt = NarrativeVerifier.buildRepairPrompt("Existing text.", List.of("FooService"));

            assertThat(prompt).containsIgnoringCase("do NOT rewrite");
            assertThat(prompt).containsIgnoringCase("do NOT repeat");
        }
    }

    @Nested
    class BatchMissing {

        @Test
        void singleBatchWhenFewItems() {
            var batches = NarrativeVerifier.batchMissing(List.of("A", "B"));
            assertThat(batches).hasSize(1);
            assertThat(batches.get(0)).containsExactly("A", "B");
        }

        @Test
        void splitIntoBatchesOfThree() {
            var batches = NarrativeVerifier.batchMissing(List.of("A", "B", "C", "D", "E", "F", "G"));
            assertThat(batches).hasSize(3);
            assertThat(batches.get(0)).containsExactly("A", "B", "C");
            assertThat(batches.get(1)).containsExactly("D", "E", "F");
            assertThat(batches.get(2)).containsExactly("G");
        }

        @Test
        void emptyListProducesNoBatches() {
            assertThat(NarrativeVerifier.batchMissing(List.of())).isEmpty();
        }
    }

    @Nested
    class DeterministicInsert {

        @Test
        void producesOneSentencePerIdentifier() {
            String result = NarrativeVerifier.deterministicInsert(List.of("FooService", "BarRepo"));

            assertThat(result).contains("FooService");
            assertThat(result).contains("BarRepo");
            assertThat(result).doesNotStartWith(" ");
        }

        @Test
        void emptyListProducesEmptyString() {
            assertThat(NarrativeVerifier.deterministicInsert(List.of())).isEmpty();
        }
    }

    // --- Helpers ---

    private static EvidenceFact fact(String value, boolean mustMention) {
        return new EvidenceFact("f-" + value, FactType.CLASS_NAME, value, "test", mustMention);
    }

    private static EvidenceBundle bundle(EvidenceFact... facts) {
        return new EvidenceBundle(List.of(facts));
    }
}
