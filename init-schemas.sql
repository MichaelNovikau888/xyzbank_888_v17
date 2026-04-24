-- ═══════════════════════════════════════════════════════
-- XYZ-Bank — PostgreSQL Schema Initialization
-- Runs automatically on first docker-compose up
-- ═══════════════════════════════════════════════════════

-- Bank core schemas (EleventhProject services)
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS anti_fraud;
CREATE SCHEMA IF NOT EXISTS authorization;
CREATE SCHEMA IF NOT EXISTS history;
CREATE SCHEMA IF NOT EXISTS profile;
CREATE SCHEMA IF NOT EXISTS transfer;
CREATE SCHEMA IF NOT EXISTS public_bank_information;

-- FastPay schemas
CREATE SCHEMA IF NOT EXISTS fastpay;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS report;

-- Public Info schema (public-info-quarkus)
CREATE SCHEMA IF NOT EXISTS public_info;

-- Grant all to postgres user
DO $$
DECLARE
  s text;
BEGIN
  FOREACH s IN ARRAY ARRAY[
    'account','anti_fraud','authorization','history',
    'profile','transfer','public_bank_information',
    'fastpay','notification','report','public_info'
  ] LOOP
    EXECUTE format('GRANT ALL PRIVILEGES ON SCHEMA %I TO postgres', s);
    EXECUTE format('ALTER SCHEMA %I OWNER TO postgres', s);
  END LOOP;
END
$$;
