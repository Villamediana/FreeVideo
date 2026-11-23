# FreeVideo relay: API (v2.0.0)

Contrato entre o app (e o FreeVideo na web) e o servidor. Endereço: `https://freevideo.simplyonegoal.com`.
Instalação: [DEPLOY.md](DEPLOY.md).

O FreeVideo não tem a parte social: aqui ficam **só** o perfil (que nasce sozinho no celular, com
`@nome` e código de recuperação), o **cofre** cifrado no celular e os **computadores** ligados ao perfil.

## Regras gerais

- JSON UTF-8, corpo até 64 KB (foto até 600 KB, cofre até 6 MB). `cache-control: no-store`.
- Erro: status + `{"error": "<código>"}`. Só HTTPS (`403 https_only`). `429 slow_down`: espere e tente de novo.
- Token no cabeçalho `Authorization: Bearer <token>`. `401 unauthorized` com token salvo = o token não
  vale mais (vá pro código de recuperação).

**Perfil**: `{id, username, name, avatar ("/avatars/<id>.jpg?v=N" ou null), since}`.

## Conta e perfil

| Método e rota | Corpo | Resposta |
|---|---|---|
| `POST /v1/accounts` | `{}` | `201 {token, recoveryCode, me}` |
| `POST /v1/accounts/recover` | `{code}` | `{token, me}` (`404 unknown_code`) |
| `GET /v1/me` | | `{me}` |
| `PATCH /v1/me` | `{name}` | `{me}` (`400 invalid_name`; o `@nome` não muda: `400 username_fixed`) |
| `PUT /v1/me/avatar` | JPEG cru | `{me}` (`400 not_jpeg`) |
| `DELETE /v1/me/avatar` | | `{me}` |
| `POST /v1/me/recovery` | | `{recoveryCode}` (o antigo deixa de valer) |
| `DELETE /v1/me/sessions` | | `{revoked}` (só este aparelho continua; computadores também saem) |
| `GET /v1/me/vault` | `?after=<rev>&wait=<s até 25>` opcionais | `{rev, key, data, updatedAt}`, ou `{rev, same: true}` se não mudou (`404 no_vault`) |
| `PUT /v1/me/vault` | `{baseRev, data, key?}` | `{rev}` (`409 stale {rev}`, `400 bad_vault`, `413`) |
| `DELETE /v1/me` | | `{ok}` (cofre e computadores vão junto) |

**Cofre**: `data` = conteúdo (JSON com nomes curtos: `h` histórico, `s` inscrições, `sv` salvos, `x`
"não tenho interesse", `q` buscas, `in` interesses — ver `app/…/tube/account/TubeVault.kt` e
`web/src/core/vault.js`), gzip, AES-256-GCM com a chave do cofre. `key` = a chave do cofre embrulhada
pela chave do código de recuperação (PBKDF2-SHA256, 60 000 voltas, sal `freevideo-vault:<id do perfil>`).
O relay não lê nenhum dos dois. Uma espera longa (`after` + `wait`) responde na hora em que outro aparelho grava.

## Computadores (a web)

Iguais aos do FreeSong (ver o API.md do relay do FreeSong, 1.8.0), com os nomes do FreeVideo:
cabeçalhos `X-FreeVideo-Sig` (sobre `freevideo-web-v1\n<método>\n<caminho>\n<ms>\n<nonce>\n<sha256 do corpo>`)
e `X-FreeVideo-Poll`, HKDF com info `freevideo-link-v1`, QR code
`https://freevideo.simplyonegoal.com/link#<id>.<pública>.<segredo>` (App Link do app).

`POST /v1/link`, `GET /v1/link/:id?wait=25`, `POST /v1/link/:id/approve` (celular), `POST /v1/link/recover`,
`GET /v1/me/computers` e `DELETE /v1/me/computers/:id` (celular), `DELETE /v1/link` e `POST /v1/link/shared`
(o computador). Computador só usa `GET/PATCH /v1/me`, foto e o cofre; o resto → `403 phone_only`.

`GET /.well-known/assetlinks.json`: o app `com.villamediana.video` (abre o QR code lido pela câmera).

## Limites

Contas novas 20/h por IP e 300/h no total; recuperação 20/h por IP; token errado 120/min por IP;
600 pedidos/min por pessoa; cofre gravado 120/h por pessoa; 10 aparelhos e 10 computadores por perfil;
códigos de computador 30/min por IP (valem 10 min).
