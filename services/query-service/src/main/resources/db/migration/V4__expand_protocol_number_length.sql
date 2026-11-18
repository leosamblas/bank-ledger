-- V4__expand_protocol_number_length.sql
-- Expand protocol_number column length to accommodate canonical UUID strings (36 characters)

ALTER TABLE statement_entry
    ALTER COLUMN protocol_number TYPE VARCHAR(64);
