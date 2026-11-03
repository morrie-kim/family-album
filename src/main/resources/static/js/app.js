/* 가족앨범 UI 스크립트 (의존성 없음) */
(() => {
  'use strict';
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const csrfToken = $('meta[name="_csrf"]')?.content;
  const csrfHeader = $('meta[name="_csrf_header"]')?.content;

  /* ── data-confirm 폼: 제출 전 확인 ─────────── */
  document.addEventListener('submit', e => {
    const msg = e.target?.dataset?.confirm;
    if (msg && !window.confirm(msg)) e.preventDefault();
  });

  /* ── 토스트 자동 닫힘 ─────────────────────── */
  $$('.toast').forEach(t => setTimeout(() => t.classList.add('hide'), 4500));

  /* ── 초대 코드 복사 ───────────────────────── */
  $$('[data-copy]').forEach(btn => btn.addEventListener('click', async () => {
    const label = $('[data-copy-label]', btn);
    try {
      await navigator.clipboard.writeText(btn.dataset.copy);
      if (label) { label.textContent = '복사됐어요'; setTimeout(() => (label.textContent = '코드 복사'), 1800); }
    } catch {
      if (label) label.textContent = '직접 복사해 주세요';
    }
  }));

  /* ── 회원가입: 새 가족 / 초대 코드 전환 ───── */
  const signup = $('#signupForm');
  if (signup) {
    const fields = $$('[data-mode]', signup);
    const radios = $$('input[name="mode"]', signup);
    const apply = () => {
      const mode = radios.find(r => r.checked)?.value || 'new';
      fields.forEach(f => {
        const on = f.dataset.mode === mode;
        f.hidden = !on;
        if (!on) $('input', f).value = ''; // 숨겨진 쪽 값은 비워 서버가 혼동하지 않게 함
      });
    };
    if ($('input[name="inviteCode"]', signup).value.trim()) radios.find(r => r.value === 'join').checked = true;
    radios.forEach(r => r.addEventListener('change', apply));
    apply();
  }

  /* ── 사진 뷰어 ────────────────────────────── */
  const viewer = $('#viewer');
  const tiles = $$('.tile');
  if (viewer && tiles.length) {
    const img = $('#viewerImg'), count = $('#viewerCount'), by = $('#viewerBy'), when = $('#viewerWhen');
    const desc = $('#viewerDesc'), folderTag = $('#viewerFolder'), del = $('#deleteForm');
    const ask = $('#deleteAsk'), cancel = $('#deleteCancel'), go = $('#deleteGo'), confirmText = $('#confirmText');
    let idx = 0;

    const resetConfirm = () => {
      confirmText.hidden = cancel.hidden = go.hidden = true;
      ask.hidden = false;
    };
    const show = i => {
      idx = (i + tiles.length) % tiles.length;
      const t = tiles[idx].dataset;
      img.src = t.src; img.alt = t.name || '';
      by.textContent = t.uploader; when.textContent = t.date;
      desc.textContent = t.desc || ''; desc.hidden = !t.desc;
      folderTag.textContent = t.folder || '';
      del.hidden = t.canDelete !== 'true';
      del.action = t.deleteUrl;
      count.textContent = `${idx + 1} / ${tiles.length}`;
      resetConfirm();
    };
    tiles.forEach((tile, i) => tile.addEventListener('click', () => { show(i); viewer.showModal(); }));
    $('#viewerPrev').addEventListener('click', () => show(idx - 1));
    $('#viewerNext').addEventListener('click', () => show(idx + 1));
    ask.addEventListener('click', () => { ask.hidden = true; confirmText.hidden = cancel.hidden = go.hidden = false; go.focus(); });
    cancel.addEventListener('click', resetConfirm);
    viewer.addEventListener('keydown', e => {
      if (e.key === 'ArrowLeft') show(idx - 1);
      if (e.key === 'ArrowRight') show(idx + 1);
    });
    viewer.addEventListener('click', e => { if (e.target === viewer) viewer.close(); });
    let x0 = null; // 스와이프
    viewer.addEventListener('touchstart', e => { x0 = e.touches[0].clientX; }, { passive: true });
    viewer.addEventListener('touchend', e => {
      if (x0 === null) return;
      const dx = e.changedTouches[0].clientX - x0; x0 = null;
      if (Math.abs(dx) > 60) show(idx + (dx < 0 ? 1 : -1));
    });
    viewer.addEventListener('close', () => { img.removeAttribute('src'); });
  }

  /* ── 구글 포토 가져오기 (Picker API) ─────── */
  const gImport = $('#googleImport');
  if (gImport) {
    const base = gImport.dataset.base;
    const startBtn = $('#gStart'), folderSel = $('#gFolder'), statusEl = $('#gStatus'), resultEl = $('#gResult');
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const headers = () => ({ 'X-Requested-With': 'XMLHttpRequest', ...(csrfHeader ? { [csrfHeader]: csrfToken } : {}) });
    const setStatus = msg => { statusEl.hidden = !msg; statusEl.textContent = msg || ''; };
    const fail = async res => { throw new Error((await res.text()) || '요청에 실패했어요.'); };

    startBtn?.addEventListener('click', async () => {
      if (startBtn.disabled) return;
      startBtn.disabled = true; resultEl.hidden = true; resultEl.replaceChildren();
      // 팝업 차단을 피하려고 클릭 직후 동기적으로 창을 열고, 세션이 만들어지면 주소를 지정한다.
      const win = window.open('', '_blank');
      const folderId = encodeURIComponent(folderSel.value);
      try {
        setStatus('구글 포토 선택 창을 준비하고 있어요…');
        let res = await fetch(`${base}/picker/sessions?folderId=${folderId}`, { method: 'POST', headers: headers() });
        if (!res.ok) await fail(res);
        const s = await res.json();
        if (win) win.location.href = s.pickerUri; else window.open(s.pickerUri, '_blank');

        setStatus('열린 창에서 사진을 고르고 “완료”를 눌러 주세요. 완료하면 이 화면이 자동으로 이어져요.');
        const deadline = Date.now() + (s.timeoutMs || 600000);
        let ready = false;
        while (Date.now() < deadline) {
          await sleep(Math.max(2000, s.pollIntervalMs || 5000));
          res = await fetch(`${base}/picker/sessions/${encodeURIComponent(s.sessionId)}`, { headers: headers() });
          if (!res.ok) await fail(res);
          if ((await res.json()).ready) { ready = true; break; }
        }
        if (!ready) throw new Error('시간이 초과됐어요. 다시 시도해 주세요.');

        setStatus('사진을 가져오는 중이에요… (장수에 따라 시간이 걸려요)');
        res = await fetch(`${base}/picker/sessions/${encodeURIComponent(s.sessionId)}/import?folderId=${folderId}`,
          { method: 'POST', headers: headers() });
        if (!res.ok) await fail(res);
        showImportResult(await res.json(), folderSel.value);
        setStatus('');
      } catch (err) {
        try { win?.close(); } catch { /* ignore */ }
        setStatus('');
        resultEl.replaceChildren();
        const p = document.createElement('p');
        p.className = 'alert error'; p.textContent = err.message || '가져오지 못했어요.';
        resultEl.append(p); resultEl.hidden = false;
      } finally {
        startBtn.disabled = false;
      }
    });

    function showImportResult(r, folderId) {
      resultEl.replaceChildren();
      const head = document.createElement('p');
      head.className = r.failed.length ? 'alert error' : 'alert info';
      head.textContent = `${r.imported}장을 가져왔어요.` + (r.skipped ? ` 이미 가져온 ${r.skipped}장은 건너뛰었어요.` : '')
        + (r.failed.length ? ` ${r.failed.length}장은 가져오지 못했어요.` : '');
      resultEl.append(head);
      if (r.failed.length) {
        const ul = document.createElement('ul');
        r.failed.slice(0, 8).forEach(f => { const li = document.createElement('li'); li.textContent = `${f.filename} — ${f.reason}`; ul.append(li); });
        resultEl.append(ul);
      }
      if (r.imported) {
        const a = document.createElement('a');
        a.className = 'btn secondary sm'; a.href = `${base.replace(/\/google$/, '')}/photos?folder=${encodeURIComponent(folderId)}`;
        a.textContent = '폴더에서 보기'; a.style.marginTop = '8px';
        resultEl.append(a);
      }
      resultEl.hidden = false;
    }
  }

  /* ── 다중 업로드 시트 ─────────────────────── */
  const dlg = $('#uploadDialog');
  if (!dlg) return;

  const form = $('#uploadForm'), input = $('#fileInput'), zone = $('#dropzone'), list = $('#previews');
  const notice = $('#notice'), submit = $('#submitBtn'), cancelBtn = $('#cancelBtn');
  const progress = $('#progress'), bar = $('#bar'), pct = $('#progressPct'), pLabel = $('#progressLabel');
  const result = $('#result');

  const MAX_BYTES = Number(dlg.dataset.maxBytes);
  const MAX_MB = Number(dlg.dataset.maxMb);
  const MAX_SELECT = 100;  // 한 번에 고를 수 있는 총 장수
  const BATCH = 10;        // 한 요청에 담는 장수 (서버 상한 이내, 순차 전송)
  const TYPES = /^image\/(jpeg|png|webp|gif|heic)$/;

  let items = [];          // { key, file, url, state }
  let busy = false;
  let finished = false;   // 전부 성공해 '확인' 버튼 상태일 때
  let uploadedAny = false;
  input.required = false; // JS 가 선택 목록을 직접 관리하므로 네이티브 검증은 끔(JS 없는 폴백에서만 required 유지)

  const keyOf = f => `${f.name}:${f.size}:${f.lastModified}`;
  const total = () => items.reduce((s, i) => s + i.file.size, 0);

  function showNotice(lines) {
    notice.hidden = !lines.length;
    notice.textContent = lines.length
      ? `${lines.length}장은 제외했어요: ` + lines.slice(0, 3).join(', ') + (lines.length > 3 ? ` 외 ${lines.length - 3}장` : '')
      : '';
  }

  function addFiles(fileList) {
    const skipped = [];
    for (const f of fileList) {
      if (items.length >= MAX_SELECT) { skipped.push(`${f.name} (최대 ${MAX_SELECT}장)`); continue; }
      if (!TYPES.test(f.type)) { skipped.push(`${f.name} (지원하지 않는 형식)`); continue; }
      if (f.size > MAX_BYTES) { skipped.push(`${f.name} (${MAX_MB}MB 초과)`); continue; }
      if (items.some(i => i.key === keyOf(f))) continue; // 중복 선택 무시
      items.push({ key: keyOf(f), file: f, url: URL.createObjectURL(f), state: 'ready' });
    }
    showNotice(skipped);
    render();
  }

  function render() {
    list.replaceChildren();
    for (const it of items) {
      const li = document.createElement('li');
      li.className = `preview ${it.state}`;
      li.dataset.ext = (it.file.name.split('.').pop() || '').toUpperCase();

      const img = new Image();
      img.src = it.url; img.alt = ''; img.decoding = 'async';
      img.addEventListener('error', () => { img.remove(); li.classList.add('noimg'); }); // HEIC 등 미리보기 불가
      li.append(img);

      const name = document.createElement('span');
      name.className = 'pname'; name.textContent = it.file.name; // textContent: 파일명 XSS 방지
      li.append(name);

      const state = document.createElement('span');
      state.className = 'state';
      li.append(state);

      if (!busy) {
        const rm = document.createElement('button');
        rm.type = 'button'; rm.className = 'rm'; rm.setAttribute('aria-label', `${it.file.name} 제거`);
        rm.innerHTML = '<svg class="icon"><use href="#i-close"/></svg>';
        rm.addEventListener('click', () => { URL.revokeObjectURL(it.url); items = items.filter(x => x !== it); render(); });
        li.append(rm);
      }
      list.append(li);
    }
    const n = items.length;
    const retry = items.some(i => i.state === 'failed');
    submit.disabled = busy || (n === 0 && !finished);
    submit.textContent = busy ? '올리는 중…' : finished ? '확인'
      : retry ? `실패한 ${n}장 다시 올리기` : n ? `${n}장 올리기` : '올리기';
    cancelBtn.disabled = busy;
  }

  function resetSheet() {
    items.forEach(i => URL.revokeObjectURL(i.url));
    items = []; busy = false; finished = false;
    showNotice([]); result.hidden = true; result.replaceChildren();
    progress.hidden = true; bar.style.width = '0%'; pct.textContent = '0%';
    form.reset(); input.value = '';
    render();
  }

  function setProgress(r) {
    const p = Math.min(100, Math.round(r * 100));
    bar.style.width = p + '%'; pct.textContent = p + '%';
  }

  /** 한 배치를 XHR 로 전송(업로드 진행률 이벤트 필요). 항상 resolve. */
  function postBatch(batch, folderId, description, onProgress) {
    return new Promise(resolve => {
      const fd = new FormData();
      batch.forEach(b => fd.append('files', b.file, b.file.name));
      fd.append('folderId', folderId);
      if (description) fd.append('description', description);

      const xhr = new XMLHttpRequest();
      xhr.open('POST', dlg.dataset.action);
      xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
      xhr.setRequestHeader('Accept', 'application/json');
      if (csrfToken && csrfHeader) xhr.setRequestHeader(csrfHeader, csrfToken);
      xhr.upload.onprogress = e => { if (e.lengthComputable) onProgress(e.loaded); };
      xhr.onerror = () => resolve({ ok: false, error: '네트워크 오류가 발생했어요.' });
      xhr.ontimeout = () => resolve({ ok: false, error: '시간이 초과됐어요.' });
      xhr.onload = () => {
        let data = null;
        try { data = JSON.parse(xhr.responseText); } catch { /* HTML(로그인 페이지 등) */ }
        if (xhr.status >= 200 && xhr.status < 300 && data && Array.isArray(data.failed)) {
          resolve({ ok: true, failed: data.failed });
        } else {
          resolve({ ok: false, error: data?.error || (xhr.status === 403 || xhr.responseURL.includes('/login')
            ? '로그인이 만료됐어요. 새로고침 후 다시 시도해 주세요.' : '서버에서 처리하지 못했어요.') });
        }
      };
      xhr.send(fd);
    });
  }

  async function upload() {
    busy = true;
    const folderId = form.elements['folderId'].value;
    const description = form.elements['description'].value.trim();
    result.hidden = true; showNotice([]);
    progress.hidden = false; pLabel.textContent = '업로드 중…'; setProgress(0);
    items.forEach(i => (i.state = 'ready'));

    const bytesTotal = total() || 1;
    let sent = 0, okCount = 0;
    const failures = []; // { name, reason }

    for (let i = 0; i < items.length; i += BATCH) {
      const batch = items.slice(i, i + BATCH);
      batch.forEach(b => (b.state = 'uploading')); render();
      const batchBytes = batch.reduce((s, b) => s + b.file.size, 0);

      const res = await postBatch(batch, folderId, description, loaded => setProgress((sent + loaded) / bytesTotal));
      sent += batchBytes; setProgress(sent / bytesTotal);

      if (!res.ok) {
        batch.forEach(b => { b.state = 'failed'; failures.push({ name: b.file.name, reason: res.error }); });
      } else {
        const pending = [...res.failed]; // 같은 이름이 여러 개여도 1:1 로 소진하며 매칭
        batch.forEach(b => {
          const k = pending.findIndex(f => f.filename === b.file.name);
          if (k >= 0) { b.state = 'failed'; failures.push({ name: b.file.name, reason: pending[k].reason }); pending.splice(k, 1); }
          else { b.state = 'done'; okCount++; }
        });
      }
      render();
    }

    busy = false;
    if (okCount) uploadedAny = true;
    pLabel.textContent = '완료';

    // 성공한 항목은 목록에서 정리, 실패한 항목만 남겨 재시도 가능하게 함
    items.filter(i => i.state === 'done').forEach(i => URL.revokeObjectURL(i.url));
    items = items.filter(i => i.state === 'failed');
    renderResult(okCount, failures);
    render();
  }

  function renderResult(okCount, failures) {
    result.replaceChildren();
    const head = document.createElement('p');
    if (!failures.length) {
      head.className = 'alert info';
      head.textContent = `${okCount}장을 모두 올렸어요. 닫으면 앨범에 반영돼요.`;
      finished = true;
    } else {
      head.className = 'alert error';
      head.textContent = `${okCount}장 성공, ${failures.length}장 실패했어요.`;
      const ul = document.createElement('ul');
      failures.slice(0, 8).forEach(f => {
        const li = document.createElement('li');
        li.textContent = `${f.name} — ${f.reason}`;
        ul.append(li);
      });
      result.append(head, ul);
      result.hidden = false;
      return;
    }
    result.append(head);
    result.hidden = false;
  }

  /* 이벤트 연결 */
  $$('[data-open-upload]').forEach(b => b.addEventListener('click', () => { resetSheet(); dlg.showModal(); }));
  $$('[data-close]', dlg).forEach(b => b.addEventListener('click', () => { if (!busy) dlg.close(); }));
  dlg.addEventListener('cancel', e => { if (busy) e.preventDefault(); }); // 업로드 중 Esc 방지
  dlg.addEventListener('close', () => {
    const reload = uploadedAny;
    resetSheet();
    if (reload) location.reload();
  });

  input.addEventListener('change', () => { addFiles(input.files); input.value = ''; });
  ['dragenter', 'dragover'].forEach(ev => zone.addEventListener(ev, e => { e.preventDefault(); zone.classList.add('over'); }));
  ['dragleave', 'drop'].forEach(ev => zone.addEventListener(ev, e => { e.preventDefault(); zone.classList.remove('over'); }));
  zone.addEventListener('drop', e => { if (!busy) addFiles(e.dataTransfer.files); });
  dlg.addEventListener('paste', e => { // 클립보드 이미지 붙여넣기
    const files = [...(e.clipboardData?.files || [])];
    if (files.length && !busy) addFiles(files);
  });

  form.addEventListener('submit', e => {
    e.preventDefault();
    if (finished) { dlg.close(); return; }
    if (busy || !items.length) return;
    upload();
  });
  render();
})();
