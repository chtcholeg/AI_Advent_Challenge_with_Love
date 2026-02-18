"use strict";

/* ── Tab switching ─────────────────────────────────────────────────────────────── */
function switchTab(name) {
  document.getElementById('tab-wizard').classList.toggle('tab-hidden', name !== 'wizard');
  document.getElementById('tab-deploy').classList.toggle('tab-hidden', name !== 'deploy');
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.tab === name);
  });
}

/* ═══════════════════════════════════════════════════════════════════════════════
   VPS WIZARD
   ═══════════════════════════════════════════════════════════════════════════════ */

/* ── State ───────────────────────────────────────────────────────────────────── */
const state = {
  page: 'scenario',
  scenarios: {},
  selectedScenario: null,
  sessionId: null,
  context: {},
  steps: [],
  currentStepIdx: 0,
  ws: null,
  execDone: false,
  fixInProgress: false,
};

/* ── Boot ────────────────────────────────────────────────────────────────────── */
async function init() {
  try {
    const res = await fetch('/api/scenarios');
    state.scenarios = await res.json();
    renderScenarioGrid();
  } catch (e) {
    showGlobalError('Не удалось подключиться к серверу приложения. Запустите backend: python main.py');
  }
}

/* ── Navigation ──────────────────────────────────────────────────────────────── */
function goToPage(page) {
  document.querySelectorAll('.page').forEach(el => el.classList.add('hidden'));
  document.getElementById(`page-${page}`).classList.remove('hidden');
  state.page = page;
  updateSidebarHighlight();
  if (page === 'inputs') buildInputsForm();
}

function updateSidebarHighlight() {
  const pageOrder = ['scenario', 'credentials', 'inputs', 'executing', 'done'];
  const idx = pageOrder.indexOf(state.page);
  document.querySelectorAll('#sidebar-steps .step-item').forEach((el, i) => {
    el.classList.remove('active', 'done');
    if (i < idx) el.classList.add('done');
    else if (i === idx) el.classList.add('active');
  });
}

/* ── Scenarios ───────────────────────────────────────────────────────────────── */
const EMOJI = { shield: '🛡️', globe: '🌐', server: '⚙️', scale: '⚖️' };

function renderScenarioGrid() {
  const grid = document.getElementById('scenario-grid');
  grid.innerHTML = '';
  for (const [key, s] of Object.entries(state.scenarios)) {
    const card = document.createElement('div');
    card.className = 'scenario-card';
    card.dataset.key = key;
    card.innerHTML = `
      <div class="scenario-icon">${EMOJI[s.icon] || '🖥️'}</div>
      <div class="scenario-name">${s.name}</div>
      <div class="scenario-sub">${s.subtitle}</div>
      <div class="scenario-desc">${s.description}</div>
    `;
    card.onclick = () => selectScenario(key, card);
    grid.appendChild(card);
  }
}

function selectScenario(key, card) {
  document.querySelectorAll('.scenario-card').forEach(c => c.classList.remove('selected'));
  card.classList.add('selected');
  state.selectedScenario = key;
  state.steps = state.scenarios[key].steps;
  document.getElementById('btn-scenario-next').disabled = false;
}

/* ── Connection ──────────────────────────────────────────────────────────────── */
async function testConnection() {
  const host    = document.getElementById('ssh-host').value.trim();
  const port    = parseInt(document.getElementById('ssh-port').value) || 22;
  const user    = document.getElementById('ssh-user').value.trim();
  const pass    = document.getElementById('ssh-pass').value;
  const privKey = document.getElementById('ssh-key').value.trim();

  if (!host) { showConnectError('Укажите IP-адрес сервера'); return; }
  if (!user) { showConnectError('Укажите имя пользователя'); return; }
  if (!pass && !privKey) { showConnectError('Укажите пароль или приватный ключ'); return; }

  hideConnectMessages();
  const btn = document.getElementById('btn-connect');
  btn.disabled = true;
  btn.textContent = 'Подключаемся…';

  try {
    const res = await fetch('/api/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ host, port, username: user, password: pass || null, private_key: privKey || null }),
    });
    const data = await res.json();

    if (data.success) {
      state.sessionId = data.session_id;
      state.context = { host, admin_user: user };
      document.getElementById('badge-text').textContent = `${user}@${host}`;
      document.getElementById('connection-badge').classList.add('connected');
      showConnectSuccess(`Подключено к ${host}`);
      document.getElementById('btn-creds-next').classList.remove('hidden');
      btn.classList.add('hidden');
    } else {
      showConnectError(data.error || 'Ошибка подключения');
      btn.disabled = false;
      btn.textContent = 'Проверить подключение';
    }
  } catch (e) {
    showConnectError('Не удалось связаться с backend: ' + e.message);
    btn.disabled = false;
    btn.textContent = 'Проверить подключение';
  }
}

