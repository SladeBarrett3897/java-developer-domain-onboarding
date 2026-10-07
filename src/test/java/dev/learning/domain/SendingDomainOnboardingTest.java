package dev.learning.domain;

import java.util.List;

public final class SendingDomainOnboardingTest {
    public static void main(String[] args) {
        SendingDomainOnboarding.validate(List.of(
                new SendingDomainOnboarding.DnsRecord("TXT", "@", "v=spf1 include:mail.example ~all", 3600),
                new SendingDomainOnboarding.DnsRecord("CNAME", "learn._domainkey", "dkim.example", 3600),
                new SendingDomainOnboarding.DnsRecord("TXT", "_dmarc", "v=DMARC1; p=quarantine", 3600)));

        boolean rejected = false;
        try {
            SendingDomainOnboarding.validate(List.of(
                    new SendingDomainOnboarding.DnsRecord("TXT", "@", "v=spf1 include:mail.example ~all", 3600)));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("An incomplete teaching-domain policy must be rejected");
        System.out.println("PASS: complete policy accepted; missing DKIM and DMARC rejected");
    }
}
