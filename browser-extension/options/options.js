// Options page: load/save settings to chrome.storage.local and run the "Test connection" probe.
// The connection test is delegated to the background worker (which holds host permission to reach the
// gateway and isn't bound by the options page's own CORS context).

const DEFAULTS = {
  baseUrl: 'http://localhost:8080',
  apiKey: '',
  failClosed: false,
  sites: { chatgpt: true, claude: true, gemini: true },
};

const els = {
  baseUrl: document.getElementById('baseUrl'),
  apiKey: document.getElementById('apiKey'),
  failClosed: document.getElementById('failClosed'),
  siteChatgpt: document.getElementById('site-chatgpt'),
  siteClaude: document.getElementById('site-claude'),
  siteGemini: document.getElementById('site-gemini'),
  test: document.getElementById('test'),
  testStatus: document.getElementById('test-status'),
  save: document.getElementById('save'),
  saved: document.getElementById('saved'),
};

function sendMessage(message) {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(message, (response) => {
      if (chrome.runtime.lastError) {
        resolve({ ok: false, reachable: false, message: chrome.runtime.lastError.message });
      } else {
        resolve(response);
      }
    });
  });
}

/** Validate a gateway URL the same way the worker will (http/https only). Returns true/false. */
function isValidUrl(value) {
  if (!value || value.trim() === '') return false;
  try {
    const u = new URL(value.trim());
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

async function load() {
  const stored = await chrome.storage.local.get(Object.keys(DEFAULTS));
  els.baseUrl.value = stored.baseUrl ?? DEFAULTS.baseUrl;
  els.apiKey.value = stored.apiKey ?? DEFAULTS.apiKey;
  els.failClosed.checked = stored.failClosed ?? DEFAULTS.failClosed;
  const sites = { ...DEFAULTS.sites, ...(stored.sites || {}) };
  els.siteChatgpt.checked = sites.chatgpt;
  els.siteClaude.checked = sites.claude;
  els.siteGemini.checked = sites.gemini;
}

function setTestStatus(text, kind) {
  els.testStatus.textContent = text;
  els.testStatus.className = 'status' + (kind ? ' ' + kind : '');
}

async function save() {
  const baseUrl = els.baseUrl.value.trim();
  if (!isValidUrl(baseUrl)) {
    els.baseUrl.classList.add('invalid');
    setTestStatus('Enter a valid http(s) gateway URL before saving.', 'bad');
    els.baseUrl.focus();
    return;
  }
  els.baseUrl.classList.remove('invalid');

  await chrome.storage.local.set({
    baseUrl,
    apiKey: els.apiKey.value,
    failClosed: els.failClosed.checked,
    sites: {
      chatgpt: els.siteChatgpt.checked,
      claude: els.siteClaude.checked,
      gemini: els.siteGemini.checked,
    },
  });

  els.saved.classList.add('show');
  setTimeout(() => els.saved.classList.remove('show'), 1500);
}

async function testConnection() {
  const baseUrl = els.baseUrl.value.trim();
  if (!isValidUrl(baseUrl)) {
    els.baseUrl.classList.add('invalid');
    setTestStatus('Enter a valid http(s) URL first.', 'bad');
    return;
  }
  els.baseUrl.classList.remove('invalid');
  els.test.disabled = true;
  setTestStatus('Testing…', '');

  // Pass the current field values so the user can test before saving.
  const res = await sendMessage({
    type: 'TEST_CONNECTION',
    baseUrl,
    apiKey: els.apiKey.value,
  });

  els.test.disabled = false;
  if (res && res.ok) {
    setTestStatus('Connected — gateway reachable and key accepted.', 'ok');
  } else if (res && res.reachable) {
    setTestStatus(res.message || 'Reachable but not authorised.', 'warn');
  } else {
    setTestStatus((res && res.message) || 'Could not reach the gateway.', 'bad');
  }
}

els.baseUrl.addEventListener('input', () => {
  if (isValidUrl(els.baseUrl.value)) els.baseUrl.classList.remove('invalid');
});
els.test.addEventListener('click', testConnection);
els.save.addEventListener('click', save);

load();
