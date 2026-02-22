/* ── helpers: providers ── */
function isGigaChat(modelName) {
  return modelName && modelName.startsWith('GigaChat');
}

function buildModelOptions(selected) {
  const gigachat = models.filter(m => m.provider === 'gigachat');
  const ollama   = models.filter(m => m.provider === 'ollama');
  let html = '';
  if (gigachat.length) {
    html += `<optgroup label="GigaChat (облако)">`;
    html += gigachat.map(m =>
      `<option value="${m.name}" ${m.name === selected ? 'selected' : ''}>${m.name}</option>`
    ).join('');
    html += `</optgroup>`;
  }
  if (ollama.length) {
    html += `<optgroup label="Ollama (локально)">`;
    html += ollama.map(m =>
      `<option value="${m.name}" ${m.name === selected ? 'selected' : ''}>${m.name} (${m.size_gb} GB)</option>`
    ).join('');
    html += `</optgroup>`;
  }
  return html;
}

/* ── state ── */
let models = [];
let prompts = {};
let samples = [];
let columns = [];
let nextId = 1;

/* ── boot ── */
async function init() {
  await Promise.all([loadModels(), loadPrompts(), loadSamples()]);
  renderSampleBtns();
  addColumn();
  addColumn();
}

async function loadModels() {
  try {
    const r = await fetch('/api/models');
    const d = await r.json();
    models = d.models || [];
  } catch { models = []; }
}

async function loadPrompts() {
  const r = await fetch('/api/prompts');
  const d = await r.json();
  prompts = d.prompts || {};
}

async function loadSamples() {
  const r = await fetch('/api/samples');
  const d = await r.json();
  samples = d.samples || [];
}

