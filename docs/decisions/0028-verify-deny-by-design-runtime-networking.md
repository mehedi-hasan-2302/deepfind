# 0028 — Verify deny-by-design runtime networking

## Context

DeepFind's privacy promise requires more than a loopback server address. Application source, browser assets, runtime dependencies, document parsers, monitoring facilities, and future desktop packaging can each introduce unexpected network behavior. A dependency's ability to use networking is not proof that it does so, while a short runtime trace alone cannot prove that every possible code path is offline.

The Phase 7 exit criterion requires no unnecessary external network activity and no obvious unsafe local API behavior. The result must be repeatable when dependencies or packaging change.

## Decision

Keep runtime networking deny-by-design:

- Backend production code contains no HTTP client, cloud client, telemetry exporter, crash uploader, analytics service, or update checker.
- Frontend production code uses relative `/api` requests only and includes no remote font, stylesheet, script, image, or analytics dependency.
- The backend listener remains fixed to `127.0.0.1`, JMX is explicitly disabled, Actuator remains absent, and the unused embedded Tomcat WebSocket module is excluded from the runtime package.
- Apache Tika receives local streams only, embedded-document extraction remains disabled, and adversarial XML tests prove that neither local-file nor HTTP external entities are resolved into extracted content.

Maintain `scripts/audit-runtime-network.ps1` as a Windows packaging regression. It starts the packaged JAR with an isolated temporary data directory, waits for loopback health, samples TCP and UDP endpoints owned by that Java process, fails on a non-loopback TCP endpoint or any UDP endpoint, verifies the expected IPv4 loopback listener, stops the process, and removes only its validated temporary directory. Run it after producing the executable JAR and again against the backend launched by the future desktop package.

Dependency resolution and installation tools such as Maven and npm may contact their configured repositories during builds. That is separate from the packaged application runtime and must not be described as application telemetry.

Any future updater or online feature requires a new decision record, explicit user-facing disclosure and control, a narrowly defined endpoint, disabled-by-default behavior unless the product policy changes deliberately, and renewed source, dependency, browser-asset, parser, and live-connection audits.

## Alternatives considered

- Claim offline behavior from source search alone: rejected because dependency initialization and packaged behavior also need evidence.
- Claim offline behavior from one packet sample alone: rejected because sampling cannot cover every user action or malicious input.
- Install a system-wide firewall rule: rejected as a core requirement because it changes operating-system state, may require elevation, and should not compensate for unnecessary application networking.
- Keep the unused WebSocket runtime because no endpoint exists: rejected because removing an unused protocol implementation reduces packaged surface at negligible cost.

## Consequences

The application has no designed external runtime request and the current packaged-JAR trace observes only its IPv4 loopback listener. The audit is documented, automated, and rerunnable without contacting the internet. The production frontend contains only React and ReactDOM runtime packages; build/test dependencies do not ship in its static bundle.

This is evidence for the implemented startup, health, idle, and adversarial XML paths—not a mathematical guarantee about every third-party byte or future document format. Dependency upgrades, a desktop shell, an updater, remote assets, or any new network-capable feature invalidate the old evidence until the audit is rerun and extended. The future shell must also apply a restrictive content security policy that permits only its own assets and the exact local backend connection.
