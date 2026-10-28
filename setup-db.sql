-- Setup script for HomeCloud database and user in PostgreSQL
-- IMPORTANT: Replace 'change_me_to_a_secure_database_password' with your strong random password before running.
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = 'homecloud') THEN
      CREATE ROLE homecloud LOGIN PASSWORD 'change_me_to_a_secure_database_password';
   ELSE
      ALTER ROLE homecloud WITH PASSWORD 'change_me_to_a_secure_database_password';
   END IF;
END
$do$;

-- Create database if not exists
SELECT 'CREATE DATABASE homecloud OWNER homecloud'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'homecloud')\gexec

GRANT ALL PRIVILEGES ON DATABASE homecloud TO homecloud;
