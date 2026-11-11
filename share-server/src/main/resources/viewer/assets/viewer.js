'use strict';

/*
 * Drive coach phone viewer ("폰으로 보기").
 *
 * A link is https://<host>/s#<id>.<key>. The id and the AES-256 key live only in the fragment, which the
 * browser never sends to the server; the page removes them from the address bar right away, fetches the
 * sealed copy with the id in the Authorization header, decrypts it here with WebCrypto and draws it with
 * DOM text only (anyone can make a link to this page, so the payload is untrusted input).
 * Blob = "DCS1" | flags (0) | iv (12) | AES-GCM ciphertext + tag, AAD = the first 5 bytes
 * (drivecoach share/ShareCrypto.kt); payload = SharePayload v1 (share/ShareModels.kt).
 */
(() => {
  const LINK_RE = /^#([A-Za-z0-9_-]{22})\.([A-Za-z0-9_-]{43})$/;
  const ID_RE = /^[A-Za-z0-9_-]{22}$/;
  const KEY_RE = /^[A-Za-z0-9_-]{43}$/;
  const SAVED = 'drivecoach.links.v1';
  const MAX_SAVED = 30;
  const EVENTS = {
    HARSH_START: ['급출발', 'amber'],
    HARSH_ACCEL: ['급가속', 'amber'],
    HARSH_BRAKE: ['급감속', 'red'],
    HARD_STOP: ['급정지', 'red'],
    ACCEL_THEN_BRAKE: ['가속 직후 제동', 'violet'],
  };
  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  const app = document.getElementById('app');

  // ---------- DOM, text only ----------
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = String(text);
    return e;
  }
  function add(parent, ...children) {
    for (const c of children) if (c) parent.append(c);
    return parent;
  }
  function svg(tag, attrs, text) {
    const e = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const k of Object.keys(attrs)) e.setAttribute(k, String(attrs[k]));
    if (text != null) e.textContent = String(text);
    return e;
  }
  function show(...nodes) {
    app.replaceChildren(...nodes.filter(Boolean));
    window.scrollTo(0, 0);
  }
  function card(title, ...body) {
    const c = el('section', 'card');
    if (title) c.append(el('h2', null, title));
    return add(c, ...body);
  }

  // ---------- validation of untrusted values ----------
  const num = (v, lo, hi) => (typeof v === 'number' && Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : null);
  const int = (v, lo, hi) => {
    const x = num(v, lo, hi);
    return x == null ? null : Math.round(x);
  };
  const str = (v, max) => (typeof v === 'string' ? v.slice(0, max) : '');
  const arr = (v, max) => (Array.isArray(v) ? v.slice(0, max) : []);
  const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : null);

  // ---------- formatting: report times on the car's clock, expiry on the phone's ----------
  let offsetMin = 0;
  const pad = (x) => String(x).padStart(2, '0');
  const carDate = (ms) => new Date(ms + offsetMin * 60000); // read with the UTC getters
  function fmtDateTime(ms) {
    const d = carDate(ms);
    return `${d.getUTCMonth() + 1}월 ${d.getUTCDate()}일 (${WEEKDAYS[d.getUTCDay()]}) ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
  }
  function fmtDay(ms) {
    const d = carDate(ms);
    return `${d.getUTCMonth() + 1}/${d.getUTCDate()}`;
  }
  function fmtLocal(ms) {
    const d = new Date(ms);
    return `${d.getMonth() + 1}월 ${d.getDate()}일 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  const fmtKm = (km) => (km < 10 ? km.toFixed(1) : String(Math.round(km)));
  function fmtDuration(s) {
    const m = Math.round(s / 60);
    return m < 60 ? `${m}분` : `${Math.floor(m / 60)}시간 ${m % 60}분`;
  }
  const minSec = (s) => `${Math.floor(s / 60)}:${pad(s % 60)}`;
  const pct = (x) => `${Math.round(x * 100)}%`;
  const one = (x) => (Math.round(x * 10) / 10).toFixed(1);

  // ---------- base64url (Uint8Array.fromBase64 is too new for many phones) ----------
  const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  function b64url(s) {
    const out = new Uint8Array(Math.floor((s.length * 3) / 4));
    let buf = 0;
    let bits = 0;
    let n = 0;
    for (const ch of s) {
      const v = ALPHABET.indexOf(ch);
      if (v < 0) throw new ViewError('badlink');
      buf = ((buf & 0xffff) << 6) | v;
      bits += 6;
      if (bits >= 8) {
        bits -= 8;
        out[n++] = (buf >> bits) & 0xff;
      }
    }
    return out.subarray(0, n);
  }

  // ---------- fetching and opening ----------
  class ViewError extends Error {
    constructor(kind) {
      super(kind);
      this.kind = kind;
    }
  }

  async function fetchSealed(id) {
    let res;
    try {
      res = await fetch('/v1/share', {
        headers: { Authorization: 'Bearer ' + id },
        cache: 'no-store',
        credentials: 'omit',
        referrerPolicy: 'no-referrer',
      });
    } catch (e) {
      throw new ViewError('network');
    }
    if (res.status === 404) throw new ViewError('gone');
    if (res.status === 429) throw new ViewError('busy');
    if (!res.ok) throw new ViewError('server');
    const expiresAt = Number(res.headers.get('X-Expires-At')) || null;
    return { blob: new Uint8Array(await res.arrayBuffer()), expiresAt };
  }

  async function openBlob(blob, keyBytes) {
    if (!window.isSecureContext || !window.crypto || !window.crypto.subtle) throw new ViewError('insecure');
    if (blob.length < 33 || String.fromCharCode(...blob.subarray(0, 4)) !== 'DCS1' || blob[4] !== 0) throw new ViewError('corrupt');
    const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['decrypt']);
    let plain;
    try {
      plain = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: blob.subarray(5, 17), additionalData: blob.subarray(0, 5), tagLength: 128 },
        key,
        blob.subarray(17),
      );
    } catch (e) {
      throw new ViewError('corrupt');
    }
    let payload;
    try {
      payload = JSON.parse(new TextDecoder().decode(plain));
    } catch (e) {
      throw new ViewError('corrupt');
    }
    if (!obj(payload) || payload.v !== 1 || (payload.kind !== 'TRIP' && payload.kind !== 'PERIOD')) throw new ViewError('version');
    return payload;
  }

  // ---------- links kept on this phone until they expire (same-origin storage only) ----------
  function loadSaved() {
    let list;
    try {
      list = JSON.parse(localStorage.getItem(SAVED) || '[]');
    } catch (e) {
      list = [];
    }
    const now = Date.now();
    return arr(list, MAX_SAVED).filter((x) => obj(x) && ID_RE.test(x.id) && KEY_RE.test(x.key) && typeof x.expiresAt === 'number' && x.expiresAt > now);
  }
  function storeSaved(list) {
    try {
      localStorage.setItem(SAVED, JSON.stringify(list.slice(0, MAX_SAVED)));
    } catch (e) {
      // private browsing: the report still opens, it just isn't remembered
    }
  }
  const remember = (link) => storeSaved([link, ...loadSaved().filter((x) => x.id !== link.id)]);
  const forget = (id) => storeSaved(loadSaved().filter((x) => x.id !== id));

  // ---------- screens ----------
  const ERRORS = {
    gone: ['링크가 만료됐거나 지워졌어요', '차에서 "폰으로 보기"를 다시 눌러 새 QR을 찍어 주세요.'],
    insecure: ['https 주소로 열어 주세요', '암호화된 리포트는 https 주소에서만 열 수 있어요.'],
    network: ['서버에 연결할 수 없어요', '인터넷 연결을 확인한 뒤 다시 시도해 주세요.'],
    busy: ['요청이 너무 많아요', '잠시 뒤 다시 시도해 주세요.'],
    server: ['서버에 문제가 있어요', '잠시 뒤 다시 시도해 주세요.'],
    corrupt: ['리포트를 열 수 없어요', '링크가 잘렸거나 손상됐어요. 차에서 QR을 다시 찍어 주세요.'],
    version: ['이 리포트 형식을 읽을 수 없어요', '페이지를 새로 고치거나 차의 드라이브 코치를 업데이트해 주세요.'],
    badlink: ['링크가 올바르지 않아요', '차에서 "폰으로 보기"를 눌러 나온 QR을 찍어 주세요.'],
  };

  function status(text) {
    show(el('p', 'status', text));
  }

  function errorScreen(kind, retry) {
    const [title, body] = ERRORS[kind] || ERRORS.corrupt;
    const box = card(null, el('h1', null, title), el('p', 'muted', body));
    if (retry && (kind === 'network' || kind === 'busy' || kind === 'server')) box.append(button('다시 시도', retry));
    const saved = loadSaved();
    show(box, saved.length ? savedList(saved) : null);
  }

  function landing(bad) {
    const [title, body] = bad
      ? ERRORS.badlink
      : ['드라이브 코치 리포트', '차에서 "폰으로 보기"를 누르고 화면의 QR을 이 폰 카메라로 찍으면 리포트가 열려요.'];
    const saved = loadSaved();
    show(card(null, el('h1', null, title), el('p', 'muted', body)), saved.length ? savedList(saved) : null);
  }

  function button(label, onClick) {
    const b = el('button', 'button', label);
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
  }

  function savedList(saved) {
    const ul = el('ul', 'links');
    for (const s of saved) {
      const b = el('button', 'link-row');
      b.type = 'button';
      add(b, el('span', 'link-title', str(s.title, 60) || '리포트'), el('span', 'muted small', `${fmtLocal(s.expiresAt)}까지`));
      b.addEventListener('click', () => open(s.id, s.key));
      ul.append(add(el('li'), b));
    }
    return card('이 폰에서 연 리포트', ul);
  }

  // Only the latest open() may draw: a slow earlier fetch must not replace the report opened after it.
  let opening = 0;

  async function open(id, key) {
    const ticket = ++opening;
    status('리포트를 여는 중…');
    try {
      const { blob, expiresAt } = await fetchSealed(id);
      const payload = await openBlob(blob, b64url(key));
      if (ticket !== opening) return;
      offsetMin = int(payload.utcOffsetMin, -900, 900) || 0;
      const view = payload.kind === 'TRIP' ? tripView(obj(payload.trip)) : periodView(obj(payload.period));
      if (expiresAt) remember({ id, key, expiresAt, title: view.title });
      const foot = el('footer', 'foot');
      add(
        foot,
        expiresAt ? el('p', null, `이 링크는 ${fmtLocal(expiresAt)}까지 열 수 있어요. 링크를 가진 사람은 누구나 볼 수 있어요.`) : null,
        el('p', null, '드라이브 코치 · 운전 습관 분석은 참고용이에요. 안전 운전의 판단과 책임은 운전자에게 있어요.'),
      );
      show(...view.nodes, foot);
    } catch (e) {
      if (ticket !== opening) return;
      const kind = e instanceof ViewError ? e.kind : 'corrupt';
      if (kind === 'gone') forget(id);
      errorScreen(kind, () => open(id, key));
    }
  }

  // ---------- building blocks ----------
  function tile(label, value, unit, cls) {
    const v = el('div', 'tile-value' + (cls ? ' ' + cls : ''), value);
    if (unit) v.append(el('span', 'unit', unit));
    return add(el('div', 'tile'), el('div', 'tile-label', label), v);
  }
  const tiles = (...items) => add(el('div', 'tiles'), ...items);
  const scoreClass = (s) => (s == null ? 'muted' : s >= 85 ? 'green' : s >= 65 ? 'amber' : 'red');

  function legendItem(label, cls) {
    return add(el('span', 'legend-item'), el('span', 'swatch ' + cls), el('span', null, label));
  }

  function eventChips(events) {
    const wrap = el('div', 'chips');
    for (const type of Object.keys(EVENTS)) {
      const n = events ? int(events[type], 0, 1e5) : null;
      if (n) wrap.append(el('span', 'chip ' + EVENTS[type][1], `${EVENTS[type][0]} ${n}`));
    }
    if (!wrap.childElementCount) wrap.append(el('span', 'chip green', '급가속·급제동 없음'));
    return wrap;
  }

  /** Horizontal 100% bar with a legend; parts = [{label, value, cls}]. */
  function stacked(parts) {
    const total = parts.reduce((a, p) => a + p.value, 0);
    if (total <= 0) return el('p', 'muted', '데이터가 부족해요.');
    const s = svg('svg', { viewBox: '0 0 100 6', preserveAspectRatio: 'none', class: 'bar', role: 'img', 'aria-label': parts.map((p) => `${p.label} ${pct(p.value / total)}`).join(', ') });
    let x = 0;
    for (const p of parts) {
      const w = (p.value / total) * 100;
      if (w > 0) s.append(svg('rect', { x, y: 0, width: w, height: 6, class: 'fill ' + p.cls }));
      x += w;
    }
    const legend = el('div', 'legend');
    for (const p of parts) legend.append(legendItem(`${p.label} ${pct(p.value / total)}`, p.cls));
    return add(el('div'), s, legend);
  }

  function mixBar(a) {
    if (!a) return el('p', 'muted', '데이터가 부족해요.');
    return stacked([
      { label: '부드럽게', value: num(a.gentleS, 0, 1e7) || 0, cls: 'green' },
      { label: '보통', value: num(a.moderateS, 0, 1e7) || 0, cls: 'amber' },
      { label: '강하게', value: num(a.hardS, 0, 1e7) || 0, cls: 'red' },
    ]);
  }

  function brakeBlock(b) {
    if (!b) return el('p', 'muted', '이 차량에서는 브레이크 신호를 받을 수 없었어요.');
    const per10 = num(b.per10Km, 0, 1e4);
    const avg = num(b.avgPressS, 0, 1e4);
    const wrap = add(el('div'), tiles(tile('10 km당', per10 == null ? '—' : one(per10), '회'), tile('평균 밟는 시간', avg == null ? '—' : one(avg), '초')));
    const coast = num(b.coastShare, 0, 1);
    if (coast != null) {
      wrap.append(stacked([{ label: '회생·관성', value: coast, cls: 'green' }, { label: '브레이크', value: 1 - coast, cls: 'red' }]));
      wrap.append(el('p', 'muted small', '발을 떼서(회생제동) 줄인 속도 에너지의 비율이에요. 높을수록 전비에 좋아요.'));
    }
    return wrap;
  }

  function speedChart(tl, moments) {
    const step = int(tl.stepS, 1, 3600) || 1;
    const ts = arr(tl.tS, 400).map((x) => int(x, 0, 1e6));
    const kmh = arr(tl.kmh, 400).map((x) => int(x, 0, 400));
    const n = Math.min(ts.length, kmh.length);
    if (n < 2 || ts.slice(0, n).includes(null) || kmh.slice(0, n).includes(null)) return el('p', 'muted', '그래프를 그릴 데이터가 부족해요.');
    const brake = Array.isArray(tl.brake) ? arr(tl.brake, 400) : null;
    // Close to a phone's card width, so 12-unit labels render at about 11 px instead of shrinking away.
    const W = 340;
    const H = 190;
    const L = 30;
    const R = 6;
    const T = 12;
    const B = 22;
    const t0 = ts[0];
    const t1 = ts[n - 1] + step;
    const top = Math.max(...kmh.slice(0, n));
    const vmax = Math.max(20, Math.ceil(top / 20) * 20);
    const x = (t) => L + ((t - t0) / Math.max(1, t1 - t0)) * (W - L - R);
    const y = (v) => T + (1 - v / vmax) * (H - T - B);
    const s = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img', 'aria-label': `속도 그래프, 최고 ${top} km/h` });
    for (let i = 0; i <= 4; i++) {
      const v = (vmax / 4) * i;
      s.append(svg('line', { x1: L, x2: W - R, y1: y(v), y2: y(v), class: 'grid' }));
      s.append(svg('text', { x: L - 6, y: y(v) + 4, class: 'axis', 'text-anchor': 'end' }, Math.round(v)));
    }
    if (brake) {
      for (let i = 0; i < n; i++) {
        if (brake[i] !== 1) continue;
        let j = i;
        while (j + 1 < n && brake[j + 1] === 1) j++;
        s.append(svg('rect', { x: x(ts[i]), y: T, width: Math.max(2, x(ts[j] + step) - x(ts[i])), height: H - T - B, class: 'brake' }));
        i = j;
      }
    }
    s.append(svg('polyline', { points: ts.slice(0, n).map((t, i) => `${x(t).toFixed(1)},${y(kmh[i]).toFixed(1)}`).join(' '), class: 'speed' }));
    for (const m of moments) {
      const mo = obj(m);
      const at = mo ? int(mo.atS, 0, 1e6) : null;
      if (!mo || !EVENTS[mo.type] || at == null) continue;
      const cls = EVENTS[mo.type][1];
      s.append(svg('line', { x1: x(at), x2: x(at), y1: T, y2: H - B, class: 'mark ' + cls }));
      s.append(svg('circle', { cx: x(at), cy: T, r: 4, class: 'dot ' + cls }));
    }
    s.append(svg('text', { x: L, y: H - 6, class: 'axis' }, '0분'));
    s.append(svg('text', { x: W - R, y: H - 6, class: 'axis', 'text-anchor': 'end' }, `${Math.max(1, Math.round((t1 - t0) / 60))}분`));
    s.append(svg('text', { x: L + 4, y: T + 12, class: 'axis' }, 'km/h'));
    const legend = add(el('div', 'legend'), legendItem('속도', 'blue'), brake ? legendItem('브레이크', 'brakefill') : null, legendItem('급가속·급출발', 'amber'), legendItem('급감속·급정지', 'red'));
    return add(el('div'), s, legend);
  }

  function momentsList(moments) {
    const ul = el('ul', 'rows');
    for (const m of moments) {
      const mo = obj(m);
      if (!mo || !EVENTS[mo.type]) continue;
      const [label, cls] = EVENTS[mo.type];
      const at = int(mo.atS, 0, 1e6) || 0;
      const from = int(mo.fromKmh, 0, 400) || 0;
      const to = int(mo.toKmh, 0, 400) || 0;
      const peak = num(mo.peakKmhPerS, 0, 1e3) || 0;
      const text = add(el('div'), add(el('div'), el('span', 'muted', minSec(at) + '  '), el('strong', null, label)), el('div', 'muted small', `${from} → ${to} km/h · 초당 ${one(peak)} km/h`));
      ul.append(add(el('li', 'row'), el('span', 'swatch ' + cls), text));
    }
    return ul;
  }

  function parkingBlock(p) {
    const maxSteer = int(p.maxSteerPct, 0, 100);
    return add(
      el('div'),
      tiles(
        tile('걸린 시간', int(p.durationS, 0, 1e5) || 0, '초'),
        tile('전·후진 전환', int(p.directionChanges, 0, 1e4) || 0, '회'),
        tile('멈춘 횟수', int(p.stops, 0, 1e4) || 0, '회'),
        maxSteer == null ? null : tile('최대 핸들', maxSteer, '%'),
      ),
      el('p', null, p.endedInReverse === true ? '후진으로 주차했어요.' : '전진으로 주차했어요.'),
    );
  }

  function tipsList(tips) {
    const wrap = el('div', 'tips');
    for (const t of tips) {
      const tip = obj(t);
      if (!tip) continue;
      add(wrap, add(el('div', 'tip'), el('h3', tip.praise === true ? 'green' : null, str(tip.title, 80)), el('p', 'muted', str(tip.body, 400))));
    }
    return wrap;
  }

  function ring(score) {
    const r = 42;
    const c = 2 * Math.PI * r;
    const s = svg('svg', { viewBox: '0 0 100 100', class: 'ring', role: 'img', 'aria-label': `부드러움 점수 ${score == null ? '없음' : score}` });
    s.append(svg('circle', { cx: 50, cy: 50, r, class: 'ring-track' }));
    if (score != null) s.append(svg('circle', { cx: 50, cy: 50, r, class: 'ring-value ' + scoreClass(score), 'stroke-dasharray': `${(c * score) / 100} ${c}`, transform: 'rotate(-90 50 50)' }));
    s.append(svg('text', { x: 50, y: 52, class: 'ring-score ' + scoreClass(score), 'text-anchor': 'middle' }, score == null ? '—' : score));
    s.append(svg('text', { x: 50, y: 68, class: 'ring-caption', 'text-anchor': 'middle' }, '부드러움'));
    return s;
  }

  function ratesBlock(per10) {
    const types = ['HARSH_START', 'HARSH_ACCEL', 'HARSH_BRAKE', 'HARD_STOP', 'ACCEL_THEN_BRAKE'];
    const values = types.map((t) => (per10 ? num(per10[t], 0, 1e4) : null) || 0);
    const scale = Math.max(3, ...values);
    const ul = el('ul', 'rates');
    types.forEach((t, i) => {
      const [label, cls] = EVENTS[t];
      const bar = svg('svg', { viewBox: '0 0 100 6', preserveAspectRatio: 'none', class: 'bar thin', 'aria-hidden': 'true' });
      bar.append(svg('rect', { x: 0, y: 0, width: 100, height: 6, class: 'fill track' }));
      if (values[i] > 0) bar.append(svg('rect', { x: 0, y: 0, width: (values[i] / scale) * 100, height: 6, class: 'fill ' + cls }));
      ul.append(add(el('li', 'rate'), el('span', 'rate-label', label), bar, el('span', 'rate-value', one(values[i]))));
    });
    return ul;
  }

  function weeksChart(weeks) {
    const items = weeks.map((w) => obj(w)).filter(Boolean);
    const vals = items.map((w) => num(w.harshPer10Km, 0, 1e4));
    const W = 340;
    const H = 150;
    const B = 22;
    const T = 20;
    const vmax = Math.max(1, ...vals.map((v) => v || 0));
    const slot = W / Math.max(1, items.length);
    const s = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img', 'aria-label': '주별 10 km당 급가속·급제동' });
    items.forEach((w, i) => {
      const v = vals[i];
      const cx = slot * i + slot / 2;
      if (v != null) {
        const h = (v / vmax) * (H - T - B);
        s.append(svg('rect', { x: cx - slot * 0.3, y: H - B - h, width: slot * 0.6, height: Math.max(1, h), rx: 4, class: 'fill blue' }));
      }
      s.append(svg('text', { x: cx, y: v == null ? H - B - 6 : H - B - ((v / vmax) * (H - T - B)) - 6, class: 'axis', 'text-anchor': 'middle' }, v == null ? '–' : one(v)));
      s.append(svg('text', { x: cx, y: H - 6, class: 'axis', 'text-anchor': 'middle' }, fmtDay(num(w.startEpochMs, 0, 1e14) || 0)));
    });
    return s;
  }

  function parkingHabit(p) {
    return tiles(
      tile('평균 걸린 시간', int(p.avgDurationS, 0, 1e5) || 0, '초'),
      tile('평균 전·후진 전환', one(num(p.avgDirectionChanges, 0, 1e4) || 0), '회'),
      tile('후진 주차', Math.round((num(p.reverseShare, 0, 1) || 0) * 100), '%'),
    );
  }

  function drivesList(drives) {
    const ul = el('ul', 'rows');
    for (const d of drives) {
      const r = obj(d);
      if (!r) continue;
      const score = int(r.score, 0, 100);
      const harsh = int(r.harsh, 0, 1e5) || 0;
      const text = add(el('div', 'drive-text'), el('div', null, fmtDateTime(num(r.startEpochMs, 0, 1e14) || 0)), el('div', 'muted small', `${fmtKm(num(r.distanceKm, 0, 1e5) || 0)} km · ${fmtDuration(int(r.durationS, 0, 1e7) || 0)}${harsh ? ` · 급가속·급제동 ${harsh}회` : ''}`));
      ul.append(add(el('li', 'row drive'), text, el('span', 'drive-score ' + scoreClass(score), score == null ? '—' : `${score}점`)));
    }
    return ul;
  }

  // ---------- reports ----------
  function tripView(t) {
    if (!t) throw new ViewError('corrupt');
    const start = num(t.startEpochMs, 0, 1e14) || 0;
    const drive = t.drive === true;
    const head = add(el('header', 'head'), el('p', 'eyebrow', drive ? '주행 리포트' : '주차·짧은 이동'), el('h1', null, fmtDateTime(start)));
    const score = int(t.score, 0, 100);
    const summary = card(
      null,
      tiles(
        drive ? tile('부드러움', score == null ? '—' : score, '점', scoreClass(score)) : null,
        tile('거리', fmtKm(num(t.distanceKm, 0, 1e5) || 0), 'km'),
        tile('시간', fmtDuration(int(t.durationS, 0, 1e7) || 0)),
        drive ? tile('평균 속도', int(t.avgKmh, 0, 400) ?? 0, 'km/h') : null,
        drive ? tile('최고 속도', int(t.maxKmh, 0, 400) ?? 0, 'km/h') : null,
      ),
      drive ? eventChips(obj(t.events)) : null,
    );
    const nodes = [head, summary];
    const moments = arr(t.moments, 200);
    const tl = obj(t.timeline);
    if (tl) nodes.push(card('속도와 브레이크', speedChart(tl, moments)));
    if (drive) {
      nodes.push(card('액셀 사용 (속도 변화로 추정)', mixBar(obj(t.accel))));
      nodes.push(card('브레이크 사용', brakeBlock(obj(t.brake))));
      if (moments.length) nodes.push(card('감지된 순간', momentsList(moments)));
    }
    const parking = obj(t.parking);
    if (parking) nodes.push(card('주차', parkingBlock(parking)));
    const tips = arr(t.tips, 10);
    if (tips.length) nodes.push(card(drive ? '이번 주행 코칭' : '코칭', tipsList(tips)));
    return { nodes, title: `${fmtDateTime(start)} ${drive ? '주행' : '주차'}` };
  }

  function periodView(p) {
    if (!p) throw new ViewError('corrupt');
    const days = int(p.days, 1, 366) || 30;
    const head = add(el('header', 'head'), el('p', 'eyebrow', '습관 분석'), el('h1', null, `최근 ${days}일`));
    const title = `최근 ${days}일 습관 분석`;
    const trips = int(p.trips, 0, 1e5) || 0;
    if (!trips) return { nodes: [head, card(null, el('p', 'muted', '이 기간에 기록된 주행이 없어요.'))], title };
    const top = add(
      el('div', 'score-row'),
      ring(int(p.score, 0, 100)),
      add(el('div'), el('p', 'big', `주행 ${trips}회`), el('p', 'muted', `${fmtKm(num(p.distanceKm, 0, 1e6) || 0)} km · ${fmtDuration(int(p.durationS, 0, 1e9) || 0)}`), eventChips(obj(p.events))),
    );
    const nodes = [head, card(null, top), card('10 km당 횟수', ratesBlock(obj(p.per10Km))), card('액셀 사용', mixBar(obj(p.accel))), card('감속 방식', brakeBlock(obj(p.brake)))];
    const weeks = arr(p.weeks, 60);
    if (weeks.length) nodes.push(card('주별 급가속·급제동 (10 km당)', weeksChart(weeks)));
    const parking = obj(p.parking);
    if (parking) nodes.push(card(`주차 습관 (${int(parking.sessions, 0, 1e5) || 0}회)`, parkingHabit(parking)));
    const tips = arr(p.tips, 10);
    if (tips.length) nodes.push(card('코칭', tipsList(tips)));
    const drives = arr(p.drives, 300);
    if (drives.length) nodes.push(card('주행 목록', drivesList(drives)));
    return { nodes, title };
  }

  // ---------- start ----------
  function route() {
    const hash = location.hash;
    if (hash) history.replaceState(null, '', location.pathname); // the key leaves the address bar and the history
    const m = LINK_RE.exec(hash);
    if (m) open(m[1], m[2]);
    else {
      opening++; // a pending open must not cover this page
      landing(Boolean(hash));
    }
  }
  // A second QR opened in the same tab only changes the fragment: no reload, so handle it here.
  window.addEventListener('hashchange', route);
  route();
})();
