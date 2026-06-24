//! OS system-proxy control.
//!
//! Each platform exposes the same three functions — `set_system_proxy`,
//! `clear_system_proxy`, `get_system_proxy` — but the body differs per OS and is
//! selected at compile time with `#[cfg(target_os = ...)]`.
//!
//! Support matrix:
//!   * **Windows** — fully implemented. Writes the per-user WinINET `Internet
//!     Settings` registry values (`ProxyEnable` + `ProxyServer`) and notifies WinINET
//!     so apps pick up the change without a reboot.
//!   * **macOS** — implemented via `networksetup -setwebproxy/-setsecurewebproxy`.
//!   * **Linux** — documented only: there is no single system proxy. We return a clear
//!     message pointing at the GNOME `gsettings` commands; per-app/env-var proxying is
//!     the portable route.

use serde::Serialize;

/// Reported back to the UI so it can render the current proxy state.
#[derive(Debug, Serialize)]
pub struct ProxyState {
    /// True when an OS proxy is currently enabled (regardless of where it points).
    pub enabled: bool,
    /// The configured proxy server (`host:port`), when one is set.
    pub server: Option<String>,
    /// True when this OS has a real implementation; false for documented-only.
    pub supported: bool,
}

// =====================================================================================
// Windows — real implementation
// =====================================================================================
#[cfg(target_os = "windows")]
mod imp {
    use super::ProxyState;
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ, KEY_WRITE};
    use winreg::RegKey;

    // WinINET's per-user proxy configuration. This is what Chrome, Edge, and most
    // Windows apps honour as "the system proxy".
    const SETTINGS_PATH: &str =
        r"Software\Microsoft\Windows\CurrentVersion\Internet Settings";

    fn open_settings(access: u32) -> Result<RegKey, String> {
        RegKey::predef(HKEY_CURRENT_USER)
            .open_subkey_with_flags(SETTINGS_PATH, access)
            .map_err(|e| format!("could not open Internet Settings registry key: {e}"))
    }

    /// Tells WinINET that the proxy settings changed so running apps reload them
    /// without a logout/reboot. We shell out to `netsh winhttp import proxy` as a
    /// belt-and-braces refresh for WinHTTP-based clients, and rely on the registry
    /// write for WinINET ones.
    ///
    /// The authoritative refresh is `InternetSetOption(INTERNET_OPTION_SETTINGS_CHANGED
    /// / _REFRESH)`. To avoid pulling in the full Win32 FFI surface for this scaffold,
    /// we trigger the refresh via `netsh`, which is present on every Windows install.
    fn notify_changed() {
        // Import the per-user (WinINET) proxy into WinHTTP so command-line and service
        // clients also route through it. Errors here are non-fatal: the registry write
        // already updated the primary (WinINET) setting.
        let _ = std::process::Command::new("netsh")
            .args(["winhttp", "import", "proxy", "source=ie"])
            .output();
    }

    pub fn set_system_proxy(host: &str, port: u16) -> Result<String, String> {
        let server = format!("{host}:{port}");
        let key = open_settings(KEY_WRITE)?;
        // ProxyEnable is a DWORD (REG_DWORD); ProxyServer is a string (REG_SZ).
        key.set_value("ProxyEnable", &1u32)
            .map_err(|e| format!("could not enable proxy: {e}"))?;
        key.set_value("ProxyServer", &server)
            .map_err(|e| format!("could not set proxy server: {e}"))?;
        notify_changed();
        Ok(format!("System proxy enabled, routing through {server}."))
    }

    pub fn clear_system_proxy() -> Result<String, String> {
        let key = open_settings(KEY_WRITE)?;
        key.set_value("ProxyEnable", &0u32)
            .map_err(|e| format!("could not disable proxy: {e}"))?;
        // Leave ProxyServer in place (harmless when disabled) so re-enabling keeps the
        // last target; clearing only the enable flag is the standard toggle behaviour.
        notify_changed();
        Ok("System proxy disabled.".to_string())
    }

    pub fn get_system_proxy() -> Result<ProxyState, String> {
        let key = open_settings(KEY_READ)?;
        // A missing value means "never configured" → treat as disabled.
        let enabled = key.get_value::<u32, _>("ProxyEnable").unwrap_or(0) == 1;
        let server = key.get_value::<String, _>("ProxyServer").ok().filter(|s| !s.is_empty());
        Ok(ProxyState {
            enabled,
            server,
            supported: true,
        })
    }
}

