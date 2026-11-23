// Relay do FreeVideo: o perfil (nasce sozinho no celular, com @nome e código de recuperação), o cofre
// cifrado no celular (histórico, inscrições, salvos — o servidor não lê) e os computadores ligados ao
// perfil (o FreeVideo na web). Sem amigos e sem conversa. HTTP JSON.
//
// Variáveis: PORT (8787), HOST (127.0.0.1), DATA_DIR (./data), NEW_ACCOUNTS_PER_HOUR (300). Ver DEPLOY.md.
import http from 'node:http';
import { isIP } from 'node:net';
import { mkdirSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Store, sha256 } from './db.js';
import { cleanJpeg } from './jpeg.js';
import { cleanDisplayName, normalizeUsername, randomIdentity } from './names.js';
import { Computers } from './computers.js';

export const VERSION = '2.0.0';
const JSON_LIMIT = 64 * 1024;
const AVATAR_LIMIT = 600 * 1024;
const VAULT_BODY_LIMIT = 6 * 1024 * 1024;
const VAULT_DATA_MAX = 4 * 1024 * 1024;
const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

/** O app que o Android abre nos links de freevideo.simplyonegoal.com (o QR code de um computador). */
const PACKAGE = process.env.APP_PACKAGE || 'com.villamediana.video';
const CERT = (process.env.APP_CERT_SHA256 || '463fc12f58856f182397337e0fe868497c2bffb4fa145383bea7f8dacbb14644').toUpperCase();
export const ASSET_LINKS = JSON.stringify([{
  relation: ['delegate_permission/common.handle_all_urls'],
  target: { namespace: 'android_app', package_name: PACKAGE, sha256_cert_fingerprints: [CERT.match(/../g).join(':')] },
}]);

