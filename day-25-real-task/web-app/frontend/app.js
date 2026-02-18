/* ── State ──────────────────────────────────────────────────────────────────── */
const state = {
  page: 'upload',
  sessionId: null,
  filename: null,
  agentsDone: 0,
  agentTotal: 4,
  model: 'GigaChat',
  currentUser: null,
};
const API = ''; // относительные URL — работает локально и на VPS

/* ── Auth ───────────────────────────────────────────────────────────────────── */
async function checkAuth() {
  const res = await fetch(`${API}/api/me`);
  if (res.status === 401) {
    window.location.href = '/login.html';
    return false;
  }
  state.currentUser = await res.json();
  document.getElementById('header-user').textContent = state.currentUser.username;
  if (state.currentUser.role === 'admin') {
    document.getElementById('btn-admin').classList.remove('hidden');
  }
  return true;
}

async function logout() {
  await fetch(`${API}/api/logout`, { method: 'POST' });
  window.location.href = '/login.html';
}

/* ── apiFetch — редирект на /login.html при 401 ─────────────────────────────── */
async function apiFetch(url, options = {}) {
  const res = await fetch(url, options);
  if (res.status === 401) {
    window.location.href = '/login.html';
    return null;
  }
  return res;
}

/* ── Boot ───────────────────────────────────────────────────────────────────── */
async function init() {
  const ok = await checkAuth();
  if (!ok) return;

  const zone  = document.getElementById('upload-zone');
  const input = document.getElementById('file-input');

  // Drag & drop
  zone.addEventListener('dragover', e => { e.preventDefault(); zone.classList.add('drag-over'); });
  zone.addEventListener('dragleave', () => zone.classList.remove('drag-over'));
  zone.addEventListener('drop', e => {
    e.preventDefault();
    zone.classList.remove('drag-over');
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  });

  // Click to pick
  zone.addEventListener('click', e => {
    if (e.target.tagName !== 'LABEL') input.click();
  });
  input.addEventListener('change', () => {
    if (input.files[0]) handleFile(input.files[0]);
  });

  // Chat Enter key
  document.getElementById('chat-input').addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendQuestion(); }
  });
}

/* ── Navigation ─────────────────────────────────────────────────────────────── */
function goToPage(page) {
  document.querySelectorAll('.page').forEach(el => el.classList.add('hidden'));
  document.getElementById(`page-${page}`).classList.remove('hidden');
  state.page = page;
  if (page === 'upload') {
    document.getElementById('header-badge').textContent = '';
  }
}

/* ── Upload & Analysis ──────────────────────────────────────────────────────── */
function handleFile(file) {
  const ext = file.name.split('.').pop().toLowerCase();
  if (!['txt', 'pdf'].includes(ext)) {
    showUploadError('Поддерживаются только файлы .txt и .pdf');
    return;
  }
  hideUploadError();
  startAnalysis(file);
}

async function startAnalysis(file) {
  state.filename = file.name;
  state.agentsDone = 0;
  state.model = document.getElementById('model-select').value;

  // Сброс агентов — все видны, все со статусом "не используется"
  state.agentTotal = 4;
  ['ip_rights', 'financial', 'obligations', 'post_contract', 'consumer_rights'].forEach(a => {
    setAgentStatus(a, 'inactive');
  });
  setProgress(0);
  setAggregatorLabel('Ожидание агентов...');

  goToPage('analysis');
  document.getElementById('analysis-title').textContent = `Анализирую: ${file.name}`;
  document.getElementById('analysis-sub').textContent = '';
  document.getElementById('header-badge').textContent = file.name;

  const formData = new FormData();
  formData.append('file', file);
  formData.append('model', state.model);

  try {
    const response = await apiFetch(`${API}/api/analyze`, {
      method: 'POST',
      body: formData,
    });

    if (!response) return;

    if (!response.ok) {
      const err = await response.json().catch(() => ({ detail: response.statusText }));
      throw new Error(err.detail || 'Ошибка анализа');
    }

    await readSSE(response);

  } catch (e) {
    goToPage('upload');
    showUploadError(`Ошибка: ${e.message}`);
  }
}