function showConnectError(msg) {
  const el = document.getElementById('connect-error');
  el.textContent = msg;
  el.classList.remove('hidden');
}
function showConnectSuccess(msg) {
  const el = document.getElementById('connect-success');
  el.textContent = '✓ ' + msg;
  el.classList.remove('hidden');
}
function hideConnectMessages() {
  document.getElementById('connect-error').classList.add('hidden');
  document.getElementById('connect-success').classList.add('hidden');
}

/* ── Inputs ──────────────────────────────────────────────────────────────────── */
function buildInputsForm() {
  const form = document.getElementById('inputs-form');
  form.innerHTML = '';
  const seen = new Set();
  const allInputs = [];
  for (const step of state.steps) {
    for (const inp of (step.inputs || [])) {
      if (!seen.has(inp.key)) {
        seen.add(inp.key);
        allInputs.push(inp);
      }
    }
  }

  if (allInputs.length === 0) {
    form.innerHTML = '<div style="color:var(--text-dim); font-size:14px;">Дополнительные параметры не требуются. Можно сразу начинать!</div>';
    return;
  }

  for (const inp of allInputs) {
    const field = document.createElement('div');
    field.className = 'field';
    const currentValue = state.context[inp.key] ?? inp.default ?? '';
    if (inp.type === 'select') {
      const opts = (inp.options || []).map(o =>
        `<option value="${escHtml(o)}"${o === currentValue ? ' selected' : ''}>${escHtml(o)}</option>`
      ).join('');
      field.innerHTML = `
        <label>${inp.label}</label>
        <select id="input-${inp.key}" autocomplete="off">${opts}</select>
      `;
    } else {
      field.innerHTML = `
        <label>${inp.label}</label>
        <input
          id="input-${inp.key}"
          type="${inp.type || 'text'}"
          placeholder="${inp.placeholder || ''}"
          value="${escHtml(currentValue)}"
          autocomplete="off"
        >
      `;
    }
    form.appendChild(field);
  }
}

function collectInputs() {
  const form = document.getElementById('inputs-form');
  const inputs = form.querySelectorAll('input, select');
  for (const inp of inputs) {
    const key = inp.id.replace('input-', '');
    state.context[key] = inp.value;
  }
}

/* ── Execution ───────────────────────────────────────────────────────────────── */
function startExecution() {
  collectInputs();
  const seen = new Set();
  for (const step of state.steps) {
    for (const inp of (step.inputs || [])) {
      if (!seen.has(inp.key)) {
        seen.add(inp.key);
        if (!state.context[inp.key] && inp.key !== 'admin_pass' && !inp.optional) {
          alert(`Заполните поле: ${inp.label}`);
          return;
        }
      }
    }
  }
  goToPage('executing');
  renderExecSteps();
  openWebSocket();
}

function renderExecSteps() {
  const list = document.getElementById('exec-steps-list');
  list.innerHTML = '';
  const scenario = state.scenarios[state.selectedScenario];
  document.getElementById('exec-scenario-title').textContent =
    `${scenario.name} — ${scenario.subtitle}`;
  for (let i = 0; i < state.steps.length; i++) {
    const step = state.steps[i];
    const el = document.createElement('div');
    el.className = `exec-step${step.type === 'info' ? ' info-step' : ''}`;
    el.id = `exec-step-${step.id}`;
    el.innerHTML = `
      <div class="exec-status" id="exec-status-${step.id}">
        <span style="color:var(--text-xs); font-size:11px;">${i + 1}</span>
      </div>
      <div>
        <div class="exec-step-name">${step.name}</div>
        <div class="exec-step-desc">${step.description}</div>
      </div>
    `;
    list.appendChild(el);
  }
  updateProgress(0);
}

