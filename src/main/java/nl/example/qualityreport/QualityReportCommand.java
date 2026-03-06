package nl.example.qualityreport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import nl.example.qualityreport.config.AppConfig;
import nl.example.qualityreport.git.GitExtractor;
import nl.example.qualityreport.llm.AnthropicProvider;
import nl.example.qualityreport.llm.LlmProvider;
import nl.example.qualityreport.llm.OllamaProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "quality-report",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Generate a testrapport from a local Git repo + Jira context.")
public class QualityReportCommand implements Callable<Integer> {

    @Option(
            names = {"-r", "--repo"},
            required = true,
            arity = "1..*",
            description = "Path(s) to local Git repo(s).")
    List<Path> repos;

    @Option(
            names = {"-b", "--branch"},
            required = true,
            description = "Source branch to analyse.")
    String branch;

    @Option(
            names = {"-t", "--target"},
            description = "Target branch (default: auto-detect main/master).")
    String target;

    @Option(
            names = {"--roster"},
            defaultValue = "team-roster.json",
            description = "Path to team roster JSON (default: ${DEFAULT-VALUE}).")
    Path rosterPath;

    @Option(
            names = {"--provider"},
            defaultValue = "anthropic",
            description = "LLM provider: anthropic, ollama (default: ${DEFAULT-VALUE}).")
    String provider;

    @Option(
            names = {"-m", "--model"},
            description = "LLM model name (overrides provider default and env variable).")
    String model;

    @Option(
            names = {"-o", "--output"},
            defaultValue = ".",
            description = "Output directory for the report (default: ${DEFAULT-VALUE}).")
    Path outputDir;

    @Option(
            names = {"--no-llm"},
            description = "Skip LLM analysis; output raw JSON payload only.")
    boolean noLlm;

    @Option(
            names = {"-v", "--verbose"},
            description = "Stream LLM responses and log request/response details to stderr.")
    boolean verbose;

    @Option(
            names = {"--num-ctx"},
            defaultValue = "0",
            description = "Ollama context window size in tokens (0 = use default: ${DEFAULT-VALUE}).")
    int numCtx;

    @Option(
            names = {"--two-pass"},
            description = "Use two-pass narrative generation (extract facts first, then narrate).")
    boolean twoPass;

    @Option(
            names = {"--evidence-first"},
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true",
            description = "Evidence-first extraction is enabled by default; use --no-evidence-first to disable.")
    boolean evidenceFirst;

    @Option(
            names = {"--verify-narrative"},
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true",
            description = "Narrative verification is enabled by default; use --no-verify-narrative to disable.")
    boolean verifyNarrative;

    @Option(
            names = {"--debug-artifacts"},
            description = "Emit debug artifacts (evidence, draft, verification) during generation.")
    boolean debugArtifacts;

    private final BufferedReader in;
    private final PrintStream out;
    private final PrintStream err;
    private final ProviderFactory providerFactory;

    public QualityReportCommand() {
        this(
                new BufferedReader(new InputStreamReader(System.in)),
                System.out,
                System.err,
                QualityReportCommand::createProvider);
    }

    public QualityReportCommand(BufferedReader in, PrintStream out, PrintStream err) {
        this(in, out, err, QualityReportCommand::createProvider);
    }

    QualityReportCommand(BufferedReader in, PrintStream out, PrintStream err, ProviderFactory providerFactory) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.providerFactory = providerFactory;
    }

    @Override
    public Integer call() {
        try {
            String resolvedTarget = resolveTarget();

            LlmProvider llm = noLlm ? null : providerFactory.create(provider, model, numCtx, verbose);
            AppConfig config = new AppConfig().withCliOverrides(evidenceFirst, verifyNarrative, debugArtifacts);

            var useCase = new GenerateReportUseCase(in, out, llm, config, twoPass);
            GenerateReportUseCase.Result result =
                    useCase.execute(repos, branch, resolvedTarget, rosterPath, outputDir, noLlm);

            if (result.isJsonMode()) {
                out.println(result.jsonPayload());
            } else {
                out.println();
                out.println("Report generated: " + result.reportPath());
            }
            return 0;

        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("Unexpected error: " + e.getMessage());
            return 1;
        }
    }

    private String resolveTarget() throws IOException {
        if (target != null && !target.isBlank()) {
            return target;
        }
        return GitExtractor.detectDefaultBranch(repos.getFirst());
    }

    static LlmProvider createProvider(String name, String model, int numCtx, boolean verbose) {
        return switch (name.toLowerCase()) {
            case "anthropic" -> model != null ? new AnthropicProvider(model) : new AnthropicProvider();
            case "ollama" -> new OllamaProvider(model, numCtx, verbose);
            default -> throw new IllegalArgumentException(
                    "Unknown LLM provider: '" + name + "'. Supported: anthropic, ollama");
        };
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new QualityReportCommand()).execute(args);
        System.exit(exitCode);
    }

    @FunctionalInterface
    interface ProviderFactory {
        LlmProvider create(String providerName, String modelName, int numCtx, boolean verbose);
    }
}