/* ── samples ── */
function renderSampleBtns() {
  const container = document.getElementById('sample-btns');
  samples.forEach((s, i) => {
    const btn = document.createElement('button');
    btn.className = 'sample-btn';
    btn.textContent = s.label;
    btn.addEventListener('click', () => {
      document.getElementById('input-text').value = s.text;
      container.querySelectorAll('.sample-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    });
    container.appendChild(btn);
  });
}

/* ── columns ── */
function addColumn() {
  const id = nextId++;
  const defaultModel = models[0]?.name || '';
  const col = {
    id,
    model: defaultModel,
    temperature: 0.1,
    numCtx: 4096,
    maxTokens: 512,
    promptEnabled: true,
    promptKey: 'detailed',
    customPrompt: prompts['detailed']?.text || '',
    result: null,
  };
  columns.push(col);
  renderColumns();
  updateColCount();
}

function removeColumn(id) {
  columns = columns.filter(c => c.id !== id);
  renderColumns();
  updateColCount();
}

function updateColCount() {
  const el = document.getElementById('col-count-info');
  el.textContent = columns.length === 0 ? 'Нет колонок' : `${columns.length} колонок`;
}

function renderColumns() {
  const grid = document.getElementById('columns-grid');
  // сохраняем add-card
  const addCard = document.getElementById('add-col-card');
  grid.innerHTML = '';

  columns.forEach((col, idx) => {
    grid.appendChild(buildColumnEl(col, idx + 1));
  });

  grid.appendChild(addCard);
}

function buildColumnEl(col, num) {
  const el = document.createElement('div');
  el.className = 'column';
  el.id = `col-${col.id}`;
  el.innerHTML = `
    <div class="column-header">
      <div class="col-num">${num}</div>
      <div class="col-title">Колонка ${num}</div>
      <div class="col-actions">
        <button class="col-btn run-col" title="Запустить" data-id="${col.id}">▶</button>
        <button class="col-btn del-col" title="Удалить" data-id="${col.id}">✕</button>
      </div>
    </div>

    <div class="column-settings">
      <!-- model -->
      <div class="field">
        <label>Модель</label>
        ${models.length === 0
          ? `<select disabled><option>Нет моделей</option></select>`
          : `<select class="col-model" data-id="${col.id}">
              ${buildModelOptions(col.model)}
            </select>`
        }
      </div>

      <!-- temperature -->
      <div class="field">
        <label>Temperature <span class="temp-val-${col.id}">${col.temperature.toFixed(1)}</span></label>
        <input type="range" min="0" max="2" step="0.1" value="${col.temperature}"
               class="col-temp-range" data-id="${col.id}">
      </div>

      <div class="fields-row">
        <!-- num_ctx -->
        <div class="field" id="numctx-field-${col.id}">
          <label>num_ctx ${isGigaChat(col.model) ? '<span style="color:var(--text2);font-weight:400">N/A</span>' : ''}</label>
          <select class="col-numctx" data-id="${col.id}" ${isGigaChat(col.model) ? 'disabled style="opacity:.4"' : ''}>
            ${[512, 1024, 2048, 4096, 8192, 16384, 32768].map(v =>
              `<option value="${v}" ${v === col.numCtx ? 'selected' : ''}>${v}</option>`
            ).join('')}
          </select>
        </div>
        <!-- max_tokens -->
        <div class="field">
          <label>max_tokens</label>
          <input type="number" class="col-maxtokens" data-id="${col.id}"
                 value="${col.maxTokens}" min="-1" max="32768" step="64">
        </div>
      </div>

      <!-- system prompt -->
      <div class="field">
        <div class="prompt-toggle">
          <label class="toggle-switch">
            <input type="checkbox" class="col-prompt-toggle" data-id="${col.id}" ${col.promptEnabled ? 'checked' : ''}>
            <span class="toggle-slider"></span>
          </label>
          System Prompt
        </div>
        <div class="prompt-preset-row" id="presets-${col.id}">
          ${Object.entries(prompts).filter(([k]) => k !== 'none').map(([k, v]) =>
            `<button class="preset-btn ${col.promptKey === k ? 'active' : ''}"
                     data-id="${col.id}" data-key="${k}">${v.label}</button>`
          ).join('')}
        </div>
        <textarea class="sys-prompt col-prompt-text" data-id="${col.id}"
                  rows="4" ${col.promptEnabled ? '' : 'disabled'}
                  placeholder="Системный промпт...">${escHtml(col.customPrompt)}</textarea>
      </div>
    </div>

    <div class="column-result" id="result-${col.id}">
      <div class="result-empty">Нажмите ▶ для запуска</div>
    </div>
  `;

  // events
  el.querySelector('.run-col').addEventListener('click', () => runColumn(col.id));
  el.querySelector('.del-col').addEventListener('click', () => removeColumn(col.id));

  el.querySelector('.col-model')?.addEventListener('change', e => {
    const c = getCol(col.id);
    c.model = e.target.value;
    // обновляем num_ctx — недоступен для GigaChat
    const numCtxField = el.querySelector(`#numctx-field-${col.id}`);
    const numCtxSelect = el.querySelector('.col-numctx');
    const gc = isGigaChat(c.model);
    numCtxSelect.disabled = gc;
    numCtxSelect.style.opacity = gc ? '0.4' : '1';
    numCtxField.querySelector('label').innerHTML =
      `num_ctx ${gc ? '<span style="color:var(--text2);font-weight:400">N/A</span>' : ''}`;
  });

  const rangeEl = el.querySelector('.col-temp-range');
  rangeEl.addEventListener('input', e => {
    const v = parseFloat(e.target.value);
    getCol(col.id).temperature = v;
    el.querySelector(`.temp-val-${col.id}`).textContent = v.toFixed(1);
  });

  el.querySelector('.col-numctx').addEventListener('change', e => {
    getCol(col.id).numCtx = parseInt(e.target.value);
  });

  el.querySelector('.col-maxtokens').addEventListener('change', e => {
    getCol(col.id).maxTokens = parseInt(e.target.value);
  });

  el.querySelector('.col-prompt-toggle').addEventListener('change', e => {
    const c = getCol(col.id);
    c.promptEnabled = e.target.checked;
    el.querySelector('.col-prompt-text').disabled = !e.target.checked;
    el.querySelector(`#presets-${col.id}`).style.opacity = e.target.checked ? '1' : '0.4';
  });

  el.querySelector('.col-prompt-text').addEventListener('input', e => {
    getCol(col.id).customPrompt = e.target.value;
    // снимаем активный пресет если текст изменён вручную
    el.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('active'));
    getCol(col.id).promptKey = '';
  });

  el.querySelectorAll('.preset-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const key = btn.dataset.key;
      const c = getCol(col.id);
      c.promptKey = key;
      c.customPrompt = prompts[key]?.text || '';
      el.querySelector('.col-prompt-text').value = c.customPrompt;
      el.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    });
  });

  return el;
}

function getCol(id) {
  return columns.find(c => c.id === id);
}

