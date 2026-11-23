// O servidor do FreeVideo no computador: as páginas (/web/…) e a ponte com o YouTube (/web/api/…).
// Fica atrás do túnel da Cloudflare junto com o relay (freevideo.simplyonegoal.com): o túnel manda
// /web pra cá e o resto pro relay. Não guarda nada de ninguém: o que a pessoa busca não
// vai pra log nenhum, e as respostas do YouTube ficam um tempo na memória pra servir a todos.
import http from 'node:http';
import { isIP } from 'node:net';
import { Cache } from './cache.js';
import { CHANNEL, PLAYLIST, Tube, VIDEO } from './tube.js';
import { HEADERS, Pages } from './pages.js';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const LOOPBACK = /^(127\.|::1$|::ffff:127\.)/;

class HttpError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

/** No máximo [max] por [windowMs] por chave (IP). */
class Limiter {
  constructor(max, windowMs, clock) {
    Object.assign(this, { max, windowMs, clock, hits: new Map() });
  }
  take(key) {
    const now = this.clock();
    const h = this.hits.get(key);
    if (h && now - h.start < this.windowMs) return ++h.n <= this.max;
    if (this.hits.size > 50_000) for (const [k, v] of this.hits) if (now - v.start >= this.windowMs) this.hits.delete(k);
    if (this.hits.size > 50_000) return false;
    this.hits.set(key, { start: now, n: 1 });
    return true;
  }
}

/** IPv4 inteiro; IPv6 pelo /64. */
function ipKey(raw) {
  let ip = String(raw ?? '').trim();
  if (/^::ffff:/i.test(ip) && isIP(ip.slice(7)) === 4) ip = ip.slice(7);
  if (isIP(ip) === 4) return ip;
  if (isIP(ip) !== 6) return '?';
  const [head, tail] = ip.toLowerCase().split('::');
  const h = head ? head.split(':') : [];
  const t = tail === undefined ? [] : tail ? tail.split(':') : [];
  const all = tail === undefined ? h : [...h, ...Array(Math.max(0, 8 - h.length - t.length)).fill('0'), ...t];
  return all.slice(0, 4).join(':') + '::/64';
}

/** Pedidos ao YouTube ao mesmo tempo: no máximo [max]; os outros esperam na fila (até [queue]). */
class Gate {
  constructor(max, queue) {
    Object.assign(this, { max, queue, busy: 0, waiting: [] });
  }
  async run(fn) {
    if (this.busy >= this.max) {
      if (this.waiting.length >= this.queue) throw new HttpError(503, 'busy');
      await new Promise((r) => this.waiting.push(r));
    }
    this.busy++;
    try {
      return await fn();
    } finally {
      this.busy--;
      this.waiting.shift()?.();
    }
  }
}

const LANG = (v) => (v === 'es' || v === 'en' ? v : 'pt');
const q = (url, name, max = 200) => (url.searchParams.get(name) ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, max);

