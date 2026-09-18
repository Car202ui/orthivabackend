-- Runs once, on first start of the postgres container (empty data volume).
-- The application database ($POSTGRES_DB = orthiva) is created by the image itself,
-- owned by the superuser $POSTGRES_USER, which is used only for administration.

-- Extensions must be created by a superuser (Flyway's CREATE EXTENSION IF NOT EXISTS
-- then becomes a no-op for the app role).
CREATE EXTENSION IF NOT EXISTS vector;     -- RAG embeddings
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- Non-superuser role for the application. Superusers bypass row-level security, so
-- the core MUST connect with this role for tenant isolation to be enforced.
CREATE ROLE orthiva_app LOGIN PASSWORD 'orthiva_app_dev';
GRANT ALL PRIVILEGES ON DATABASE orthiva TO orthiva_app;
GRANT ALL ON SCHEMA public TO orthiva_app;
ALTER SCHEMA public OWNER TO orthiva_app;

-- Separate database for Keycloak so identity data never mixes with clinical data.
CREATE DATABASE keycloak;
