-- CreateRequest allows notes up to 300 characters, so the column must hold 300 (it was 255).
ALTER TABLE donations MODIFY notes VARCHAR(300);
