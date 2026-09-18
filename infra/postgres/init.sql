-- Runs once, on first start of the postgres container (empty data volume).
-- The application database ($POSTGRES_DB = orthiva) is created by the image itself.

-- Vector extension for the RAG embeddings (schema managed later by Flyway).
CREATE EXTENSION IF NOT EXISTS vector;

-- Separate database for Keycloak so identity data never mixes with clinical data.
CREATE DATABASE keycloak;
