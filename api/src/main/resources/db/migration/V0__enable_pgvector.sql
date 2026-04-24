-- V0: enable pgvector extension before any schema that uses the vector type.
-- Kept in its own migration so a future cluster-wide switch (e.g. from
-- self-managed pgvector to Cloud SQL's built-in support) does not force
-- rewriting the schema DDL.

CREATE EXTENSION IF NOT EXISTS vector;