async function readSSE(response) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop(); // сохраняем незавершённую строку

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        try {
          const event = JSON.parse(line.slice(6));
          handleSSEEvent(event);
        } catch (_) {}
      }
    }
  }
}

function handleSSEEvent(event) {
  switch (event.type) {

    case 'meta':
      state.filename = event.filename;
      document.getElementById('analysis-title').textContent = `Анализирую: ${event.filename}`;
      document.getElementById('header-badge').textContent = event.filename;
      document.getElementById('analysis-sub').textContent =
        `${event.chars.toLocaleString('ru')} символов${event.truncated ? ' (обрезан до 150 000)' : ''}`;
      break;

    case 'classified':
      state.agentTotal = event.active_agents.length;
      // Активные агенты → ожидание, остальные → не используется
      ['ip_rights', 'financial', 'obligations', 'post_contract', 'consumer_rights'].forEach(a => {
        setAgentStatus(a, event.active_agents.includes(a) ? 'waiting' : 'inactive');
      });
      break;

    case 'agent_start':
      setAgentStatus(event.agent, 'running');
      break;

    case 'agent_done':
      setAgentStatus(event.agent, 'done');
      state.agentsDone++;
      setProgress(state.agentsDone / state.agentTotal * 80);
      break;

    case 'aggregating':
      setAggregatorLabel('Агрегация и финальная проверка...');
      setProgress(85);
      break;

    case 'done':
      setProgress(100);
      setAggregatorLabel('Готово');
      setTimeout(() => showResult(event.markdown), 400);
      break;

    case 'session':
      state.sessionId = event.session_id;
      break;

    case 'error':
      goToPage('upload');
      showUploadError(`Ошибка анализа: ${event.message}`);
      break;
  }
}

/* ── Agent status ────────────────────────────────────────────────────────────── */
function setAgentStatus(agent, status) {
  const card   = document.getElementById(`agent-${agent}`);
  const label  = document.getElementById(`status-${agent}`);

  card.classList.remove('running', 'done', 'error', 'inactive');

  const LABELS = {
    inactive: 'не используется',
    waiting:  'ожидание',
    running:  '<span class="spinner"></span> анализирует...',
    done:     '✓ готово',
    error:    '✗ ошибка',
  };

  label.innerHTML = LABELS[status] || status;
  if (status !== 'waiting') card.classList.add(status);
}

function setProgress(pct) {
  document.getElementById('progress-fill').style.width = pct + '%';
}

function setAggregatorLabel(text) {
  document.getElementById('aggregator-label').textContent = text;
}

/* ── Result ──────────────────────────────────────────────────────────────────── */
function showResult(markdown) {
  document.getElementById('result-filename').textContent = state.filename;
  const contentEl = document.getElementById('result-content');
  contentEl.innerHTML = marked.parse(markdown);

  // Оборачиваем таблицы для горизонтальной прокрутки на мобильных
  contentEl.querySelectorAll('table').forEach(table => {
    const wrapper = document.createElement('div');
    wrapper.className = 'table-scroll';
    table.parentNode.insertBefore(wrapper, table);
    wrapper.appendChild(table);
  });

  // Сброс чата
  document.getElementById('chat-messages').innerHTML = '';
  document.getElementById('chat-input').value = '';

  goToPage('result');
}

