# ADR-007 — Trade-offs entre algoritmos de rate limiting

**Status**: Aceita | **Data**: 2026-06-01

## Contexto
O RateMaster suporta três algoritmos de rate limiting distribuído, cada um com
características de precisão, consumo de memória Redis e comportamento de borda distintos.
Esta ADR documenta os trade-offs para guiar a escolha do consumidor.

## Algoritmos e trade-offs

| Característica          | Token Bucket          | Sliding Window         | Fixed Window          |
|-------------------------|-----------------------|------------------------|-----------------------|
| Bursts controlados      | ✅ Sim (até capacidade)| ❌ Não                 | ⚠️ No boundary        |
| Precisão temporal       | Alta                  | Máxima                 | Baixa (edge case)     |
| Estrutura Redis         | 2 campos (Hash)       | Sorted Set (N entradas)| 1 campo (String+TTL)  |
| Custo Redis por request | O(1)                  | O(log N) + cleanup     | O(1)                  |
| Edge case documentado   | Nenhum                | Nenhum                 | Burst duplo na virada |
| Melhor para             | APIs com tráfego variável | Rate limit jurídico | Alta escala, baixo custo |

## Decisão
- **Token Bucket** como padrão: equilibra permissividade (bursts legítimos) com proteção.
  Implementado no MVP. Adequado para a maioria dos casos.
- **Sliding Window** para precisão máxima: não há edge case, mas consome mais memória
  Redis proporcional ao tráfego. Adequado para compliance e limites legais/regulatórios.
- **Fixed Window** para custo mínimo: O(1) e TTL nativo do Redis. Adequado para
  ambientes de alta escala onde o burst no boundary é aceitável ou improvável
  (janelas curtas, ex.: 1 segundo).

## Edge case do Fixed Window (documentado intencionalmente)
Com janela de 60s e limite de 100 req: às 00:59 entram 100 req (janela A esgotada).
Às 01:00 nova janela abre — entram mais 100 req imediatamente. Resultado: 200 req em
~1 segundo. Mitigação: usar janelas curtas ou migrar para Sliding Window quando
o burst no boundary é inaceitável.

## Consequências
A escolha do algoritmo é por instância de `@RateLimit` — o atributo `algorithm`
permite misturar estratégias no mesmo serviço conforme o endpoint.
