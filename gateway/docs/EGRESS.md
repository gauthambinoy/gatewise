# Egress / forward transparent-proxy (TLS MITM)

The egress proxy is the "catch every AI call" mode. It intercepts AI-bound HTTPS from apps
that don't cooperatively point at the gateway, and runs that traffic through the *same*
governance pipeline (redaction, prompt-injection screening, policy, audit) as the normal
`/v1/chat/completions` path.

It is **off by default**. With `auvex.egress.enabled=false` no extra listener is opened, no CA
is generated, and the main app on `:8080` is completely untouched.

## How it works

The proxy is an HTTP `CONNECT` forward proxy on its own port (default `8888`):

- A `CONNECT` to a host **not** in `intercept-hosts` is tunnelled **opaquely** — the proxy
  relays the encrypted bytes and never sees the plaintext.
- A `CONNECT` to a host **in** `intercept-hosts` is **TLS-intercepted** (MITM): the proxy
  presents a leaf certificate it mints on the fly for that host, signed by its own root CA. A
  client that trusts the root completes the handshake, the proxy terminates TLS, reads the
  request, **governs** it, opens a *real, verified* TLS connection to the genuine provider,
  forwards the (redacted) request, and relays the response back.

The proxy impersonates the provider to the client, but it always verifies the provider's real
certificate on the way out — the provider can never be impersonated *to* the proxy.

Implementation note: this is plain JDK sockets + `SSLSocket` on Java 21 virtual threads (one
thread per connection), no Netty. The gateway already runs request-per-virtual-thread, so a
blocking thread-per-connection proxy scales the same way, and the JDK cleanly wraps an accepted
socket for server-side MITM.

## Turning it on

```yaml
auvex:
  egress:
    enabled: true
    port: 8888
    tenant-id: <an existing tenant UUID>   # required: intercepted calls carry the app's
                                           # provider key, not an Auvex key, so the tenant
                                           # that owns the policy/audit can't be inferred
    block-uncovered: false                 # see "Mandatory routing" below
    intercept-hosts:
      - api.openai.com
      - api.anthropic.com
      - generativelanguage.googleapis.com
    # ca-file: /etc/auvex/egress-ca.pem    # optional: also drop the CA here on startup
```

Or via environment variables: `AUVEX_EGRESS_ENABLED=true`,
`AUVEX_EGRESS_TENANT_ID=...`, `AUVEX_EGRESS_BLOCK_UNCOVERED=true`, etc.

## Trusting the CA (8.4)

TLS interception only works if the client trusts the proxy's root CA — otherwise the client
correctly rejects the leaf with a certificate error. Distribute and trust the CA once per
machine:

1. Fetch it (no API key needed — it's a public certificate, never the private key):

   ```bash
   curl -k http://<gateway-host>:8080/v1/egress/ca.pem -o auvex-egress-ca.pem
   ```

   (Or read it from the `ca-file` path if you configured one.)

2. Install it into the OS / browser trust store:

   - **Windows:** `certutil -addstore -f Root auvex-egress-ca.pem` (admin), or
     `Import-Certificate -FilePath auvex-egress-ca.pem -CertStoreLocation Cert:\LocalMachine\Root`.
   - **macOS:** `sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain auvex-egress-ca.pem`.
   - **Linux (Debian/Ubuntu):** copy to `/usr/local/share/ca-certificates/auvex-egress-ca.crt`
     then `sudo update-ca-certificates`.
   - **Firefox / NSS:** import via `Settings → Privacy & Security → Certificates → Authorities`.

3. Point the client at the proxy (system proxy, `HTTPS_PROXY=http://<gateway-host>:8888`, a
   `.pac` file, or the companion desktop app).

The CA is currently generated in memory at startup, so it changes on restart unless you set
`ca-file` and re-distribute, or pin a persisted CA (a follow-up). For a fleet, generate one org
CA and distribute it via MDM/group policy.

## Mandatory routing (8.3)

A proxy can only govern traffic that is actually sent through it. Making it **un-bypassable** is
an environment job — the gateway cannot enforce an OS firewall from inside the JVM. The pattern:

1. **Route clients through the proxy** — system proxy settings, `HTTPS_PROXY`, a `.pac` file, or
   the desktop agent.
2. **Block direct egress to the AI hosts** so the proxy is the *only* way out:
   - **Firewall:** deny outbound `:443` to the AI provider IP ranges for everything except the
     gateway host; allow the gateway host out.
   - **DNS:** sinkhole `api.openai.com`, `api.anthropic.com`,
     `generativelanguage.googleapis.com` (and friends) to the proxy, or to nothing, on the
     corporate resolver.
   - **PAC file:** return `PROXY gateway:8888` for the AI hosts so every browser/app is steered
     through the proxy.
3. **Set `block-uncovered: true`.** With it on, an intercepted AI request that fails the
   tenant's policy is **refused** (a `403` is returned to the client) instead of forwarded. With
   it off (default), a policy failure is recorded and the call still goes through — detect-first,
   so you can observe before you enforce.

Steps 1–2 are configured outside Auvex; step 3 is the gateway's half of the contract.
