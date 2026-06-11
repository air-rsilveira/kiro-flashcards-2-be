# Flashcards API

API REST de flashcards com repetição espaçada, construída com Java 21 e Spring Boot 3.3.

## Sobre o Projeto

Sistema para criação e gerenciamento de baralhos (decks) e cartões (cards) de estudo, com sessões de revisão baseadas em repetição espaçada e estatísticas de desempenho.

### Funcionalidades

- **Decks**: CRUD completo com paginação e ordenação por data de criação
- **Cards**: CRUD com busca por texto (case-insensitive) e paginação
- **Estudo**: Sessão de estudo com seleção inteligente de cartões (nunca revisados + vencidos)
- **Revisão**: Registro de resultados (EASY, GOOD, HARD, AGAIN) com cálculo automático do próximo intervalo
- **Estatísticas**: Total de cartões, cartões estudados, cartões pendentes, distribuição de resultados e streak de estudo
- **Erros padronizados**: Respostas de erro em JSON consistente para 400, 404, 405 e 500

### Stack

- Java 21
- Spring Boot 3.3.5
- Spring Data JPA
- H2 (desenvolvimento/testes) / PostgreSQL (produção)
- Jakarta Validation
- jqwik (property-based testing)
- JUnit 5
- Gradle (Kotlin DSL)

## Como Rodar Localmente

### Pré-requisitos

- Java 21+
- Docker e Docker Compose (opcional, para rodar com PostgreSQL)

### Opção 1: Modo desenvolvimento (H2 in-memory)

Não precisa de banco externo — usa H2 em memória.

```bash
./gradlew bootRun
```

A API estará disponível em `http://localhost:8080`.

O console H2 fica acessível em `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:flashcards`, user: `sa`, sem senha).

### Opção 2: Docker Compose (PostgreSQL)

Sobe a aplicação + PostgreSQL em containers:

```bash
docker compose up --build
```

A API estará disponível em `http://localhost:8080`.

Para parar:

```bash
docker compose down
```

Para remover os dados do banco:

```bash
docker compose down -v
```

### Rodar os testes

```bash
./gradlew test
```

Executa 76 testes (unitários, property-based e integração) usando H2 em memória.

## Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/decks` | Criar deck |
| GET | `/api/decks` | Listar decks (paginado) |
| GET | `/api/decks/{id}` | Buscar deck por ID |
| PUT | `/api/decks/{id}` | Atualizar deck |
| DELETE | `/api/decks/{id}` | Remover deck (cascata) |
| POST | `/api/decks/{deckId}/cards` | Criar card |
| GET | `/api/decks/{deckId}/cards` | Listar cards (paginado, `?q=` para busca) |
| GET | `/api/cards/{id}` | Buscar card por ID |
| PUT | `/api/cards/{id}` | Atualizar card |
| DELETE | `/api/cards/{id}` | Remover card |
| GET | `/api/decks/{deckId}/study` | Cards prontos para revisão (máx. 20) |
| POST | `/api/cards/{id}/review` | Registrar resultado de revisão |
| GET | `/api/decks/{deckId}/stats` | Estatísticas do deck |

## Algoritmo de Repetição Espaçada

| Resultado | Próxima revisão |
|-----------|-----------------|
| AGAIN | +1 minuto |
| HARD | +10 minutos |
| GOOD | +1 dia |
| EASY | +4 dias |
