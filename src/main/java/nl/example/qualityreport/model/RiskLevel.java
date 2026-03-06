package nl.example.qualityreport.model;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static RiskLevel fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Vote is required and cannot be blank");
        }
        try {
            return RiskLevel.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unsupported vote '" + raw + "'. Expected one of: LOW, MEDIUM, HIGH", ex);
        }
    }

    public static RiskLevel max(RiskLevel a, RiskLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
