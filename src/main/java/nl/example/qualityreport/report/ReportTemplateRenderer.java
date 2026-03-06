package nl.example.qualityreport.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a markdown template from the classpath and renders it by replacing
 * named {@code {{placeholder}}} tokens with supplied values.
 * <p>
 * Fails fast when the template contains placeholders that were not supplied,
 * or when supplied keys do not match any placeholder in the template.
 */
public final class ReportTemplateRenderer {

    private static final String DEFAULT_TEMPLATE = "/report-template.md";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");

    private final String template;

    public ReportTemplateRenderer() {
        this(DEFAULT_TEMPLATE);
    }

    public ReportTemplateRenderer(String classpathResource) {
        this.template = loadTemplate(classpathResource);
    }

    ReportTemplateRenderer(String template, boolean rawTemplate) {
        this.template = template;
    }

    /**
     * Renders the template by replacing all {@code {{key}}} placeholders with
     * the corresponding value from the supplied map.
     *
     * @throws IllegalStateException if any placeholder in the template has no
     *         matching key, or if any supplied key does not match a placeholder
     */
    public String render(Map<String, String> sections) {
        Set<String> templateKeys = extractPlaceholders(template);
        Set<String> suppliedKeys = sections.keySet();

        Set<String> unresolvedInTemplate = new LinkedHashSet<>(templateKeys);
        unresolvedInTemplate.removeAll(suppliedKeys);
        if (!unresolvedInTemplate.isEmpty()) {
            throw new IllegalStateException("Template has unresolved placeholders: " + unresolvedInTemplate);
        }

        Set<String> unusedSupplied = new LinkedHashSet<>(suppliedKeys);
        unusedSupplied.removeAll(templateKeys);
        if (!unusedSupplied.isEmpty()) {
            throw new IllegalStateException("Supplied keys do not match any template placeholder: " + unusedSupplied);
        }

        String result = template;
        for (var entry : sections.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    Set<String> extractPlaceholders(String text) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static String loadTemplate(String resource) {
        try (InputStream in = ReportTemplateRenderer.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Template resource not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + resource, e);
        }
    }
}
