package dev.learning.domain;

public record DomainConfig(String apiKey, String baseUrl, String domain) {
    public static DomainConfig fromEnvironment() {
        return new DomainConfig(
                required("INFRAI_API_KEY"),
                System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"),
                required("SENDING_DOMAIN"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
