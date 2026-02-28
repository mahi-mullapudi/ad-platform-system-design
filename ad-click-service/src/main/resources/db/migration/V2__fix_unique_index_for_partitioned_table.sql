-- Fix: unique index on partitioned table must include all partition key columns.
-- V1 tried to create: CREATE UNIQUE INDEX idx_event_id ON ad_click_events (event_id)
-- which fails because the table is partitioned by 'timestamp'.
-- This migration drops the failed index (if it exists) and recreates it with 'timestamp' included.

DROP INDEX IF EXISTS idx_event_id;
CREATE UNIQUE INDEX IF NOT EXISTS idx_event_id ON ad_click_events (event_id, timestamp);