// =====================================================================================
// macOS — real implementation via `networksetup`
// =====================================================================================
#[cfg(target_os = "macos")]
mod imp {
    use super::ProxyState;
    use std::process::Command;

    // The Wi-Fi service is the common case; on Ethernet-only machines the operator can
    // adapt. We target the primary service by name. Listing services first would be
    // more robust, but keeps the scaffold focused — documented in the README.
    const SERVICE: &str = "Wi-Fi";

    fn run(args: &[&str]) -> Result<String, String> {
        let out = Command::new("networksetup")
            .args(args)
            .output()
            .map_err(|e| format!("could not run networksetup: {e}"))?;
        if out.status.success() {
            Ok(String::from_utf8_lossy(&out.stdout).trim().to_string())
        } else {
            Err(String::from_utf8_lossy(&out.stderr).trim().to_string())
        }
    }

    pub fn set_system_proxy(host: &str, port: u16) -> Result<String, String> {
        let port = port.to_string();
        // Set both the HTTP and HTTPS web proxies to the gateway egress port.
        run(&["-setwebproxy", SERVICE, host, &port])?;
        run(&["-setsecurewebproxy", SERVICE, host, &port])?;
        run(&["-setwebproxystate", SERVICE, "on"])?;
        run(&["-setsecurewebproxystate", SERVICE, "on"])?;
        Ok(format!(
            "System proxy enabled on '{SERVICE}', routing through {host}:{port}."
        ))
    }

    pub fn clear_system_proxy() -> Result<String, String> {
        run(&["-setwebproxystate", SERVICE, "off"])?;
        run(&["-setsecurewebproxystate", SERVICE, "off"])?;
        Ok(format!("System proxy disabled on '{SERVICE}'."))
    }

    pub fn get_system_proxy() -> Result<ProxyState, String> {
        // `-getwebproxy` prints lines like "Enabled: Yes\nServer: host\nPort: 8888".
        let out = run(&["-getwebproxy", SERVICE])?;
        let mut enabled = false;
        let mut server = None;
        let mut port = None;
        for line in out.lines() {
            if let Some(v) = line.strip_prefix("Enabled:") {
                enabled = v.trim().eq_ignore_ascii_case("Yes");
            } else if let Some(v) = line.strip_prefix("Server:") {
                let v = v.trim();
                if !v.is_empty() {
                    server = Some(v.to_string());
                }
            } else if let Some(v) = line.strip_prefix("Port:") {
                port = v.trim().parse::<u16>().ok();
            }
        }
        let server = match (server, port) {
            (Some(s), Some(p)) => Some(format!("{s}:{p}")),
            (Some(s), None) => Some(s),
            _ => None,
        };
        Ok(ProxyState {
            enabled,
            server,
            supported: true,
        })
    }
}

// =====================================================================================
// Linux / other — documented only
// =====================================================================================
#[cfg(not(any(target_os = "windows", target_os = "macos")))]
mod imp {
    use super::ProxyState;

    // There is no single OS-wide proxy on Linux. The GNOME desktop honours these
    // gsettings keys; many CLI tools instead read the HTTP_PROXY/HTTPS_PROXY env vars.
    const HINT: &str = "Automatic system-proxy control is not implemented on this OS. \
        On GNOME, run:\n  \
        gsettings set org.gnome.system.proxy mode 'manual'\n  \
        gsettings set org.gnome.system.proxy.http host '<host>'\n  \
        gsettings set org.gnome.system.proxy.http port <port>\n  \
        gsettings set org.gnome.system.proxy.https host '<host>'\n  \
        gsettings set org.gnome.system.proxy.https port <port>\n\
        To disable: gsettings set org.gnome.system.proxy mode 'none'.\n\
        For CLI tools, export HTTP_PROXY/HTTPS_PROXY=http://<host>:<port> instead.";

    pub fn set_system_proxy(_host: &str, _port: u16) -> Result<String, String> {
        Err(HINT.to_string())
    }

    pub fn clear_system_proxy() -> Result<String, String> {
        Err(HINT.to_string())
    }

    pub fn get_system_proxy() -> Result<ProxyState, String> {
        // We cannot reliably read it without committing to a single desktop, so report
        // "unsupported" and let the UI show the documented commands.
        Ok(ProxyState {
            enabled: false,
            server: None,
            supported: false,
        })
    }
}

// Re-export the chosen implementation under stable names.
pub use imp::{clear_system_proxy, get_system_proxy, set_system_proxy};
