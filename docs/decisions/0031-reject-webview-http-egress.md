# 0031 — Reject WebView HTTP egress within the application

## Context

The backend-only network audit passed, but a full desktop process-tree audit observed a WebView2 network-service connection to a public HTTPS endpoint. This shows why bundling a WebView changes the privacy boundary. No packet-content evidence was collected, so the connection does not establish what data was sent.

## Decision

Before creating the WebView, bind a small rejecting HTTP proxy to an ephemeral IPv4 loopback port and apply it only through the Tauri window's supported `proxy_url` option. Do not modify system proxy, firewall, telemetry, or security settings. WebView2's implicit loopback bypass keeps the local UI/API usable. Every connection received by the proxy gets a fixed 403 response; the implementation does not read, log, resolve, or forward requests, and uses one bounded worker with a write timeout.

Retain navigation restrictions, no renderer capabilities, and the production UI CSP. Keep Wry's existing browser defaults rather than replacing its argument list with speculative feature switches. Remove the broad no-analytics UI claim; DeepFind itself does not upload documents, but WebView2 is separately serviced software.

## Consequences

The proxy blocks the HTTP/HTTPS requests routed through it, not arbitrary OS networking. Microsoft explicitly documents that the proxy-server flag affects HTTP/HTTPS only. Native browser services and future runtime changes remain reasons to audit installed builds and avoid universal zero-network claims. No external destination or private request payload is recorded by this guard.

The desktop audit separately tracks Java and discovered WebView2 descendants, requires a Java loopback listener, and rejects observed external TCP or UDP. Windows wildcard `Bound` reservations with no remote endpoint are recorded but are not mislabeled as listeners or external traffic. Sampling remains evidence, not proof of all possible behavior.

References: [WebView2 browser flags](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/webview-features-flags), [WebView2 privacy](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/data-privacy).
