// O feed do FreeVideo, o mesmo cérebro do app (tube/feed/FeedEngine.kt e FeedLoader.kt), em JS: o gosto
// da pessoa tirado do cofre (o que assistiu e quanto, curtidos, "mais tarde", inscrições, "não tenho
// interesse", buscas, interesses), as fontes de candidatos (o que o YouTube sugere depois dos vídeos
// que mais dizem dela, um passo além a partir do que o próprio feed mostrou, novidades das inscrições e
// dos canais que ela assiste muito, buscas pelos assuntos dela) e a ordem com variedade. Tudo aqui, no
// navegador: o servidor só vê as buscas e os vídeos pedidos, como o YouTube veria do celular.
import { channelKey } from './vault.js';

const DAY = 86_400_000;
export const SESSION_MS = 3 * 3_600_000;

const STOP = new Set([
  'que', 'com', 'para', 'por', 'uma', 'uns', 'umas', 'dos', 'das', 'nos', 'nas', 'pelo', 'pela', 'pelos', 'pelas',
  'mais', 'muito', 'muita', 'isso', 'esse', 'essa', 'este', 'esta', 'isto', 'sobre', 'porque', 'quando', 'como',
  'voce', 'voces', 'meu', 'minha', 'seu', 'sua', 'nao', 'sim', 'tudo', 'todo', 'toda', 'todos', 'quem', 'qual',
  'onde', 'ate', 'entre', 'depois', 'antes', 'aqui', 'ali', 'ser', 'foi', 'sao', 'tem', 'ter', 'vai', 'vou',
  'fiz', 'fez', 'ele', 'ela', 'eles', 'elas', 'nosso', 'nossa', 'agora', 'hoje', 'novo', 'nova', 'novos',
  'parte', 'episodio', 'video', 'videos', 'canal', 'oficial', 'clipe', 'completo', 'completa', 'vivo',
  'the', 'and', 'for', 'you', 'your', 'with', 'this', 'that', 'from', 'how', 'why', 'what', 'who', 'are',
  'was', 'were', 'has', 'have', 'not', 'but', 'all', 'out', 'about', 'into', 'its', 'his', 'her', 'they',
  'official', 'music', 'full', 'part', 'episode', 'new', 'best', 'live', 'feat', 'lyrics', 'lyric',
  'los', 'las', 'del', 'una', 'con', 'mas', 'pero', 'sus', 'esto', 'nuevo', 'nueva', 'capitulo',
  'hd', '4k', '1080p', '720p', 'ft', 'vs', 'shorts', 'short',
]);

/** Palavras minúsculas sem acento, sem as pequenas e o ruído dos títulos do YouTube. */
export function tokens(text) {
  const plain = String(text ?? '').toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '');
  const seen = new Set();
  for (const w of plain.split(/[^\p{L}\p{N}]+/u)) if (w.length >= 3 && !/^\d+$/.test(w) && !STOP.has(w)) seen.add(w);
  return [...seen];
}

export const decay = (ageMs, halfLifeDays) => 0.5 ** (Math.max(0, ageMs) / (halfLifeDays * DAY));

export function jaccard(a, b) {
  if (!a.size || !b.size) return 0;
  let common = 0;
  for (const x of a) if (b.has(x)) common++;
  return common / (a.size + b.size - common);
}