function setStepStatus(stepId, status) {
  const el   = document.getElementById(`exec-step-${stepId}`);
  const icon = document.getElementById(`exec-status-${stepId}`);
  el.classList.remove('current', 'done', 'failed');
  if (status === 'running') {
    el.classList.add('current');
    icon.innerHTML = '<div class="spinner"></div>';
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  } else if (status === 'done') {
    el.classList.add('done');
    icon.innerHTML = svg('icon-check', 16, 'var(--success)');
  } else if (status === 'failed') {
    el.classList.add('failed');
    icon.innerHTML = svg('icon-x', 16, 'var(--danger)');
  } else if (status === 'info') {
    icon.innerHTML = svg('icon-info', 16, 'var(--warning)');
  } else if (status === 'skipped') {
    el.classList.add('skipped');
    icon.innerHTML = svg('icon-skip', 16, 'var(--text-xs)');
  }
}

function updateProgress(done) {
  const total = state.steps.length;
  const pct   = total ? Math.round(done / total * 100) : 0;
  document.getElementById('exec-progress').style.width = pct + '%';
  document.getElementById('exec-progress-label').textContent = `${done} / ${total}`;
}

/* ── WebSocket (wizard) ──────────────────────────────────────────────────────── */
function openWebSocket() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  const wsUrl = `${proto}://${location.host}/ws/exec/${encodeURIComponent(state.sessionId)}`;
  state.ws = new WebSocket(wsUrl);

  state.ws.onopen = () => {
    termLog('> Соединение установлено', 't-info');
    state.currentStepIdx = 0;
    runNextStep();
  };
  state.ws.onmessage = (evt) => {
    const msg = JSON.parse(evt.data);
    handleWsMessage(msg);
  };
  state.ws.onerror = () => {
    termLog('WebSocket ошибка', 't-stderr');
  };
  state.ws.onclose = () => {
    if (!state.execDone) termLog('> Соединение закрыто', 't-dim');
  };
}

function handleWsMessage(msg) {
  switch (msg.type) {
    case 'stdout':
      termLog(msg.data);
      break;
    case 'stderr':
      termLog(msg.data, 't-stderr');
      break;
    case 'done':
      if (msg.success) {
        const step = state.steps[state.currentStepIdx];
        setStepStatus(step.id, step.type === 'info' ? 'info' : 'done');
        updateProgress(state.currentStepIdx + 1);
        state.currentStepIdx++;
        setTimeout(runNextStep, 300);
      } else {
        const step = state.steps[state.currentStepIdx];
        setStepStatus(step.id, 'failed');
        termLog(`\n✖ Шаг «${step.name}» завершился с ошибкой (exit code ${msg.exit_code}).`, 't-stderr');
        showStepErrorPanel(step);
      }
      break;
    case 'fix_done':
      state.fixInProgress = false;
      if (msg.success) {
        termLog('\n✓ Исправление применено. Нажмите «Повторить шаг».', 't-success');
        document.querySelectorAll('.error-fix-btn').forEach(b => {
          b.disabled = true;
          b.textContent = '✓ Применено';
        });
      } else {
        termLog(`\n✖ Исправление завершилось с ошибкой (exit code ${msg.exit_code}).`, 't-stderr');
        document.querySelectorAll('.error-fix-btn').forEach(b => { b.disabled = false; });
      }
      break;
    case 'error':
      termLog('Ошибка: ' + msg.message, 't-stderr');
      break;
    case 'pong':
      break;
  }
}

function runNextStep() {
  if (state.currentStepIdx >= state.steps.length) {
    finishExecution();
    return;
  }
  const step = state.steps[state.currentStepIdx];
  setStepStatus(step.id, 'running');
  termLog(`\n▶ ${step.name}`, 't-info');

  if (step.type === 'download') {
    addDownloadButton(step);
    setStepStatus(step.id, 'done');
    updateProgress(state.currentStepIdx + 1);
    state.currentStepIdx++;
    setTimeout(runNextStep, 200);
    return;
  }

  state.ws.send(JSON.stringify({
    type: 'exec',
    scenario: state.selectedScenario,
    step_id: step.id,
    context: state.context,
  }));
}

