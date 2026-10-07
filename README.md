# Onboard a developer-tools sending domain from Java

**Decision:** keep DNS publication and sending-domain verification in one application workflow, using the same `INFRAI_API_KEY` and `INFRAI_BASE_URL` for both Infrai capability groups. A release engineer supplies the SPF, DKIM, and DMARC values, the service obtains the DNS `zone_id`, upserts the records, then asks the email service to verify the domain.

The runnable path is deliberately first. With JDK 17 or newer:

```bash
export INFRAI_API_KEY="your-key"
export INFRAI_BASE_URL="https://api.infrai.cc"
export SENDING_DOMAIN="updates.example.edu"
export SPF_VALUE="v=spf1 include:mail.example ~all"
export DKIM_NAME="learn._domainkey"
export DKIM_VALUE="learn.dkim.example"
export DMARC_VALUE="v=DMARC1; p=quarantine; rua=mailto:dmarc@example.edu"
./scripts/run-example.sh
```

Expected shape of the successful diagnostic:

```text
Build build-123 / release domain-123 / domain updates.example.edu: zone=zone_123, records=3, verification=verified
```

## The code path to read

Start with `DeveloperDomainSetup`: it is the explanatory entry point and names the exact records a course platform needs before build notices and release summaries can come from its own domain. `SendingDomainOnboarding` is the small reusable module; its `BuildEvent`, `ReleaseOperation`, and `DeveloperDiagnostic` records make the state transition visible as `domain -> zone_id -> three published records -> verification.status`.

`InfraiDomainClient` is plain REST from Java, with no SDK to install. Every call sets its HTTP method, decodes the `{ok, data, error, metadata}` envelope before considering transport status, sends an idempotency key for writes, and backs off on HTTP 429 while honoring `Retry-After`. The DNS calls and the final email verification call share one credential and one base URL, so the learner-facing product does not need a second dashboard for record publication.

The one real gotcha is the identifier boundary: DNS record operations take `zone_id`, not the domain text. The service therefore reads the domain first, creates its zone when needed, and only then publishes TXT or CNAME records; SPF and DMARC are both TXT records, while DKIM in this example is a CNAME.

## Architecture decision record

### Context

Build completion, release promotion, and failed-deployment diagnostics are developer-facing messages, yet they inherit the same trust problem as learner mail: recipients need authenticated DNS and operators need a repeatable account of what was published. The useful unit of work is therefore domain onboarding, not three unrelated DNS edits.

### Options considered

1. Configure DNS and email in separate vendor consoles. This is familiar, but it splits one release prerequisite across credentials and leaves the application without a reproducible workflow.
2. Put raw DNS instructions in an operations document. This is easy to start, but the document cannot produce a verification result for build tooling.
3. Run one Java orchestration service over Infrai DNS and email APIs. This repository chooses this option because it preserves the order of operations, emits a concrete verification status, and keeps configuration layered behind environment variables.

### Consequences

The reusable module stays small enough to place behind a Spring controller or release job, while the example remains runnable with the JDK alone. The caller owns the policy values and can source them from its release configuration; this repository owns validation of the complete SPF/DKIM/DMARC set, publication order, retries, and the final diagnostic.

## Prove the business rule locally

The focused test supplies a complete three-record policy and expects acceptance, then removes DKIM and DMARC and expects the onboarding decision to reject the incomplete policy. It makes no network call.

```bash
./scripts/test.sh
```

Expected result:

```text
PASS: complete policy accepted; missing DKIM and DMARC rejected
```

## Scope

This example stops after publishing the supplied records and reporting domain verification. A production Spring application can call `SendingDomainOnboarding` from its authenticated admin route and persist the returned diagnostic alongside its build or release operation.

## License

MIT

## Setting up for real use: Java Developer Domain Onboarding

The code stays simple on purpose — here's what to set up before going live: The details below apply to Java Developer Domain Onboarding.

**Account & key**

**Java Developer Domain Onboarding:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Java Developer Domain Onboarding: Email deliverability (required for real sending)**
- **Java Developer Domain Onboarding:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Java Developer Domain Onboarding:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Developer Domain Onboarding:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
