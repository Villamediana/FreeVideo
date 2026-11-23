# FreeVideo relay — instalar no servidor

Roda neste VPS (o mesmo do Novelo e do FreeSong), atrás do túnel da Cloudflare dele.

| | |
|---|---|
| Endereço público | `https://freevideo.simplyonegoal.com` (o túnel manda `/web` pro `web/server.js` e o resto pra cá) |
| Código | `/srv/freevideo/relay` (clone de `Villamediana/FreeVideo`, só leitura pro serviço) |
| Porta | `127.0.0.1:3990` |
| Node | 22.13+ (`node:sqlite`). Nenhuma dependência de npm |
| Dados | `/var/lib/freevideo-relay` (`freevideo.db` + `avatars/`) |
| Segredos | nenhum |

Unit `freevideo-relay.service` (como a do FreeSong: `DynamicUser`, `StateDirectory=freevideo-relay`,
`PORT=3990 HOST=127.0.0.1 DATA_DIR=/var/lib/freevideo-relay`, modelo de permissões do Node, sem rede de
saída: `IPAddressDeny=any` + localhost).

Atualizar: `cd /srv/freevideo && git pull && systemctl restart freevideo-relay freevideo-web`.
