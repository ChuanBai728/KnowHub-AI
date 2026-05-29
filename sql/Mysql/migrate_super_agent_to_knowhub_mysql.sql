-- Migrate legacy Super Agent MySQL data into KnowHub AI.
-- Run after creating the target database and tables with create_database_mysql.sql
-- and create_table_mysql.sql.

CREATE DATABASE IF NOT EXISTS knowhub_business_chat
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE knowhub_business_chat;

SET @legacy_schema = 'super_agent_business_chat';
SET @target_schema = 'knowhub_business_chat';

-- Copy data table-by-table when the legacy table exists. The dynamic SQL keeps
-- this script idempotent for installations that only used part of the old app.

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_chat_dialogue'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_chat_dialogue SELECT * FROM super_agent_business_chat.super_agent_chat_dialogue',
  'SELECT ''skip super_agent_chat_dialogue'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_chat_exchange'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_chat_exchange SELECT * FROM super_agent_business_chat.super_agent_chat_exchange',
  'SELECT ''skip super_agent_chat_exchange'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_chat_memory_summary'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_chat_memory_summary SELECT * FROM super_agent_business_chat.super_agent_chat_memory_summary',
  'SELECT ''skip super_agent_chat_memory_summary'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_chat_exchange_trace_stage'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_chat_exchange_trace_stage SELECT * FROM super_agent_business_chat.super_agent_chat_exchange_trace_stage',
  'SELECT ''skip super_agent_chat_exchange_trace_stage'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document SELECT * FROM super_agent_business_chat.super_agent_document',
  'SELECT ''skip super_agent_document'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_task'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_task SELECT * FROM super_agent_business_chat.super_agent_document_task',
  'SELECT ''skip super_agent_document_task'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_task_log'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_task_log SELECT * FROM super_agent_business_chat.super_agent_document_task_log',
  'SELECT ''skip super_agent_document_task_log'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_profile'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_profile SELECT * FROM super_agent_business_chat.super_agent_document_profile',
  'SELECT ''skip super_agent_document_profile'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_strategy_plan'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_strategy_plan SELECT * FROM super_agent_business_chat.super_agent_document_strategy_plan',
  'SELECT ''skip super_agent_document_strategy_plan'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_strategy_step'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_strategy_step SELECT * FROM super_agent_business_chat.super_agent_document_strategy_step',
  'SELECT ''skip super_agent_document_strategy_step'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_parent_block'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_parent_block SELECT * FROM super_agent_business_chat.super_agent_document_parent_block',
  'SELECT ''skip super_agent_document_parent_block'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_structure_node'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_structure_node SELECT * FROM super_agent_business_chat.super_agent_document_structure_node',
  'SELECT ''skip super_agent_document_structure_node'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_document_chunk'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_document_chunk SELECT * FROM super_agent_business_chat.super_agent_document_chunk',
  'SELECT ''skip super_agent_document_chunk'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_knowledge_scope_node'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_knowledge_scope_node SELECT * FROM super_agent_business_chat.super_agent_knowledge_scope_node',
  'SELECT ''skip super_agent_knowledge_scope_node'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_knowledge_topic_node'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_knowledge_topic_node SELECT * FROM super_agent_business_chat.super_agent_knowledge_topic_node',
  'SELECT ''skip super_agent_knowledge_topic_node'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_topic_document_relation'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_topic_document_relation SELECT * FROM super_agent_business_chat.super_agent_topic_document_relation',
  'SELECT ''skip super_agent_topic_document_relation'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'super_agent_knowledge_route_trace'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.knowhub_knowledge_route_trace SELECT * FROM super_agent_business_chat.super_agent_knowledge_route_trace',
  'SELECT ''skip super_agent_knowledge_route_trace'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Optional graph checkpoint tables used by Spring AI Alibaba.
SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'GRAPH_THREAD'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.GRAPH_THREAD SELECT * FROM super_agent_business_chat.GRAPH_THREAD',
  'SELECT ''skip GRAPH_THREAD'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = @legacy_schema AND table_name = 'GRAPH_CHECKPOINT'
  ),
  'INSERT IGNORE INTO knowhub_business_chat.GRAPH_CHECKPOINT SELECT * FROM super_agent_business_chat.GRAPH_CHECKPOINT',
  'SELECT ''skip GRAPH_CHECKPOINT'' AS migration_step'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

