package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.llm.RecordingLlmProvider;
import nl.example.qualityreport.model.AgentVote;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.model.RiskLevel;
import nl.example.qualityreport.report.evidence.DomainKeywordDictionary;
import nl.example.qualityreport.report.evidence.EvidenceFact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NarrativeServiceTest {

    @Nested
    class ContextPayloadShape {

        @Test
        void changeContextContainsAllGroundingSections() {
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("change", fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("<narrative_request type=\"change\">");
            assertThat(ctx).contains("<changed_files>");
            assertThat(ctx).contains("<commit_messages>");
            assertThat(ctx).contains("<agent_votes");
            assertThat(ctx).contains("<diff_excerpt>");
            assertThat(ctx).doesNotContain("<acceptance_criteria>");
        }

        @Test
        void riskContextContainsAcceptanceCriteriaNotDiff() {
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("risk", fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("<narrative_request type=\"risk\">");
            assertThat(ctx).contains("<agent_votes");
            assertThat(ctx).contains("<acceptance_criteria>");
            assertThat(ctx).doesNotContain("<diff_excerpt>");
        }

        @Test
        void changedFilesListsAllFiles() {
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("change", fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("PlCache.java");
            assertThat(ctx).contains("PlCacheTest.java");
            assertThat(ctx).contains("V42__add_cache_ttl.sql");
        }

        @Test
        void commitMessagesIncludeAuthorAndRole() {
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("change", fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("author=\"Alice Dev\"");
            assertThat(ctx).contains("role=\"BE\"");
            assertThat(ctx).contains("invalidate cache on PL update");
        }

        @Test
        void agentVotesIncludeReasoningAndConsensus() {
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("risk", fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("consensus=\"unanimous\"");
            assertThat(ctx).contains("agent=\"diff-analyst\"");
            assertThat(ctx).contains("Moderate code impact.");
        }

        @Test
        void metadataPreserved() {
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("change", fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("<tickets>PROJ-128530, PROJ-128531</tickets>");
            assertThat(ctx).contains("<risk_level>MEDIUM</risk_level>");
            assertThat(ctx).contains("<total_commits>2</total_commits>");
        }

        @Test
        void emptyAcceptanceCriteriaOmitsSection() {
            var service = new NarrativeService(null);
            JiraData sparse = sparseJiraData();
            String ctx = service.buildNarrativeContext("risk", fullChangeData(), sparse, unanimousRisk());

            assertThat(ctx).doesNotContain("<acceptance_criteria>");
        }
    }

    @Nested
    class DiffExcerptBounding {

        @Test
        void shortDiffIncludedFully() {
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("change", fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("-old");
            assertThat(ctx).contains("+new");
            assertThat(ctx).doesNotContain("[truncated]");
        }

        @Test
        void longDiffIsTruncated() {
            String longDiff = "x".repeat(NarrativeService.MAX_DIFF_EXCERPT_CHARS + 1000);
            var changes = ChangeData.from(
                    fullChangeData().commits(), longDiff, fullChangeData().changedFiles());
            var service = new NarrativeService(null);
            String ctx = service.buildNarrativeContext("change", changes, fullJiraData(), unanimousRisk());

            assertThat(ctx).contains("[truncated]");
        }
    }

    @Nested
    class LlmIntegration {

        @Test
        void generationPassesEnrichedContextToLlm() {
            var recorder =
                    new RecordingLlmProvider(List.of("De cache-invalidatie is aangepast.", "Het risico is beperkt."));
            var service = new NarrativeService(recorder);

            service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(recorder.callCount()).isEqualTo(2);

            String changeCtx = recorder.userMessages().get(0);
            assertThat(changeCtx).contains("<changed_files>");
            assertThat(changeCtx).contains("<diff_excerpt>");
            assertThat(changeCtx).contains("<agent_votes");

            String riskCtx = recorder.userMessages().get(1);
            assertThat(riskCtx).contains("<acceptance_criteria>");
            assertThat(riskCtx).doesNotContain("<diff_excerpt>");
        }

        @Test
        void nullProviderReturnsEmptyNarratives() {
            var service = new NarrativeService(null);
            var result = service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(result.changeNarrative()).isEmpty();
            assertThat(result.riskNarrative()).isEmpty();
        }

        @Test
        void promptContainsGroundingInstructions() {
            var recorder = new RecordingLlmProvider(List.of("change", "risk"));
            var service = new NarrativeService(recorder);

            service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            String prompt = recorder.systemPrompts().get(0);
            assertThat(prompt).contains("changed_files");
            assertThat(prompt).contains("agent_votes");
            assertThat(prompt).containsIgnoringCase("grounding");
        }
    }

    @Nested
    class TwoPassMode {

        @Test
        void twoPassMakes4LlmCalls() {
            var recorder = new RecordingLlmProvider(List.of(
                    "1. PlCache.java modified\n2. Cache invalidation added",
                    "De cache-invalidatie is aangepast.",
                    "1. Risk level MEDIUM\n2. Moderate code impact",
                    "Het risico is beperkt."));
            var service = new NarrativeService(recorder, true);

            var result = service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(recorder.callCount()).isEqualTo(4);
            assertThat(result.changeNarrative()).isEqualTo("De cache-invalidatie is aangepast.");
            assertThat(result.riskNarrative()).isEqualTo("Het risico is beperkt.");
        }

        @Test
        void factExtractionPromptReceivesXmlContext() {
            var recorder = new RecordingLlmProvider(List.of(
                    "1. fact one", "narrative one",
                    "1. fact two", "narrative two"));
            var service = new NarrativeService(recorder, true);

            service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            String extractionMessage = recorder.userMessages().get(0);
            assertThat(extractionMessage).contains("<narrative_request type=\"change\">");
            assertThat(extractionMessage).contains("<changed_files>");
            assertThat(extractionMessage).contains("<diff_excerpt>");

            String extractionPrompt = recorder.systemPrompts().get(0);
            assertThat(extractionPrompt).containsIgnoringCase("fact");
            assertThat(extractionPrompt).containsIgnoringCase("numbered");
        }

        @Test
        void narrativePromptReceivesExtractedFacts() {
            var recorder = new RecordingLlmProvider(List.of(
                    "1. PlCache modified\n2. BSN invalidation",
                    "De cache is aangepast.",
                    "1. Risk MEDIUM",
                    "Het risico is laag."));
            var service = new NarrativeService(recorder, true);

            service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            String narrativeMessage = recorder.userMessages().get(1);
            assertThat(narrativeMessage).contains("<extracted_facts>");
            assertThat(narrativeMessage).contains("PlCache modified");
            assertThat(narrativeMessage).contains("<narrative_from_facts type=\"change\">");
        }

        @Test
        void fallsBackToSinglePassWhenExtractionReturnsEmpty() {
            var recorder =
                    new RecordingLlmProvider(List.of("", "Fallback change narrative", "", "Fallback risk narrative"));
            var service = new NarrativeService(recorder, true);

            var result = service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(recorder.callCount()).isEqualTo(4);
            assertThat(result.changeNarrative()).isEqualTo("Fallback change narrative");
            assertThat(result.riskNarrative()).isEqualTo("Fallback risk narrative");

            String fallbackMessage = recorder.userMessages().get(1);
            assertThat(fallbackMessage)
                    .as("Fallback should send full XML context, not extracted_facts")
                    .contains("<narrative_request type=\"change\">");
        }

        @Test
        void singlePassDefaultStillWorksWith2Calls() {
            var recorder = new RecordingLlmProvider(List.of("single-pass change", "single-pass risk"));
            var service = new NarrativeService(recorder, false);

            var result = service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(recorder.callCount()).isEqualTo(2);
            assertThat(result.changeNarrative()).isEqualTo("single-pass change");
            assertThat(result.riskNarrative()).isEqualTo("single-pass risk");
        }

        @Test
        void riskFactExtractionReceivesAcceptanceCriteria() {
            var recorder = new RecordingLlmProvider(List.of(
                    "1. change fact", "change narrative",
                    "1. risk fact with AC", "risk narrative"));
            var service = new NarrativeService(recorder, true);

            service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            String riskExtractionMessage = recorder.userMessages().get(2);
            assertThat(riskExtractionMessage).contains("<acceptance_criteria>");
            assertThat(riskExtractionMessage).doesNotContain("<diff_excerpt>");
        }
    }

    @Nested
    class EvidenceFirstWithDomainKeywords {

        @Test
        void dictionaryTermsAppearInAugmentedEvidence() {
            var dict = dictOf("GBA-V", "BSN");

            var recorder = new RecordingLlmProvider(List.of(
                    "[\"GBA-V\"]",
                    "{\"change_narrative\":\"Narrative.\",\"claims\":[]}",
                    "Change narrative met GBA-V en BSN.",
                    "[\"GBA-V\"]",
                    "{\"risk_narrative\":\"Risk.\",\"claims\":[]}",
                    "Risk narrative."));

            var service = new NarrativeService(recorder, false, true, false, 0, dict);
            service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(service.lastEvidence).isNotNull();
            List<String> domainValues = service.lastEvidence.facts().stream()
                    .filter(f -> f.type() == EvidenceFact.FactType.DOMAIN_TERM)
                    .map(EvidenceFact::value)
                    .toList();
            assertThat(domainValues).contains("GBA-V");
        }

        @Test
        void augmentationFailsOpenWhenDictionaryEmpty() {
            var recorder = new RecordingLlmProvider(List.of(
                    "[]",
                    "{\"change_narrative\":\"Change.\",\"claims\":[]}",
                    "Change.",
                    "[]",
                    "{\"risk_narrative\":\"Risk.\",\"claims\":[]}",
                    "Risk."));

            var service = new NarrativeService(recorder, false, true, false, 0, null);
            var result = service.generate(fullChangeData(), fullJiraData(), unanimousRisk());

            assertThat(result.changeNarrative()).isNotEmpty();
            assertThat(result.riskNarrative()).isNotEmpty();
        }
    }

    // --- fixtures ---

    private static CommitInfo commit(
            String hash,
            String author,
            String email,
            String role,
            String team,
            String message,
            int files,
            int ins,
            int del) {
        return new CommitInfo(
                hash, author, email, role, team, message, Instant.parse("2025-01-15T10:00:00Z"), files, ins, del);
    }

    private static ChangeData fullChangeData() {
        return ChangeData.from(
                List.of(
                        commit(
                                "a1b2c3d",
                                "Alice Dev",
                                "alice@example.nl",
                                "BE",
                                "Team Alpha",
                                "fix: invalidate cache on PL update",
                                2,
                                15,
                                5),
                        commit(
                                "e4f5g6h",
                                "Bob Test",
                                "bob@example.nl",
                                "TE",
                                "Team Alpha",
                                "test: add cache invalidation tests",
                                1,
                                35,
                                0)),
                "diff --git a/f.java b/f.java\n@@ -1 +1 @@\n-old\n+new\n",
                List.of(
                        "src/main/java/PlCache.java",
                        "src/test/java/PlCacheTest.java",
                        "migrations/V42__add_cache_ttl.sql"));
    }

    private static JiraData fullJiraData() {
        return new JiraData(
                List.of("PROJ-128530", "PROJ-128531"),
                "GBA-V query for category 08 returns stale data after PL update.",
                "- After PL update, subsequent query returns fresh data\n- Cache TTL respects configured window",
                new JiraData.Impact(Map.of(
                        "MuniPortal", true,
                        "Database(s) wijzigingen", true)),
                new JiraData.TestEvidence("Verified locally", true, "42 passed, 0 failed", "78%"),
                new JiraData.Deployment(true, false, false, false),
                true,
                "5.13.0");
    }

    private static JiraData sparseJiraData() {
        return new JiraData(
                List.of("PROJ-99999"),
                "none",
                "none",
                new JiraData.Impact(Map.of()),
                JiraData.TestEvidence.none(),
                JiraData.Deployment.defaults(),
                false,
                "");
    }

    private static RiskAssessment unanimousRisk() {
        return RiskAssessment.fromConsensus(
                List.of(
                        new AgentVote("diff-analyst", RiskLevel.MEDIUM, 0.80, "Moderate code impact."),
                        new AgentVote("process-assessor", RiskLevel.MEDIUM, 0.75, "Moderate process impact."),
                        new AgentVote("evidence-checker", RiskLevel.MEDIUM, 0.85, "Evidence quality acceptable.")),
                1);
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
}
