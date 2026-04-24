-- V3: switch article_embedding to Upstage solar-embedding-1-large (4096 dims).
--
-- Rationale in ADR-0003. Task 8 is where we first write embedding rows, so
-- the table is empty here — dropping and recreating the column is safe.
--
-- Indexing note: pgvector 0.8 caps both ivfflat and hnsw at 2000 dimensions
-- for the plain `vector` type. halfvec supports higher dimensions but also
-- requires the pgvector Java driver's halfvec class. For MVP (~1,500
-- article rows) a sequential scan over 4096-dim vectors takes <10ms, which
-- is well under our response budget. We drop the old index and leave the
-- table unindexed; when row count grows or recall becomes a bottleneck,
-- migrate to halfvec(4096) + HNSW via a follow-up migration.

-- Defensive: if this migration ever re-runs in a branched env, make sure
-- the vector column no longer holds 1536-dim rows.
DELETE FROM article_embedding;

DROP INDEX IF EXISTS article_embedding_ivfflat_idx;

ALTER TABLE article_embedding ALTER COLUMN vector TYPE vector(4096);

ALTER TABLE article_embedding ALTER COLUMN model SET DEFAULT 'upstage:solar-embedding-1-large-passage';

COMMENT ON COLUMN article_embedding.vector IS
    'Upstage solar-embedding-1-large-passage 4096-dim embedding. See ADR-0003.';
