// Auvex Guard content script.
//
// Runs on the supported AI sites. It intercepts the user's "send" action, lifts the prompt text out
// of the page, asks the background worker to screen it through the Auvex gateway, and — if anything
// sensitive or adversarial is found — shows an in-page modal letting the user cancel or send anyway
// (or blocks outright in fail-closed mode).
//
// IMPORTANT (maintenance): the DOM of these sites changes often. Every site-specific selector and
// behaviour lives in the SITE_ADAPTERS map below so that when a site ships a redesign, this is the
// ONE place to update. Each adapter uses an ordered list of selector fallbacks; the first that
// matches wins, which buys resilience across A/B tests and gradual rollouts.
//
// Content scripts are not ES modules, so this file is intentionally self-contained (no imports). The
// real network call and policy decision happen in the background worker / src/screen.js.

(() => {
  'use strict';

  // ---- Site adapters ------------------------------------------------------------------------------
  //
  // Each adapter describes how to find the prompt editor, read its text, find the send button, and
  // which keystroke submits. `match` decides whether this adapter applies to the current hostname.

  const SITE_ADAPTERS = {
    chatgpt: {
      id: 'chatgpt',
      label: 'ChatGPT',
      match: (host) => host === 'chatgpt.com' || host === 'chat.openai.com',
      // ChatGPT uses a ProseMirror contenteditable; older builds used a <textarea>.
      editorSelectors: [
        'div#prompt-textarea[contenteditable="true"]',
        'div.ProseMirror[contenteditable="true"]',
        'textarea#prompt-textarea',
        'form textarea',
      ],
      sendButtonSelectors: [
        'button[data-testid="send-button"]',
        'button[aria-label*="Send" i]',
        'form button[type="submit"]',
      ],
    },

    claude: {
      id: 'claude',
      label: 'Claude',
      match: (host) => host === 'claude.ai',
      editorSelectors: [
        'div[contenteditable="true"].ProseMirror',
        'div[contenteditable="true"][role="textbox"]',
        'div.ProseMirror[contenteditable="true"]',
        'fieldset div[contenteditable="true"]',
      ],
      sendButtonSelectors: [
        'button[aria-label*="Send" i]',
        'button[data-testid*="send" i]',
        'fieldset button[type="submit"]',
      ],
    },

    gemini: {
      id: 'gemini',
      label: 'Gemini',
      match: (host) => host === 'gemini.google.com',
      // Gemini wraps a Quill editor inside an Angular component.
      editorSelectors: [
        'div.ql-editor[contenteditable="true"]',
        'rich-textarea div[contenteditable="true"]',
        'div[contenteditable="true"][role="textbox"]',
      ],
      sendButtonSelectors: [
        'button[aria-label*="Send" i]',
        'button.send-button',
        'button[mattooltip*="Send" i]',
      ],
    },
  };

  const host = location.hostname;
  const adapter = Object.values(SITE_ADAPTERS).find((a) => a.match(host));
  if (!adapter) return; // not a supported site (shouldn't happen given manifest matches, but be safe)

  // ---- DOM helpers --------------------------------------------------------------------------------

  /** First element matching any selector in the ordered list, or null. */
  function firstMatch(selectors, root = document) {
    for (const sel of selectors) {
      const el = root.querySelector(sel);
      if (el) return el;
    }
    return null;
  }

  /** Read the prompt text from an editor element, handling both contenteditable and <textarea>. */
  function readEditorText(editor) {
    if (!editor) return '';
    if (editor.tagName === 'TEXTAREA' || editor.tagName === 'INPUT') {
      return editor.value || '';
    }
    // contenteditable: innerText preserves line breaks roughly as the user sees them.
    return (editor.innerText || editor.textContent || '').trim();
  }

  // ---- Screening pipeline -------------------------------------------------------------------------

  // Guards against infinite loops: when the user picks "Send anyway" we re-dispatch the original
  // action, and must let that one through without re-screening.
  let bypassNextSend = false;
  // Prevents firing two overlapping screenings (e.g. Enter + button click for the same submit).
  let screeningInFlight = false;

  /**
   * Ask the background worker to screen the given text. Resolves to the worker's response, or a
   * synthetic fail-open allow if the messaging itself fails (e.g. worker asleep mid-navigation) so a
   * messaging glitch never blocks the user.
   */
  function requestScreening(text) {
    return new Promise((resolve) => {
      let settled = false;
      const safety = setTimeout(() => {
        if (!settled) {
          settled = true;
          resolve(allowFallback('Screening timed out; allowed.'));
        }
      }, 8000);

      try {
        chrome.runtime.sendMessage(
          { type: 'SCREEN_PROMPT', text, siteId: adapter.id, url: location.href },
          (response) => {
            if (settled) return;
            settled = true;
            clearTimeout(safety);
            if (chrome.runtime.lastError || !response) {
              resolve(allowFallback('Guard unavailable; allowed.'));
            } else {
              resolve(response);
            }
          },
        );
      } catch (e) {
        if (!settled) {
          settled = true;
          clearTimeout(safety);
          resolve(allowFallback('Guard error; allowed.'));
        }
      }
    });
  }

  function allowFallback(message) {
    return {
      decision: 'allow',
      flagged: false,
      findings: [],
      status: { ok: false, message },
    };
  }

  /**
   * The interception entry point. Returns true if the original send should proceed immediately,
   * false if we are intercepting (we'll re-trigger the send later if the user confirms).
   */
  async function interceptSend(originalReSend) {
    if (bypassNextSend) {
      bypassNextSend = false;
      return true;
    }
    if (screeningInFlight) return false;

    const editor = firstMatch(adapter.editorSelectors);
    const text = readEditorText(editor);
    if (!text || text.trim() === '') return true; // nothing to screen

    screeningInFlight = true;
    let response;
    try {
      response = await requestScreening(text);
    } finally {
      screeningInFlight = false;
    }

    if (response.decision === 'allow') {
      return true; // clean, disabled, or fail-open after a gateway error
    }

    if (response.decision === 'block') {
      // fail-closed, or findings under fail-closed: hard stop, no override.
      showModal({
        mode: 'block',
        findings: response.findings || [],
        status: response.status,
        onConfirm: null, // no "send anyway" in block mode
        onCancel: () => {},
      });
      return false;
    }

    // decision === 'warn': findings under fail-open — let the user decide.
    showModal({
      mode: 'warn',
      findings: response.findings || [],
      status: response.status,
      onConfirm: () => {
        bypassNextSend = true;
        originalReSend();
      },
      onCancel: () => {},
    });
    return false;
  }

  // ---- Event interception -------------------------------------------------------------------------
  //
  // We hook two paths a user uses to send: Enter in the editor, and clicking the send button. Both
  // are captured in the *capture* phase so we can stop them before the site's own handlers run.

  // Enter-to-send (without Shift). We re-send by simulating the same keystroke after confirmation.
  document.addEventListener(
    'keydown',
    (event) => {
      if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
      const editor = firstMatch(adapter.editorSelectors);
      if (!editor || !editorContains(editor, event.target)) return;
      if (bypassNextSend) return; // confirmed re-send: let it through

      // Hold the send while we screen.
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();

      const reSend = () => {
        const target = firstMatch(adapter.editorSelectors) || editor;
        target.focus();
        // Re-dispatch a faithful Enter so the site's own handler submits.
        for (const phase of ['keydown', 'keyup']) {
          target.dispatchEvent(
            new KeyboardEvent(phase, {
              key: 'Enter',
              code: 'Enter',
              keyCode: 13,
              which: 13,
              bubbles: true,
              cancelable: true,
            }),
          );
        }
        // Some editors only submit via the button; nudge it too as a fallback.
        const btn = firstMatch(adapter.sendButtonSelectors);
        if (btn && !btn.disabled) btn.click();
      };

      interceptSend(reSend).then((proceed) => {
        if (proceed) reSend();
      });
    },
    true,
  );

  // Click-to-send on the send button.
  document.addEventListener(
    'click',
    (event) => {
      const btn = event.target && event.target.closest
        ? event.target.closest(adapter.sendButtonSelectors.join(','))
        : null;
      if (!btn) return;
      if (bypassNextSend) return; // confirmed re-send

      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();

      const reSend = () => {
        const liveBtn = firstMatch(adapter.sendButtonSelectors);
        if (liveBtn && !liveBtn.disabled) liveBtn.click();
      };

      interceptSend(reSend).then((proceed) => {
        if (proceed) reSend();
      });
    },
    true,
  );

  /** True if `node` is the editor or inside it. */
  function editorContains(editor, node) {
    return editor === node || (node && editor.contains(node));
  }

  // ---- In-page warning modal ----------------------------------------------------------------------
  //
  // Built with a shadow root so the host page's CSS can't restyle or hide it, and so our styles don't
  // leak into the page. Plain DOM, no innerHTML with untrusted data (findings are inserted as text).

  let activeOverlay = null;

  function showModal({ mode, findings, status, onConfirm, onCancel }) {
    closeModal();

    const overlay = document.createElement('div');
    overlay.setAttribute('data-auvex-guard', 'overlay');
    overlay.style.cssText =
      'position:fixed;inset:0;z-index:2147483647;background:rgba(15,23,42,0.55);' +
      'display:flex;align-items:center;justify-content:center;';

    const shadow = overlay.attachShadow({ mode: 'open' });

    const style = document.createElement('style');
    style.textContent = `
      .card{font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:#ffffff;
        color:#0f172a;width:min(440px,92vw);border-radius:12px;padding:20px 22px;
        box-shadow:0 20px 60px rgba(0,0,0,0.35);border-top:4px solid var(--accent);}
      .head{display:flex;align-items:center;gap:10px;margin-bottom:6px;}
      .shield{width:22px;height:22px;flex:0 0 auto;color:var(--accent);}
      h1{font-size:16px;margin:0;font-weight:600;}
      p.sub{margin:4px 0 12px;font-size:13px;color:#475569;line-height:1.4;}
      ul{margin:0 0 14px;padding:0;list-style:none;display:flex;flex-direction:column;gap:6px;}
      li{font-size:13px;background:#fef2f2;color:#991b1b;border:1px solid #fecaca;border-radius:6px;
        padding:6px 10px;}
      .note{font-size:12px;color:#64748b;margin:0 0 14px;}
      .row{display:flex;gap:10px;justify-content:flex-end;}
      button{font:inherit;font-size:13px;font-weight:600;border-radius:8px;padding:8px 14px;
        cursor:pointer;border:1px solid transparent;}
      .cancel{background:#f1f5f9;color:#0f172a;border-color:#e2e8f0;}
      .cancel:hover{background:#e2e8f0;}
      .send{background:var(--accent);color:#fff;}
      .send:hover{filter:brightness(0.95);}
      .only{justify-content:flex-end;}
    `;
    shadow.appendChild(style);

    const card = document.createElement('div');
    card.className = 'card';
    card.style.setProperty('--accent', mode === 'block' ? '#b91c1c' : '#b45309');

    const head = document.createElement('div');
    head.className = 'head';
    head.appendChild(shieldIcon());
    const h1 = document.createElement('h1');
    h1.textContent = mode === 'block' ? 'Auvex Guard blocked this prompt' : 'Auvex Guard: sensitive content found';
    head.appendChild(h1);
    card.appendChild(head);

    const sub = document.createElement('p');
    sub.className = 'sub';
    sub.textContent =
      mode === 'block'
        ? 'Your gateway policy is set to fail-closed. The following was detected in this prompt and it will not be sent.'
        : 'The following was detected in this prompt before sending it to ' + adapter.label + '.';
    card.appendChild(sub);

    if (findings && findings.length) {
      const ul = document.createElement('ul');
      for (const f of findings) {
        const li = document.createElement('li');
        li.textContent = f; // text only — never interpret as HTML
        ul.appendChild(li);
      }
      card.appendChild(ul);
    }

    // If the verdict came from a gateway error under fail-closed, explain that honestly.
    if (mode === 'block' && status && status.ok === false) {
      const note = document.createElement('p');
      note.className = 'note';
      note.textContent = 'Reason: the gateway could not screen this prompt (' +
        (status.message || 'gateway error') + ') and your policy blocks on doubt.';
      card.appendChild(note);
    }

    const row = document.createElement('div');
    row.className = 'row' + (mode === 'block' ? ' only' : '');

    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'cancel';
    cancelBtn.textContent = mode === 'block' ? 'OK' : 'Cancel';
    cancelBtn.addEventListener('click', () => {
      closeModal();
      if (onCancel) onCancel();
    });
    row.appendChild(cancelBtn);

    if (mode === 'warn' && onConfirm) {
      const sendBtn = document.createElement('button');
      sendBtn.className = 'send';
      sendBtn.textContent = 'Send anyway';
      sendBtn.addEventListener('click', () => {
        closeModal();
        onConfirm();
      });
      row.appendChild(sendBtn);
    }
    card.appendChild(row);

    shadow.appendChild(card);

    // Click on the dimmed backdrop = cancel (warn mode only; block mode requires explicit OK so the
    // user reads it).
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay && mode !== 'block') {
        closeModal();
        if (onCancel) onCancel();
      }
    });

    document.documentElement.appendChild(overlay);
    activeOverlay = overlay;
    cancelBtn.focus();
  }

  function closeModal() {
    if (activeOverlay && activeOverlay.parentNode) {
      activeOverlay.parentNode.removeChild(activeOverlay);
    }
    activeOverlay = null;
  }

  function shieldIcon() {
    const ns = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('class', 'shield');
    svg.setAttribute('fill', 'currentColor');
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M12 2 4 5v6c0 5 3.4 8.5 8 11 4.6-2.5 8-6 8-11V5l-8-3Z');
    svg.appendChild(path);
    return svg;
  }
})();
