# FreeVideo no computador (web)

`https://freevideo.simplyonegoal.com` — o FreeVideo no navegador do computador: o feed (o mesmo
cérebro do app, `src/core/feed.js` = `FeedEngine` + `FeedLoader`), a página do vídeo, busca, canais,
inscrições, biblioteca — com o histórico, as inscrições e os salvos do celular, nos dois sentidos. No
celular a página só diz pra baixar o app.

- **Ligar**: QR code lido pelo app (Biblioteca › foto › Computador) ou pela câmera; sem o celular, o
  código de recuperação. Igual ao FreeSong (ECDH + HKDF `freevideo-link-v1`, pedidos assinados).
- **O cofre**: `src/core/vault.js` = `tube/account/TubeVault.kt` (mesmos nomes curtos, mesma junção).
- **O vídeo**: o player do YouTube (com os controles dele) num frame; o tempo assistido e onde parou
  vão pro histórico como no app; sai da página do vídeo e ele segue pequeno no canto.
- **A ponte** (`server/`): busca, vídeo (sem o player), comentários, canal, playlist, sugestões e as
  novidades dos canais pelo RSS; cache na memória, limite por IP, nada em log.
- **O passe da ponte** (1.6): a ponte só atende computador ligado. O relay dá um passe assinado
  (Ed25519, `GET /v1/pass`, pedido assinado pelo computador, vale 2 h; `relay/src/pass.js`); a ponte
  confere com a chave pública que pede ao relay dentro da máquina (`GET /v1/pass-key`,
  `server/pass.js`, `RELAY_URL`, padrão `http://127.0.0.1:3990`). Relay reiniciado = chave nova: a
  página pede outro passe sozinha. Limites: por IP (400/min) e por computador (400 pedidos/min, 240
  idas ao YouTube/min fora do cache — as novidades das inscrições contam um por canal —, 6 ao mesmo
  tempo). **Deploy: relay antes da web.**
- **Abrindo um vídeo** (1.6): no lugar do preto, a capa escurecida e o "carregando" até a imagem
  aparecer; sem poder tocar sozinho (ou travado 12 s), um botão de tocar. O mesmo cuidado do FreeSong
  com o frame do YouTube (`src/core/youtube.js`, testado em `test/youtube.test.js`). As prateleiras
  mostram uma linha só (quantos couberem).
## Rodar e testar

```bash
export PATH=/opt/node/bin:$PATH
cd web && npm ci && npm run build && npm test
node test/e2e.mjs /tmp/fotos      # Chromium, rede de verdade
PORT=3992 npm start               # http://127.0.0.1:3992/
```

Paridade com o app: `TubeWebParityTest` abre o que `tools/fixtures.mjs` gerou e grava
`test/fixtures/phone.json`, aberto por `test/parity.test.js`.

## No servidor

`/srv/freevideo/web`, unit `freevideo-web.service` em `127.0.0.1:3992`; o túnel manda
pro relay (`freevideo-relay.service`, 3990) só `/v1`, `/avatars` e `/.well-known`, e o resto pra ela.
