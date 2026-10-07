package dev.learning.domain;

import java.util.List;
import java.util.Map;

public final class SendingDomainOnboarding {
    public record DnsRecord(String recordType, String name, String content, int ttl) {}
    public record BuildEvent(String buildId, String commitSha) {}
    public record ReleaseOperation(String releaseId, String domain, List<DnsRecord> records) {}
    public record DeveloperDiagnostic(String buildId, String releaseId, String domain,
                                      String zoneId, int recordsWritten, String verificationStatus) {}

    private final InfraiDomainClient infrai;

    public SendingDomainOnboarding(InfraiDomainClient infrai) {
        this.infrai = infrai;
    }

    public DeveloperDiagnostic onboard(BuildEvent build, ReleaseOperation release) {
        validate(release.records());
        String runId = "release-" + release.releaseId();
        String zoneId = ensureZone(release.domain(), runId);
        for (int i = 0; i < release.records().size(); i++) {
            DnsRecord record = release.records().get(i);
            infrai.upsertRecord(zoneId, record.recordType(), record.name(), record.content(),
                    record.ttl(), runId + "-record-" + i);
        }
        Map<String, String> verification = infrai.verifySendingDomain(release.domain(), runId + "-verify");
        return new DeveloperDiagnostic(build.buildId(), release.releaseId(), release.domain(), zoneId,
                release.records().size(), verification.getOrDefault("status", "pending"));
    }

    private String ensureZone(String domain, String runId) {
        try {
            return requireZoneId(infrai.getZone(domain));
        } catch (InfraiException error) {
            if (error.statusCode() != 404) throw error;
            return requireZoneId(infrai.addZone(domain, runId + "-zone"));
        }
    }

    private static String requireZoneId(Map<String, String> zone) {
        String zoneId = zone.get("zone_id");
        if (zoneId == null || zoneId.isBlank()) throw new IllegalStateException("zone_id missing from response");
        return zoneId;
    }

    static void validate(List<DnsRecord> records) {
        long spf = records.stream().filter(r -> r.recordType().equals("TXT") && r.content().startsWith("v=spf1")).count();
        long dmarc = records.stream().filter(r -> r.recordType().equals("TXT") && r.name().startsWith("_dmarc")).count();
        long dkim = records.stream().filter(r -> r.recordType().equals("CNAME") && r.name().contains("._domainkey")).count();
        if (spf != 1 || dmarc != 1 || dkim < 1) {
            throw new IllegalArgumentException("Provide one SPF TXT, one DMARC TXT, and at least one DKIM CNAME record");
        }
    }
}