/* ── run ── */
async function runColumn(id) {
  const col = getCol(id);
  const text = document.getElementById('input-text').value.trim();
  if (!text) { showToast('Введите текст'); return; }
  if (!col.model) { showToast('Выберите модель'); return; }

  const colEl = document.getElementById(`col-${id}`);
  colEl.className = 'column running';
  setResultLoading(id);

  const body = {
    text,
    model: col.model,
    temperature: col.temperature,
    num_ctx: col.numCtx,
    max_tokens: col.maxTokens,
    system_prompt: col.promptEnabled ? col.customPrompt : '',
  };

  try {
    const r = await fetch('/api/extract', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const result = await r.json();
    col.result = result;
    renderResult(id, result);
    colEl.className = `column ${result.error ? 'error' : result.valid_json ? 'success' : 'error'}`;
  } catch (e) {
    colEl.className = 'column error';
    renderResult(id, { error: String(e) });
  }
}

async function runAll() {
  const text = document.getElementById('input-text').value.trim();
  if (!text) { showToast('Введите текст'); return; }
  if (columns.length === 0) { showToast('Нет колонок'); return; }

  document.getElementById('btn-run-all').disabled = true;
  try {
    await Promise.all(columns.map(c => runColumn(c.id)));
  } finally {
    document.getElementById('btn-run-all').disabled = false;
  }
}

/* ── result rendering ── */
function setResultLoading(id) {
  document.getElementById(`result-${id}`).innerHTML =
    `<div class="result-status"><div class="spinner"></div> Выполняется...</div>`;
}

function renderResult(id, result) {
  const el = document.getElementById(`result-${id}`);

  if (result.error) {
    el.innerHTML = `
      <div class="result-status">
        <span class="badge badge-err">Ошибка</span>
      </div>
      <div class="result-content json-err">${escHtml(result.error)}</div>
    `;
    return;
  }

  const badgeClass = result.valid_json ? 'badge-ok' : 'badge-err';
  const badgeText = result.valid_json ? '✓ Валидный JSON' : '✗ Невалидный JSON';
  const providerBadge = result.provider === 'gigachat'
    ? `<span class="badge" style="background:rgba(108,142,247,0.15);color:var(--accent)">GigaChat</span>`
    : `<span class="badge" style="background:rgba(167,139,250,0.15);color:var(--accent2)">Ollama</span>`;

  const metaChips = [
    `<span class="meta-chip">⏱ <strong>${result.elapsed}s</strong></span>`,
    result.tokens_generated != null
      ? `<span class="meta-chip">↗ <strong>${result.tokens_generated}</strong> токенов</span>` : '',
    result.tokens_prompt != null
      ? `<span class="meta-chip">↙ <strong>${result.tokens_prompt}</strong> prompt</span>` : '',
  ].filter(Boolean).join('');

  const jsonFormatted = result.parsed
    ? escHtml(JSON.stringify(result.parsed, null, 2))
    : null;
  const rawEscaped = escHtml(result.raw || '');

  el.innerHTML = `
    <div class="result-status">
      <span class="badge ${badgeClass}">${badgeText}</span>
      ${providerBadge}
    </div>
    <div class="result-meta">${metaChips}</div>
    <div class="result-tabs">
      <button class="tab-btn active" data-tab="json-${id}">JSON</button>
      <button class="tab-btn" data-tab="raw-${id}">Сырой ответ</button>
    </div>
    <div id="json-${id}" class="result-content ${result.valid_json ? 'json-ok' : 'json-err'}" data-pane="json-${id}">
      ${jsonFormatted ?? `<span style="color:var(--red)">${result.parse_error || 'Не удалось распарсить'}</span>\n\n${rawEscaped}`}
    </div>
    <div id="raw-${id}" class="result-content" data-pane="raw-${id}" style="display:none">
      ${rawEscaped}
    </div>
  `;

  el.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      el.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const tabId = btn.dataset.tab;
      el.querySelectorAll('[data-pane]').forEach(p => {
        p.style.display = p.id === tabId ? 'block' : 'none';
      });
    });
  });
}

/* ── helpers ── */
function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function showToast(msg) {
  const t = document.createElement('div');
  t.className = 'toast';
  t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 3000);
}

/* ── global events ── */
document.getElementById('btn-add-col').addEventListener('click', addColumn);
document.getElementById('add-col-card').addEventListener('click', addColumn);
document.getElementById('btn-run-all').addEventListener('click', runAll);
document.getElementById('btn-clear-all').addEventListener('click', () => {
  columns.forEach(c => {
    c.result = null;
    const el = document.getElementById(`col-${c.id}`);
    if (el) {
      el.className = 'column';
      document.getElementById(`result-${c.id}`).innerHTML =
        '<div class="result-empty">Нажмите ▶ для запуска</div>';
    }
  });
});

init();
