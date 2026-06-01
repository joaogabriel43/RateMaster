# RateMaster - Conhecimento do Projeto
## Visão Geral
Biblioteca de rate limiting distribuído de alta performance para Spring Boot, utilizando Redis e scripts Lua para garantir atomicidade. Distribuída como Spring Boot Starter e configurada via anotações (@RateLimit).

## Decisões Arquiteturais (ADRs)
- **ADR-001: Algoritmo do MVP**: O Token Bucket será o único algoritmo implementado para o MVP. Abordagens de Sliding Window e Fixed Window ficam postergadas para sprints futuras.
- **ADR-002: Isolamento de Scripts Lua**: Cada algoritmo terá seu próprio script Lua dedicado e autocontido, otimizando a legibilidade e garantindo atomicidade auditável individualmente.
- **ADR-003: Mecanismo de Interceptação**: Utilização de Spring AOP para interceptação de métodos anotados com @RateLimit. Resolução de chaves dinâmicas (IP, headers) via RequestContextHolder.
- **ADR-004: Estratégia de Fallback**: O comportamento padrão em caso de indisponibilidade ou timeout do Redis será 'fail-open' (permitir a requisição), utilizando Resilience4j (Circuit Breaker + Timeout) de forma integrada para evitar degradação em cascata e amplificação de latência.

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