/* ── Download helpers ────────────────────────────────────────────────────────── */
function addDownloadButton(step) {
  const section = document.getElementById('download-section');
  section.classList.remove('hidden');
  const btn = document.createElement('button');
  btn.className = 'btn btn-primary';
  btn.style.cssText = 'justify-content: flex-start; gap: 10px;';
  btn.innerHTML = `
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
      <polyline points="7 10 12 15 17 10"/>
      <line x1="12" y1="15" x2="12" y2="3"/>
    </svg>
    Скачать <strong>${step.download_name}</strong>
    <span style="margin-left:auto; font-size:11px; opacity:.7;">${step.download_file}</span>
  `;
  btn.onclick = () => downloadFile(step.download_file, step.download_name, btn);
  document.getElementById('download-buttons').appendChild(btn);
  termLog(`\n⬇ Файл готов к скачиванию: ${step.download_file}`, 't-success');
}

async function downloadFile(filePath, fileName, btn) {
  const origHtml = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = '<div class="spinner" style="width:14px;height:14px;"></div> Загрузка…';
  try {
    const url = `/api/download/${encodeURIComponent(state.sessionId)}?file_path=${encodeURIComponent(filePath)}`;
    const res = await fetch(url);
    if (!res.ok) {
      const err = await res.json().catch(() => ({ detail: res.statusText }));
      throw new Error(err.detail || res.statusText);
    }
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(a.href);
    btn.innerHTML = `✓ Скачан: <strong>${fileName}</strong>`;
    btn.style.background = 'var(--success)';
    btn.disabled = false;
  } catch (e) {
    btn.innerHTML = origHtml;
    btn.disabled = false;
    termLog(`\n✖ Ошибка скачивания: ${e.message}`, 't-stderr');
  }
}

function finishExecution() {
  state.execDone = true;
  if (state.ws) state.ws.close();
  termLog('\n✓ Все шаги выполнены!', 't-success');
  setTimeout(() => goToDone(), 800);
}

/* ── Done page ───────────────────────────────────────────────────────────────── */
function goToDone() {
  const scenario = state.scenarios[state.selectedScenario];
  document.getElementById('done-subtitle').textContent =
    `${scenario.name} (${scenario.subtitle}) — сервер готов к работе!`;
  const lastInfo = [...state.steps].reverse().find(s => s.type === 'info' && s.info_text);
  if (lastInfo) {
    const infoEl = document.getElementById('done-info');
    infoEl.innerHTML = markdownToHtml(interpolate(lastInfo.info_text, state.context));
    document.getElementById('done-info-card').style.display = 'block';
  }
  // Переносим блок скачивания на страницу done, чтобы кнопки были доступны
  const dlSection = document.getElementById('download-section');
  if (!dlSection.classList.contains('hidden')) {
    const btnRow = document.querySelector('#page-done .btn-row');
    document.getElementById('page-done').insertBefore(dlSection, btnRow);
  }
  goToPage('done');
}

function resetWizard() {
  state.page = 'scenario';
  state.selectedScenario = null;
  state.sessionId = null;
  state.context = {};
  state.steps = [];
  state.currentStepIdx = 0;
  state.execDone = false;
  state.fixInProgress = false;
  if (state.ws) { try { state.ws.close(); } catch (e) {} state.ws = null; }
  hideStepErrorPanel();

  // Возвращаем блок скачивания обратно на страницу выполнения, если он был перенесён
  const dlSection = document.getElementById('download-section');
  if (dlSection.parentElement.id === 'page-done') {
    const errorPanel = document.getElementById('exec-error-panel');
    document.getElementById('page-executing').insertBefore(dlSection, errorPanel);
  }
  dlSection.classList.add('hidden');
  document.getElementById('download-buttons').innerHTML = '';
  document.getElementById('badge-text').textContent = 'не подключён';
  document.getElementById('connection-badge').classList.remove('connected');
  document.getElementById('btn-connect').classList.remove('hidden');
  document.getElementById('btn-connect').disabled = false;
  document.getElementById('btn-connect').textContent = 'Проверить подключение';
  document.getElementById('btn-creds-next').classList.add('hidden');
  document.getElementById('btn-scenario-next').disabled = true;
  document.querySelectorAll('.scenario-card').forEach(c => c.classList.remove('selected'));
  hideConnectMessages();
  goToPage('scenario');
}

