package nl.example.qualityreport.analysis;

import static nl.example.qualityreport.context.XmlUtils.escapeXml;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import nl.example.qualityreport.git.CodeReferenceResolver;
import nl.example.qualityreport.git.CodeReferenceResolver.Resolution;
import nl.example.qualityreport.git.GitFileContentProvider;
import nl.example.qualityreport.model.RepoFile;
import nl.example.qualityreport.model.ToolRequest;

/**
 * Executes validated tool requests from agents by resolving references
 * and reading file contents from git revisions.
 *
 * <p>Returns XML-formatted results for injection into agent context.
 * When resolution yields a repo-qualified match, the file is read from
 * exactly that repository to prevent wrong-repo reads.
 */
public class ToolExecutor {

    private final GitFileContentProvider contentProvider;
    private final CodeReferenceResolver resolver;
    private final Map<String, Path> reposByName;
    private final List<Path> repoPaths;
    private final String branch;
    private final String target;

    public ToolExecutor(
            GitFileContentProvider contentProvider,
            CodeReferenceResolver resolver,
            List<Path> repoPaths,
            String branch,
            String target) {
        this.contentProvider = contentProvider;
        this.resolver = resolver;
        this.repoPaths = repoPaths;
        this.reposByName =
                repoPaths.stream().collect(Collectors.toMap(p -> p.getFileName().toString(), p -> p, (a, b) -> a));
        this.branch = branch;
        this.target = target;
    }

    /**
     * Result of executing a tool request.
     *
     * @param xml       the XML string to inject into context
     * @param resolved  whether the reference was successfully resolved
     * @param strategy  which resolution strategy was used
     */
    public record ToolResult(String xml, boolean resolved, String strategy) {}

    /**
     * Executes a tool request: resolves the reference, reads file content,
     * and returns XML-formatted result for context injection.
     */
    public ToolResult execute(ToolRequest request) {
        Resolution resolution = resolver.resolve(request.ref(), true);

        return switch (resolution.status()) {
            case UNIQUE_MATCH -> executeRead(request, resolution.repoMatches().getFirst(), resolution.strategy());
            case AMBIGUOUS -> ambiguousResult(request, resolution.repoMatches(), resolution.strategy());
            case NO_MATCH -> noMatchResult(request);
        };
    }

    private ToolResult executeRead(ToolRequest request, RepoFile resolved, String strategy) {
        String revision = "branch".equals(request.revision()) ? branch : target;
        String canonicalPath = resolved.path();

        List<Path> searchPaths = resolveRepoPaths(resolved);

        for (Path repoPath : searchPaths) {
            try {
                Optional<String> content = contentProvider.readFileAtRevision(repoPath, canonicalPath, revision);
                if (content.isPresent()) {
                    String repoAttr =
                            resolved.repoName().isEmpty() ? "" : " repo=\"" + escapeXml(resolved.repoName()) + "\"";
                    String xml =
                            """
                            <tool_result tool="%s" ref="%s" resolved_path="%s" revision="%s" strategy="%s"%s status="success">
                            %s
                            </tool_result>"""
                                    .formatted(
                                            escapeXml(request.tool()),
                                            escapeXml(request.ref()),
                                            escapeXml(canonicalPath),
                                            escapeXml(request.revision()),
                                            escapeXml(strategy),
                                            repoAttr,
                                            escapeXml(content.get()));
                    return new ToolResult(xml, true, strategy);
                }
            } catch (IOException e) {
                return errorResult(request, "IO error reading file: " + e.getMessage());
            }
        }

        return errorResult(
                request,
                "File resolved to '" + canonicalPath + "' but content not found at revision '" + request.revision()
                        + "'");
    }

    /**
     * When the resolver provides a repo-qualified match, restrict the read
     * to that specific repository. Otherwise fall back to scanning all repos.
     */
    private List<Path> resolveRepoPaths(RepoFile resolved) {
        if (!resolved.repoName().isEmpty()) {
            Path specific = reposByName.get(resolved.repoName());
            if (specific != null) {
                return List.of(specific);
            }
        }
        return repoPaths;
    }

    private ToolResult ambiguousResult(ToolRequest request, List<RepoFile> candidates, String strategy) {
        var sb = new StringBuilder();
        sb.append("<tool_result tool=\"")
                .append(escapeXml(request.tool()))
                .append("\" ref=\"")
                .append(escapeXml(request.ref()))
                .append("\" status=\"ambiguous\" strategy=\"")
                .append(escapeXml(strategy))
                .append("\" candidates=\"")
                .append(candidates.size())
                .append("\">\n");
        sb.append("Multiple files match your reference. Please specify the exact path:\n");
        for (RepoFile candidate : candidates) {
            sb.append("  - ");
            if (!candidate.repoName().isEmpty()) {
                sb.append('[').append(escapeXml(candidate.repoName())).append("] ");
            }
            sb.append(escapeXml(candidate.path())).append('\n');
        }
        sb.append("</tool_result>");
        return new ToolResult(sb.toString(), false, strategy);
    }

    private ToolResult noMatchResult(ToolRequest request) {
        String xml =
                """
                <tool_result tool="%s" ref="%s" status="no_match">
                No file found matching '%s'. Check the file path against the diff.
                </tool_result>"""
                        .formatted(escapeXml(request.tool()), escapeXml(request.ref()), escapeXml(request.ref()));
        return new ToolResult(xml, false, "none");
    }

    private ToolResult errorResult(ToolRequest request, String message) {
        String xml =
                """
                <tool_result tool="%s" ref="%s" status="error">
                %s
                </tool_result>"""
                        .formatted(escapeXml(request.tool()), escapeXml(request.ref()), escapeXml(message));
        return new ToolResult(xml, false, "error");
    }
}
