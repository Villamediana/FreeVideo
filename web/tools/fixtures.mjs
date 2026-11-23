// Gera o que o teste do app (TubeWebParityTest.kt) abre: um cofre fechado pela página, a chave dele
// embrulhada pelo código e um QR code de computador (com a privada dele, só pra teste). Rodar de novo
// só se o formato mudar: node web/tools/fixtures.mjs
import { writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as lock from '../src/core/crypto.js';
import { compact, normalize } from '../src/core/vault.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const subtle = globalThis.crypto.subtle;
const code = '7K2Q-M9XD-4TFA-H3RB';
const user = 'uparity01';
const raw = globalThis.crypto.getRandomValues(new Uint8Array(32));
const content = compact(normalize({
  h: [{ i: 'dQw4w9WgXcQ', t: 'Pão caseiro ção', cn: 'Amo Pão', cu: 'https://www.youtube.com/channel/UCrpLBn22arVETOO5alUbW7A', d: 600000, p: 120000, w: 300000, n: 2, f: 1700000000000, l: 1700000001000 }],
  s: [{ u: 'https://www.youtube.com/channel/UCrpLBn22arVETOO5alUbW7A', n: 'Amo Pão', at: 5 }],
  sv: [{ li: 'later', i: 'abcdefghijk', t: 'Depois', cn: 'Canal', at: 7 }],
  x: [{ k: 'v:zzzzzzzzzzz', t: 'Não', at: 8 }],
  q: [{ q: 'pão', n: 3, at: 9 }],
  in: [{ t: 'culinária', at: 10 }],
}));
const key = await subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt']);
const data = await lock.sealVault(content, key);
const wrapped = (await lock.phoneVault({}, raw.slice(), code, user)).key;
const pair = await subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
const pub = lock.b64u.enc(new Uint8Array(await subtle.exportKey('raw', pair.publicKey)));
const secret = lock.b64u.enc(globalThis.crypto.getRandomValues(new Uint8Array(16)));
const linkId = lock.b64u.enc(globalThis.crypto.getRandomValues(new Uint8Array(16)));
const out = {
  code,
  user,
  rawKey: lock.b64.enc(raw),
  vault: data,
  wrappedKey: wrapped,
  content,
  link: { url: lock.linkUrl('https://freevideo.simplyonegoal.com', linkId, pub, secret), id: linkId, pub, secret, privateJwk: await subtle.exportKey('jwk', pair.privateKey) },
};
writeFileSync(path.join(here, '../../app/src/test/resources/web-fixtures.json'), JSON.stringify(out, null, 2) + '\n');
console.log('app/src/test/resources/web-fixtures.json');