export function createWeb({ publicDir, tube = new Tube(), clock = Date.now, log = console } = {}) {
  const pages = new Pages({ dir: publicDir });
  const cache = new Cache({ max: 5000, clock });
  const gate = new Gate(8, 300);
  const perIp = new Limiter(400, MINUTE, clock);

  const yt = (key, ttl, lang, fn) => cache.get(`${lang}|${key}`, ttl, () => gate.run(async () => {
    try {
      return await fn();
    } catch (e) {
      if (e instanceof HttpError) throw e;
      tube.reset(lang);
      return fn();
    }
  }));

  const api = {
    search: (url, lang) => {
      const text = q(url, 'q', 150);
      if (!text) throw new HttpError(400, 'no_query');
      const type = q(url, 'type', 20);
      return yt(`search|${type}|${text.toLowerCase()}`, 30 * MINUTE, lang, () => tube.search(text, type, lang));
    },
    more: async (url) => {
      const out = await gate.run(() => tube.more(q(url, 'token', 80)));
      if (!out) throw new HttpError(404, 'no_page');
      return out;
    },
    suggest: (url, lang) => {
      const text = q(url, 'q', 100);
      if (!text) return { queries: [] };
      return yt(`suggest|${text.toLowerCase()}`, HOUR, lang, () => tube.suggest(text, lang));
    },
    /** As novidades de vários canais de uma vez (as inscrições): ?ids=UC…,UC… (até 60). */
    latest: async (url) => {
      const ids = q(url, 'ids', 60 * 25).split(',').filter((x) => CHANNEL.test(x)).slice(0, 60);
      const out = await Promise.all(ids.map((id) => cache.get(`rss|${id}`, 15 * MINUTE, () => gate.run(() => tube.latest(id))).catch(() => [])));
      return { items: out.flat() };
    },
  };

  const byId = {
    video: [VIDEO, 30 * MINUTE, (id, lang) => tube.video(id, lang)],
    comments: [VIDEO, 30 * MINUTE, (id, lang) => tube.comments(id, lang)],
    channel: [CHANNEL, HOUR, (id, lang) => tube.channel(id, lang)],
    playlist: [PLAYLIST, HOUR, (id, lang) => tube.playlist(id, lang)],
  };

  function send(res, status, body, extra = {}) {
    const bytes = Buffer.from(JSON.stringify(body));
    res.writeHead(status, {
      ...HEADERS,
      'Content-Type': 'application/json; charset=utf-8',
      'Content-Length': bytes.length,
      'Cache-Control': status === 200 ? 'private, max-age=300' : 'no-store',
      ...extra,
    });
    res.end(bytes);
  }

  // O IP de verdade vem da Cloudflare (cf-connecting-ip), e só se acredita nele vindo do próprio aparelho (o cloudflared).
  const ipOf = (req) => {
    const peer = req.socket.remoteAddress ?? '';
    const cf = req.headers['cf-connecting-ip'];
    return ipKey(LOOPBACK.test(peer) && typeof cf === 'string' && cf ? cf : peer);
  };

  async function handle(req, res) {
    let url;
    try {
      url = new URL('http://web' + (req.url?.startsWith('/') ? req.url : '/'));
    } catch {
      return send(res, 400, { error: 'bad_url' });
    }
    const p = url.pathname;
    if (req.method !== 'GET' && req.method !== 'HEAD') return send(res, 405, { error: 'method' });
    if (p === '/web/api/health') return send(res, 200, { ok: true });
    if (!p.startsWith('/web/api/')) {
      if (p === '/web' || p.startsWith('/web/')) return pages.serve(req, res, p);
      return send(res, 404, { error: 'not_found' });
    }
    try {
      if (!perIp.take(ipOf(req))) throw new HttpError(429, 'slow_down');
      const lang = LANG(url.searchParams.get('lang'));
      const [, , , what, id] = p.split('/');
      let out;
      if (Object.hasOwn(api, what) && id === undefined) out = await api[what](url, lang);
      else if (Object.hasOwn(byId, what) && id !== undefined) {
        const [pattern, ttl, fn] = byId[what];
        if (!pattern.test(id)) throw new HttpError(400, 'bad_id');
        out = await yt(`${what}|${id}`, ttl, lang, () => fn(id, lang));
      } else throw new HttpError(404, 'not_found');
      return send(res, 200, out);
    } catch (e) {
      if (e instanceof HttpError) return send(res, e.status, { error: e.code }, e.status === 429 ? { 'Retry-After': '30' } : {});
      // Nunca a busca da pessoa no log: só o tipo de pedido.
      log.error?.(`[web] ${p.split('/').slice(0, 4).join('/')}: ${e?.message ?? e}`);
      return send(res, 502, { error: 'upstream' });
    }
  }

  const server = http.createServer((req, res) => {
    handle(req, res).catch(() => res.destroy());
  });
  server.headersTimeout = 20_000;
  server.requestTimeout = 60_000;
  server.keepAliveTimeout = 100_000;
  return { server, tube, cache };
}
