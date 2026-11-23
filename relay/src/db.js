// Banco do relay do FreeVideo (SQLite embutido no Node, node:sqlite). Guarda SÓ o básico: o perfil
// (@nome, nome, foto, data de entrada), tokens e código de recuperação em hash, e o cofre — o que o
// celular sabe da pessoa (histórico, inscrições, salvos), cifrado NO CELULAR: aqui são bytes que o
// servidor não consegue ler. Sem amigos, sem conversa: o FreeVideo não tem a parte social.
import { DatabaseSync } from 'node:sqlite';
import { createHash, randomBytes } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const SCHEMA = `
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA busy_timeout = 2000;
PRAGMA journal_size_limit = 16777216;
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  username      TEXT NOT NULL UNIQUE,
  name          TEXT NOT NULL,
  avatar_rev    INTEGER NOT NULL DEFAULT 0,
  created_at    INTEGER NOT NULL,
  recovery_hash TEXT NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS tokens (
  hash       TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS tokens_user ON tokens(user_id);
CREATE TABLE IF NOT EXISTS vaults (
  user_id    TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  rev        INTEGER NOT NULL,
  key        TEXT NOT NULL,
  data       BLOB NOT NULL,
  updated_at INTEGER NOT NULL
);
`;

export const sha256 = (text) => createHash('sha256').update(text).digest('hex');

/** Aparelhos (tokens) por pessoa; o mais antigo sai quando entra um novo além disso. */
export const MAX_TOKENS = 10;

export const newToken = () => randomBytes(32).toString('base64url');

const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

/** Código de recuperação "7K2Q-M9XD-4TFA-H3RB" (80 bits); no banco só o hash da forma normalizada. */
export function newRecoveryCode() {
  const bytes = randomBytes(10);
  let bits = 0n;
  for (const b of bytes) bits = (bits << 8n) | BigInt(b);
  let out = '';
  for (let i = 0; i < 16; i++) {
    out = CROCKFORD[Number(bits & 31n)] + out;
    bits >>= 5n;
  }
  return out.match(/.{4}/g).join('-');
}

/** "7k2q m9xd-4tfa h3rb" → "7K2QM9XD4TFAH3RB" (O vira 0, I/L viram 1, como no Crockford). */
export function normalizeRecoveryCode(raw) {
  return String(raw ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '').replace(/O/g, '0').replace(/[IL]/g, '1');
}

const newId = () => 'u' + randomBytes(9).toString('base64url').replace(/[-_]/g, 'x').toLowerCase();

export class Store {
  constructor(file) {
    if (file !== ':memory:') mkdirSync(dirname(file), { recursive: true });
    this.db = new DatabaseSync(file);
    this.db.exec(SCHEMA);
    this.q = {};
  }

  st(sql) {
    return (this.q[sql] ??= this.db.prepare(sql));
  }

  tx(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const out = fn();
      this.db.exec('COMMIT');
      return out;
    } catch (e) {
      this.db.exec('ROLLBACK');
      throw e;
    }
  }

  close() {
    this.db.close();
  }

  createUser({ username, name, now }) {
    const id = newId();
    const token = newToken();
    const recoveryCode = newRecoveryCode();
    this.tx(() => {
      this.st('INSERT INTO users (id, username, name, created_at, recovery_hash) VALUES (?, ?, ?, ?, ?)')
        .run(id, username, name, now, sha256(normalizeRecoveryCode(recoveryCode)));
      this.st('INSERT INTO tokens (hash, user_id, created_at) VALUES (?, ?, ?)').run(sha256(token), id, now);
    });
    return { id, token, recoveryCode };
  }

  userByToken(token) {
    if (!token) return null;
    return this.st('SELECT u.* FROM tokens t JOIN users u ON u.id = t.user_id WHERE t.hash = ?').get(sha256(token)) ?? null;
  }

  userByRecovery(code) {
    const norm = normalizeRecoveryCode(code);
    if (norm.length !== 16) return null;
    return this.st('SELECT * FROM users WHERE recovery_hash = ?').get(sha256(norm)) ?? null;
  }

  /** Celular novo (recuperação). Guarda só os [MAX_TOKENS] mais novos. */
  addToken(userId, now) {
    const token = newToken();
    this.tx(() => {
      this.st('INSERT INTO tokens (hash, user_id, created_at) VALUES (?, ?, ?)').run(sha256(token), userId, now);
      this.st(`DELETE FROM tokens WHERE user_id = ? AND hash NOT IN
               (SELECT hash FROM tokens WHERE user_id = ? ORDER BY created_at DESC, rowid DESC LIMIT ?)`).run(userId, userId, MAX_TOKENS);
    });
    return token;
  }

  revokeOtherTokens(userId, keepHash) {
    return Number(this.st('DELETE FROM tokens WHERE user_id = ? AND hash != ?').run(userId, keepHash).changes);
  }

  newRecovery(userId) {
    const code = newRecoveryCode();
    this.st('UPDATE users SET recovery_hash = ? WHERE id = ?').run(sha256(normalizeRecoveryCode(code)), userId);
    return code;
  }

  user(id) {
    return this.st('SELECT * FROM users WHERE id = ?').get(id) ?? null;
  }

  userByName(username) {
    return this.st('SELECT * FROM users WHERE username = ?').get(username) ?? null;
  }

  rename(id, name) {
    this.st('UPDATE users SET name = ? WHERE id = ?').run(name, id);
  }

  bumpAvatar(id, present) {
    if (present) this.st('UPDATE users SET avatar_rev = abs(avatar_rev) + 1 WHERE id = ?').run(id);
    else this.st('UPDATE users SET avatar_rev = -(abs(avatar_rev) + 1) WHERE id = ?').run(id);
  }

  vault(userId) {
    return this.st('SELECT rev, key, data, updated_at FROM vaults WHERE user_id = ?').get(userId) ?? null;
  }

  /** Grava o cofre se [baseRev] é a versão que está aqui (0 = ainda não há). {ok, rev} | {stale, rev} | {nokey}. */
  putVault(userId, baseRev, key, data, now) {
    return this.tx(() => {
      const cur = this.st('SELECT rev, key FROM vaults WHERE user_id = ?').get(userId);
      const rev = cur ? cur.rev : 0;
      if (baseRev !== rev) return { stale: true, rev };
      if (!cur) {
        if (!key) return { nokey: true };
        this.st('INSERT INTO vaults (user_id, rev, key, data, updated_at) VALUES (?, 1, ?, ?, ?)').run(userId, key, data, now);
        return { ok: true, rev: 1 };
      }
      this.st('UPDATE vaults SET rev = ?, key = ?, data = ?, updated_at = ? WHERE user_id = ?').run(rev + 1, key ?? cur.key, data, now, userId);
      return { ok: true, rev: rev + 1 };
    });
  }

  deleteUser(id) {
    this.st('DELETE FROM users WHERE id = ?').run(id);
  }
}
