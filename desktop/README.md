# Auvex Desktop

A small **system-tray application** that brings an [Auvex](../README.md) governance
gateway to a whole machine. Install it on a PC and it can route the machine's AI-bound
traffic through your gateway, screen prompts before they leave, and keep the egress
TLS-interception trust in place — so governance is applied everywhere, not just inside
one browser or one app.

Built with **Tauri v2** (Rust backend + a tiny dependency-free HTML/JS frontend).

## What it does

1. **Connect & status.** Point it at a gateway base URL (e.g. `http://localhost:8080`
   or `https://auvex.54.170.218.176.nip.io`) and an API key. It polls
   `GET /actuator/health` (no auth) for reachability and `GET /v1/models` (with the
   key) to confirm the credential, showing a single live status line.
2. **System-proxy toggle.** Buttons turn the OS HTTP/HTTPS proxy on/off, pointing it at
   the gateway's egress proxy `host:port`, so AI traffic is forced through Auvex.
3. **CA-install helper.** Fetches the Auvex Egress root CA from the gateway when an
   endpoint exists, and gives per-OS instructions to trust it (required for TLS
   interception in egress mode).
4. **Prompt screen.** A test box posts text to `POST /v1/moderations` and renders the
   verdict (flagged + which sensitive-data, injection, and moderation categories were
   found) — an end-to-end proof of the connection.

The app lives in the tray; closing the window leaves it running, and the tray menu has
**Show** and **Quit**.

## Prerequisites

This is a **source scaffold**. It was authored without a Rust/Node toolchain present
and therefore **was not compiled** in its authoring environment. To run or build it you
need:

- **Rust** (stable, 1.77+) — <https://rustup.rs>
- **Node.js** 18+ and npm
- **Tauri v2 system dependencies** for your OS (WebView2 on Windows — preinstalled on
  Windows 11; `webkit2gtk` etc. on Linux; Xcode CLT on macOS). See the Tauri v2
  prerequisites guide.
- The **Tauri CLI**, installed locally by `npm install` (it's a dev dependency).

## Build & run

```sh
cd desktop
npm install                 # installs @tauri-apps/cli and @tauri-apps/api

# One-time: generate the raster icons the bundler/tray need from the SVG source.
npm run icon                # = tauri icon src-tauri/icons/shield.svg

npm run dev                 # = tauri dev   (debug, hot window)
npm run build               # = tauri build (produces an installer/bundle)
```

There is **no frontend build step**: the UI is static files under `src/`, served
directly (`frontendDist: "../src"`), and it calls the Rust commands through the Tauri
global (`window.__TAURI__`, enabled via `app.withGlobalTauri`).

## Per-platform proxy support

| OS          | System-proxy control | How                                                                                 |
| ----------- | -------------------- | ----------------------------------------------------------------------------------- |
| **Windows** | **Real**             | Writes per-user WinINET `Internet Settings` registry (`ProxyEnable` + `ProxyServer`) and refreshes WinHTTP via `netsh winhttp import proxy source=ie`. |
| **macOS**   | **Real**             | `networksetup -setwebproxy / -setsecurewebproxy / -set…state` on the `Wi-Fi` service. |
| **Linux**   | **Documented only**  | No single OS-wide proxy. The command returns the GNOME `gsettings` recipe and the `HTTP_PROXY`/`HTTPS_PROXY` env-var route. |

Windows is the primary target and is fully implemented. On macOS the implementation
targets the `Wi-Fi` network service by name; on Ethernet-only or multi-service setups,
adapt the service name in `src-tauri/src/proxy.rs`.

## CA trust step

In egress/MITM mode the gateway terminates TLS with a leaf certificate signed by the
**Auvex Egress CA**, so that root must be trusted on each machine. The app's **Fetch CA
from gateway** button tries a few conventional endpoints
(`/egress/ca.pem`, `/v1/egress/ca.pem`, `/actuator/egress-ca`). The current gateway
generates the egress CA **in memory** and a download endpoint is a planned follow-up;
until it ships, the button will report that no endpoint exists and you should obtain the
PEM from the gateway operator and install it manually:

- **Windows:** `certutil -addstore -user Root auvex-ca.crt` (current user), or open the
  `.crt` and choose _Install Certificate → Local Machine → Trusted Root_.
- **macOS:** add to the login keychain and set _Always Trust_, or
  `security add-trusted-cert -d -r trustRoot -k ~/Library/Keychains/login.keychain auvex-ca.pem`.
- **Linux:** copy to `/usr/local/share/ca-certificates/auvex-ca.crt` and run
  `sudo update-ca-certificates`. Browsers with their own stores (Firefox/Chrome) need a
  separate import.

## Where settings are stored

Connection settings (base URL, API key, proxy host/port) are saved as `config.json` in
the platform app-config directory (Tauri resolves it):

- Windows: `%APPDATA%\com.auvex.desktop\config.json`
- macOS: `~/Library/Application Support/com.auvex.desktop/config.json`
- Linux: `~/.config/com.auvex.desktop/config.json`

**Security note:** the API key is stored in **plaintext** under your user profile. That
is acceptable for a developer/operator tool but is not a secret-vault. A follow-up could
move it to the OS keychain (Windows Credential Manager / macOS Keychain / Secret
Service).

## Project layout

```
desktop/
├── README.md
├── .gitignore
├── package.json                 # tauri scripts + @tauri-apps/{cli,api}
├── src/                         # frontend (static, no build step)
│   ├── index.html
│   ├── styles.css               # dark theme
│   └── main.js                  # invoke() wiring via window.__TAURI__
└── src-tauri/                   # Rust backend
    ├── Cargo.toml
    ├── build.rs
    ├── tauri.conf.json
    ├── capabilities/
    │   └── default.json         # window/tray/menu permissions
    ├── icons/
    │   ├── shield.svg           # monochrome source glyph
    │   └── README.md            # how to generate the raster set
    └── src/
        ├── main.rs              # thin desktop entry point
        ├── lib.rs              # commands + tray + app bootstrap
        ├── config.rs           # load/save settings
        └── proxy.rs            # per-OS system-proxy control (cfg-gated)
```

## Tauri commands (Rust ↔ JS)

| Command              | Purpose                                                |
| -------------------- | ------------------------------------------------------ |
| `health(base)`       | Probe `GET /actuator/health`.                          |
| `check_key(base,key)`| Validate the key via `GET /v1/models`.                 |
| `moderate(base,key,text)` | `POST /v1/moderations`, returns the verdict.      |
| `set_system_proxy(host,port)` | Enable the OS proxy (per-platform).          |
| `clear_system_proxy()` | Disable the OS proxy.                                |
| `get_system_proxy()` | Report current OS-proxy state.                         |
| `get_ca_pem(base)`   | Best-effort fetch of the egress root CA.               |
| `load_config()` / `save_config(cfg)` | Persisted settings.                    |

## Notes

- Not compiled here (no toolchain). The Rust and config were re-read for correctness
  (types, `?`/error handling, serde derives, command registration in
  `invoke_handler!`, valid TOML/JSON) but a real build requires `cargo`/`npm`.
- The raster icons are generated, not checked in — run `npm run icon` first.
