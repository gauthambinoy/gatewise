// GateWise Desktop — frontend logic.
//
// Wires the plain-HTML UI to the Rust `#[tauri::command]` functions through
// `@tauri-apps/api`'s `invoke`. No framework and no bundler: we use the Tauri global
// (`window.__TAURI__`), which is injected when `app.withGlobalTauri` is true in
// tauri.conf.json, so the page loads as-is from `frontendDist` with no build step.

const { invoke } = window.__TAURI__.core;

// ---- element handles -------------------------------------------------------

const el = (id) => document.getElementById(id);

const baseUrl = el("base-url");
const apiKey = el("api-key");
const proxyHost = el("proxy-host");
const proxyPort = el("proxy-port");

const statusDot = el("status-dot");
const statusText = el("status-text");
const proxyDot = el("proxy-dot");
const proxyText = el("proxy-text");
const toast = el("toast");

// ---- small UI helpers ------------------------------------------------------

/** Sets a status line's coloured dot and text. `kind` is ok | warn | bad | unknown. */
function setStatus(dot, text, kind, message) {
  dot.className = `dot dot-${kind}`;
  text.textContent = message;
}

/** Flashes a transient message in the footer. */
let toastTimer;
function flash(message) {
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("show"), 4000);
}

/** Runs an async handler with the button disabled, so double-clicks can't pile up. */
async function withButton(button, fn) {
  button.disabled = true;
  try {
    await fn();
  } catch (err) {
    // Command errors arrive as plain strings (see AppError's Serialize impl).
    flash(typeof err === "string" ? err : String(err));
  } finally {
    button.disabled = false;
  }
}

// ---- config load/save ------------------------------------------------------

async function loadConfig() {
  const cfg = await invoke("load_config");
  baseUrl.value = cfg.base_url ?? "";
  apiKey.value = cfg.api_key ?? "";
  proxyHost.value = cfg.proxy_host ?? "";
  proxyPort.value = cfg.proxy_port ?? "";
}

function currentConfig() {
  return {
    base_url: baseUrl.value.trim(),
    api_key: apiKey.value.trim(),
    proxy_host: proxyHost.value.trim() || "127.0.0.1",
    // The Rust side expects a u16; coerce and clamp.
    proxy_port: clampPort(proxyPort.value),
  };
}

function clampPort(value) {
  const n = parseInt(value, 10);
  if (Number.isNaN(n) || n < 1) return 8888;
  if (n > 65535) return 65535;
  return n;
}

async function saveConfig() {
  await invoke("save_config", { cfg: currentConfig() });
  flash("Settings saved.");
}

// ---- connection test -------------------------------------------------------

async function testConnection() {
  const base = baseUrl.value.trim();
  const key = apiKey.value.trim();

  setStatus(statusDot, statusText, "unknown", "Testing…");

  // 1) Health (no auth) tells us the gateway is up at all.
  const h = await invoke("health", { base });
  if (!h.reachable) {
    setStatus(statusDot, statusText, "bad", h.detail || "Gateway unreachable.");
    return;
  }

  // 2) Key check tells us the credential is accepted.
  const k = await invoke("check_key", { base, key });
  if (k.valid) {
    const status = h.status ? ` (${h.status})` : "";
    setStatus(statusDot, statusText, "ok", `Connected${status}, key valid.`);
  } else {
    setStatus(
      statusDot,
      statusText,
      "warn",
      k.detail || "Gateway reachable but key was rejected."
    );
  }
}

// ---- system proxy ----------------------------------------------------------

async function refreshProxyState() {
  const s = await invoke("get_system_proxy");
  if (!s.supported) {
    setStatus(proxyDot, proxyText, "unknown", "Proxy control not automated on this OS.");
    return;
  }
  if (s.enabled) {
    const where = s.server ? ` → ${s.server}` : "";
    setStatus(proxyDot, proxyText, "ok", `System proxy ON${where}.`);
  } else {
    setStatus(proxyDot, proxyText, "warn", "System proxy OFF.");
  }
}

async function proxyOn() {
  const host = proxyHost.value.trim() || "127.0.0.1";
  const port = clampPort(proxyPort.value);
  const msg = await invoke("set_system_proxy", { host, port });
  flash(msg);
  await refreshProxyState();
}

async function proxyOff() {
  const msg = await invoke("clear_system_proxy");
  flash(msg);
  await refreshProxyState();
}

// ---- CA fetch --------------------------------------------------------------

async function fetchCa() {
  const base = baseUrl.value.trim();
  const out = el("ca-output");
  const pem = await invoke("get_ca_pem", { base });
  out.textContent = pem;
  out.classList.remove("hidden");
  flash("CA fetched. Follow the install steps below for your OS.");
}

// ---- moderation test -------------------------------------------------------

async function screenText() {
  const base = baseUrl.value.trim();
  const key = apiKey.value.trim();
  const text = el("mod-input").value;
  const box = el("mod-result");

  const r = await invoke("moderate", { base, key, text });
  renderModeration(box, r);
}

function renderModeration(box, r) {
  box.classList.remove("hidden", "flagged", "clean");
  box.classList.add(r.flagged ? "flagged" : "clean");

  const parts = [];
  parts.push(
    `<div class="mod-verdict">${
      r.flagged ? "Flagged" : "Clean — nothing detected"
    }</div>`
  );

  const sensitive = Object.entries(r.sensitiveData || {});
  if (sensitive.length) {
    parts.push(
      `<div>Sensitive data: ${sensitive
        .map(([type, count]) => `<span class="tag">${type} ×${count}</span>`)
        .join("")}</div>`
    );
  }
  if ((r.injection || []).length) {
    parts.push(
      `<div>Injection: ${r.injection
        .map((c) => `<span class="tag">${c}</span>`)
        .join("")}</div>`
    );
  }
  if ((r.moderation || []).length) {
    parts.push(
      `<div>Moderation: ${r.moderation
        .map((c) => `<span class="tag">${c}</span>`)
        .join("")}</div>`
    );
  }

  box.innerHTML = parts.join("");
}

// ---- wire up ---------------------------------------------------------------

function bind() {
  el("save-btn").addEventListener("click", () =>
    withButton(el("save-btn"), saveConfig)
  );
  el("test-btn").addEventListener("click", () =>
    withButton(el("test-btn"), testConnection)
  );
  el("proxy-on-btn").addEventListener("click", () =>
    withButton(el("proxy-on-btn"), proxyOn)
  );
  el("proxy-off-btn").addEventListener("click", () =>
    withButton(el("proxy-off-btn"), proxyOff)
  );
  el("ca-btn").addEventListener("click", () => withButton(el("ca-btn"), fetchCa));
  el("mod-btn").addEventListener("click", () => withButton(el("mod-btn"), screenText));
}

async function init() {
  bind();
  try {
    await loadConfig();
  } catch (err) {
    flash(`Could not load settings: ${err}`);
  }
  // Reflect the real proxy state on launch.
  await refreshProxyState().catch(() => {});
}

window.addEventListener("DOMContentLoaded", init);
