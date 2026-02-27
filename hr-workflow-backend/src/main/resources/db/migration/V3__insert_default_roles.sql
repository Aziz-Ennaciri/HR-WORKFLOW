-- Insert default roles based on RoleName enum
-- Using ON CONFLICT to make this idempotent (safe to run multiple times)
INSERT INTO roles (name) VALUES ('ROLE_RH') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_ADMIN') ON CONFLICT (name) DO NOTHING;