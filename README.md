# Skillforge

Construção de competências pela prática.

O repositório, o pacote Java e a interface usam o mesmo nome: **skillforge**.

## Incremento 0

A aplicação sobe e responde duas sondas:

- **Vida** — o processo está no ar, mesmo se o Postgres não estiver.
- **Prontidão** — o processo atende. Se o Postgres não aceita conexão, a prontidão responde `503` e o tráfego deve ser recusado.

| Sonda | Caminho |
| --- | --- |
| Vida | `GET /actuator/health/liveness` |
| Prontidão | `GET /actuator/health/readiness` |

A interface em `http://localhost:3000` lê as duas sondas e diz se o ambiente está de pé.

## Arranque

É preciso Java 21, Node.js 22, Docker e curl.

```bash
./scripts/start.sh
```

Na primeira execução o script cria um `.env` local, com senha gerada, e sobe o Postgres, a API e a interface. O `.env` não entra no repositório. O modelo vazio está em `.env.example`.

## Versão

`VERSION` é a versão do produto. O CI publica a tag anotada `vX.Y.Z` quando essa versão chega em `main`.
