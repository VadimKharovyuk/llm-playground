# llm-playground

Веб-стенд для сравнения LLM. Вы пишете задание, например «напиши контроллер и репозиторий на Spring Boot»,
выбираете модели: облачные через **OpenRouter** и локальные через **Ollama**. Ответы появляются рядом,
у каждого показаны время, токены и стоимость, и каждому можно поставить оценку качества от 1 до 5.
Всё сохраняется в PostgreSQL, поэтому новую модель потом можно прогнать на тех же заданиях
и сравнить со старыми результатами.

Что замеряется у каждого вызова:

- полное время и время до первого токена (TTFT, при стриминге);
- токены prompt / completion / total и скорость генерации в токенах в секунду;
- `finish_reason`, стоимость (у OpenRouter берётся точная, у Ollama 0);
- холодный старт Ollama и её собственные счётчики `eval_count`, `eval_duration`, `load_duration`;
- статус `OK` / `ERROR` / `EMPTY` и причина ошибки;
- полный текст ответа (сохраняется без изменений) и ваша оценка с заметкой.

## Страницы

| Страница | Что на ней |
|---|---|
| `/` — **Новое сравнение** | задание (новое или из библиотеки), выбор моделей, параметры, запуск |
| `/runs/{id}` — **Прогон** | сводка по моделям, ответы рядом (Markdown с подсветкой кода), оценки, экспорт в Markdown/CSV |
| `/runs` — **Прогоны** | история прогонов |
| `/prompts` — **Промпты** | библиотека заданий; на странице задания видно, как на него ответили все модели во всех прогонах |
| `/models` — **Модели** | общий рейтинг: оценка, время, токены, скорость, стоимость по всем прогонам |

Прогоны выполняются в фоне по одному: параллельные прогоны искажали бы замеры локальных моделей.
Пока прогон идёт, страница обновляется сама.

## Требования

- Java 21
- PostgreSQL (база должна существовать, схему создаёт Flyway)
- Ключ OpenRouter: https://openrouter.ai/keys (без него работает только Ollama)
- [Ollama](https://ollama.com) с нужными моделями. Установленные модели сами появляются в форме:

```bash
ollama pull llama3.2:3b
```

## Настройка

Секреты задаются только через переменные окружения или файл `.env` в корне проекта (он в `.gitignore`):

```bash
cp .env.example .env
```

```properties
CHAT_PROVIDERS=ollama
OPENROUTER_API_KEY=sk-or-...
DB_URL=jdbc:postgresql://localhost:5432/llm-playground
DB_USER=postgres
DB_PASSWORD=
```

### Выбор провайдера

Провайдер переключается переменной `CHAT_PROVIDERS` в `.env`. После правки приложение нужно перезапустить.

| Значение | Что доступно |
|---|---|
| `ollama` (по умолчанию) | только локальные модели |
| `openrouter` | только облачные модели (нужен `OPENROUTER_API_KEY`) |
| `ollama,openrouter` | оба провайдера, их модели можно сравнивать в одном прогоне |

Модели выключенного провайдера не показываются в форме. Если вписать такую модель вручную, форма её отклонит.
Список `playground.models` в `application.properties` при переключении править не нужно: из него берутся
только модели включённого провайдера.

Модели и параметры по умолчанию задаются в `src/main/resources/application.properties`:

```properties
playground.models[0]=openrouter:anthropic/claude-haiku-5.5
playground.models[1]=ollama:llama3.2:3b
playground.temperature=0.2
playground.max-tokens=2048
# reasoning-моделям нужен запас токенов, иначе вернётся пустой ответ с finish_reason=length
playground.model-overrides[0].model=openrouter:deepseek/deepseek-r1-0528
playground.model-overrides[0].max-tokens=8192
```

Модель записывается в формате `provider:model`, где provider — `openrouter` или `ollama`.
В форме можно выбрать любую другую модель: поле подсказывает весь каталог OpenRouter.

## Запуск

```bash
./mvnw spring-boot:run
```

Откройте http://localhost:1510 (порт задаётся в `server.port`). Сервер слушает только `127.0.0.1`,
потому что через него можно тратить кредиты OpenRouter.

## Как считаются метрики

- **Время** считается только по успешным измеряемым вызовам. В него не входят прогревочный вызов Ollama и вызовы,
  во время которых Ollama заново загружала модель (`load_duration` больше 1 с): такие вызовы помечаются `cold start`.
- **Токены в секунду** у Ollama считаются как `eval_count / eval_duration`, то есть чистая скорость генерации.
  У остальных моделей это completion-токены, делённые на время после первого токена при стриминге
  или на полное время без стриминга.
- **Стоимость OpenRouter** запрашивается после вызова через `GET /api/v1/generation`, вне замеряемого времени.
- **Ошибки** не прерывают прогон: вызов сохраняется со статусом `ERROR` и понятной причиной
  (`TIMEOUT`, `CONNECTION REFUSED`, ответ HTTP).
- **Промпт с ответами не меняется.** Если его отредактировать, изменения сохранятся как новый промпт,
  а старые ответы останутся привязаны к тому тексту, на который они были даны.

Все числа выводятся с точкой (`Locale.ROOT`), независимо от локали системы.

## Структура

```
config/       PlaygroundProperties, сборка gateway и провайдеров, фоновый executor
llm/          LlmGateway, MeteredLlmGateway (замер), RecordingLlmGateway (запись в БД), ErrorDescriber
llm/provider/ OpenRouterProvider, OllamaProvider, ChatModelExecutor (call/stream), OpenRouterCostClient
persistence/  сущности run / llm_call / prompt и репозитории
prompt/       PromptService, PromptSeeder (примеры из resources/prompts)
runner/       RunService (форма → фоновый прогон), BenchmarkService, ModelCatalog
report/       SQL-агрегации, экспорт в Markdown и CSV
web/          контроллеры; шаблоны Thymeleaf в resources/templates
```

Схема БД задаётся миграциями в `src/main/resources/db/migration`.

## Тесты

```bash
./mvnw test
```

Тесты не обращаются ни к OpenRouter, ни к Ollama: провайдеры заменены заглушками. Страницы рендерятся
настоящими шаблонами через MockMvc. Тесты репозиториев и контекста запускаются на PostgreSQL
в Testcontainers, а без Docker пропускаются.

## Автор

[Вадим Харовюк](https://webscraft.org/blog/java-backend-rozrobnik-vadim-harovyuk) — Java backend-разработчик.

## Лицензия

[MIT](LICENSE)
