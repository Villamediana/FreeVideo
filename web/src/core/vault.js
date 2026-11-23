// O cofre do perfil (o mesmo do celular, tube/account/TubeVault.kt): o histórico (os mais novos, com
// onde cada um parou), as inscrições, curtidos e "assistir mais tarde", "não tenho interesse", as buscas
// e os interesses. Os nomes curtos são os do app. Aqui: ler (com os padrões que o celular omite),
// juntar três versões (a mesma regra do TubeVaultMerge) e mudar.

export const HISTORY = 1500;
export const SEARCHES = 200;
export const LIKED = 'liked';
export const LATER = 'later';

const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
const str = (v, d = '') => (typeof v === 'string' ? v : d);
const strOrNull = (v) => (typeof v === 'string' ? v : null);
const num = (v, d = 0) => (Number.isFinite(v) ? Math.trunc(v) : d);
const list = (v) => (Array.isArray(v) ? v : []);

const watch = (w) => (w && typeof w.i === 'string' && typeof w.t === 'string'
  ? { i: w.i, t: w.t, cn: str(w.cn), cu: strOrNull(w.cu), th: strOrNull(w.th), d: num(w.d), p: num(w.p), w: num(w.w), n: num(w.n, 1), f: num(w.f), l: num(w.l) }
  : null);
const sub = (s) => (s && typeof s.u === 'string' && typeof s.n === 'string' ? { u: s.u, n: s.n, a: strOrNull(s.a), at: num(s.at) } : null);
const saved = (v) => (v && typeof v.li === 'string' && typeof v.i === 'string' && typeof v.t === 'string'
  ? { li: v.li, i: v.i, t: v.t, cn: str(v.cn), cu: strOrNull(v.cu), th: strOrNull(v.th), d: num(v.d), at: num(v.at) }
  : null);
const dismissal = (x) => (x && typeof x.k === 'string' ? { k: x.k, t: str(x.t), at: num(x.at) } : null);
const search = (q) => (q && typeof q.q === 'string' ? { q: q.q, n: num(q.n, 1), at: num(q.at) } : null);
const interest = (x) => (x && typeof x.t === 'string' ? { t: x.t, at: num(x.at) } : null);

export function normalize(raw) {
  const v = raw && typeof raw === 'object' ? raw : {};
  return {
    v: num(v.v, 1) || 1,
    h: list(v.h).map(watch).filter(Boolean),
    s: list(v.s).map(sub).filter(Boolean),
    sv: list(v.sv).map(saved).filter(Boolean),
    x: list(v.x).map(dismissal).filter(Boolean),
    q: list(v.q).map(search).filter(Boolean),
    in: list(v.in).map(interest).filter(Boolean),
  };
}

export const empty = () => normalize({});
export const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** Sem os padrões (como o celular escreve). */
export function compact(v) {
  const drop = (o, defaults) => {
    const out = {};
    for (const [k, val] of Object.entries(o)) {
      if (val === null || val === undefined) continue;
      if (k in defaults && defaults[k] === val) continue;
      out[k] = val;
    }
    return out;
  };
  const out = {};
  if (v.h.length) out.h = v.h.map((w) => drop(w, { cn: '', d: 0, p: 0, w: 0, n: 1, f: 0, l: 0 }));
  if (v.s.length) out.s = v.s.map((s) => drop(s, { at: 0 }));
  if (v.sv.length) out.sv = v.sv.map((s) => drop(s, { cn: '', d: 0, at: 0 }));
  if (v.x.length) out.x = v.x.map((x) => drop(x, { t: '', at: 0 }));
  if (v.q.length) out.q = v.q.map((q) => drop(q, { n: 1, at: 0 }));
  if (v.in.length) out.in = v.in.map((x) => drop(x, { at: 0 }));
  return out;
}

export function threeWay(base, local, remote, key, pick) {
  const b = base ? new Set(base.map(key)) : null;
  const l = new Map(local.map((x) => [key(x), x]));
  const r = new Map(remote.map((x) => [key(x), x]));
  const out = new Map();
  for (const [k, lv] of l) {
    const rv = r.get(k);
    if (rv !== undefined) out.set(k, pick(lv, rv));
    else if (!b || !b.has(k)) out.set(k, lv);
  }
  for (const [k, rv] of r) if (!l.has(k) && (!b || !b.has(k))) out.set(k, rv);
  return [...out.values()];
}

/** Dois aparelhos do perfil juntos — a mesma regra do TubeVaultMerge.merge do app. */
export function merge(base, local, remote) {
  const savedKey = (x) => `${x.li}:${x.i}`;
  return {
    v: 1,
    h: threeWay(base?.h, local.h, remote.h, (x) => x.i, (l, r) => {
      const newer = r.l > l.l ? r : l;
      const firsts = [l.f, r.f].filter((x) => x > 0);
      return { ...newer, w: Math.max(l.w, r.w), n: Math.max(l.n, r.n), f: firsts.length ? Math.min(...firsts) : 0, d: Math.max(l.d, r.d) };
    }).sort((a, b) => b.l - a.l || cmp(a.i, b.i)).slice(0, HISTORY),
    s: threeWay(base?.s, local.s, remote.s, (x) => x.u, (l, r) => (r.at > l.at ? r : l)).sort((a, b) => cmp(a.u, b.u)),
    sv: threeWay(base?.sv, local.sv, remote.sv, savedKey, (l, r) => (r.at > l.at ? r : l)).sort((a, b) => cmp(savedKey(a), savedKey(b))),
    x: threeWay(base?.x, local.x, remote.x, (x) => x.k, (l, r) => (r.at > l.at ? r : l)).sort((a, b) => cmp(a.k, b.k)),
    q: threeWay(base?.q, local.q, remote.q, (x) => x.q, (l, r) => ({ q: l.q, n: Math.max(l.n, r.n), at: Math.max(l.at, r.at) }))
      .sort((a, b) => b.at - a.at || cmp(a.q, b.q)).slice(0, SEARCHES),
    in: threeWay(base?.in, local.in, remote.in, (x) => x.t, (l, r) => (r.at > l.at ? r : l)).sort((a, b) => cmp(a.t, b.t)),
  };
}

