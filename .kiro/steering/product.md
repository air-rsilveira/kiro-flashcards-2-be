# Product: Flashcards API

REST API for a spaced-repetition flashcard system. Users create decks of cards and study them through review sessions that schedule future reviews based on recall quality.

## Core Domain

- **Decks**: Collections of flashcards with name and description
- **Cards**: Front/back flashcards belonging to a deck
- **Study sessions**: Presents cards due for review (never-reviewed + overdue), max 20 per session
- **Reviews**: Records study results (EASY, GOOD, HARD, AGAIN) and calculates next review date
- **Stats**: Per-deck statistics including total/studied/pending cards, result distribution, study streak

## Spaced Repetition Algorithm

| Result | Next Review Interval |
|--------|---------------------|
| AGAIN  | +1 minute           |
| HARD   | +10 minutes         |
| GOOD   | +1 day              |
| EASY   | +4 days             |

## API Base Path

All endpoints are under `/api/`. Resources use UUID identifiers.
