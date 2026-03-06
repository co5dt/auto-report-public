package nl.example.qualityreport.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.example.qualityreport.llm.LlmProvider;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.report.evidence.DomainKeywordDictionary;

/**
 * Orchestrates report generation: narratives, markdown assembly, file writing.
 * Narrative generation is delegated to {@link NarrativeService}.
 */
public class ReportGenerator {

    private final NarrativeService narrativeService;
    private final ReportTemplateRenderer templateRenderer;
    private final Path outputDir;
    private final LocalDate reportDate;

    public ReportGenerator(LlmProvider llm) {
        this(llm, Path.of("."), LocalDate.now(), false, false, false, 0, null);
    }

    public ReportGenerator(LlmProvider llm, Path outputDir, LocalDate reportDate) {
        this(llm, outputDir, reportDate, false, false, false, 0, null);
    }

    public ReportGenerator(LlmProvider llm, Path outputDir, LocalDate reportDate, boolean twoPass) {
        this(llm, outputDir, reportDate, twoPass, false, false, 0, null);
    }

    public ReportGenerator(
            LlmProvider llm, Path outputDir, LocalDate reportDate, boolean twoPass, boolean evidenceFirst) {
        this(llm, outputDir, reportDate, twoPass, evidenceFirst, false, 0, null);
    }

    public ReportGenerator(
            LlmProvider llm,
            Path outputDir,
            LocalDate reportDate,
            boolean twoPass,
            boolean evidenceFirst,
            boolean verifyEnabled,
            int repairMaxRetries) {
        this(llm, outputDir, reportDate, twoPass, evidenceFirst, verifyEnabled, repairMaxRetries, null);
    }

    public ReportGenerator(
            LlmProvider llm,
            Path outputDir,
            LocalDate reportDate,
            boolean twoPass,
            boolean evidenceFirst,
            boolean verifyEnabled,
            int repairMaxRetries,
            DomainKeywordDictionary dictionary) {
        this(llm, outputDir, reportDate, twoPass, evidenceFirst, verifyEnabled, repairMaxRetries, dictionary, false);
    }

    public ReportGenerator(
            LlmProvider llm,
            Path outputDir,
            LocalDate reportDate,
            boolean twoPass,
            boolean evidenceFirst,
            boolean verifyEnabled,
            int repairMaxRetries,
            DomainKeywordDictionary dictionary,
            boolean debugArtifacts) {
        this.narrativeService = new NarrativeService(
                llm, twoPass, evidenceFirst, verifyEnabled, repairMaxRetries, dictionary, debugArtifacts);
        this.templateRenderer = new ReportTemplateRenderer();
        this.outputDir = outputDir;
        this.reportDate = reportDate;
    }

    public Path generate(ChangeData changes, JiraData jira, RiskAssessment risk) throws IOException {
        NarrativeService.Narratives narratives = narrativeService.generate(changes, jira, risk);

        String markdown = assembleMarkdown(changes, jira, risk, narratives);
        ReportTemplateValidator.requireValid(markdown);

        String filename = ReportFileNamer.filename(jira.tickets(), reportDate);
        Path outputPath = outputDir.resolve(filename);
        Files.createDirectories(outputPath.getParent());
        return writeWithCollisionSafety(outputPath, markdown);
    }

    static final int MAX_COLLISION_SUFFIX = 99;

    private Path writeWithCollisionSafety(Path basePath, String content) throws IOException {
        try {
            Files.writeString(basePath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return basePath;
        } catch (FileAlreadyExistsException ignored) {
        }

        String name = basePath.getFileName().toString();
        String base = name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;

        for (int i = 2; i <= MAX_COLLISION_SUFFIX; i++) {
            Path candidate = basePath.getParent().resolve(base + "-" + i + ".md");
            try {
                Files.writeString(candidate, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
            }
        }

        throw new IOException("Could not write report: all filenames up to suffix " + MAX_COLLISION_SUFFIX
                + " already exist for " + basePath);
    }

    private String assembleMarkdown(
            ChangeData changes, JiraData jira, RiskAssessment risk, NarrativeService.Narratives narratives) {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("fix_version", ReportSections.headerFixVersion(jira));
        slots.put("primary_ticket", ReportSections.headerPrimaryTicket(jira));
        slots.put("team", ReportSections.headerTeam(changes));
        slots.put("date", ReportSections.headerDate(reportDate));
        slots.put("risk_score", ReportSections.headerRiskScore(risk));
        slots.put("tickets_body", ReportSections.ticketsBody(jira));
        slots.put("change_narrative_body", ReportSections.changeNarrativeBody(narratives.changeNarrative()));
        slots.put("changes_per_type", ReportSections.changesPerType(changes));
        slots.put("impact_checklist", ReportSections.impactChecklistBody(jira));
        slots.put("acceptance_criteria_body", ReportSections.acceptanceCriteriaBody(jira));
        slots.put("risk_narrative_body", ReportSections.riskNarrativeBody(narratives.riskNarrative()));
        slots.put("deliberation_table", ReportSections.deliberationTable(risk));
        slots.put("manual_test_body", ReportSections.manualTestBody(jira));
        slots.put("automated_test_body", ReportSections.automatedTestBody(jira));
        slots.put("deployment_body", ReportSections.deploymentBody(jira));
        slots.put("dod_body", ReportSections.dodBody(jira));
        return templateRenderer.render(slots);
    }
}
