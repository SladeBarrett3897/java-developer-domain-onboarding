package dev.learning.domain;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

public final class DeveloperDomainSetup {
    private DeveloperDomainSetup() {}

    public static void main(String[] args) {
        DomainConfig config = DomainConfig.fromEnvironment();
        SendingDomainOnboarding service = new SendingDomainOnboarding(
                new InfraiDomainClient(HttpClient.newHttpClient(), config));

        List<SendingDomainOnboarding.DnsRecord> records = List.of(
                new SendingDomainOnboarding.DnsRecord("TXT", "@", required("SPF_VALUE"), 3600),
                new SendingDomainOnboarding.DnsRecord("CNAME", required("DKIM_NAME"), required("DKIM_VALUE"), 3600),
                new SendingDomainOnboarding.DnsRecord("TXT", "_dmarc", required("DMARC_VALUE"), 3600));

        String operationId = UUID.randomUUID().toString();
        SendingDomainOnboarding.DeveloperDiagnostic result = service.onboard(
                new SendingDomainOnboarding.BuildEvent("build-" + operationId, "local"),
                new SendingDomainOnboarding.ReleaseOperation("domain-" + operationId, config.domain(), records));
        System.out.printf("Build %s / release %s / domain %s: zone=%s, records=%d, verification=%s%n",
                result.buildId(), result.releaseId(), result.domain(), result.zoneId(),
                result.recordsWritten(), result.verificationStatus());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
