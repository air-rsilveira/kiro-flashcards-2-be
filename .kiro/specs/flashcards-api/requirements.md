# Requirements Document

## Introduction

Este documento descreve os requisitos para uma API REST de flashcards construída com Java/Spring Boot. O sistema permite que usuários criem baralhos (decks) de cartões (cards), organizem perguntas e respostas, e estudem usando um algoritmo de repetição espaçada (spaced repetition). A API oferece endpoints para gerenciamento de decks, cards, sessões de estudo com revisão espaçada e estatísticas de desempenho.

## Glossary

- **API**: A aplicação REST construída com Java/Spring Boot que expõe os endpoints de flashcards
- **Deck**: Um baralho que agrupa cartões relacionados por tema ou assunto. Possui id, name, description, createdAt e updatedAt
- **Card**: Um cartão de estudo pertencente a um Deck, contendo uma pergunta (front) e uma resposta (back). Possui id, deckId, front, back, createdAt e updatedAt
- **Review**: O registro de uma sessão de revisão de um Card. Possui id, cardId, result (EASY/GOOD/HARD/AGAIN), reviewedAt e nextReviewAt
- **Spaced_Repetition_Engine**: O componente responsável por calcular o próximo intervalo de revisão de um Card com base no resultado da revisão
- **Pagination**: Mecanismo de paginação com parâmetros page (número da página, base 0) e size (quantidade de itens por página, padrão 20)
- **Review_Result**: Enumeração dos resultados possíveis de uma revisão: EASY, GOOD, HARD ou AGAIN

## Requirements

### Requisito 1: Criar Deck

**User Story:** Como um estudante, eu quero criar um novo baralho de flashcards, para que eu possa organizar meus cartões de estudo por tema.

#### Critérios de Aceitação

1. WHEN uma requisição POST é recebida em /api/decks com um campo name de 1 a 100 caracteres (não vazio e não em branco), THE API SHALL criar um novo Deck e retornar o Deck criado com status HTTP 201, incluindo os campos id, name, description, createdAt e updatedAt
2. IF uma requisição POST é recebida em /api/decks sem o campo name, ou com name vazio ou em branco, THEN THE API SHALL retornar um erro de validação com status HTTP 400 contendo uma mensagem indicando que o campo name é obrigatório
3. IF uma requisição POST é recebida em /api/decks com name excedendo 100 caracteres ou description excedendo 500 caracteres, THEN THE API SHALL retornar um erro de validação com status HTTP 400 contendo uma mensagem indicando o limite excedido
4. THE API SHALL atribuir automaticamente os campos id, createdAt e updatedAt ao criar um Deck
5. WHEN uma requisição POST é recebida em /api/decks sem o campo description ou com description nula, THE API SHALL criar o Deck com description vazia, tratando o campo como opcional

### Requisito 2: Listar Decks

**User Story:** Como um estudante, eu quero listar todos os meus baralhos, para que eu possa visualizar e escolher qual estudar.

#### Critérios de Aceitação

1. WHEN uma requisição GET é recebida em /api/decks, THE API SHALL retornar uma lista paginada de todos os Decks com status HTTP 200, incluindo os metadados de paginação: content (lista de Decks), totalElements, totalPages, number (página atual) e size (itens por página)
2. WHEN parâmetros de Pagination são fornecidos (?page=0&size=20), THE API SHALL retornar a página correspondente com o número de itens especificado, limitando o valor máximo de size a 100
3. WHEN nenhum parâmetro de Pagination é fornecido, THE API SHALL usar page=0 e size=20 como valores padrão
4. THE API SHALL retornar os Decks ordenados por createdAt em ordem decrescente
5. IF o parâmetro page for negativo ou o parâmetro size for menor que 1 ou maior que 100, THEN THE API SHALL retornar um erro de validação com status HTTP 400
6. WHEN uma requisição GET é recebida em /api/decks e nenhum Deck existe, THE API SHALL retornar uma lista paginada vazia com totalElements igual a 0 e status HTTP 200

### Requisito 3: Buscar Deck por ID

**User Story:** Como um estudante, eu quero buscar um baralho específico pelo ID, para que eu possa ver seus detalhes.

#### Critérios de Aceitação

