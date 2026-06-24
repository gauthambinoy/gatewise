//! Persisted connection settings.
//!
//! Settings live as a single JSON file in the platform app-config directory
//! (e.g. `%APPDATA%\com.auvex.desktop\config.json` on Windows,
//! `~/Library/Application Support/com.auvex.desktop/config.json` on macOS,
//! `~/.config/com.auvex.desktop/config.json` on Linux). Tauri resolves that path for
//! us via `path().app_config_dir()`, so we never hard-code OS layout.

use std::fs;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use tauri::Manager;

/// What the user configures and we persist between runs.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    /// Gateway origin, e.g. `http://localhost:8080`.
    #[serde(default = "default_base_url")]
    pub base_url: String,
    /// API key sent as `Authorization: Bearer <key>`. Stored in plaintext locally —
    /// see the README's security note.
    #[serde(default)]
    pub api_key: String,
    /// Host of the gateway's egress proxy that the system proxy should point at.
    #[serde(default = "default_proxy_host")]
    pub proxy_host: String,
    /// Port of the gateway's egress proxy.
    #[serde(default = "default_proxy_port")]
    pub proxy_port: u16,
}

fn default_base_url() -> String {
    "http://localhost:8080".to_string()
}

fn default_proxy_host() -> String {
    "127.0.0.1".to_string()
}

fn default_proxy_port() -> u16 {
    // The egress/MITM proxy listener. Configurable in the UI; this is just the seed
    // value the gateway's egress slice is expected to use.
    8888
}

impl Default for AppConfig {
    fn default() -> Self {
        AppConfig {
            base_url: default_base_url(),
            api_key: String::new(),
            proxy_host: default_proxy_host(),
            proxy_port: default_proxy_port(),
        }
    }
}

/// Resolves the config file path, creating the parent directory if needed.
fn config_path(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("could not resolve config directory: {e}"))?;
    fs::create_dir_all(&dir).map_err(|e| format!("could not create config directory: {e}"))?;
    Ok(dir.join("config.json"))
}

/// Loads settings, returning defaults when the file is missing. A corrupt file is
/// reported as an error rather than silently overwritten, so the user can recover it.
pub fn load(app: &tauri::AppHandle) -> Result<AppConfig, String> {
    let path = config_path(app)?;
    if !path.exists() {
        return Ok(AppConfig::default());
    }
    let bytes = fs::read(&path).map_err(|e| format!("could not read config: {e}"))?;
    serde_json::from_slice(&bytes).map_err(|e| format!("config file is not valid JSON: {e}"))
}

/// Persists settings atomically-ish: writes the whole file in one call. Good enough
/// for a tiny single-writer config.
pub fn save(app: &tauri::AppHandle, cfg: &AppConfig) -> Result<(), String> {
    let path = config_path(app)?;
    let json =
        serde_json::to_vec_pretty(cfg).map_err(|e| format!("could not serialize config: {e}"))?;
    fs::write(&path, json).map_err(|e| format!("could not write config: {e}"))
}
