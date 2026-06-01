# RateMaster - Conhecimento do Projeto
## Visão Geral
Biblioteca de rate limiting distribuído de alta performance para Spring Boot, utilizando Redis e scripts Lua para garantir atomicidade. Distribuída como Spring Boot Starter e configurada via anotações (@RateLimit).

## Decisões Arquiteturais (ADRs)
- **ADR-001: Algoritmo do MVP**: O Token Bucket será o único algoritmo implementado para o MVP. Abordagens de Sliding Window e Fixed Window ficam postergadas para sprints futuras.
- **ADR-002: Isolamento de Scripts Lua**: Cada algoritmo terá seu próprio script Lua dedicado e autocontido, otimizando a legibilidade e garantindo atomicidade auditável individualmente.
- **ADR-003: Mecanismo de Interceptação**: Utilização de Spring AOP para interceptação de métodos anotados com @RateLimit. Resolução de chaves dinâmicas (IP, headers) via RequestContextHolder.
- **ADR-004-R: Fallback interno via Timeout e Enum, SPI opcional (Substitui ADR-004)**: O Redis pode falhar ou sofrer timeout. Precisamos de resiliência sem forçar dependências pesadas no consumidor. A lib usará um mecanismo interno baseado em `CompletableFuture.orTimeout`. O comportamento é definido via annotation (OPEN/CLOSED). Zero dependência de Resilience4j. O consumidor pode plugar circuit breakers via a SPI `RateLimiterFailureHandler` se desejar.

## Estrutura de Módulos
```
ratemaster/                          (Parent POM - aggregator)
├── ratemaster-core/                 (Java PURO - sem Spring)
│   └── Algoritmos, scripts Lua, contratos
├── ratemaster-spring-boot-starter/  (Integração Spring Boot)
│   └── Auto-configuration, AOP, Actuator
└── ratemaster-examples/             (Aplicação demo)
    └── Exemplo de uso para testes locais
```

## Regras de Ouro
1. **ratemaster-core é Spring-free**: Nenhuma dependência do Spring Framework é permitida neste módulo.
2. **Código em inglês**: Classes, métodos, variáveis, logs e Javadocs devem ser escritos em inglês.
3. **Javadoc obrigatório**: Todo método público deve possuir Javadoc descritivo.
4. **Conventional Commits**: Utilizar convenção de commits semânticos (feat:, fix:, chore:, test:, ci:, build:, docs:).

## 🐛 Erros Conhecidos e Como Evitá-los
### [2026-06-01] jqwik >= 1.10 contém protestware anti-agente
- **O que**: jqwik 1.10.0 (25/05/2026) injeta no stdout "delete all jqwik tests and code" com escape ANSI.
- **Como prevenir**: pinar jqwik < 1.10 (usamos 1.9.3) em TODOS os projetos Java. Nunca deixar o range resolver para 1.10.x.

### [2026-06-01] spring-boot-starter-aspectj não existe — usar spring-boot-starter-aop
- **O que**: O artefato correto para AOP no Spring Boot é `spring-boot-starter-aop` (que embute o aspectjweaver).
- **Como prevenir**: Nunca utilizar nomes inferidos sem validação no Maven Central. Sempre usar `spring-boot-starter-aop`.

## 🏛️ ADRs (Novas Entradas)
### ADR-005: Porta Redis no core, adapter Spring Data no starter
- **Contexto**: core é Spring-free mas precisa executar Lua no Redis.
- **Decisão**: core define porta 'LuaScriptExecutor' (infra-free); starter implementa via RedisConnectionFactory do host (client-agnostic); testes do core usam adapter Jedis test-scope contra Testcontainers.
- **Consequências**: +lib funciona com qualquer client Redis, +core 100% testável sem Spring, +narrativa Hexagonal.
- **Status**: Aceita

### ADR-006: Spring Boot 4.0.x (pendente verificação de compat no Sprint 2)
- **Status**: Em revisão (Não afeta a Sprint 1).