// ------------------------------------------------------------------ a página ↔ o cofre

/** O canal de um vídeo como o app guarda: a URL /channel/UC… quando há o id. */
export const channelUrlOf = (v) => v.channelUrl ?? (v.channelId ? `https://www.youtube.com/channel/${v.channelId}` : null);

/** Um vídeo do cofre (histórico ou salvo) como a página mostra. */
export function fromV(w) {
  const chId = /(UC[A-Za-z0-9_-]{22})/.exec(w.cu ?? '')?.[1] ?? null;
  return {
    kind: 'video', id: w.i, title: w.t, channelName: w.cn, channelUrl: w.cu, channelId: chId, channelAvatar: null,
    thumbnailUrl: w.th ?? `https://i.ytimg.com/vi/${w.i}/mqdefault.jpg`, durationMs: w.d, views: -1, viewsText: null,
    uploadedAt: null, uploadedText: null, live: false, short: false, verified: false,
  };
}

/** Uma chave por canal (como o FeedEngine.channelKey do app): o UC… quando há, senão a URL, senão o nome. */
export function channelKey(url, name) {
  if (url) {
    const m = /(UC[A-Za-z0-9_-]{22})/.exec(url);
    if (m) return m[1];
    return url.trim().toLowerCase().replace(/\/$/, '');
  }
  return `name:${String(name ?? '').trim().toLowerCase()}`;
}

export const edit = {
  /** Tempo assistido de um vídeo: [addMs] tocado de verdade, parado em [positionMs]; [newVisit] = acabou de abrir. */
  watch(v, video, positionMs, durationMs, addMs, newVisit, now) {
    const old = v.h.find((w) => w.i === video.id);
    const length = durationMs > 0 ? durationMs : video.durationMs > 0 ? video.durationMs : old?.d ?? 0;
    let watched = (old?.w ?? 0) + Math.max(0, addMs);
    if (length > 0) watched = Math.min(watched, length * 4);
    const w = {
      i: video.id,
      t: video.title || old?.t || '',
      cn: video.channelName || old?.cn || '',
      cu: channelUrlOf(video) ?? old?.cu ?? null,
      th: video.thumbnailUrl ?? old?.th ?? null,
      d: Math.round(length),
      p: Math.max(0, Math.round(positionMs)),
      w: Math.round(watched),
      n: (old?.n ?? 0) + (newVisit || !old ? 1 : 0),
      f: old?.f || now,
      l: now,
    };
    return { ...v, h: [w, ...v.h.filter((x) => x.i !== video.id)].slice(0, HISTORY) };
  },
  forget(v, id) {
    return { ...v, h: v.h.filter((w) => w.i !== id) };
  },
  clearHistory(v) {
    return { ...v, h: [] };
  },
  toggleSaved(v, list, video, now) {
    const there = v.sv.some((x) => x.li === list && x.i === video.id);
    if (there) return { ...v, sv: v.sv.filter((x) => !(x.li === list && x.i === video.id)) };
    const s = { li: list, i: video.id, t: video.title, cn: video.channelName ?? '', cu: channelUrlOf(video), th: video.thumbnailUrl ?? null, d: video.durationMs || 0, at: now };
    return { ...v, sv: [...v.sv, s] };
  },
  toggleSub(v, channel, now) {
    const key = channelKey(channel.url, channel.name);
    const there = v.s.filter((s) => channelKey(s.u, s.n) === key);
    if (there.length) return { ...v, s: v.s.filter((s) => channelKey(s.u, s.n) !== key) };
    // Seguir um canal desfaz o "não recomendar".
    return { ...v, s: [...v.s, { u: channel.url, n: channel.name, a: channel.avatarUrl ?? null, at: now }], x: v.x.filter((x) => x.k !== `c:${channel.url}`) };
  },
  dismissVideo(v, video, now) {
    return { ...v, x: [...v.x.filter((x) => x.k !== `v:${video.id}`), { k: `v:${video.id}`, t: video.title, at: now }] };
  },
  dismissChannel(v, url, name, now) {
    return { ...v, x: [...v.x.filter((x) => x.k !== `c:${url}`), { k: `c:${url}`, t: name, at: now }] };
  },
  undoDismissal(v, key) {
    return { ...v, x: v.x.filter((x) => x.k !== key) };
  },
  search(v, query, now) {
    const q = query.trim();
    if (!q) return v;
    const old = v.q.find((x) => x.q === q);
    return { ...v, q: [{ q, n: (old?.n ?? 0) + 1, at: now }, ...v.q.filter((x) => x.q !== q)].slice(0, SEARCHES) };
  },
  forgetSearch(v, query) {
    return { ...v, q: v.q.filter((x) => x.q !== query) };
  },
  setInterests(v, topics, now) {
    return { ...v, in: [...new Set(topics)].map((t, i) => ({ t, at: now + i })) };
  },
};
