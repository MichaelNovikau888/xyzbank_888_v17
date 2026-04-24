-- Тестовые email-шаблоны для notification-service
-- Файл: src/main/resources/import.sql
-- Шаблоны для платежей и переводов

-- ════════════════════════════════════════════════════════════════════════════
-- ПЛАТЕЖИ (payment-api)
-- ════════════════════════════════════════════════════════════════════════════

-- 1. CREATED - платёж создан
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('payment_created',
     '💳 Платёж создан - Example Bank',
     'Уважаемый клиент!

    Ваш платёж №{payment_id} на сумму {amount} {currency} успешно создан.
    Получатель: {recipient_account}

    Статус: СОЗДАН
    Платёж будет обработан в течение 1 рабочего дня.

    С уважением,
    Example Bank
    Служба поддержки: support@examplebank.ru',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );

-- 2. PROCESSING - платёж в обработке
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('payment_processing',
     '⏳ Платёж в обработке - Example Bank',
     'Уважаемый клиент!

    Ваш платёж №{payment_id} на сумму {amount} {currency} принят в обработку.

    Статус: В ОБРАБОТКЕ
    Ожидаемое время завершения: в течение 24 часов

    Вы получите уведомление сразу после завершения операции.

    С уважением,
    Example Bank
    Служба поддержки: support@examplebank.ru',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );

-- 3. COMPLETED - платёж успешно завершён
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('payment_completed',
     '✅ Платёж успешно завершён - Example Bank',
     'Уважаемый клиент!

    Ваш платёж №{payment_id} на сумму {amount} {currency} успешно завершён!

    Статус: ЗАВЕРШЁН ✅
    Получатель: {recipient_account}
    Дата и время: {completion_date}

    Средства зачислены на счёт получателя.

    Спасибо за использование наших услуг!

    С уважением,
    Example Bank
    Служба поддержки: support@examplebank.ru',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );

-- 4. FAILED - ошибка обработки платежа
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('payment_failed',
     '❌ Ошибка обработки платежа - Example Bank',
     'Уважаемый клиент!

    К сожалению, ваш платёж №{payment_id} на сумму {amount} {currency} не был обработан.

    Статус: ОШИБКА ❌
    Причина: {failure_reason}

    Пожалуйста, проверьте следующее:
    • Достаточность средств на счёте
    • Правильность реквизитов получателя
    • Отсутствие ограничений на операции

    Для решения проблемы свяжитесь с нашей службой поддержки:
    Email: support@examplebank.ru
    Телефон: 8-800-555-35-35 (круглосуточно)

    С уважением,
    Example Bank',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );

-- 5. CANCELLED - платёж отменён
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('payment_cancelled',
     '🚫 Платёж отменён - Example Bank',
     'Уважаемый клиент!

    Ваш платёж №{payment_id} на сумму {amount} {currency} был отменён.

    Статус: ОТМЕНЁН
    Причина отмены: {cancellation_reason}
    Дата отмены: {cancellation_date}

    Если отмена произошла по ошибке, вы можете создать новый платёж в любое время.

    С уважением,
    Example Bank
    Служба поддержки: support@examplebank.ru
    Телефон: 8-800-555-35-35',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );

-- ════════════════════════════════════════════════════════════════════════════
-- ПЕРЕВОДЫ (transfer-service)
-- Плейсхолдеры: {transfer_id}, {transfer_type}, {amount}, {currency},
--               {recipient_display}, {purpose}, {occurred_date},
--               {block_reason}, {cancel_reason}
-- ════════════════════════════════════════════════════════════════════════════

-- 6. transfer_completed — перевод успешно выполнен
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('transfer_completed',
     '✅ Перевод выполнен - Example Bank',
     'Уважаемый клиент!

Ваш перевод №{transfer_id} успешно выполнен.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Детали перевода
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Тип:          {transfer_type}
  Сумма:        {amount} {currency}
  Получатель:   {recipient_display}
  Назначение:   {purpose}
  Дата и время: {occurred_date}
  Статус:       ВЫПОЛНЕН ✅
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Средства зачислены получателю. Если у вас есть вопросы — обратитесь
в службу поддержки.

С уважением,
Example Bank
Email: support@examplebank.ru | Тел.: 8-800-555-35-35',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );

-- 7. transfer_blocked — перевод заблокирован антифродом
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('transfer_blocked',
     '❌ Перевод заблокирован - Example Bank',
     'Уважаемый клиент!

К сожалению, ваш перевод №{transfer_id} был заблокирован системой безопасности.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Детали перевода
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Тип:          {transfer_type}
  Сумма:        {amount} {currency}
  Получатель:   {recipient_display}
  Назначение:   {purpose}
  Дата и время: {occurred_date}
  Статус:       ЗАБЛОКИРОВАН ❌
  Причина:      {block_reason}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Что делать дальше:
  • Проверьте сумму перевода — крупные суммы требуют дополнительной верификации
  • Свяжитесь с нашей службой поддержки для разблокировки
  • Вы можете повторить перевод на меньшую сумму

Деньги не были списаны с вашего счёта.

С уважением,
Example Bank
Email: support@examplebank.ru | Тел.: 8-800-555-35-35 (круглосуточно)',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );

-- 8. transfer_cancelled — перевод отменён
INSERT INTO email_templates (name, subject, body, created_at, updated_at) VALUES
    ('transfer_cancelled',
     '🚫 Перевод отменён - Example Bank',
     'Уважаемый клиент!

Ваш перевод №{transfer_id} был отменён.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Детали перевода
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Тип:          {transfer_type}
  Сумма:        {amount} {currency}
  Получатель:   {recipient_display}
  Назначение:   {purpose}
  Дата и время: {occurred_date}
  Статус:       ОТМЕНЁН 🚫
  Причина:      {cancel_reason}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Если отмена произошла по ошибке или вы не инициировали её — немедленно
свяжитесь с нашей службой поддержки.

Деньги не были списаны с вашего счёта.

С уважением,
Example Bank
Email: support@examplebank.ru | Тел.: 8-800-555-35-35 (круглосуточно)',
     CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP
    );