/** Um gerador pseudoaleatório com semente (o mesmo feed pra mesma semente). */
export function rng(seed) {
  let s = (Number(BigInt.asUintN(32, BigInt(Math.trunc(seed)))) ^ 0x9e3779b9) >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** [n] itens sorteados sem repetir, cada um com chance ∝ ao peso (Efraimidis–Spirakis). */
export function weightedSample(items, n, rnd, weight) {
  if (n <= 0 || !items.length) return [];
  return items
    .map((it) => [it, Math.max(rnd(), 1e-12) ** (1 / Math.max(weight(it), 1e-9))])
    .sort((a, b) => b[1] - a[1])
    .slice(0, n)
    .map((x) => x[0]);
}

const seenShare = (e) => (e.d > 0 ? Math.min(1, Math.max(0, e.w / e.d)) : Math.min(1, e.w / 300_000));
const progress = (e) => (e.d > 0 ? Math.min(1, Math.max(0, e.p / e.d)) : 0);
export const isBounce = (e) => e.w < 20_000 && seenShare(e) < 0.1;

export function engagement(e, now) {
  const seen = seenShare(e);
  const base = isBounce(e) ? 0.05 : 0.25 + seen;
  const replays = 1 + 0.35 * Math.log(Math.max(1, e.n));
  return base * replays * decay(now - e.l, 14);
}

function normalizeMap(map) {
  const vals = [...map.values()];
  const maxPos = Math.max(0, ...vals);
  const max = maxPos > 0 ? maxPos : -Math.min(0, ...vals);
  if (!(max > 0)) return new Map();
  return new Map([...map].map(([k, v]) => [k, Math.max(-1, Math.min(1, v / max))]));
}

/** O que a pessoa parece gostar, tirado do cofre ([v]) — o FeedEngine.buildTaste do app. */
export function buildTaste(v, now, { seed = 0, seedCount = 6, exclude = new Set() } = {}) {
  const channels = new Map();
  const words = new Map();
  const sessionWords = new Map();
  const sessionChannels = new Set();
  const seedWeights = new Map();
  const names = new Map();
  let lnSum = 0, lnSq = 0, lnWeight = 0, lnCount = 0;
  const add = (map, k, w) => map.set(k, (map.get(k) ?? 0) + w);
  const addChannel = (url, name, w) => {
    if (!name && !url) return;
    const k = channelKey(url, name);
    add(channels, k, w);
    if (url) names.set(k, [url, name]);
  };
  const addWords = (into, text, w) => {
    const t = tokens(text);
    if (!t.length) return;
    const each = w / Math.sqrt(t.length);
    for (const x of t) add(into, x, each);
  };
  for (const e of v.h) {
    if (isBounce(e)) {
      if (e.n <= 1) {
        const neg = 0.3 * decay(now - e.l, 14);
        addChannel(e.cu, e.cn, -neg);
        addWords(words, e.t, -neg * 0.5);
      }
      continue;
    }
    const w = engagement(e, now);
    addChannel(e.cu, e.cn, w);
    addWords(words, e.t, w);
    const inSession = now - e.l < SESSION_MS;
    if (inSession) {
      addWords(sessionWords, e.t, w);
      sessionChannels.add(channelKey(e.cu, e.cn));
    }
    const seen = seenShare(e);
    if (seen >= 0.3 || e.w >= 120_000) seedWeights.set(e.i, { videoId: e.i, title: e.t, weight: inSession ? w * 2 : w, session: inSession });
    if (seen >= 0.5 && e.d > 20_000) {
      const l = Math.log(e.d / 1000);
      lnSum += l * w;
      lnSq += l * l * w;
      lnWeight += w;
      lnCount++;
    }
  }
  const liked = v.sv.filter((s) => s.li === 'liked');
  const later = v.sv.filter((s) => s.li === 'later');
  for (const s of liked) {
    const w = 1.5 * decay(now - s.at, 60);
    addChannel(s.cu, s.cn, w);
    addWords(words, s.t, w);
    const prev = seedWeights.get(s.i);
    seedWeights.set(s.i, { videoId: s.i, title: s.t, weight: (prev?.weight ?? 0) + w, session: !!prev?.session });
  }
  for (const s of later) {
    const w = 0.4 * decay(now - s.at, 30);
    addChannel(s.cu, s.cn, w);
    addWords(words, s.t, w);
  }
  for (const s of v.s) addChannel(s.u, s.n, 1);
  for (const q of v.q) addWords(words, q.q, 0.8 * Math.sqrt(q.n) * decay(now - q.at, 21));
  for (const i of v.in) addWords(words, i.t, 0.6);
  const dismissedVideos = new Set();
  const dismissedChannels = new Set();
  for (const d of v.x) {
    if (d.k.startsWith('v:')) {
      dismissedVideos.add(d.k.slice(2));
      addWords(words, d.t, -0.5 * decay(now - d.at, 45));
    } else if (d.k.startsWith('c:')) dismissedChannels.add(channelKey(d.k.slice(2), ''));
  }
  for (const c of dismissedChannels) channels.delete(c);
  const subscribed = new Set(v.s.map((s) => channelKey(s.u, s.n)));
  const rnd = rng(seed);
  const pool = [...seedWeights.values()].filter((s) => !exclude.has(s.videoId) && !dismissedVideos.has(s.videoId));
  const picked = weightedSample(pool, seedCount, rnd, (s) => s.weight);
  const maxSeed = Math.max(0, ...picked.map((s) => s.weight)) || 1;
  const topicScores = new Map();
  for (const q of v.q) {
    const t = q.q.trim();
    if (t.length >= 2) add(topicScores, t, Math.sqrt(q.n) * decay(now - q.at, 21));
  }
  for (const i of v.in) add(topicScores, i.t, 0.7);
  const topics = weightedSample([...topicScores], topicScores.size, rnd, (x) => x[1]).map((x) => x[0]);
  const frequent = [...channels].filter(([k, w]) => !subscribed.has(k) && names.has(k) && w >= 0.6).sort((a, b) => b[1] - a[1]).slice(0, 10);
  const maxFrequent = Math.max(0, ...frequent.map((x) => x[1])) || 1;
  let duration = null;
  if (lnCount >= 5 && lnWeight > 0) {
    const mean = lnSum / lnWeight;
    duration = { meanLn: mean, spreadLn: Math.max(0.5, Math.sqrt(Math.max(0, lnSq / lnWeight - mean * mean))) };
  }
  return {
    channels: normalizeMap(channels),
    words: normalizeMap(words),
    sessionWords: normalizeMap(sessionWords),
    sessionChannels,
    seeds: picked.map((s) => ({ ...s, weight: Math.max(0, Math.min(1, s.weight / maxSeed)) })),
    topics,
    subscribed,
    dismissedVideos,
    dismissedChannels,
    watched: new Map(v.h.map((e) => [e.i, e])),
    frequentChannels: frequent.map(([k, w]) => ({ url: names.get(k)[0], name: names.get(k)[1], weight: Math.max(0, Math.min(1, w / maxFrequent)) })),
    duration,
    isNew: !v.h.length && !liked.length && !v.s.length && !v.in.length && !v.q.length,
  };
}

export const SOURCES = { related: 1.0, subscription: 0.95, channel: 0.75, topic: 0.7, similar: 0.6, trending: 0.4 };

export function wordMatch(title, words) {
  if (!words.size) return 0;
  const t = tokens(title);
  if (!t.length) return 0;
  let sum = 0;
  for (const x of t) sum += words.get(x) ?? 0;
  return Math.max(-1, Math.min(1, sum / Math.sqrt(t.length)));
}

export function freshness(ageMs) {
  if (ageMs == null) return 0.3;
  return 0.65 * decay(ageMs, 7) + 0.35 * decay(ageMs, 90);
}

export function agedFactor(ageMs, fit) {
  if (ageMs == null) return 1;
  const years = ageMs / (365 * DAY);
  const base = years <= 1 ? 1 : years >= 3 ? 0.5 : 1 - 0.25 * (years - 1);
  return 1 - (1 - base) * (1 - 0.5 * fit);
}

export function durationFit(ms, taste) {
  if (!taste || !(ms > 0)) return 0.5;
  const z = (Math.log(ms / 1000) - taste.meanLn) / taste.spreadLn;
  return Math.exp(-0.5 * z * z);
}

const richness = (v) => (v.durationMs > 0 ? 2 : 0) + (v.views >= 0 ? 1 : 0) + (v.uploadedAt != null ? 1 : 0) + (v.channelAvatar ? 1 : 0);

/** Um candidato: {video, weight (0..1), reason: {source, label}}. */
function score(id, list, taste, now) {
  const strength = (c) => c.weight * SOURCES[c.reason.source];
  const best = list.reduce((a, b) => (strength(b) > strength(a) ? b : a));
  const v = list.reduce((a, b) => (richness(b.video) > richness(a.video) ? b : a)).video;
  if (v.short) return null;
  if (taste.dismissedVideos.has(id)) return null;
  const ch = channelKey(v.channelUrl, v.channelName);
  if (taste.dismissedChannels.has(ch)) return null;
  let factor = 1;
  const w = taste.watched.get(id);
  if (w) {
    const done = w.d > 0 ? w.w / w.d : 0;
    if (done >= 0.8 || progress(w) >= 0.1) return null;
    factor *= w.w < 30_000 ? 0.4 : 0.6;
  }
  const bySource = new Map();
  for (const c of list) bySource.set(c.reason.source, Math.max(bySource.get(c.reason.source) ?? 0, strength(c)));
  const strengths = [...bySource.values()].sort((a, b) => b - a);
  const rel = strengths[0] + strengths.slice(1).reduce((s, x) => s + x * 0.5, 0);
  const aff = taste.channels.get(ch) ?? 0;
  const sub = taste.subscribed.has(ch) ? 1 : 0;
  const kw = wordMatch(v.title, taste.words);
  const session = Math.max(wordMatch(v.title, taste.sessionWords), taste.sessionChannels.has(ch) ? 0.6 : 0);
  const age = v.uploadedAt != null ? Math.max(0, now - v.uploadedAt) : null;
  const fresh = freshness(age);
  const pop = v.views > 0 ? Math.max(0, Math.min(1, (Math.log10(v.views + 1) - 3) / 4)) : 0.3;
  const length = durationFit(v.durationMs, taste.duration);
  let s = rel + 0.5 * aff + 0.3 * sub + 0.55 * kw + 0.4 * session + 0.6 * fresh + 0.12 * pop + 0.15 * length;
  factor *= agedFactor(age, Math.max(0, Math.min(1, Math.max(kw, aff, session))));
  if (best.reason.source === 'subscription') {
    s += 0.25 * fresh;
    if (age != null && age > 21 * DAY) s *= 0.5;
  }
  if (v.live && !sub) s *= 0.8;
  if (aff < -0.1) factor *= Math.max(0.15, 1 + aff * 1.5);
  return { video: v, score: Math.max(0, s * factor), reason: best.reason, sources: new Set(list.map((c) => c.reason.source)) };
}

const reasonKey = (r) => (r.source === 'related' ? `r:${r.label}` : r.source === 'similar' ? `s:${r.label}` : r.source === 'topic' ? `t:${r.label}` : r.source === 'trending' ? 'k' : null);

function diversify(entries, taste, limit) {
  const pool = [...entries].sort((a, b) => b.score - a.score);
  const wordsCache = new Map();
  const wordsOf = (e) => {
    let w = wordsCache.get(e.video.id);
    if (!w) wordsCache.set(e.video.id, (w = new Set(tokens(e.video.title))));
    return w;
  };
  const out = [];
  const perChannel = new Map();
  const perReason = new Map();
  const pick = (explore, capReasons) => {
    let bestIndex = -1;
    let bestScore = -Infinity;
    const recent = out.slice(-4);
    for (let i = 0; i < pool.length; i++) {
      const e = pool[i];
      if (e.score <= bestScore) break;
      const ch = channelKey(e.video.channelUrl, e.video.channelName);
      const n = perChannel.get(ch) ?? 0;
      if (n >= 3) continue;
      const rk = reasonKey(e.reason);
      const cap = rk === 'k' && !taste.isNew ? 2 : 6;
      if (capReasons && rk && (perReason.get(rk) ?? 0) >= cap) continue;
      if (explore && Math.abs(taste.channels.get(ch) ?? 0) > 0.05) continue;
      let s = e.score * 0.55 ** n;
      if (recent.length >= 3 && recent.slice(-3).every((r) => r.reason.source === e.reason.source)) s *= 0.85;
      const mine = wordsOf(e);
      const alike = Math.max(0, ...recent.map((r) => jaccard(mine, wordsOf(r))));
      s *= 1 - 0.5 * alike;
      if (s > bestScore) {
        bestScore = s;
        bestIndex = i;
      }
    }
    return bestIndex;
  };
  while (out.length < limit && pool.length) {
    const slot = out.length;
    const explore = !taste.isNew && slot > 0 && slot % 6 === 5;
    let index = pick(explore, true);
    if (index < 0 && explore) index = pick(false, true);
    if (index < 0) index = pick(false, false);
    if (index < 0) break;
    const [picked] = pool.splice(index, 1);
    const mine = wordsOf(picked);
    if (mine.size >= 3 && out.some((o) => jaccard(mine, wordsOf(o)) >= 0.75)) continue;
    const ch = channelKey(picked.video.channelUrl, picked.video.channelName);
    perChannel.set(ch, (perChannel.get(ch) ?? 0) + 1);
    const rk = reasonKey(picked.reason);
    if (rk) perReason.set(rk, (perReason.get(rk) ?? 0) + 1);
    out.push(picked);
  }
  return out;
}

/** Os candidatos viram no máximo [limit] entradas, sem as já mostradas ([shown]). */
export function rank(candidates, taste, now, { seed = 0, limit = 40, shown = new Set() } = {}) {
  const rnd = rng(Number(BigInt.asUintN(32, BigInt(Math.trunc(seed)) ^ 0x5deece66dn)));
  const merged = new Map();
  for (const c of candidates) {
    if (shown.has(c.video.id)) continue;
    if (!merged.has(c.video.id)) merged.set(c.video.id, []);
    merged.get(c.video.id).push(c);
  }
  const scored = [];
  for (const [id, list] of merged) {
    const e = score(id, list, taste, now);
    if (e) scored.push({ ...e, score: e.score * (0.92 + 0.16 * rnd()) });
  }
  return diversify(scored, taste, limit);
}

/** "Continuar assistindo": começados, não terminados, de agora. */
export function continueWatching(v, now, limit = 12) {
  return v.h.filter((e) => e.d > 60_000 && progress(e) >= 0.03 && progress(e) <= 0.92 && now - e.l < 21 * DAY).sort((a, b) => b.l - a.l).slice(0, limit);
}

const positionWeight = (i) => 1 / (1 + i * 0.06);

/**
 * Uma rolagem da Início (o FeedLoader do app): cada página traz candidatos novos pro conjunto, e o
 * rank escolhe os próximos de tudo. [api] é a ponte (relacionados, novidades, busca).
 */
export class FeedSession {
  constructor({ api, vault, starterTopics = [], seed = Date.now(), clock = () => Date.now() }) {
    Object.assign(this, { api, vault, starterTopics, seed, clock });
    this.pool = [];
    this.shown = new Set();
    this.usedSeeds = new Set();
    this.hops = [];
    this.topics = new Map(); // tópico → token da próxima página (null = acabou)
    this.catalogs = new Map(); // canal → {name, subscribed, next, started, pages}
    this.page = 0;
  }

  async fetchAll(jobs) {
    const out = await Promise.all(jobs.map((j) => j().catch(() => [])));
    return out.flat();
  }

  async next() {
    const v = this.vault();
    const out = [];
    let round = 0;
    while (out.length < 12 && round < 3) {
      const first = this.page === 0 && round === 0;
      const roundSeed = this.seed + this.page * 7919 + round * 104729;
      const now = this.clock();
      const taste = buildTaste(v, now, { seed: roundSeed, seedCount: first ? 6 : 4, exclude: this.usedSeeds });
      const fresh = await this.gather(v, taste, first, rng(roundSeed));
      this.pool.push(...fresh);
      const ranked = rank(this.pool, taste, now, { seed: roundSeed, limit: 24 - out.length, shown: this.shown });
      for (const e of ranked) this.shown.add(e.video.id);
      out.push(...ranked);
      this.hops.push(...ranked);
      round++;
      if (!fresh.length && !ranked.length) break;
    }
    this.page++;
    this.pool = this.pool.filter((c) => !this.shown.has(c.video.id));
    if (this.pool.length > 700) this.pool = this.pool.sort((a, b) => b.weight * SOURCES[b.reason.source] - a.weight * SOURCES[a.reason.source]).slice(0, 700);
    return out;
  }

  async gather(v, taste, first, rnd) {
    const jobs = [];
    const wanted = first ? 6 : 4;
    for (const s of taste.seeds) {
      this.usedSeeds.add(s.videoId);
      jobs.push(async () => (await this.api.video(s.videoId)).related.map((x, i) => ({ video: x, weight: s.weight * positionWeight(i), reason: { source: 'related', label: s.title } })));
    }
    if (taste.seeds.length < wanted) {
      for (const hop of this.nextHops(wanted - taste.seeds.length)) {
        this.usedSeeds.add(hop.video.id);
        jobs.push(async () => (await this.api.video(hop.video.id)).related.map((x, i) => ({ video: x, weight: 0.9 * positionWeight(i), reason: { source: 'similar', label: hop.video.title } })));
      }
    }
    if (first) {
      const subs = shuffle(v.s, rnd).slice(0, 40);
      const subIds = subs.map((s) => /(UC[A-Za-z0-9_-]{22})/.exec(s.u)?.[1]).filter(Boolean);
      for (const s of subs) this.catalogs.set(s.u, { name: s.n, subscribed: true, started: false, next: null, pages: 0 });
      if (subIds.length) {
        jobs.push(async () => {
          const r = await this.api.latest(subIds);
          const byChannel = new Map();
          for (const x of r.items) {
            if (!byChannel.has(x.channelId)) byChannel.set(x.channelId, []);
            byChannel.get(x.channelId).push(x);
          }
          return [...byChannel.values()].flatMap((list) => list.slice(0, 8)).map((x) => ({ video: x, weight: 1, reason: { source: 'subscription', label: x.channelName } }));
        });
      }
      const freq = taste.frequentChannels.slice(0, 5);
      const freqIds = freq.map((c) => /(UC[A-Za-z0-9_-]{22})/.exec(c.url)?.[1]).filter(Boolean);
      for (const c of freq) this.catalogs.set(c.url, { name: c.name, subscribed: false, started: false, next: null, pages: 0 });
      if (freqIds.length) {
        jobs.push(async () => {
          const r = await this.api.latest(freqIds);
          const weightOf = new Map(freq.map((c) => [/(UC[A-Za-z0-9_-]{22})/.exec(c.url)?.[1], c.weight]));
          return r.items.map((x) => ({ video: x, weight: weightOf.get(x.channelId) ?? 0.5, reason: { source: 'channel', label: x.channelName } }));
        });
      }
    } else {
      const open = [...this.catalogs].filter(([, c]) => !c.started || c.next);
      if (open.length) {
        const [url, c] = open[this.page % open.length];
        jobs.push(async () => {
          const id = /(UC[A-Za-z0-9_-]{22})/.exec(url)?.[1];
          if (!id) return [];
          let items;
          if (!c.started) {
            c.started = true;
            const r = await this.api.channel(id);
            items = r.videos;
            c.next = r.next;
          } else {
            const r = await this.api.more(c.next);
            items = r.items;
            c.next = ++c.pages < 3 ? r.next : null;
          }
          const reason = c.subscribed ? { source: 'subscription', label: c.name } : { source: 'channel', label: c.name };
          return items.filter((x) => x.kind === 'video').map((x, i) => ({ video: x, weight: 0.7 * positionWeight(i), reason }));
        });
      }
    }
    const starters = taste.isNew || (taste.seeds.length < 2 && !this.hops.length) ? shuffle(this.starterTopics, rnd) : [];
    const fresh = [...taste.topics, ...starters].filter((t) => !this.topics.has(t)).slice(0, taste.isNew ? 3 : first ? 2 : 1);
    for (const t of fresh) {
      this.topics.set(t, null);
      jobs.push(async () => {
        const r = await this.api.search(t, 'videos');
        this.topics.set(t, r.next);
        return r.items.filter((x) => x.kind === 'video').map((x, i) => ({ video: x, weight: positionWeight(i), reason: { source: 'topic', label: t } }));
      });
    }
    if (!fresh.length) {
      const open = [...this.topics].filter(([, next]) => next);
      if (open.length) {
        const [t, token] = open[this.page % open.length];
        jobs.push(async () => {
          const r = await this.api.more(token);
          this.topics.set(t, r.next);
          return r.items.filter((x) => x.kind === 'video').map((x, i) => ({ video: x, weight: 0.7 * positionWeight(i), reason: { source: 'topic', label: t } }));
        });
      }
    }
    return this.fetchAll(jobs);
  }

  nextHops(n) {
    if (n <= 0) return [];
    const channels = new Set();
    const picked = this.hops
      .filter((h) => !this.usedSeeds.has(h.video.id) && !h.video.live && !h.video.short)
      .sort((a, b) => b.score - a.score)
      .filter((h) => {
        const k = channelKey(h.video.channelUrl, h.video.channelName);
        if (channels.has(k)) return false;
        channels.add(k);
        return true;
      })
      .slice(0, n);
    const ids = new Set(picked.map((h) => h.video.id));
    this.hops = this.hops.filter((h) => !ids.has(h.video.id)).sort((a, b) => b.score - a.score).slice(0, 80);
    return picked;
  }
}

/** As novidades de todas as inscrições, mais novas primeiro (a aba Inscrições). */
export async function subscriptionsFeed(api, v) {
  const ids = v.s.map((s) => /(UC[A-Za-z0-9_-]{22})/.exec(s.u)?.[1]).filter(Boolean);
  const out = [];
  for (let i = 0; i < ids.length; i += 60) out.push(...(await api.latest(ids.slice(i, i + 60))).items);
  const seen = new Set();
  return out.filter((x) => !x.short && !seen.has(x.id) && seen.add(x.id)).sort((a, b) => (b.uploadedAt ?? 0) - (a.uploadedAt ?? 0));
}

function shuffle(list, rnd) {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
