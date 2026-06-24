// Popup script: shows live gateway status, recent findings, and links to options.
// All data comes from the background worker via a single GET_STATUS message; the popup performs no
// network or storage I/O of its own, keeping it a thin view.

const els = {
  connDot: document.getElementById('conn-dot'),
  connText: document.getElementById('conn-text'),
  connDetail: document.getElementById('conn-detail'),
  policy: document.getElementById('policy'),
  gatewayUrl: document.getElementById('gateway-url'),
  count: document.getElementById('count'),
  findings: document.getElementById('findings'),
  clearLog: document.getElementById('clear-log'),
  openOptions: document.getElementById('open-options'),
};

function sendMessage(message) {
  return new Promise((resolve) => {
    chrome.runtime.sendMessage(message, (response) => {
      if (chrome.runtime.lastError) {
        resolve({ error: chrome.runtime.lastError.message });
      } else {
        resolve(response);
      }
    });
  });
}

/** Relative "x ago" formatting for log timestamps. */
function timeAgo(ts) {
  const s = Math.max(0, Math.round((Date.now() - ts) / 1000));
  if (s < 60) return s + 's ago';
  const m = Math.round(s / 60);
  if (m < 60) return m + 'm ago';
  const h = Math.round(m / 60);
  if (h < 24) return h + 'h ago';
  return Math.round(h / 24) + 'd ago';
}

function renderConnection(connection) {
  if (!connection) {
    els.connDot.className = 'dot';
    els.connText.textContent = 'Status unknown';
    return;
  }
  if (connection.ok) {
    els.connDot.className = 'dot ok';
    els.connText.textContent = 'Gateway connected';
    els.connDetail.textContent = '';
  } else if (connection.reachable) {
    // Reachable but not OK (most commonly a 401 bad key).
    els.connDot.className = 'dot warn';
    els.connText.textContent = 'Gateway reachable, not authorised';
    els.connDetail.textContent = connection.message || '';
  } else {
    els.connDot.className = 'dot bad';
    els.connText.textContent = 'Gateway unreachable';
    els.connDetail.textContent = connection.message || '';
  }
}

function renderSettings(settings) {
  if (!settings) return;
  els.policy.textContent = settings.failClosed ? 'Policy: fail-closed' : 'Policy: fail-open';
  try {
    els.gatewayUrl.textContent = new URL(settings.baseUrl).host;
  } catch {
    els.gatewayUrl.textContent = settings.baseUrl || '';
  }
  if (!settings.hasApiKey) {
    els.connDetail.textContent = (els.connDetail.textContent
      ? els.connDetail.textContent + ' '
      : '') + 'No API key set — open Options.';
  }
}

function renderFindings(recent, count) {
  els.count.textContent = String(count || 0);
  els.findings.textContent = '';

  if (!recent || recent.length === 0) {
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = 'No findings yet. Prompts are screened as you send them.';
    els.findings.appendChild(li);
    return;
  }

  for (const entry of recent) {
    const li = document.createElement('li');

    const top = document.createElement('div');
    top.className = 'finding-top';

    const left = document.createElement('span');
    left.textContent = labelForSite(entry.site) + ' · ' + timeAgo(entry.time);
    top.appendChild(left);

    const badge = document.createElement('span');
    if (entry.decision === 'block') {
      badge.className = 'badge block';
      badge.textContent = 'blocked';
    } else if (entry.decision === 'warn') {
      badge.className = 'badge warn';
      badge.textContent = 'warned';
    } else {
      badge.className = 'badge err';
      badge.textContent = 'gateway error';
    }
    top.appendChild(badge);
    li.appendChild(top);

    const items = document.createElement('div');
    items.className = 'finding-items';
    if (entry.findings && entry.findings.length) {
      items.textContent = entry.findings.join(', ');
    } else {
      items.textContent = entry.message || 'No detail';
    }
    li.appendChild(items);

    els.findings.appendChild(li);
  }
}

function labelForSite(id) {
  return { chatgpt: 'ChatGPT', claude: 'Claude', gemini: 'Gemini' }[id] || id || 'site';
}

async function load() {
  const status = await sendMessage({ type: 'GET_STATUS' });
  if (!status || status.error) {
    els.connDot.className = 'dot bad';
    els.connText.textContent = 'Extension error';
    els.connDetail.textContent = (status && status.error) || 'No response from background worker.';
    return;
  }
  renderConnection(status.connection);
  renderSettings(status.settings);
  renderFindings(status.recent, status.recentCount);
}

els.clearLog.addEventListener('click', async () => {
  await sendMessage({ type: 'CLEAR_LOG' });
  load();
});

els.openOptions.addEventListener('click', (e) => {
  e.preventDefault();
  if (chrome.runtime.openOptionsPage) {
    chrome.runtime.openOptionsPage();
  } else {
    window.open(chrome.runtime.getURL('options/options.html'));
  }
});

load();
