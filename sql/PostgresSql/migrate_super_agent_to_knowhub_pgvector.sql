-- Migrate legacy Super Agent PGVector data into KnowHub AI.
-- Connect to the target database `knowhub_pgvector` before running this file.

CREATE EXTENSION IF NOT EXISTS vector;

DO $$
BEGIN
    IF to_regclass('public.super_agent_document_embedding') IS NOT NULL THEN
        INSERT INTO public.knowhub_document_embedding
        SELECT *
        FROM public.super_agent_document_embedding
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;

-- If the old installation used a separate database named super_agent_pgvector,
-- run this from psql after creating the dblink extension and adjusting the
-- connection string:
--
-- CREATE EXTENSION IF NOT EXISTS dblink;
-- INSERT INTO public.knowhub_document_embedding
-- SELECT *
-- FROM dblink(
--   'host=127.0.0.1 port=5432 dbname=super_agent_pgvector user=postgres password=postgres',
--   'SELECT * FROM public.super_agent_document_embedding'
-- ) AS old_embedding(
--   id BIGINT,
--   document_id BIGINT,
--   task_id BIGINT,
--   plan_id BIGINT,
--   parent_block_id BIGINT,
--   chunk_no INTEGER,
--   source_type SMALLINT,
--   section_path VARCHAR(1000),
--   structure_node_id BIGINT,
--   structure_node_type SMALLINT,
--   canonical_path VARCHAR(1000),
--   item_index INTEGER,
--   chunk_text TEXT,
--   char_count INTEGER,
--   token_count INTEGER,
--   embedding_model VARCHAR(128),
--   metadata_json JSONB,
--   embedding VECTOR,
--   create_time TIMESTAMP,
--   edit_time TIMESTAMP,
--   status SMALLINT
-- )
-- ON CONFLICT (id) DO NOTHING;
