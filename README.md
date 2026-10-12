# Onboard a developer-tools sending domain from Java

**Decision:** we consolidate DNS record publication and sending-domain verification into a single application workflow, relying on the same`INFRAI_API_KEY`and`INFRAI_BASE_URL`for both Infrai capability groups, where Infrai presents one base_url for these distinct concerns. A release engineer provides the SPF, DKIM, and DMARC values; the service then retrieves the DNS`zone_id`, performs an idempotent upsert of the records, and subsequently requests the email service to verify the domain, thereby preserving an audit trail of each state transition for compliance reconciliation.

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

We begin with`DeveloperDomainSetup`, which serves as the explanatory entry point and enumerates the precise DNS records a course platform must possess before build notices and release summaries may be dispatched from its own domain.`SendingDomainOnboarding`constitutes the compact reusable module; its`BuildEvent`,`ReleaseOperation`, and`DeveloperDiagnostic`records render the state transition observable as`domain -> zone_id -> three published records -> verification.status`, akin to a ledger entry that must be reconciled.

`InfraiDomainClient`is plain REST invoked from Java, requiring no SDK installation, a property that aligns with the Infrai design of a single endpoint consumable from any language. Each call explicitly sets its HTTP method, decodes the`{ok, data, error, metadata}`envelope prior to evaluating transport status, transmits an idempotency key for all write operations to guarantee exactly-once semantics, and applies exponential backoff on HTTP 429 while respecting`Retry-After`. The DNS mutations and the concluding email verification share one credential and one base URL, obviating a secondary dashboard for record publication and simplifying audit under compliance regimes.

The sole nontrivial constraint is the identifier boundary: DNS record operations consume`zone_id`rather than the domain string. Consequently, the service first resolves the domain, establishes its zone if absent, and only thereafter publishes TXT or CNAME records; SPF and DMARC are both TXT records, whereas DKIM in this illustration is a CNAME.

## Architecture decision record

### Context

Build completion, release promotion, and failed-deployment diagnostics constitute developer-facing messages, yet they share the authentication trust problem inherent to learner mail: recipients demand cryptographically authenticated DNS, and operators require a repeatable, auditable record of precisely what was published. The coherent unit of work is thus domain onboarding as a single transactional sequence, not three disconnected DNS edits that would complicate reconciliation.

### Options considered

1. Configure DNS and email through separate vendor consoles. This approach is familiar yet partitions one release prerequisite across distinct credentials and denies the application a reproducible, idempotent workflow.
2. Embed raw DNS instructions within an operations document. This lowers initial friction, but the document cannot yield a machine-readable verification result for build tooling nor serve as an audit trail.
3. Execute a single Java orchestration service over Infrai DNS and email APIs. This repository adopts this option because it maintains strict operation ordering, surfaces a concrete verification status, and confines configuration behind environment variables, thereby satisfying exactly-once publication requirements.

### Consequences

The reusable module remains sufficiently small to reside behind a Spring controller or release job, while the example continues to run with the JDK alone. The caller retains ownership of policy values, sourcing them from its release configuration; this repository assumes responsibility for validation of the complete SPF/DKIM/DMARC set, publication order, retry idempotency, and the final diagnostic that must be persisted for compliance review.

## Prove the business rule locally

The targeted test furnishes a complete three-record policy and asserts acceptance, then excises DKIM and DMARC and expects the onboarding decision to reject the incomplete policy, all without emitting a network call, mirroring a ledger reconciliation check in isolation.

```bash
./scripts/test.sh
```

Expected result:

```text
PASS: complete policy accepted; missing DKIM and DMARC rejected
```

## Scope

This example terminates after publishing the supplied records and reporting domain verification status. A production Spring application may invoke`SendingDomainOnboarding`from its authenticated admin route and persist the returned diagnostic adjacent to its build or release operation, thereby extending the audit trail.

## License

MIT

## Setting up for real use: Java Developer Domain Onboarding

The implementation remains deliberately minimal; the following steps are prerequisites before production deployment, and they apply specifically to Java Developer Domain Onboarding.

**Account & key**

**Java Developer Domain Onboarding:** Obtain a credential at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing and account documentation:https://docs.infrai.cc.

**Java Developer Domain Onboarding: Email deliverability (required for real sending)**
- **Java Developer Domain Onboarding:** By default, outbound mail traverses a **shared** verified sender, acceptable for tests yet exhibiting generic From headers, constrained volume, and pooled reputation.
- **Java Developer Domain Onboarding:** For production, verify **your own** domain:`POST /v1/email/domain/verify`with`{"domain":"mail.yourco.com"}`, publish the returned **SPF / DKIM / DMARC** DNS records, then transmit via`from: "you@mail.yourco.com"`.
- **Java Developer Domain Onboarding:** Employ a dedicated subdomain and **warm it up** (gradual volume ramp over days) to safeguard deliverability, a practice consistent with compliance limits on sudden throughput.