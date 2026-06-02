# ADR-007 — Trade-offs entre algoritmos de rate limiting

**Status**: Aceita | **Data**: 2026-06-01 (Atualizada na Sprint 6)

## Contexto
O RateMaster suporta três algoritmos de rate limiting distribuído, cada um com
características de precisão, consumo de memória Redis e comportamento de borda distintos.
Esta ADR documenta a complexidade assintótica (Big O), estrutura interna e trade-offs para guiar a escolha arquitetural de engenheiros sêniores utilizando a plataforma.

## Algoritmos, Complexidade e Estruturas

| Característica          | Token Bucket | Sliding Window | Fixed Window |
|-------------------------|--------------|----------------|--------------|
| **Bursts controlados**  | ✅ Sim (até capacidade) | ❌ Não | ⚠️ No boundary (Burst duplo) |
| **Precisão temporal**   | Alta | Máxima | Baixa (edge case) |
| **Estrutura no Redis**  | Hash (2 campos: `tokens`, `timestamp`) | Sorted Set (N entradas por req) | String simples (1 contador) |
| **Complexidade de Tempo**| **O(1)** | **O(log N)** + `ZREMRANGEBYSCORE` cleanup | **O(1)** |
| **Custo de Memória**    | Baixo (Constante) | Alto (Linear ao tráfego) | Mínimo (Constante) |
| **Comando Core Lua**    | `HSET` / `HMGET` | `ZADD` / `ZREMRANGEBYSCORE` | `INCR` / `PEXPIREAT` |
| **Script Lua**          | [token_bucket.lua](../../ratemaster-core/src/main/resources/lua/token_bucket.lua) | [sliding_window.lua](../../ratemaster-core/src/main/resources/lua/sliding_window.lua) | [fixed_window.lua](../../ratemaster-core/src/main/resources/lua/fixed_window.lua) |

## Decisão e Casos de Uso (Trade-offs)

1. **[Token Bucket]** (Padrão)
   - **Trade-off**: Equilibra permissividade para rajadas (bursts) legítimas com proteção de longo prazo. Ocupa espaço constante O(1).
   - **Ideal para**: APIs REST gerais, endpoints com tráfego variável e tolerância a rajadas controladas.

2. **[Sliding Window]** (Alta Precisão)
   - **Trade-off**: Fornece precisão milimétrica e elimina qualquer tipo de edge case em bordas de janelas. O custo é o aumento no consumo de memória RAM do Redis e complexidade O(log N) nas inserções.
   - **Ideal para**: Rate limits jurídicos, compliance (Open Finance, BACEN), e proteção estrita contra DoS/Brute-force distribuído.

3. **[Fixed Window]** (Alta Escala e Baixo Custo)
   - **Trade-off**: Utiliza apenas operações atômicas `INCR` e `PEXPIREAT`. Custo O(1) com uso minúsculo de memória. Porém, sofre do conhecido *Edge Case Matemático de Borda*.
   - **Ideal para**: Ambientes de hiper-escala, quotas de billing (ex: 10.000 requisições por hora) ou janelas muito curtas (ex: limite por segundo).

## O "Edge Case" Matemático do Fixed Window

**Documentado Intencionalmente**: O algoritmo Fixed Window limpa seu contador em horários absolutos predeterminados (ex: a cada minuto virado no relógio).
Isso cria um comportamento matemático chamado de "Double Burst Boundary":

*Exemplo: Limite de 100 requisições por minuto.*
- Às **00:59.998**, um cliente envia 100 requisições. O contador atinge 100 e a janela (minuto 00) está exaurida.
- Às **01:00.000**, o relógio vira, a chave expira via `PEXPIREAT` e uma nova janela se inicia limpa.
- Às **01:00.002**, o mesmo cliente envia mais 100 requisições. O contador permite.
- **Resultado Prático**: O cliente realizou **200 requisições em 4 milissegundos**, apesar do limite configurado ser "100 por minuto".

**Mitigação**: 
Não utilize Fixed Window para proteção rigorosa contra ataques de força bruta, a não ser que o limite seja atrelado a janelas minúsculas (1 segundo). Para proteção estrita, utilize o **Sliding Window**.

## Consequências
A escolha do algoritmo é granular. O atributo `algorithm` na anotação `@RateLimit` permite misturar estratégias de rate limiting num mesmo microsserviço (ex: Token Bucket para `GET /produtos` e Sliding Window para `POST /login`).
