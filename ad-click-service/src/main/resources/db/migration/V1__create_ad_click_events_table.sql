-- Create ad_click_events table with partitioning by campaign_id and event date
-- Uses BRIN indexes for time-series scans

CREATE TABLE IF NOT EXISTS ad_click_events (
    id BIGSERIAL,
    event_id UUID NOT NULL,
    ad_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, campaign_id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Create unique index on event_id for idempotency
CREATE UNIQUE INDEX idx_event_id ON ad_click_events (event_id, timestamp);

-- Create BRIN indexes for time-series scans
CREATE INDEX idx_campaign_timestamp ON ad_click_events USING BRIN (campaign_id, timestamp);
CREATE INDEX idx_ad_timestamp ON ad_click_events USING BRIN (ad_id, timestamp);
CREATE INDEX idx_event_type ON ad_click_events (event_type);

-- Create index on JSONB metadata for flexible querying
CREATE INDEX idx_metadata_gin ON ad_click_events USING GIN (metadata);

-- Create initial partitions (last 30 days + next 7 days)
DO $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    FOR i IN -30..7 LOOP
        partition_date := CURRENT_DATE + (i || ' days')::INTERVAL;
        partition_name := 'ad_click_events_' || TO_CHAR(partition_date, 'YYYY_MM_DD');
        start_date := partition_date;
        end_date := partition_date + INTERVAL '1 day';

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF ad_click_events FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
    END LOOP;
END $$;

-- Create function to auto-create future partitions
CREATE OR REPLACE FUNCTION create_partition_if_not_exists()
RETURNS TRIGGER AS $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_date TIMESTAMP WITH TIME ZONE;
    end_date TIMESTAMP WITH TIME ZONE;
BEGIN
    partition_date := DATE(NEW.timestamp);
    partition_name := 'ad_click_events_' || TO_CHAR(partition_date, 'YYYY_MM_DD');
    start_date := partition_date;
    end_date := partition_date + INTERVAL '1 day';

    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF ad_click_events FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-create partitions
CREATE TRIGGER trigger_create_partition
BEFORE INSERT ON ad_click_events
FOR EACH ROW
EXECUTE FUNCTION create_partition_if_not_exists();

-- Create updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_updated_at
BEFORE UPDATE ON ad_click_events
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