const SECURITY_HEADERS = {
  'x-content-type-options': 'nosniff',
  'content-security-policy': "default-src 'none'; frame-ancestors 'none'",
  'referrer-policy': 'no-referrer',
  'strict-transport-security': 'max-age=31536000',
};
const JSON_HEADERS = { ...SECURITY_HEADERS, 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' };

class HttpError extends Error {
  constructor(status, code, extra) {
    super(code);
    this.status = status;
    this.code = code;
    this.extra = extra;
  }
}

/** Janela fixa por chave: no máximo [max] em [windowMs]. */
export class Limiter {
  constructor(max, windowMs, clock = Date.now, cap = 50_000) {
    Object.assign(this, { max, windowMs, clock, cap, hits: new Map() });
  }
  take(key) {
    const now = this.clock();
    const h = this.hits.get(key);
    if (h && now - h.start < this.windowMs) return ++h.n <= this.max;
    if (!h && this.hits.size >= this.cap) {
      this.sweep(now);
      if (this.hits.size >= this.cap) return false;
    }
    this.hits.set(key, { start: now, n: 1 });
    return this.max >= 1;
  }
  sweep(now = this.clock()) {
    for (const [key, h] of this.hits) if (now - h.start >= this.windowMs) this.hits.delete(key);
  }
}

/** IPv4 inteiro; IPv6 pelo /64. */
export function ipKey(raw) {
  let ip = String(raw ?? '').trim().replace(/%.*$/, '');
  if (/^::ffff:/i.test(ip) && isIP(ip.slice(7)) === 4) ip = ip.slice(7);
  const version = isIP(ip);
  if (version === 4) return ip;
  if (version !== 6) return '?';
  const [head, tail] = ip.toLowerCase().split('::');
  const groups = (s) => (s ? s.split(':') : []);
  const h = groups(head);
  const t = tail === undefined ? [] : groups(tail);
  const all = tail === undefined ? h : [...h, ...Array(Math.max(0, 8 - h.length - t.length)).fill('0'), ...t];
  return all.slice(0, 4).map((g) => parseInt(g, 16).toString(16)).join(':') + '::/64';
}

const LOOPBACK = /^(127\.|::1$|::ffff:127\.)/;

function parseUrl(raw) {
  if (typeof raw !== 'string' || !raw.startsWith('/')) return null;
  try {
    return new URL('http://relay' + raw);
  } catch {
    return null;
  }
}

export function createRelay({ dataDir = resolve('data'), clock = Date.now, log = console, newAccountsPerHour = 300 } = {}) {
  mkdirSync(join(dataDir, 'avatars'), { recursive: true });
  const store = new Store(join(dataDir, 'freevideo.db'));
  const computers = new Computers(store.db, clock);
  const limits = {
    accounts: new Limiter(20, HOUR, clock),
    accountsAll: new Limiter(newAccountsPerHour, HOUR, clock),
    recover: new Limiter(20, HOUR, clock),
    badAuth: new Limiter(120, MINUTE, clock),
    requests: new Limiter(600, MINUTE, clock),
    vaultPuts: new Limiter(120, HOUR, clock),
    links: new Limiter(30, MINUTE, clock),
    linkPolls: new Limiter(120, MINUTE, clock),
  };
  const vaultWaits = new Map(); // userId → quem espera o cofre mudar (celular aberto, a web)
  let vaultWaiting = 0;

  const avatarPath = (id) => join(dataDir, 'avatars', `${id}.jpg`);
  const profile = (u) => ({
    id: u.id,
    username: u.username,
    name: u.name,
    avatar: u.avatar_rev > 0 ? `/avatars/${u.id}.jpg?v=${u.avatar_rev}` : null,
    since: u.created_at,
  });

  async function body(req, limit) {
    if (req.rawBody) {
      if (req.rawBody.length > limit) throw new HttpError(413, 'too_large');
      return req.rawBody;
    }
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > limit) throw new HttpError(413, 'too_large');
      chunks.push(chunk);
    }
    return Buffer.concat(chunks);
  }

  async function json(req) {
    const raw = await body(req, JSON_LIMIT);
    if (!raw.length) return {};
    try {
      const v = JSON.parse(raw.toString('utf8'));
      if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error();
      return v;
    } catch {
      throw new HttpError(400, 'bad_json');
    }
  }

  const ipOf = (req) => {
    const peer = req.socket.remoteAddress ?? '';
    const cf = req.headers['cf-connecting-ip'];
    return ipKey(LOOPBACK.test(peer) && typeof cf === 'string' && cf ? cf : peer);
  };
  const plainHttp = (req) => {
    if (!LOOPBACK.test(req.socket.remoteAddress ?? '')) return false;
    const visitor = req.headers['cf-visitor'];
    return req.headers['x-forwarded-proto'] === 'http' || (typeof visitor === 'string' && /"scheme"\s*:\s*"http"/.test(visitor));
  };
  const tokenOf = (req) => {
    const h = req.headers.authorization;
    return typeof h === 'string' && h.startsWith('Bearer ') ? h.slice(7).trim() : null;
  };

  /** Celular: o token. Computador: o token e a assinatura (conferidos em handle), só onde [computer]. */
  function auth(req, { computer = false } = {}) {
    if (req.computer) {
      if (!computer) throw new HttpError(403, 'phone_only');
      const user = store.user(req.computer.user_id);
      if (!user) throw new HttpError(401, 'unlinked');
      if (!limits.requests.take(user.id)) throw new HttpError(429, 'slow_down');
      return user;
    }
    const user = store.userByToken(tokenOf(req));
    if (!user) {
      if (!limits.badAuth.take(ipOf(req))) throw new HttpError(429, 'slow_down');
      throw new HttpError(401, 'unauthorized');
    }
    if (!limits.requests.take(user.id)) throw new HttpError(429, 'slow_down');
    return user;
  }

  function vaultChange(userId, ms) {
    const set = vaultWaits.get(userId);
    if ((set?.size ?? 0) >= 6 || vaultWaiting >= 2000) return Promise.resolve();
    vaultWaiting++;
    return new Promise((resolveWait) => {
      let s = vaultWaits.get(userId);
      if (!s) vaultWaits.set(userId, (s = new Set()));
      const done = () => {
        clearTimeout(timer);
        s.delete(done);
        if (!s.size) vaultWaits.delete(userId);
        vaultWaiting--;
        resolveWait();
      };
      const timer = setTimeout(done, ms);
      s.add(done);
    });
  }

  const routes = [];
  const route = (method, pattern, handler) => routes.push({ method, pattern, handler });

  route('GET', /^\/v1\/health$/, () => ({ ok: true, version: VERSION, time: clock() }));

  route('POST', /^\/v1\/accounts$/, async (req) => {
    if (!limits.accounts.take(ipOf(req)) || !limits.accountsAll.take('all')) throw new HttpError(429, 'slow_down');
    await json(req);
    for (let attempt = 0; attempt < 20; attempt++) {
      const { username, name } = randomIdentity();
      if (store.userByName(username)) continue;
      const created = store.createUser({ username, name, now: clock() });
      return { status: 201, body: { token: created.token, recoveryCode: created.recoveryCode, me: profile(store.user(created.id)) } };
    }
    throw new HttpError(503, 'try_again');
  });

  route('POST', /^\/v1\/accounts\/recover$/, async (req) => {
    if (!limits.recover.take(ipOf(req))) throw new HttpError(429, 'slow_down');
    const b = await json(req);
    const user = store.userByRecovery(b.code);
    if (!user) throw new HttpError(404, 'unknown_code');
    return { token: store.addToken(user.id, clock()), me: profile(user) };
  });

  route('DELETE', /^\/v1\/me\/sessions$/, (req) => {
    const me = auth(req);
    return { revoked: store.revokeOtherTokens(me.id, sha256(tokenOf(req))) + computers.unlinkAll(me.id) };
  });

  route('GET', /^\/v1\/me$/, (req) => ({ me: profile(auth(req, { computer: true })) }));

  route('PATCH', /^\/v1\/me$/, async (req) => {
    const me = auth(req, { computer: true });
    const b = await json(req);
    if (b.name !== undefined) {
      const name = cleanDisplayName(b.name);
      if (!name) throw new HttpError(400, 'invalid_name');
      store.rename(me.id, name);
    }
    if (b.username !== undefined && normalizeUsername(b.username) !== me.username) throw new HttpError(400, 'username_fixed');
    return { me: profile(store.user(me.id)) };
  });

  route('POST', /^\/v1\/me\/recovery$/, (req) => ({ recoveryCode: store.newRecovery(auth(req).id) }));

  route('DELETE', /^\/v1\/me$/, (req) => {
    const me = auth(req);
    store.deleteUser(me.id);
    rmSync(avatarPath(me.id), { force: true });
    for (const wake of [...(vaultWaits.get(me.id) ?? [])]) wake();
    return { ok: true };
  });

  route('PUT', /^\/v1\/me\/avatar$/, async (req) => {
    const me = auth(req, { computer: true });
    const img = cleanJpeg(await body(req, AVATAR_LIMIT));
    if (!img) throw new HttpError(400, 'not_jpeg');
    const tmp = avatarPath(me.id) + '.tmp';
    writeFileSync(tmp, img);
    renameSync(tmp, avatarPath(me.id));
    store.bumpAvatar(me.id, true);
    return { me: profile(store.user(me.id)) };
  });

  route('DELETE', /^\/v1\/me\/avatar$/, (req) => {
    const me = auth(req, { computer: true });
    rmSync(avatarPath(me.id), { force: true });
    store.bumpAvatar(me.id, false);
    return { me: profile(store.user(me.id)) };
  });

  // O cofre: ?after=<rev> devolve só {rev, same} se não mudou; &wait=<s> (até 25) espera mudar.
  route('GET', /^\/v1\/me\/vault$/, async (req, url) => {
    const me = auth(req, { computer: true });
    const after = /^\d{1,12}$/.test(url.searchParams.get('after') ?? '') ? Number(url.searchParams.get('after')) : null;
    const wait = Math.min(25, Math.max(0, parseInt(url.searchParams.get('wait') ?? '0', 10) || 0)) * 1000;
    let v = store.vault(me.id);
    if (after != null && wait > 0 && (v?.rev ?? 0) === after) {
      await vaultChange(me.id, wait);
      v = store.vault(me.id);
    }
    if (!v) throw new HttpError(404, 'no_vault');
    if (after != null && v.rev === after) return { rev: v.rev, same: true, updatedAt: v.updated_at };
    return { rev: v.rev, key: v.key, data: Buffer.from(v.data).toString('base64'), updatedAt: v.updated_at };
  });

  route('PUT', /^\/v1\/me\/vault$/, async (req) => {
    const me = auth(req, { computer: true });
    if (!limits.vaultPuts.take(me.id)) throw new HttpError(429, 'slow_down');
    const raw = await body(req, VAULT_BODY_LIMIT);
    let b;
    try {
      b = JSON.parse(raw.toString('utf8'));
    } catch {
      throw new HttpError(400, 'bad_json');
    }
    if (!b || typeof b !== 'object' || Array.isArray(b)) throw new HttpError(400, 'bad_json');
    if (!Number.isInteger(b.baseRev) || b.baseRev < 0) throw new HttpError(400, 'bad_vault');
    if (typeof b.data !== 'string' || !BASE64.test(b.data)) throw new HttpError(400, 'bad_vault');
    const data = Buffer.from(b.data, 'base64');
    if (data.length > VAULT_DATA_MAX) throw new HttpError(413, 'too_large');
    if (data.length < 28) throw new HttpError(400, 'bad_vault');
    let key = null;
    if (b.key !== undefined) {
      if (typeof b.key !== 'string' || b.key.length > 512 || !BASE64.test(b.key)) throw new HttpError(400, 'bad_vault');
      key = b.key;
    }
    const r = store.putVault(me.id, b.baseRev, key, data, clock());
    if (r.stale) throw new HttpError(409, 'stale', { rev: r.rev });
    if (r.nokey) throw new HttpError(400, 'bad_vault');
    for (const wake of [...(vaultWaits.get(me.id) ?? [])]) wake();
    return { rev: r.rev };
  });

  // ---------------------------------------------------------------- computadores (a web)

  const LINK_ERRORS = {
    bad_key: [400, 'bad_key'], bad_proof_key: [400, 'bad_proof_key'], bad_wrapped: [400, 'bad_wrapped'],
    busy: [503, 'busy'], expired: [410, 'expired'], delivered: [410, 'delivered'], no_link: [404, 'no_link'], too_many: [409, 'too_many'],
  };
  const linkError = (code) => new HttpError(...(LINK_ERRORS[code] ?? [400, code]));

  route('POST', /^\/v1\/link$/, async (req) => {
    if (!limits.links.take(ipOf(req))) throw new HttpError(429, 'slow_down');
    const b = await json(req);
    const r = computers.start(b.key, b.proof, b.name);
    if (r.error) throw linkError(r.error);
    return { status: 201, body: r };
  });

  route('POST', /^\/v1\/link\/recover$/, async (req) => {
    if (!limits.recover.take(ipOf(req))) throw new HttpError(429, 'slow_down');
    const b = await json(req);
    const user = store.userByRecovery(b.code);
    if (!user) throw new HttpError(404, 'unknown_code');
    const r = computers.recover(user.id, b.proof, b.name);
    if (r.error) throw linkError(r.error);
    return { token: r.token, me: profile(user) };
  });

  route('POST', /^\/v1\/link\/shared$/, (req) => {
    auth(req, { computer: true });
    if (!req.computer) throw new HttpError(403, 'computer_only');
    computers.markShared(req.computer.id);
    return { ok: true };
  });

  route('DELETE', /^\/v1\/link$/, (req) => {
    const me = auth(req, { computer: true });
    if (!req.computer) throw new HttpError(403, 'computer_only');
    computers.unlink(me.id, req.computer.id);
    return { ok: true };
  });

  route('GET', /^\/v1\/link\/([A-Za-z0-9_-]{22})$/, async (req, url, [id]) => {
    if (!limits.linkPolls.take(ipOf(req))) throw new HttpError(429, 'slow_down');
    const wait = Math.min(25, Math.max(0, parseInt(url.searchParams.get('wait') ?? '0', 10) || 0)) * 1000;
    const r = await computers.poll(id, req.headers['x-freevideo-poll'], wait);
    if (r.error) throw linkError(r.error);
    if (r.status !== 'linked') return r;
    const user = store.user(r.userId);
    if (!user) throw linkError('expired');
    return { status: 'linked', token: r.token, wrapped: r.wrapped, owner: r.owner, me: profile(user) };
  });

  route('POST', /^\/v1\/link\/([A-Za-z0-9_-]{22})\/approve$/, async (req, url, [id]) => {
    const me = auth(req);
    const b = await json(req);
    const r = computers.approve(me.id, id, b.wrapped, b.owner);
    if (r.error) throw linkError(r.error);
    return r;
  });

  route('GET', /^\/v1\/me\/computers$/, (req) => ({ computers: computers.list(auth(req).id) }));

  route('DELETE', /^\/v1\/me\/computers\/([A-Za-z0-9_-]{22})$/, (req, url, [id]) => {
    const me = auth(req);
    return { ok: computers.unlink(me.id, id) };
  });

  // ---------------------------------------------------------------- servidor

  async function handle(req, res) {
    const url = parseUrl(req.url);
    try {
      if (!url) throw new HttpError(400, 'bad_url');
      if (plainHttp(req)) throw new HttpError(403, 'https_only');
      if ((req.method === 'GET' || req.method === 'HEAD') && url.pathname === '/.well-known/assetlinks.json') {
        res.writeHead(200, { ...SECURITY_HEADERS, 'content-type': 'application/json', 'content-length': Buffer.byteLength(ASSET_LINKS), 'cache-control': 'public, max-age=3600' });
        res.end(req.method === 'HEAD' ? undefined : ASSET_LINKS);
        return;
      }
      if (req.method === 'GET' && url.pathname === '/') {
        res.writeHead(302, { ...SECURITY_HEADERS, location: '/web/', 'cache-control': 'no-store' });
        res.end();
        return;
      }
      const avatar = req.method === 'GET' && url.pathname.match(/^\/avatars\/([a-z0-9]+)\.jpg$/);
      if (avatar) {
        let img;
        try {
          img = await readFile(avatarPath(avatar[1]));
        } catch (e) {
          if (e.code === 'ENOENT') throw new HttpError(404, 'no_avatar');
          throw e;
        }
        res.writeHead(200, { ...SECURITY_HEADERS, 'content-type': 'image/jpeg', 'content-length': img.length, 'cache-control': 'public, max-age=3600' });
        res.end(img);
        return;
      }
      const sig = req.headers['x-freevideo-sig'];
      if (sig !== undefined) {
        const c = computers.byToken(tokenOf(req));
        if (!c) {
          if (!limits.badAuth.take(ipOf(req))) throw new HttpError(429, 'slow_down');
          throw new HttpError(401, 'unlinked');
        }
        const raw = await body(req, VAULT_BODY_LIMIT);
        const why = computers.check(c, req.method, req.url, sig, raw);
        if (why) throw new HttpError(401, 'bad_proof', { now: clock(), why });
        req.rawBody = raw;
        req.computer = c;
      }
      let methodMismatch = false;
      for (const r of routes) {
        const m = url.pathname.match(r.pattern);
        if (!m) continue;
        if (r.method !== req.method) {
          methodMismatch = true;
          continue;
        }
        const out = await r.handler(req, url, m.slice(1));
        const status = out?.status && out.body ? out.status : 200;
        res.writeHead(status, JSON_HEADERS);
        res.end(JSON.stringify(out?.status && out.body ? out.body : out));
        return;
      }
      throw new HttpError(methodMismatch ? 405 : 404, methodMismatch ? 'method' : 'not_found');
    } catch (e) {
      const status = e instanceof HttpError ? e.status : 500;
      if (status === 500) log.error?.(`[relay] ${req.method} ${url?.pathname ?? '?'}`, e);
      if (!res.headersSent) {
        res.writeHead(status, status === 413 ? { ...JSON_HEADERS, connection: 'close' } : JSON_HEADERS);
        res.end(JSON.stringify({ error: e instanceof HttpError ? e.code : 'server_error', ...(e.extra ?? {}) }));
      } else res.destroy();
    }
  }

  const server = http.createServer((req, res) => {
    handle(req, res).catch((e) => {
      log.error?.('[relay] http', e);
      res.destroy();
    });
  });
  server.headersTimeout = 20_000;
  server.requestTimeout = 60_000;
  server.keepAliveTimeout = 100_000;

  const sweeper = setInterval(() => {
    try {
      computers.sweep();
      for (const limiter of Object.values(limits)) limiter.sweep();
    } catch (e) {
      log.error?.('[relay] sweep', e);
    }
  }, 30_000);
  sweeper.unref();

  function close() {
    clearInterval(sweeper);
    for (const set of vaultWaits.values()) for (const wake of [...set]) wake();
    return new Promise((done) => {
      server.close(() => {
        store.close();
        done();
      });
      server.closeIdleConnections?.();
    });
  }

  return { server, store, computers, close };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = parseInt(process.env.PORT ?? '8787', 10);
  const host = process.env.HOST ?? '127.0.0.1';
  const dataDir = resolve(process.env.DATA_DIR ?? 'data');
  process.umask(0o077);
  const perHour = parseInt(process.env.NEW_ACCOUNTS_PER_HOUR ?? '', 10);
  const relay = createRelay({ dataDir, newAccountsPerHour: perHour > 0 ? perHour : 300 });
  relay.server.listen(port, host, () => console.log(`[relay] FreeVideo relay ${VERSION} em http://${host}:${port} (dados em ${dataDir})`));
  const stop = () => {
    setTimeout(() => process.exit(0), 5000).unref();
    relay.close().then(() => process.exit(0));
  };
  process.on('SIGTERM', stop);
  process.on('SIGINT', stop);
}
