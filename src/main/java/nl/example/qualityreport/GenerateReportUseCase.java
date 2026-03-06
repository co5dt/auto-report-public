package nl.example.qualityreport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.analysis.AgentDeliberation;
import nl.example.qualityreport.analysis.ToolExecutor;
import nl.example.qualityreport.config.AppConfig;
import nl.example.qualityreport.git.CodeReferenceResolver;
import nl.example.qualityreport.git.GitExtractor;
import nl.example.qualityreport.git.GitFileContentProvider;
import nl.example.qualityreport.llm.LlmProvider;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.ChangeSummaryPrinter;
import nl.example.qualityreport.model.JiraData;
import nl.example.qualityreport.model.RepoFile;
import nl.example.qualityreport.model.RiskAssessment;
import nl.example.qualityreport.prompt.InteractivePrompter;
import nl.example.qualityreport.report.ReportGenerator;
import nl.example.qualityreport.report.evidence.DomainKeywordDictionary;
import nl.example.qualityreport.roster.Roster;

/**
 * Application-level orchestration for generating a quality report.
 * Owns the full workflow: extract, prompt, deliberate, report.
 * Decoupled from CLI framework concerns (argument parsing, exit codes).
 */
public class GenerateReportUseCase {

    private final BufferedReader in;
    private final PrintStream out;
    private final LlmProvider llm;
    private final AppConfig config;
    private final boolean twoPass;

    public GenerateReportUseCase(BufferedReader in, PrintStream out, LlmProvider llm, AppConfig config) {
        this(in, out, llm, config, false);
    }

    public GenerateReportUseCase(
            BufferedReader in, PrintStream out, LlmProvider llm, AppConfig config, boolean twoPass) {
        this.in = in;
        this.out = out;
        this.llm = llm;
        this.config = config;
        this.twoPass = twoPass;
    }

    public Result execute(
            List<Path> repos, String branch, String target, Path rosterPath, Path outputDir, boolean noLlm)
            throws IOException {
        Roster roster = Roster.load(rosterPath);
        GitExtractor extractor = new GitExtractor(roster);

        GitExtractor.ExtractionResult extraction = extractor.extractWithWarnings(repos, branch, target);
        ChangeData changes = extraction.changeData();

        for (String warning : extraction.warnings()) {
            out.println("WARNING: " + warning);
        }

        if (changes.commits().isEmpty() && changes.changedFiles().isEmpty()) {
            throw new IOException(
                    "No commits or changed files between '%s' and '%s'. Nothing to report.".formatted(branch, target));
        }

        ChangeSummaryPrinter.print(changes, out);
        out.println();

        InteractivePrompter prompter = new InteractivePrompter(in, out, changes);
        JiraData jira = prompter.collect();

        if (noLlm) {
            String json = serializePayload(changes, jira);
            return new Result(json, null);
        }

        RiskAssessment risk = deliberate(repos, branch, target, changes, jira);

        DomainKeywordDictionary dictionary = loadDictionary();
        ReportGenerator reportGen = new ReportGenerator(
                llm,
                outputDir,
                LocalDate.now(),
                twoPass,
                config.evidenceFirstEnabled(),
                config.narrativeVerifyEnabled(),
                config.repairMaxRetries(),
                dictionary,
                config.debugArtifactsEnabled());
        Path reportPath = reportGen.generate(changes, jira, risk);

        return new Result(null, reportPath);
    }

    private DomainKeywordDictionary loadDictionary() {
        String path = config.domainKeywordsPath();
        if (path != null) {
            return DomainKeywordDictionary.loadFromPath(Path.of(path));
        }
        return DomainKeywordDictionary.loadFromClasspath("/domain-keywords.json");
    }

    private RiskAssessment deliberate(List<Path> repos, String branch, String target, ChangeData changes, JiraData jira)
            throws IOException {
        AgentDeliberation deliberation;
        if (config.toolLoopEnabled()) {
            ToolExecutor toolExecutor = buildToolExecutor(repos, branch, target, changes.repoFiles());
            deliberation = new AgentDeliberation(llm, config.agentCount(), true, toolExecutor);
        } else {
            deliberation = new AgentDeliberation(llm);
        }
        return deliberation.assess(changes, jira);
    }

    private static ToolExecutor buildToolExecutor(
            List<Path> repos, String branch, String target, List<RepoFile> changedRepoFiles) throws IOException {
        var contentProvider = new GitFileContentProvider();
        List<RepoFile> allTracked = new ArrayList<>();
        for (Path repo : repos) {
            String repoName = repo.getFileName().toString();
            for (String path : contentProvider.listTrackedFiles(repo, branch)) {
                allTracked.add(new RepoFile(repoName, path));
            }
        }
        var resolver = new CodeReferenceResolver(allTracked, changedRepoFiles, true);
        return new ToolExecutor(contentProvider, resolver, repos, branch, target);
    }

    private static String serializePayload(ChangeData changes, JiraData jira) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("changes", changes);
        payload.put("jira", jira);
        return mapper.writeValueAsString(payload);
    }

    public record Result(String jsonPayload, Path reportPath) {
        public boolean isJsonMode() {
            return jsonPayload != null;
        }
    }
}
