-- Setup script for HomeCloud database and user in PostgreSQL
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = 'homecloud') THEN
      CREATE ROLE homecloud LOGIN PASSWORD 'Your_Secret';
   ELSE
      ALTER ROLE homecloud WITH PASSWORD 'Your_Secret';
   END IF;
END
$do$;

-- Create database if not exists
SELECT 'CREATE DATABASE homecloud OWNER homecloud'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'homecloud')\gexec

GRANT ALL PRIVILEGES ON DATABASE homecloud TO homecloud;