/* ── Q&A ─────────────────────────────────────────────────────────────────────── */
async function sendQuestion() {
  const input = document.getElementById('chat-input');
  const question = input.value.trim();
  if (!question) return;

  if (!state.sessionId) {
    appendBubble('assistant', 'Сессия недоступна — загрузите документ снова.');
    return;
  }

  input.value = '';
  appendBubble('user', question);

  const btn = document.getElementById('btn-ask');
  btn.disabled = true;
  btn.textContent = '...';

  const thinkBubble = appendBubble('assistant', '<span class="spinner"></span>');

  try {
    const res = await apiFetch(`${API}/api/ask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ session_id: state.sessionId, question }),
    });

    if (!res) return;

    if (!res.ok) {
      const err = await res.json().catch(() => ({ detail: res.statusText }));
      throw new Error(err.detail || 'Ошибка запроса');
    }

    const data = await res.json();
    thinkBubble.innerHTML = simpleMarkdown(data.answer);

  } catch (e) {
    thinkBubble.textContent = `Ошибка: ${e.message}`;
  } finally {
    btn.disabled = false;
    btn.textContent = 'Отправить';
  }
}

function appendBubble(role, html) {
  const messages = document.getElementById('chat-messages');
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${role}`;
  bubble.innerHTML = html;
  messages.appendChild(bubble);
  messages.scrollTop = messages.scrollHeight;
  return bubble;
}

/* ── History ─────────────────────────────────────────────────────────────────── */
async function openHistory() {
  try {
    const res = await apiFetch(`${API}/api/history`);
    if (!res) return;
    if (!res.ok) throw new Error(res.statusText);
    const items = await res.json();
    renderHistory(items);
    goToPage('history');
  } catch (e) {
    alert('Ошибка загрузки истории: ' + e.message);
  }
}

function renderHistory(items) {
  const list  = document.getElementById('history-list');
  const empty = document.getElementById('history-empty');

  if (items.length === 0) {
    list.innerHTML = '';
    empty.classList.remove('hidden');
    return;
  }

  empty.classList.add('hidden');
  list.innerHTML = items.map(item => `
    <div class="history-item" id="hist-${item.id}">
      <div>
        <div class="history-filename">📄 ${escHtml(item.filename)}</div>
        <div class="history-meta">${formatDate(item.uploaded_at)} · ${item.char_count.toLocaleString('ru')} символов</div>
      </div>
      <div class="history-actions">
        <button class="btn btn-primary btn-sm" onclick="openHistoryItem('${item.id}')">Открыть</button>
        <button class="btn btn-outline btn-sm" onclick="reanalyzeHistoryItem('${item.id}')">Обновить</button>
        <button class="btn btn-danger btn-sm" onclick="deleteHistoryItem('${item.id}')">Удалить</button>
      </div>
    </div>
  `).join('');
}

async function openHistoryItem(id) {
  try {
    const model = encodeURIComponent(state.model || 'GigaChat');
    const res = await apiFetch(`${API}/api/history/${id}?model=${model}`);
    if (!res) return;
    if (!res.ok) {
      const err = await res.json().catch(() => ({ detail: res.statusText }));
      throw new Error(err.detail || res.statusText);
    }
    const data = await res.json();
    state.sessionId = data.session_id;
    state.filename  = data.filename;
    document.getElementById('header-badge').textContent = data.filename;
    showResult(data.result_markdown);
  } catch (e) {
    alert('Ошибка: ' + e.message);
  }
}

async function reanalyzeHistoryItem(id) {
  state.agentsDone = 0;
  state.agentTotal = 4;
  ['ip_rights', 'financial', 'obligations', 'post_contract', 'consumer_rights'].forEach(a => {
    setAgentStatus(a, 'inactive');
  });
  setProgress(0);
  setAggregatorLabel('Ожидание агентов...');

  goToPage('analysis');
  document.getElementById('analysis-title').textContent = 'Обновляю анализ...';
  document.getElementById('analysis-sub').textContent = '';

  const model = encodeURIComponent(state.model || 'GigaChat');

  try {
    const response = await apiFetch(`${API}/api/history/${id}/reanalyze?model=${model}`, {
      method: 'POST',
    });

    if (!response) return;

    if (!response.ok) {
      const err = await response.json().catch(() => ({ detail: response.statusText }));
      throw new Error(err.detail || 'Ошибка анализа');
    }

    await readSSE(response);

  } catch (e) {
    goToPage('history');
    alert(`Ошибка переанализа: ${e.message}`);
  }
}

async function deleteHistoryItem(id) {
  if (!confirm('Удалить этот анализ из истории?')) return;
  try {
    const res = await apiFetch(`${API}/api/history/${id}`, { method: 'DELETE' });
    if (!res) return;
    if (!res.ok && res.status !== 204) throw new Error(res.statusText);
    document.getElementById(`hist-${id}`)?.remove();
    if (!document.querySelector('.history-item')) {
      document.getElementById('history-empty').classList.remove('hidden');
    }
  } catch (e) {
    alert('Ошибка удаления: ' + e.message);
  }
}

function formatDate(iso) {
  return new Date(iso).toLocaleString('ru', {
    day: 'numeric', month: 'long', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

/* ── Admin panel ─────────────────────────────────────────────────────────────── */
async function openAdmin() {
  await loadAdminUsers();
  goToPage('admin');
}

async function loadAdminUsers() {
  const res = await apiFetch(`${API}/api/admin/users`);
  if (!res) return;
  if (!res.ok) { alert('Ошибка загрузки пользователей'); return; }
  const users = await res.json();
  renderAdminUsers(users);
}

function renderAdminUsers(users) {
  const list = document.getElementById('admin-user-list');
  if (users.length === 0) {
    list.innerHTML = '<div class="history-empty">Нет пользователей</div>';
    return;
  }
  list.innerHTML = users.map(u => `
    <div class="history-item" id="admin-user-${escHtml(u.username)}">
      <div>
        <div class="history-filename">${escHtml(u.username)} <span class="role-badge role-${u.role}">${u.role}</span></div>
        <div class="history-meta">${formatDate(u.created_at)}</div>
      </div>
      <div class="history-actions">
        ${u.username !== state.currentUser.username
          ? `<button class="btn btn-danger btn-sm" onclick="deleteAdminUser('${escHtml(u.username)}')">Удалить</button>`
          : '<span class="history-meta">вы</span>'
        }
      </div>
    </div>
  `).join('');
}

async function createAdminUser() {
  const username = document.getElementById('new-username').value.trim();
  const password = document.getElementById('new-password').value;
  const errEl    = document.getElementById('admin-error');
  const okEl     = document.getElementById('admin-success');

  errEl.classList.add('hidden');
  okEl.classList.add('hidden');

  if (!username || !password) {
    errEl.textContent = 'Заполните все поля';
    errEl.classList.remove('hidden');
    return;
  }

  const res = await apiFetch(`${API}/api/admin/users`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res) return;

  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    errEl.textContent = data.detail || 'Ошибка создания пользователя';
    errEl.classList.remove('hidden');
    return;
  }

  document.getElementById('new-username').value = '';
  document.getElementById('new-password').value = '';
  okEl.textContent = `Пользователь «${escHtml(username)}» создан`;
  okEl.classList.remove('hidden');
  await loadAdminUsers();
}

async function deleteAdminUser(username) {
  if (!confirm(`Удалить пользователя «${username}»?`)) return;
  const res = await apiFetch(`${API}/api/admin/users/${encodeURIComponent(username)}`, {
    method: 'DELETE',
  });
  if (!res) return;
  if (!res.ok && res.status !== 204) {
    const data = await res.json().catch(() => ({}));
    alert(data.detail || 'Ошибка удаления');
    return;
  }
  await loadAdminUsers();
}

/* ── Reset ───────────────────────────────────────────────────────────────────── */
function resetApp() {
  state.sessionId = null;
  state.filename = null;
  state.agentsDone = 0;
  document.getElementById('header-badge').textContent = '';
  document.getElementById('file-input').value = '';
  hideUploadError();
  goToPage('upload');
}

/* ── Helpers ─────────────────────────────────────────────────────────────────── */
function showUploadError(msg) {
  const el = document.getElementById('upload-error');
  el.textContent = msg;
  el.classList.remove('hidden');
}

function hideUploadError() {
  document.getElementById('upload-error').classList.add('hidden');
}

function escHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Минимальный Markdown → HTML для чат-пузырей (без таблиц) */
function simpleMarkdown(text) {
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n\n/g, '<br><br>')
    .replace(/\n/g, '<br>');
}

/* ── Start ───────────────────────────────────────────────────────────────────── */
init();