1. WHEN uma requisição GET é recebida em /api/decks/{id} com um ID válido existente, THE API SHALL retornar o Deck correspondente com os campos id, name, description, createdAt e updatedAt, com status HTTP 200
2. WHEN uma requisição GET é recebida em /api/decks/{id} com um ID inexistente porém em formato UUID válido, THE API SHALL retornar um erro com status HTTP 404
3. IF uma requisição GET é recebida em /api/decks/{id} com um ID em formato inválido (não UUID), THEN THE API SHALL retornar um erro com status HTTP 400

### Requisito 4: Atualizar Deck

**User Story:** Como um estudante, eu quero atualizar o nome ou descrição de um baralho, para que eu possa manter minhas informações organizadas.

#### Critérios de Aceitação

1. WHEN uma requisição PUT é recebida em /api/decks/{id} com um campo name de 1 a 100 caracteres e um ID existente, THE API SHALL atualizar o Deck e retornar o Deck atualizado com status HTTP 200
2. WHEN uma requisição PUT é recebida em /api/decks/{id} com um ID inexistente, THE API SHALL retornar um erro com status HTTP 404
3. IF uma requisição PUT é recebida em /api/decks/{id} sem o campo name, ou com name vazio, em branco, ou excedendo 100 caracteres, THEN THE API SHALL retornar um erro de validação com status HTTP 400
4. THE API SHALL atualizar o campo updatedAt automaticamente ao modificar um Deck, mantendo o campo createdAt inalterado
5. WHEN uma requisição PUT é recebida em /api/decks/{id} com description nula ou ausente, THE API SHALL aceitar a atualização e definir description como vazia

### Requisito 5: Remover Deck

**User Story:** Como um estudante, eu quero remover um baralho que não preciso mais, para que minha lista fique organizada.

#### Critérios de Aceitação

1. WHEN uma requisição DELETE é recebida em /api/decks/{id} com um ID existente, THE API SHALL remover o Deck e retornar status HTTP 204 sem corpo na resposta
2. IF uma requisição DELETE é recebida em /api/decks/{id} com um ID inexistente, THEN THE API SHALL retornar um erro com status HTTP 404
3. WHEN um Deck é removido, THE API SHALL remover todos os Cards associados ao Deck e todas as Reviews associadas a esses Cards (exclusão em cascata)
4. IF uma requisição DELETE é recebida em /api/decks/{id} com um ID em formato inválido, THEN THE API SHALL retornar um erro com status HTTP 400

### Requisito 6: Criar Card em um Deck

**User Story:** Como um estudante, eu quero criar um novo cartão dentro de um baralho, para que eu possa adicionar conteúdo de estudo.

#### Critérios de Aceitação

1. WHEN uma requisição POST é recebida em /api/decks/{deckId}/cards com front e back não vazios (cada um com no máximo 5000 caracteres) e um deckId existente, THE API SHALL criar um novo Card associado ao Deck e retornar o Card criado (contendo id, deckId, front, back, createdAt e updatedAt) com status HTTP 201
2. WHEN uma requisição POST é recebida em /api/decks/{deckId}/cards com um deckId inexistente, THE API SHALL retornar um erro com status HTTP 404
3. WHEN uma requisição POST é recebida em /api/decks/{deckId}/cards sem o campo front ou back, THE API SHALL retornar um erro de validação com status HTTP 400
4. WHEN uma requisição POST é recebida em /api/decks/{deckId}/cards com front ou back vazio ou em branco, THE API SHALL retornar um erro de validação com status HTTP 400
5. IF uma requisição POST é recebida em /api/decks/{deckId}/cards com front ou back excedendo 5000 caracteres, THEN THE API SHALL retornar um erro de validação com status HTTP 400
6. THE API SHALL atribuir automaticamente os campos id, deckId, createdAt e updatedAt ao criar um Card

### Requisito 7: Listar Cards de um Deck

**User Story:** Como um estudante, eu quero listar todos os cartões de um baralho, para que eu possa revisar o conteúdo disponível.

#### Critérios de Aceitação

1. WHEN uma requisição GET é recebida em /api/decks/{deckId}/cards com um deckId existente, THE API SHALL retornar uma lista paginada dos Cards do Deck ordenados por createdAt em ordem decrescente
2. WHEN uma requisição GET é recebida em /api/decks/{deckId}/cards com um deckId inexistente, THE API SHALL retornar um erro com status HTTP 404
3. WHEN o parâmetro de filtro ?q=termo é fornecido com pelo menos 1 caractere não-branco, THE API SHALL retornar apenas os Cards cujo front ou back contenham o termo de busca como substring (busca case-insensitive)
4. WHEN parâmetros de Pagination são fornecidos com page >= 0 e size entre 1 e 100, THE API SHALL retornar a página correspondente com o número de itens especificado
5. WHEN nenhum parâmetro de Pagination é fornecido, THE API SHALL usar page=0 e size=20 como valores padrão
6. IF o parâmetro ?q é fornecido vazio ou contendo apenas espaços em branco, THEN THE API SHALL ignorar o filtro e retornar todos os Cards do Deck paginados
7. IF parâmetros de Pagination inválidos são fornecidos (page negativo ou size menor que 1 ou maior que 100), THEN THE API SHALL retornar um erro de validação com status HTTP 400

