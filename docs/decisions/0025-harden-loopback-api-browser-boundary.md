# 0025 — Harden the loopback API browser boundary

## Context

Binding the backend to `127.0.0.1` prevents access from other machines, but it does not by itself make browser requests safe. A hostile public webpage can attempt simple form or resource requests to localhost, and DNS rebinding can present a non-local authority while connecting to loopback. A missing CORS allowlist prevents response reading but is not a complete defense against state-changing request submission. The application also carried an unused Actuator health surface alongside its purpose-built health endpoint.

## Decision

Keep the fixed IPv4 loopback bind and add a highest-precedence filter to all `/api` requests. Accept only `localhost`, `127.0.0.1`, or IPv6 loopback authority names; reject non-local or malformed Origin and Referer values and explicit `Sec-Fetch-Site: cross-site` requests. Require `X-DeepFind-Client: browser` on POST, PUT, PATCH, and DELETE. The React client sends this header on every mutation. Cross-origin JavaScript cannot send it without a preflight, and DeepFind exposes no permissive CORS policy; ordinary HTML forms cannot add it.

Add `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, a same-origin resource policy, and a restrictive JSON content security policy to API responses. Return a stable 403 body for boundary rejection and a stable 404 for unknown resources. Remove the unused Actuator runtime/test dependencies so `/actuator/health` and the discovery surface are absent; retain the minimal `/api/health` endpoint.

## Alternatives considered

- Trust loopback plus absent CORS: rejected because CORS governs browser response access, not every request submission path.
- Put a secret token in frontend JavaScript: rejected because a browser-delivered constant is not a durable secret and would overstate the boundary.
- Add session login or user accounts: rejected as unnecessary for the current single-user, offline-first product and contrary to its account-free baseline.
- Retain Actuator health: rejected because the application already owns a smaller health contract and does not need the additional web surface.

## Consequences

The browser UI and future desktop shell must attach the documented header to mutations. Local scripts must do the same. This blocks common cross-site browser triggers and DNS-rebinding authority tricks, but does not authenticate or sandbox another process already executing as the user; such a process can call the loopback API and read the local data directly. Future desktop packaging must preserve or deliberately supersede this boundary without weakening it.
