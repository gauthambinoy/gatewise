//! GateWise Desktop — backend.
//!
//! A small tray application that brings an GateWise governance gateway to a whole machine:
//!
//!   * connects to a gateway (base URL + API key) and reports live status,
//!   * toggles the OS system proxy to route traffic through the gateway's egress port,
//!   * helps install/trust the egress root CA so TLS interception works, and
//!   * sends text to `/v1/moderations` to prove the connection end-to-end.
//!
//! The frontend (plain HTML/JS) calls the `#[tauri::command]` functions below via
//! `@tauri-apps/api`'s `invoke`.

use std::time::Duration;

use serde::{Deserialize, Serialize};

mod config;
mod proxy;

/// HTTP timeout for every gateway probe. Short, because the UI is interactive — a
/// hung gateway should surface as "unreachable" quickly rather than spin forever.
const HTTP_TIMEOUT: Duration = Duration::from_secs(8);

/// One error type for the whole command surface. Everything a command can fail with
/// converts into this, so command bodies can use `?` freely. It serializes to a plain
/// string for the frontend, which only ever needs a readable message.
#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("network error: {0}")]
    Http(#[from] reqwest::Error),

    #[error("config error: {0}")]
    Config(String),

    #[error("{0}")]
    Proxy(String),

    #[error("{0}")]
    Message(String),
}

impl Serialize for AppError {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}

/// Convenience alias: every command returns this.
type CommandResult<T> = Result<T, AppError>;

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

/// Builds a reqwest client with a sane timeout. Built per call (cheap) so the latest
/// settings always apply and there is no shared mutable state to manage.
fn http_client() -> Result<reqwest::Client, reqwest::Error> {
    reqwest::Client::builder()
        .timeout(HTTP_TIMEOUT)
        .user_agent(concat!("gatewise-desktop/", env!("CARGO_PKG_VERSION")))
        .build()
}

/// Normalizes a user-entered base URL: trims whitespace and any trailing slash, so
/// `http://localhost:8080/` and `http://localhost:8080` behave identically when we
/// append `/actuator/health` etc.
fn normalize_base(base: &str) -> String {
    base.trim().trim_end_matches('/').to_string()
}

// ---------------------------------------------------------------------------
// Commands: gateway status
// ---------------------------------------------------------------------------

/// Result of probing the gateway health endpoint.
#[derive(Debug, Serialize)]
pub struct HealthStatus {
    /// True if the gateway answered `GET /actuator/health` at all.
    reachable: bool,
    /// The reported status string (e.g. "UP"), when the body parsed.
    status: Option<String>,
    /// A human-readable note for the UI when something went wrong.
    detail: Option<String>,
}

/// Probes `GET {base}/actuator/health` (no auth required). Reports whether the
/// gateway is reachable and, if so, the Spring Boot Actuator status field.
#[tauri::command]
async fn health(base: String) -> CommandResult<HealthStatus> {
    let base = normalize_base(&base);
    if base.is_empty() {
        return Ok(HealthStatus {
            reachable: false,
            status: None,
            detail: Some("No base URL configured.".into()),
        });
    }

    let url = format!("{base}/actuator/health");
    let client = http_client()?;

    match client.get(&url).send().await {
        Ok(resp) => {
            let ok = resp.status().is_success();
            // Actuator returns `{ "status": "UP", ... }`. Parse leniently — a gateway
            // behind a proxy might wrap or omit it, and we still want "reachable".
            let status = resp
                .json::<serde_json::Value>()
                .await
                .ok()
                .and_then(|v| v.get("status").and_then(|s| s.as_str()).map(str::to_string));
            Ok(HealthStatus {
                reachable: ok,
                status,
                detail: if ok {
                    None
                } else {
                    Some("Gateway responded but health was not UP.".into())
                },
            })
        }
        Err(e) => Ok(HealthStatus {
            reachable: false,
            status: None,
            detail: Some(format!("Could not reach gateway: {e}")),
        }),
    }
}

/// Result of validating an API key against the gateway.
#[derive(Debug, Serialize)]
pub struct KeyStatus {
    /// True only when the key was accepted (HTTP 2xx).
    valid: bool,
    /// HTTP status code observed, for the UI to distinguish 401 from a 5xx/outage.
    http_status: Option<u16>,
    detail: Option<String>,
}

