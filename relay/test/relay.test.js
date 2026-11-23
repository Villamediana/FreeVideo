// O relay do FreeVideo: perfil que nasce sozinho, recuperação, nome e foto, o cofre, apagar — e o que
// ele não guarda nem aceita.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { createRelay, ipKey } from '../src/server.js';
import { MAX_TOKENS } from '../src/db.js';

let relay, base, dir;
let ip = 0;
before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'freevideo-relay-'));
  relay = createRelay({ dataDir: dir, log: { error() {} } });
  await new Promise((r) => relay.server.listen(0, '127.0.0.1', r));
  base = `http://127.0.0.1:${relay.server.address().port}`;
});
after(async () => {
  await relay.close();
  rmSync(dir, { recursive: true, force: true });
});

async function api(method, path, { token, body, raw, headers = {} } = {}) {
  const h = { 'cf-connecting-ip': `10.1.${Math.floor(++ip / 200)}.${ip % 200}`, ...headers };
  if (token) h.authorization = `Bearer ${token}`;
  let payload = raw;
  if (body !== undefined) {
    h['content-type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(base + path, { method, headers: h, body: payload });
  const type = res.headers.get('content-type') ?? '';
  return { status: res.status, json: type.includes('json') ? await res.json() : null, res };
}

const person = async () => {
  const r = await api('POST', '/v1/accounts', { body: {} });
  assert.equal(r.status, 201);
  return { token: r.json.token, code: r.json.recoveryCode, me: r.json.me };
};

test('o perfil nasce sozinho: @nome, nome, código de recuperação', async () => {
  const p = await person();
  assert.match(p.me.username, /^[a-z]+_\d{3,4}$/);
  assert.match(p.code, /^[0-9A-Z]{4}(-[0-9A-Z]{4}){3}$/);
  assert.deepEqual(Object.keys(p.me).sort(), ['avatar', 'id', 'name', 'since', 'username']);
  const me = await api('GET', '/v1/me', { token: p.token });
  assert.equal(me.json.me.id, p.me.id);
  assert.equal((await api('GET', '/v1/me')).status, 401);
});

test('nome muda, @nome não; texto limpo', async () => {
  const p = await person();
  const r = await api('PATCH', '/v1/me', { token: p.token, body: { name: '  Ana‮ Clara\n ' } });
  assert.equal(r.json.me.name, 'Ana Clara');
  assert.equal((await api('PATCH', '/v1/me', { token: p.token, body: { name: '​' } })).json.error, 'invalid_name');
  assert.equal((await api('PATCH', '/v1/me', { token: p.token, body: { username: 'outro' } })).json.error, 'username_fixed');
});

test('celular novo: o código traz o mesmo perfil; código novo invalida o velho', async () => {
  const p = await person();
  const r = await api('POST', '/v1/accounts/recover', { body: { code: p.code.toLowerCase().replace(/-/g, ' ') } });
  assert.equal(r.json.me.id, p.me.id);
  assert.equal((await api('GET', '/v1/me', { token: r.json.token })).status, 200);
  const fresh = await api('POST', '/v1/me/recovery', { token: p.token });
  assert.notEqual(fresh.json.recoveryCode, p.code);
  assert.equal((await api('POST', '/v1/accounts/recover', { body: { code: p.code } })).status, 404);
  assert.equal((await api('POST', '/v1/accounts/recover', { body: { code: fresh.json.recoveryCode } })).status, 200);
});

test('até MAX_TOKENS aparelhos; sair dos outros', async () => {
  const p = await person();
  const tokens = [p.token];
  for (let i = 0; i < MAX_TOKENS; i++) tokens.push((await api('POST', '/v1/accounts/recover', { body: { code: p.code } })).json.token);
  assert.equal((await api('GET', '/v1/me', { token: tokens[0] })).status, 401);
  const last = tokens.at(-1);
  const out = await api('DELETE', '/v1/me/sessions', { token: last });
  assert.equal(out.json.revoked, MAX_TOKENS - 1);
  assert.equal((await api('GET', '/v1/me', { token: tokens[2] })).status, 401);
  assert.equal((await api('GET', '/v1/me', { token: last })).status, 200);
});

test('foto: só JPEG de verdade; apagar perfil leva tudo', async () => {
  const p = await person();
  assert.equal((await api('PUT', '/v1/me/avatar', { token: p.token, raw: Buffer.from('não é jpeg') })).json.error, 'not_jpeg');
  const sealed = Buffer.alloc(64, 7).toString('base64');
  await api('PUT', '/v1/me/vault', { token: p.token, body: { baseRev: 0, data: sealed, key: Buffer.alloc(61, 3).toString('base64') } });
  assert.equal((await api('DELETE', '/v1/me', { token: p.token })).json.ok, true);
  assert.equal((await api('GET', '/v1/me', { token: p.token })).status, 401);
  const db = new DatabaseSync(join(dir, 'freevideo.db'), { readOnly: true });
  assert.equal(db.prepare('SELECT count(*) AS n FROM vaults WHERE user_id = ?').get(p.me.id).n, 0);
  db.close();
});

test('o cofre: versão certa grava, velha dá 409, sem chave na criação não', async () => {
  const p = await person();
  const data = Buffer.alloc(64, 1).toString('base64');
  assert.equal((await api('PUT', '/v1/me/vault', { token: p.token, body: { baseRev: 0, data } })).json.error, 'bad_vault');
  assert.equal((await api('PUT', '/v1/me/vault', { token: p.token, body: { baseRev: 0, data, key: 'a2V5' } })).json.rev, 1);
  assert.equal((await api('PUT', '/v1/me/vault', { token: p.token, body: { baseRev: 0, data } })).status, 409);
  assert.equal((await api('PUT', '/v1/me/vault', { token: p.token, body: { baseRev: 1, data: 'x' } })).json.error, 'bad_vault');
  const v = await api('GET', '/v1/me/vault', { token: p.token });
  assert.deepEqual([v.json.rev, v.json.key, v.json.data], [1, 'a2V5', data]);
});

test('o banco guarda só o básico', async () => {
  const db = new DatabaseSync(join(dir, 'freevideo.db'), { readOnly: true });
  const tables = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").all().map((t) => t.name).sort();
  assert.deepEqual(tables, ['computers', 'tokens', 'users', 'vaults']);
  const cols = db.prepare('PRAGMA table_info(users)').all().map((c) => c.name).sort();
  assert.deepEqual(cols, ['avatar_rev', 'created_at', 'id', 'name', 'recovery_hash', 'username']);
  db.close();
});

test('o App Link do QR code, http é recusado, limites', async () => {
  const al = await api('GET', '/.well-known/assetlinks.json');
  assert.equal(al.json[0].target.package_name, 'com.villamediana.video');
  assert.equal((await api('GET', '/v1/health', { headers: { 'x-forwarded-proto': 'http' } })).json.error, 'https_only');
  let last;
  for (let i = 0; i < 21; i++) last = await api('POST', '/v1/accounts', { body: {}, headers: { 'cf-connecting-ip': '203.0.113.7' } });
  assert.equal(last.status, 429);
  assert.equal(ipKey('2001:db8:1:2:3:4:5:6'), '2001:db8:1:2::/64');
});
