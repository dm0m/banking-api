-- country now holds an ISO 3166-1 alpha-2 code ("FR") instead of a free-text name
ALTER TABLE addresses
    ALTER COLUMN country TYPE CHAR(2),
    ADD CONSTRAINT addresses_country_iso_code CHECK (country ~ '^[A-Z]{2}$');