/// Validates the API key by calling `GET {base}/v1/models` with `Authorization:
/// Bearer <key>`. A 2xx means the key works; 401/403 means it was rejected; anything
/// else is surfaced as a transport/server problem.
#[tauri::command]
async fn check_key(base: String, key: String) -> CommandResult<KeyStatus> {
    let base = normalize_base(&base);
    let key = key.trim().to_string();
    if base.is_empty() || key.is_empty() {
        return Ok(KeyStatus {
            valid: false,
            http_status: None,
            detail: Some("Base URL and API key are both required.".into()),
        });
    }

    let url = format!("{base}/v1/models");
    let client = http_client()?;

    match client.get(&url).bearer_auth(&key).send().await {
        Ok(resp) => {
            let code = resp.status().as_u16();
            let valid = resp.status().is_success();
            let detail = match code {
                401 | 403 => Some("API key was rejected by the gateway.".into()),
                c if (200..300).contains(&c) => None,
                c => Some(format!("Unexpected response from gateway (HTTP {c}).")),
            };
            Ok(KeyStatus {
                valid,
                http_status: Some(code),
                detail,
            })
        }
        Err(e) => Ok(KeyStatus {
            valid: false,
            http_status: None,
            detail: Some(format!("Could not reach gateway: {e}")),
        }),
    }
}

// ---------------------------------------------------------------------------
// Commands: moderation test
// ---------------------------------------------------------------------------

/// Mirrors the gateway's `ModerationResult` record. Field names match the JSON exactly
/// (`sensitiveData` is camelCase on the wire), so we rename it.
#[derive(Debug, Serialize, Deserialize)]
pub struct ModerationResult {
    flagged: bool,
    #[serde(default, rename = "sensitiveData")]
    sensitive_data: std::collections::HashMap<String, i64>,
    #[serde(default)]
    injection: Vec<String>,
    #[serde(default)]
    moderation: Vec<String>,
}

/// Sends `text` to `POST {base}/v1/moderations` and returns the verdict. This is the
/// end-to-end proof: it exercises the base URL, the key, and the governance pipeline
/// in one call.
#[tauri::command]
async fn moderate(base: String, key: String, text: String) -> CommandResult<ModerationResult> {
    let base = normalize_base(&base);
    let key = key.trim().to_string();
    if base.is_empty() || key.is_empty() {
        return Err(AppError::Message(
            "Configure a base URL and API key before testing.".into(),
        ));
    }

    let url = format!("{base}/v1/moderations");
    let client = http_client()?;

    let resp = client
        .post(&url)
        .bearer_auth(&key)
        .json(&serde_json::json!({ "input": text }))
        .send()
        .await?;

    let status = resp.status();
    if !status.is_success() {
        // Surface the gateway's own error body if there is one — it is usually a
        // helpful `{ "error": { "message": ... } }` envelope.
        let body = resp.text().await.unwrap_or_default();
        let msg = serde_json::from_str::<serde_json::Value>(&body)
            .ok()
            .and_then(|v| {
                v.pointer("/error/message")
                    .and_then(|m| m.as_str())
                    .map(str::to_string)
            })
            .unwrap_or_else(|| format!("Gateway returned HTTP {}.", status.as_u16()));
        return Err(AppError::Message(msg));
    }

    let result = resp.json::<ModerationResult>().await?;
    Ok(result)
}

// ---------------------------------------------------------------------------
// Commands: system proxy
// ---------------------------------------------------------------------------

/// Turns the OS system HTTP/HTTPS proxy ON, pointing it at `host:port` (the gateway's
/// egress proxy). Real implementation per platform lives in `proxy.rs`, gated by
/// `#[cfg(target_os = ...)]`.
#[tauri::command]
async fn set_system_proxy(host: String, port: u16) -> CommandResult<String> {
    let host = host.trim();
    if host.is_empty() {
        return Err(AppError::Proxy("Proxy host must not be empty.".into()));
    }
    proxy::set_system_proxy(host, port).map_err(AppError::Proxy)
}

/// Turns the OS system proxy OFF, restoring direct connections.
#[tauri::command]
async fn clear_system_proxy() -> CommandResult<String> {
    proxy::clear_system_proxy().map_err(AppError::Proxy)
}

/// Reports the current system-proxy state so the UI can reflect reality on launch
/// rather than assuming "off".
#[tauri::command]
async fn get_system_proxy() -> CommandResult<proxy::ProxyState> {
    proxy::get_system_proxy().map_err(AppError::Proxy)
}

// ---------------------------------------------------------------------------
// Commands: CA helper
// ---------------------------------------------------------------------------

