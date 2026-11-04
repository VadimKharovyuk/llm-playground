# llm-playground

Консольный стенд для тестирования LLM. Один и тот же набор промптов прогоняется через облачные модели
(**OpenRouter**) и локальные (**Ollama**). Каждый вызов замеряется и сохраняется в PostgreSQL,
а по прогону строится сводка, которую можно вставить в статью.

Что замеряется у каждого вызова:

- полное время и время до первого токена (TTFT, при стриминге);
- токены prompt / completion / total и скорость генерации в токенах в секунду;
- `finish_reason`, стоимость (у OpenRouter берётся точная, у Ollama 0);
- холодный старт Ollama и её собственные счётчики `eval_count`, `eval_duration`, `load_duration`;
- статус `OK` / `ERROR` / `EMPTY`, причина ошибки и полный текст ответа.

## Требования

- Java 21
- PostgreSQL (база должна существовать, схему создаёт Flyway)
- Ключ OpenRouter: https://openrouter.ai/keys (без него работает только Ollama)
- [Ollama](https://ollama.com) с нужными моделями:

```bash
ollama pull llama3.2:3b
```

```bash
ollama pull qwen3:8b
```

## Настройка

Секреты задаются только через переменные окружения или файл `.env` в корне проекта (он в `.gitignore`):

```bash
cp .env.example .env
```

```properties
OPENROUTER_API_KEY=sk-or-...
DB_URL=jdbc:postgresql://localhost:5432/llm-playground
DB_USER=postgres
DB_PASSWORD=
```

Остальные настройки лежат в `src/main/resources/application.yml`, в секции `playground`:

| Параметр | По умолчанию | Описание |
|---|---|---|
| `models` | haiku-5.5, llama3.2:3b | модели в формате `provider:model`, где provider — `openrouter` или `ollama` |
| `prompts` | все | имена промптов из `src/main/resources/prompts/` (имя файла без расширения) |
| `temperature` / `max-tokens` | 0.2 / 1024 | общие параметры для всех моделей |
| `model-overrides` | — | свой `max-tokens` / `temperature` для отдельной модели (нужно reasoning-моделям) |
| `runs-per-prompt` | 3 | сколько раз повторять каждый промпт |
| `streaming` | true | стриминг нужен для замера TTFT |
| `warmup` | true | прогревочный вызов Ollama, сохраняется с `cold_start=true` |
| `http.connect-timeout` / `http.read-timeout` | 10s / 120s | HTTP-таймауты; ретраев Spring AI максимум 2 |
| `comment` | — | комментарий к прогону, сохраняется в `run` |

## Запуск

```bash
./mvnw spring-boot:run
```

Любой параметр можно переопределить из командной строки:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--playground.models[0]=ollama:qwen3:8b --playground.models[1]=openrouter:openai/gpt-5-mini --playground.runs-per-prompt=5"
```

Или собрать jar и запускать его:

```bash
./mvnw package -DskipTests
```

```bash
java -jar target/llm-playground-0.0.1-SNAPSHOT.jar --playground.prompts=json-extraction,logic-puzzle --playground.comment="local vs cloud"
```

Reasoning-модели тратят токены на скрытые рассуждения. Если лимита не хватает, они возвращают пустой ответ
с `finish_reason=length`, и вызов получает статус `EMPTY`. Таким моделям лимит поднимают отдельно:

```bash
java -jar target/llm-playground-0.0.1-SNAPSHOT.jar "--playground.models[0]=openrouter:deepseek/deepseek-r1-0528" "--playground.model-overrides[0].model=openrouter:deepseek/deepseek-r1-0528" "--playground.model-overrides[0].max-tokens=8192"
```

## Отчёт

После каждого прогона в консоль выводится сводка по моделям: число вызовов, ошибок и пустых ответов,
среднее, минимальное, максимальное и медианное время, TTFT, токены, токены в секунду, стоимость и время
холодного старта. Одновременно в `reports/` записываются три файла:

- `run-N.md` — таблица в Markdown для статьи;
- `run-N.csv` — та же сводка в CSV;
- `run-N-calls.csv` — метрики каждого вызова, без текста ответа.

Отчёт по уже сохранённому прогону строится из БД (без `run-id` берётся последний прогон):

```bash
java -jar target/llm-playground-0.0.1-SNAPSHOT.jar --playground.mode=report --playground.report.run-id=3
```

Числа всегда печатаются с точкой (`Locale.ROOT`), независимо от локали системы.

## Как считаются метрики

- **Время** считается только по успешным измеряемым вызовам. В него не входят прогревочный вызов и вызовы,
  во время которых Ollama заново загружала модель (`load_duration` больше 1 с): такие вызовы помечаются
  `cold_start`.
- **Токены в секунду** у Ollama считаются как `eval_count / eval_duration`, то есть чистая скорость генерации.
  У остальных моделей это completion-токены, делённые на время после первого токена при стриминге
  или на полное время без стриминга.
- **Стоимость OpenRouter** запрашивается после вызова через `GET /api/v1/generation`, вне замеряемого времени.
- **Ошибки** не прерывают прогон: вызов сохраняется со статусом `ERROR` и понятной причиной
  (`TIMEOUT`, `CONNECTION REFUSED`, ответ HTTP), а не «Error while extracting response».

## Структура

```
config/       PlaygroundProperties, сборка gateway и провайдеров
llm/          LlmGateway, MeteredLlmGateway (замер), RecordingLlmGateway (запись в БД), ErrorDescriber
llm/provider/ OpenRouterProvider, OllamaProvider, ChatModelExecutor (call/stream), OpenRouterCostClient
persistence/  сущности run / llm_call и репозитории
prompt/       PromptLibrary: промпты из resources/prompts
runner/       PlaygroundRunner (CommandLineRunner), BenchmarkService
report/       SQL-агрегации, вывод в консоль, Markdown и CSV
```

Схема БД задаётся миграциями в `src/main/resources/db/migration`.

## Тесты

```bash
./mvnw test
```

Тесты не обращаются ни к OpenRouter, ни к Ollama: провайдеры заменены заглушками.
Тесты репозиториев и контекста запускаются на PostgreSQL в Testcontainers, а без Docker пропускаются.

## Лицензия

[MIT](LICENSE)