### Requisito 8: Buscar Card por ID

**User Story:** Como um estudante, eu quero buscar um cartão específico pelo ID, para que eu possa ver seus detalhes completos.

#### Critérios de Aceitação

1. WHEN uma requisição GET é recebida em /api/cards/{id} com um ID válido existente, THE API SHALL retornar o Card correspondente com todos os seus campos (id, deckId, front, back, createdAt, updatedAt) e status HTTP 200
2. WHEN uma requisição GET é recebida em /api/cards/{id} com um ID inexistente, THE API SHALL retornar um erro com status HTTP 404
3. IF o parâmetro {id} não é um UUID válido, THEN THE API SHALL retornar um erro com status HTTP 400

### Requisito 9: Atualizar Card

**User Story:** Como um estudante, eu quero atualizar o conteúdo de um cartão, para que eu possa corrigir ou melhorar minhas perguntas e respostas.

#### Critérios de Aceitação

1. WHEN uma requisição PUT é recebida em /api/cards/{id} com front e back não vazios (cada um com no máximo 5000 caracteres) e um ID existente, THE API SHALL atualizar o Card e retornar o Card atualizado com status HTTP 200
2. WHEN uma requisição PUT é recebida em /api/cards/{id} com um ID inexistente, THE API SHALL retornar um erro com status HTTP 404
3. IF uma requisição PUT é recebida em /api/cards/{id} com front ou back vazio, em branco, ausente ou excedendo 5000 caracteres, THEN THE API SHALL retornar um erro de validação com status HTTP 400
4. THE API SHALL atualizar o campo updatedAt automaticamente ao modificar um Card, mantendo createdAt e deckId inalterados

### Requisito 10: Remover Card

**User Story:** Como um estudante, eu quero remover um cartão que não preciso mais, para que meu baralho fique atualizado.

#### Critérios de Aceitação

1. WHEN uma requisição DELETE é recebida em /api/cards/{id} com um ID existente, THE API SHALL remover o Card e todas as Reviews associadas de forma atômica e retornar status HTTP 204 sem corpo de resposta
2. IF uma requisição DELETE é recebida em /api/cards/{id} com um ID inexistente, THEN THE API SHALL retornar um erro com status HTTP 404 no formato padronizado de erro
3. IF uma requisição DELETE é recebida em /api/cards/{id} com um ID em formato inválido, THEN THE API SHALL retornar um erro com status HTTP 400
4. WHEN um Card é removido, THE API SHALL remover todas as Reviews associadas ao Card em uma única transação, garantindo que nenhuma Review órfã permaneça caso a operação seja concluída com sucesso

### Requisito 11: Buscar Cards para Revisão

**User Story:** Como um estudante, eu quero buscar os próximos cartões que preciso revisar em um baralho, para que eu possa estudar de forma eficiente usando repetição espaçada.

#### Critérios de Aceitação

1. WHEN uma requisição GET é recebida em /api/decks/{deckId}/study com um deckId existente, THE API SHALL retornar com status HTTP 200 uma lista de Cards que estão prontos para revisão, onde cada Card contém os campos id, deckId, front, back, createdAt e updatedAt
2. WHEN uma requisição GET é recebida em /api/decks/{deckId}/study com um deckId inexistente, THE API SHALL retornar um erro com status HTTP 404
3. THE Spaced_Repetition_Engine SHALL incluir na lista de estudo apenas Cards cujo nextReviewAt (da Review mais recente) seja anterior ou igual ao timestamp do servidor no momento da requisição
4. THE Spaced_Repetition_Engine SHALL incluir Cards que nunca foram revisados (sem nenhuma Review associada) na lista de estudo
5. THE API SHALL limitar a quantidade de Cards retornados a no máximo 20 por requisição de estudo
6. THE API SHALL ordenar os Cards retornados por prioridade: Cards nunca revisados primeiro (ordenados por createdAt ascendente entre si), seguidos por Cards com nextReviewAt mais antigo primeiro
7. IF nenhum Card está pronto para revisão no Deck solicitado, THEN THE API SHALL retornar status HTTP 200 com uma lista vazia