/// Best-effort fetch of the egress root CA in PEM form from the gateway.
///
/// The gateway generates the egress CA in memory; a download endpoint is a planned
/// follow-up. We probe a couple of conventional locations and, if none exists, return
/// a clear error so the UI can fall back to the manual instructions instead of
/// pretending it worked.
#[tauri::command]
async fn get_ca_pem(base: String) -> CommandResult<String> {
    let base = normalize_base(&base);
    if base.is_empty() {
        return Err(AppError::Message("No base URL configured.".into()));
    }

    let client = http_client()?;
    // Conventional paths a gateway might expose the root CA at. The first that returns
    // a PEM-looking body wins.
    let candidates = [
        format!("{base}/egress/ca.pem"),
        format!("{base}/v1/egress/ca.pem"),
        format!("{base}/actuator/egress-ca"),
    ];

    for url in candidates {
        if let Ok(resp) = client.get(&url).send().await {
            if resp.status().is_success() {
                if let Ok(body) = resp.text().await {
                    if body.contains("BEGIN CERTIFICATE") {
                        return Ok(body);
                    }
                }
            }
        }
    }

    Err(AppError::Message(
        "This gateway does not expose a CA download endpoint yet. \
         Export the GateWise Egress CA from the gateway operator and install it manually \
         (see the CA Trust section in the app)."
            .into(),
    ))
}

// ---------------------------------------------------------------------------
// Commands: config load/save
// ---------------------------------------------------------------------------

/// Loads the saved connection settings from the app config directory. Returns defaults
/// (localhost, empty key) when nothing has been saved yet.
#[tauri::command]
async fn load_config(app: tauri::AppHandle) -> CommandResult<config::AppConfig> {
    config::load(&app).map_err(AppError::Config)
}

/// Persists connection settings to the app config directory. The API key is stored in
/// plaintext under the user's profile — documented in the README — which is acceptable
/// for a developer tool and avoids a keychain dependency in this scaffold.
#[tauri::command]
async fn save_config(app: tauri::AppHandle, cfg: config::AppConfig) -> CommandResult<()> {
    config::save(&app, &cfg).map_err(AppError::Config)
}

// ---------------------------------------------------------------------------
// App bootstrap
// ---------------------------------------------------------------------------

/// Builds and runs the Tauri application. Called by `main.rs` (desktop) so the run
/// logic stays in the library.
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            // Build a minimal tray with a "Show" and "Quit" menu so the app lives in
            // the system tray, matching the "install on PCs, runs in the background"
            // purpose.
            tray::build(app.handle())?;
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            health,
            check_key,
            moderate,
            set_system_proxy,
            clear_system_proxy,
            get_system_proxy,
            get_ca_pem,
            load_config,
            save_config,
        ])
        .run(tauri::generate_context!())
        .expect("error while running the GateWise desktop application");
}

mod tray {
    //! System-tray wiring. Kept here (rather than its own file) because it is short and
    //! only used at startup.
    use tauri::{
        menu::{Menu, MenuItem},
        tray::TrayIconBuilder,
        AppHandle, Manager, Runtime,
    };

    /// Creates the tray icon and its menu. Left-clicking the tray (or "Show") reveals
    /// the main window; "Quit" exits the app.
    pub fn build<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<()> {
        let show = MenuItem::with_id(app, "show", "Show GateWise", true, None::<&str>)?;
        let quit = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
        let menu = Menu::with_items(app, &[&show, &quit])?;

        TrayIconBuilder::with_id("gatewise-tray")
            .tooltip("GateWise Desktop")
            .icon(app.default_window_icon().unwrap().clone())
            .menu(&menu)
            .show_menu_on_left_click(false)
            .on_menu_event(|app, event| match event.id.as_ref() {
                "show" => reveal_main(app),
                "quit" => app.exit(0),
                _ => {}
            })
            .on_tray_icon_event(|tray, event| {
                // A left click on the icon itself also reveals the window.
                if let tauri::tray::TrayIconEvent::Click {
                    button: tauri::tray::MouseButton::Left,
                    button_state: tauri::tray::MouseButtonState::Up,
                    ..
                } = event
                {
                    reveal_main(tray.app_handle());
                }
            })
            .build(app)?;
        Ok(())
    }

    /// Shows and focuses the main window, creating nothing — the window is declared in
    /// tauri.conf.json.
    fn reveal_main<R: Runtime>(app: &AppHandle<R>) {
        if let Some(window) = app.get_webview_window("main") {
            let _ = window.show();
            let _ = window.set_focus();
        }
    }
}