/* ── Terminal helpers (wizard) ───────────────────────────────────────────────── */
const terminal = () => document.getElementById('terminal');

function termLog(text, cls = '') {
  const t = terminal();
  const span = document.createElement('span');
  if (cls) span.className = cls;
  span.textContent = text;
  t.appendChild(span);
  t.scrollTop = t.scrollHeight;
}

function clearTerminal() {
  terminal().innerHTML = '';
}

/* ── Step error panel ────────────────────────────────────────────────────────── */
function showStepErrorPanel(step) {
  const panel   = document.getElementById('exec-error-panel');
  const title   = document.getElementById('error-panel-title');
  const hints   = document.getElementById('error-hints-list');
  const actions = document.getElementById('error-panel-actions');

  title.textContent = `Шаг «${step.name}» завершился с ошибкой`;
  hints.innerHTML = '';
  const hintList = step.error_hints || [];
  if (hintList.length === 0) {
    hints.innerHTML = '<div class="error-hint"><div class="error-hint-text">Проверьте вывод терминала выше — там должна быть детальная информация об ошибке.</div></div>';
  } else {
    hintList.forEach((h) => {
      const div = document.createElement('div');
      div.className = 'error-hint';
      div.innerHTML = `
        <div class="error-hint-title">${escHtml(h.title)}</div>
        <div class="error-hint-text">${escHtml(h.hint)}</div>
        ${h.fix_id !== null && h.fix_id !== undefined
          ? `<button class="btn btn-outline error-fix-btn" style="font-size:12px;padding:5px 12px;" onclick="applyStepFix(${h.fix_id})">⚙ Применить исправление</button>`
          : ''}
      `;
      hints.appendChild(div);
    });
  }
  actions.innerHTML = `
    <button class="btn btn-primary" onclick="retryCurrentStep()">↺ Повторить шаг</button>
    ${step.skippable
      ? `<button class="btn btn-outline" onclick="skipCurrentStep()">⟶ Пропустить шаг</button>`
      : ''}
  `;
  panel.classList.remove('hidden');
  panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideStepErrorPanel() {
  document.getElementById('exec-error-panel').classList.add('hidden');
}

function retryCurrentStep() {
  hideStepErrorPanel();
  const step = state.steps[state.currentStepIdx];
  setStepStatus(step.id, 'running');
  termLog(`\n↺ Повтор шага: ${step.name}`, 't-info');
  state.ws.send(JSON.stringify({
    type: 'exec',
    scenario: state.selectedScenario,
    step_id: step.id,
    context: state.context,
  }));
}

function skipCurrentStep() {
  hideStepErrorPanel();
  const step = state.steps[state.currentStepIdx];
  setStepStatus(step.id, 'skipped');
  termLog(`\n⟶ Шаг «${step.name}» пропущен.`, 't-dim');
  updateProgress(state.currentStepIdx + 1);
  state.currentStepIdx++;
  setTimeout(runNextStep, 200);
}

function applyStepFix(fixId) {
  if (state.fixInProgress) return;
  state.fixInProgress = true;
  document.querySelectorAll('.error-fix-btn').forEach(b => {
    b.disabled = true;
    b.textContent = '⚙ Применяем…';
  });
  const step = state.steps[state.currentStepIdx];
  termLog(`\n⚙ Применяем исправление...`, 't-info');
  state.ws.send(JSON.stringify({
    type: 'fix',
    scenario: state.selectedScenario,
    step_id: step.id,
    fix_id: fixId,
    context: state.context,
  }));
}

/* ── Utils ───────────────────────────────────────────────────────────────────── */
function svg(id, size, color) {
  return `<svg width="${size}" height="${size}" style="color:${color}"><use href="#${id}"/></svg>`;
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function interpolate(text, ctx) {
  return text.replace(/\{(\w+)\}/g, (_, k) => ctx[k] || `{${k}}`);
}

function markdownToHtml(md) {
  return md
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/```[\w]*\n([\s\S]*?)```/g, (_, c) => `<pre>${c}</pre>`)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h2>$1</h2>')
    .replace(/^[-*] (.+)$/gm, '<li>$1</li>')
    .replace(/((?:^<li>.*$\n?)+)/gm, '<ul>$1</ul>')
    .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
    .replace(/\n\n/g, '<br><br>')
    .replace(/\n/g, '\n');
}

function showGlobalError(msg) {
  document.getElementById('main').innerHTML = `
    <div class="alert alert-danger" style="font-size:15px;">${msg}</div>
  `;
}

/* ── Start wizard ────────────────────────────────────────────────────────────── */
init();

/* ═══════════════════════════════════════════════════════════════════════════════
   DEPLOY MANAGER
   ═══════════════════════════════════════════════════════════════════════════════ */

/* ── State ───────────────────────────────────────────────────────────────────── */
let dm_sessionId = null;
let dm_ws        = null;

/* ── Helpers ─────────────────────────────────────────────────────────────────── */
const dm$ = id => document.getElementById(id);

function dm_stripAnsi(str) {
  return str.replace(/\x1b\[[0-9;]*[mGKHFJ]/g, "");
}

/* ── Init ────────────────────────────────────────────────────────────────────── */
window.addEventListener("DOMContentLoaded", async () => {
  try {
    const res  = await fetch("/api/config");
    const cfg  = await res.json();
    dm$("dm-local-path").value   = cfg.local_path  || "";
    dm$("dm-remote-path").value  = cfg.remote_path || "";
    dm$("dm-service-name").value = cfg.service     || "jurilytics";
  } catch (_) {
    // не критично — пользователь заполнит сам
  }

  try {
    const res  = await fetch("/api/last-connection");
    const conn = await res.json();
    if (conn.host) {
      dm$("dm-ssh-host").value = conn.host     || "";
      dm$("dm-ssh-port").value = conn.port     || 22;
      dm$("dm-ssh-user").value = conn.username || "";
    }
  } catch (_) {
    // не критично — пользователь заполнит сам
  }
});

/* ── Connection ──────────────────────────────────────────────────────────────── */
async function dm_connect() {
  const btn = dm$("dm-btn-connect");
  dm$("dm-connect-error").classList.add("hidden");
  btn.disabled = true;
  dm$("dm-connect-spinner").classList.remove("hidden");

  const body = {
    host:        dm$("dm-ssh-host").value.trim(),
    port:        parseInt(dm$("dm-ssh-port").value) || 22,
    username:    dm$("dm-ssh-user").value.trim(),
    password:    dm$("dm-ssh-pass").value || null,
    private_key: dm$("dm-ssh-key").value.trim() || null,
  };

  if (!body.host) {
    dm_showConnectError("Укажите IP-адрес сервера");
    btn.disabled = false;
    dm$("dm-connect-spinner").classList.add("hidden");
    return;
  }

  try {
    const res  = await fetch("/api/connect", {
      method:  "POST",
      headers: { "Content-Type": "application/json" },
      body:    JSON.stringify(body),
    });
    const data = await res.json();

    if (data.success) {
      dm_sessionId = data.session_id;
      dm_onConnected(body.host, body.username);
    } else {
      dm_showConnectError(data.error || "Не удалось подключиться");
    }
  } catch (e) {
    dm_showConnectError(`Ошибка сети: ${e.message}`);
  } finally {
    btn.disabled = false;
    dm$("dm-connect-spinner").classList.add("hidden");
  }
}

function dm_showConnectError(msg) {
  const el = dm$("dm-connect-error");
  el.textContent = msg;
  el.classList.remove("hidden");
}

function dm_onConnected(host, username) {
  if (!dm$("dm-service-user").value) {
    dm$("dm-service-user").value = username;
  }

  const badge = document.getElementById("connection-badge");
  badge.classList.add("connected");
  document.getElementById("badge-text").textContent = `${username}@${host}`;

  dm$("dm-card-connect").classList.add("hidden");
  dm$("dm-card-config").classList.remove("hidden");
  dm$("dm-actions-panel").classList.remove("hidden");
  dm$("dm-card-terminal").classList.remove("hidden");

  dm_appendTerminal(`Подключено к ${username}@${host}\n`, false);
}

/* ── Operations ──────────────────────────────────────────────────────────────── */
function dm_runOp(type) {
  if (!dm_sessionId) {
    alert("Сначала подключитесь к серверу");
    return;
  }
  if (dm_ws) {
    dm_ws.close();
    dm_ws = null;
  }

  dm_clearTerminal();
  dm_setActionBtnsDisabled(true);
  dm$("dm-result-badge").textContent = "";

  const service      = dm$("dm-service-name").value.trim() || "jurilytics";
  const local_path   = dm$("dm-local-path").value.trim();
  const remote_path  = dm$("dm-remote-path").value.trim();
  const service_user = dm$("dm-service-user").value.trim() || "root";

  const proto = location.protocol === "https:" ? "wss" : "ws";
  dm_ws = new WebSocket(`${proto}://${location.host}/ws/${dm_sessionId}`);

  dm_ws.onopen = () => {
    const payload = { type, service };
    if (type === "deploy") {
      if (!local_path || !remote_path) {
        dm_appendTerminal("ОШИБКА: заполните локальный и удалённый пути\n", true);
        dm_setActionBtnsDisabled(false);
        dm_ws.close();
        return;
      }
      payload.local_path  = local_path;
      payload.remote_path = remote_path;
      payload.run_pip     = true;
      const adminUsername = dm$("dm-admin-username").value.trim();
      const adminPassword = dm$("dm-admin-password").value.trim();
      if (adminUsername && adminPassword) {
        payload.admin_username = adminUsername;
        payload.admin_password = adminPassword;
      }
    }
    if (type === "install_service") {
      if (!remote_path) {
        dm_appendTerminal("ОШИБКА: заполните путь на сервере\n", true);
        dm_setActionBtnsDisabled(false);
        dm_ws.close();
        return;
      }
      payload.remote_path  = remote_path;
      payload.service_user = service_user;
    }
    if (type === "logs") {
      payload.lines = 100;
    }
    dm_ws.send(JSON.stringify(payload));
  };

  dm_ws.onmessage = e => {
    const msg = JSON.parse(e.data);
    if (msg.type === "output") {
      dm_appendTerminal(dm_stripAnsi(msg.data), !!msg.stderr);
    } else if (msg.type === "done") {
      const ok = msg.success;
      dm$("dm-result-badge").textContent = ok ? "✓ Успешно" : "✗ Ошибка";
      dm$("dm-result-badge").className   = ok ? "result-ok" : "result-err";
      dm_setActionBtnsDisabled(false);
      dm_ws.close();
      dm_ws = null;
    } else if (msg.type === "error") {
      dm_appendTerminal(`\nОШИБКА: ${msg.message}\n`, true);
      dm$("dm-result-badge").textContent = "✗ Ошибка";
      dm$("dm-result-badge").className   = "result-err";
      dm_setActionBtnsDisabled(false);
      dm_ws.close();
      dm_ws = null;
    } else if (msg.type === "pong") {
      // keepalive — игнорируем
    }
  };

  dm_ws.onerror = () => {
    dm_appendTerminal("\nОшибка WebSocket соединения\n", true);
    dm_setActionBtnsDisabled(false);
    dm_ws = null;
  };

  dm_ws.onclose = () => {
    dm_setActionBtnsDisabled(false);
    dm_ws = null;
  };

  const pingInterval = setInterval(() => {
    if (dm_ws && dm_ws.readyState === WebSocket.OPEN) {
      dm_ws.send(JSON.stringify({ type: "ping" }));
    } else {
      clearInterval(pingInterval);
    }
  }, 20_000);
}

/* ── Terminal (deploy) ───────────────────────────────────────────────────────── */
function dm_appendTerminal(text, isErr = false) {
  const term = dm$("dm-terminal");
  const span = document.createElement("span");
  span.className = isErr ? "stderr" : "";
  span.textContent = text;
  term.appendChild(span);
  term.scrollTop = term.scrollHeight;
}

function dm_clearTerminal() {
  dm$("dm-terminal").innerHTML = "";
  dm$("dm-result-badge").textContent = "";
}

/* ── UI helpers ──────────────────────────────────────────────────────────────── */
function dm_setActionBtnsDisabled(disabled) {
  ["dm-btn-deploy", "dm-btn-restart", "dm-btn-status", "dm-btn-logs", "dm-btn-install"].forEach(id => {
    dm$(id).disabled = disabled;
  });
}
