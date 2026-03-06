package nl.example.qualityreport.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;

/**
 * Describes a single end-to-end report scenario: what code changes to
 * simulate, what Jira context to provide, and what the report must contain.
 */
record ReportE2EScenario(
        String id,
        String description,
        Difficulty difficulty,
        RepoRecipe repo,
        List<RepoRecipe> additionalRepos,
        JiraFixture jira,
        List<String> criticalFacts,
        List<String> knownAbsentClaims,
        String branchName,
        String fixVersion,
        double minFactRecallPercent) {

    ReportE2EScenario(
            String id,
            String description,
            Difficulty difficulty,
            RepoRecipe repo,
            JiraFixture jira,
            List<String> criticalFacts,
            List<String> knownAbsentClaims,
            String branchName,
            String fixVersion,
            double minFactRecallPercent) {
        this(
                id,
                description,
                difficulty,
                repo,
                List.of(),
                jira,
                criticalFacts,
                knownAbsentClaims,
                branchName,
                fixVersion,
                minFactRecallPercent);
    }

    boolean isMultiRepo() {
        return !additionalRepos.isEmpty();
    }

    List<RepoRecipe> allRepos() {
        var all = new ArrayList<>(List.of(repo));
        all.addAll(additionalRepos);
        return all;
    }

    @Override
    public String toString() {
        return id;
    }

    enum Difficulty {
        SMALL(75.0),
        MEDIUM(60.0),
        LARGE(50.0);

        final double defaultMinRecall;

        Difficulty(double defaultMinRecall) {
            this.defaultMinRecall = defaultMinRecall;
        }
    }

    record RepoRecipe(String moduleName, List<CommitSpec> commits, List<RosterEntry> roster) {}

    record CommitSpec(String message, String authorName, String authorEmail, String role, Map<String, String> files) {}

    record RosterEntry(String name, String email, List<String> emails, String role, String team) {
        RosterEntry(String name, String email, String role, String team) {
            this(name, email, List.of(), role, team);
        }
    }

    record JiraFixture(
            List<String> tickets,
            String description,
            String acceptanceCriteria,
            List<ImpactOverride> impactOverrides,
            String manualTestDescription,
            boolean hasAutomatedTests,
            String automatedTestResult,
            String coveragePercent,
            boolean standardDeployment,
            boolean featureToggle,
            boolean manualScript,
            boolean hypercare,
            boolean dodComplete) {}

    record ImpactOverride(String area, boolean checked) {}

    ChangeData toChangeData() {
        var allFiles = new ArrayList<String>();
        for (RepoRecipe r : allRepos()) {
            for (CommitSpec commit : r.commits()) {
                allFiles.addAll(commit.files().keySet());
            }
        }
        return ChangeData.from(List.of(), "", allFiles);
    }

    JiraData toJiraData() {
        Map<String, Boolean> areas = new LinkedHashMap<>();
        if (jira.impactOverrides() != null) {
            for (ImpactOverride o : jira.impactOverrides()) {
                areas.put(o.area(), o.checked());
            }
        }
        return new JiraData(
                jira.tickets(),
                jira.description(),
                jira.acceptanceCriteria(),
                new JiraData.Impact(areas),
                new JiraData.TestEvidence(
                        jira.manualTestDescription(),
                        jira.hasAutomatedTests(),
                        jira.automatedTestResult(),
                        jira.coveragePercent()),
                new JiraData.Deployment(
                        jira.standardDeployment(), jira.featureToggle(), jira.manualScript(), jira.hypercare()),
                jira.dodComplete(),
                fixVersion());
    }
}