### Requisito 12: Registrar Resultado de Revisão

**User Story:** Como um estudante, eu quero registrar o resultado de uma revisão de cartão, para que o sistema calcule quando devo revisar novamente.

#### Critérios de Aceitação

1. WHEN uma requisição POST é recebida em /api/cards/{id}/review com um Review_Result válido (EASY, GOOD, HARD ou AGAIN) e um cardId existente, THE API SHALL criar uma nova Review e retornar a Review criada com status HTTP 201, incluindo os campos id, cardId, result, reviewedAt e nextReviewAt
2. WHEN uma requisição POST é recebida em /api/cards/{id}/review com um cardId inexistente, THE API SHALL retornar um erro com status HTTP 404
3. IF uma requisição POST é recebida em /api/cards/{id}/review com um Review_Result inválido, ausente ou nulo, THEN THE API SHALL retornar um erro de validação com status HTTP 400
4. THE Spaced_Repetition_Engine SHALL calcular o campo nextReviewAt adicionando ao reviewedAt o intervalo baseado no Review_Result: AGAIN resulta em +1 minuto, HARD em +10 minutos, GOOD em +1 dia, EASY em +4 dias
5. THE API SHALL registrar automaticamente o campo reviewedAt com o timestamp UTC do servidor no momento da revisão

### Requisito 13: Estatísticas de Estudo do Deck

**User Story:** Como um estudante, eu quero visualizar estatísticas de estudo de um baralho, para que eu possa acompanhar meu progresso.

#### Critérios de Aceitação

1. WHEN uma requisição GET é recebida em /api/decks/{deckId}/stats com um deckId existente, THE API SHALL retornar as estatísticas de estudo do Deck com status HTTP 200
2. WHEN uma requisição GET é recebida em /api/decks/{deckId}/stats com um deckId inexistente, THE API SHALL retornar um erro com status HTTP 404
3. THE API SHALL incluir nas estatísticas: totalCards (total de cartões no deck), cardsStudied (cartões que possuem ao menos uma Review), cardsDue (cartões cujo nextReviewAt é anterior ou igual ao momento atual, ou que nunca foram revisados), totalReviews (total de revisões realizadas no deck), e resultDistribution (objeto com a contagem de Reviews por cada Review_Result: EASY, GOOD, HARD, AGAIN)
4. THE API SHALL incluir nas estatísticas o campo studyStreak representando o número de dias consecutivos com ao menos uma revisão no deck, contados a partir de hoje em direção ao passado; se hoje não possui nenhuma revisão, o studyStreak SHALL ser 0
5. IF o Deck não possui nenhum Card, THEN THE API SHALL retornar totalCards=0, cardsStudied=0, cardsDue=0, totalReviews=0, studyStreak=0, e resultDistribution com todos os valores zerados

### Requisito 14: Tratamento de Erros Padronizado

**User Story:** Como um consumidor da API, eu quero receber respostas de erro em formato padronizado, para que eu possa tratar erros de forma consistente.

#### Critérios de Aceitação

1. IF um erro de validação ocorre, THEN THE API SHALL retornar uma resposta JSON com status HTTP 400 contendo os campos: timestamp (formato ISO 8601), status (código numérico), error (descrição do tipo de erro) e messages (lista onde cada item identifica o campo com falha e o motivo da rejeição)
2. IF um recurso não é encontrado, THEN THE API SHALL retornar uma resposta JSON com status HTTP 404 contendo os campos: timestamp (formato ISO 8601), status (código numérico), error (descrição do tipo de erro) e message (indicando qual recurso e identificador não foi encontrado)
3. IF um erro interno ocorre, THEN THE API SHALL retornar uma resposta JSON com status HTTP 500 contendo os campos: timestamp (formato ISO 8601), status (código numérico), error (descrição do tipo de erro) e message fixa que não contenha stack traces, nomes de classes, queries SQL ou detalhes de infraestrutura
4. IF uma requisição utiliza um método HTTP não suportado pelo endpoint, THEN THE API SHALL retornar uma resposta JSON com status HTTP 405 contendo os campos: timestamp, status, error e message indicando os métodos permitidos
5. THE API SHALL retornar todas as respostas de erro com Content-Type application/json e corpo no formato JSON consistente com os campos definidos neste requisito
