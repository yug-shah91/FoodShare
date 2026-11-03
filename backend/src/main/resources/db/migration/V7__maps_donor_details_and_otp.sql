-- Add donor profile fields to users table
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
ALTER TABLE users ADD COLUMN organization_name VARCHAR(100);
ALTER TABLE users ADD COLUMN donor_type VARCHAR(50);
ALTER TABLE users ADD COLUMN badge VARCHAR(60);
ALTER TABLE users ADD COLUMN rating DOUBLE DEFAULT 4.9;
ALTER TABLE users ADD COLUMN karma_points INT DEFAULT 100;

-- Add map coordinates, dietary type, and pickup verification OTP to donations table
ALTER TABLE donations ADD COLUMN latitude DOUBLE;
ALTER TABLE donations ADD COLUMN longitude DOUBLE;
ALTER TABLE donations ADD COLUMN dietary_type VARCHAR(30) DEFAULT 'VEG';
ALTER TABLE donations ADD COLUMN pickup_otp VARCHAR(6);
ALTER TABLE donations ADD COLUMN completed_at TIMESTAMP;
ALTER TABLE donations ADD COLUMN rating INT;
ALTER TABLE donations ADD COLUMN feedback VARCHAR(300);
