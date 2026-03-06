package nl.example.qualityreport.model;

import java.util.List;
import java.util.Map;

/**
 * All Jira-related data collected during Phase 2 interactive prompts.
 * Designed for downstream consumption by ContextBuilder and ReportGenerator.
 */
public record JiraData(
        List<String> tickets,
        String description,
        String acceptanceCriteria,
        Impact impact,
        TestEvidence testEvidence,
        Deployment deployment,
        boolean dodComplete,
        String fixVersion) {

    public record Impact(Map<String, Boolean> areas) {
        public static final List<String> ALL_AREAS = List.of(
                "MuniPortal",
                "Impact op meer dan 1 module",
                "Onderliggende techniek (monorepo)",
                "Operations deployment",
                "Database(s) wijzigingen",
                "Datamodelwijzigingen",
                "Balieprocessen",
                "Reisdocumenten/rijbewijzen proces",
                "Verkiezingen proces",
                "Verwijderen van functionaliteit",
                "Front-end / Javascript");

        public boolean isChecked(String area) {
            return areas.getOrDefault(area, false);
        }
    }

    public record TestEvidence(
            String manualDescription, boolean hasAutomatedTests, String passFail, String coveragePercent) {
        public static TestEvidence none() {
            return new TestEvidence("none", false, "", "unknown");
        }
    }

    public record Deployment(
            boolean standardDeployment, boolean featureToggle, boolean manualScript, boolean hypercare) {
        public static Deployment defaults() {
            return new Deployment(true, false, false, false);
        }
    }
}
