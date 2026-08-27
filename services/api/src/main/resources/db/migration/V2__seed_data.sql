-- Seed catalog, fabrics, and default admin (password: Admin@12345)
-- BCrypt hash for Admin@12345

INSERT INTO users (email, password_hash, full_name, phone, role, enabled)
VALUES (
  'admin@menswear.local',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'Store Admin',
  '923001234567',
  'ADMIN',
  TRUE
);

INSERT INTO categories (name, slug, description) VALUES
  ('Kameez Shalwar', 'kameez-shalwar', 'Traditional men''s kameez shalwar suits'),
  ('Kurta', 'kurta', 'Kurta pajama and zipper kurtas'),
  ('Waistcoat', 'waistcoat', 'Formal and traditional waistcoats'),
  ('Accessories', 'accessories', 'Cufflinks and finishing pieces');

INSERT INTO fabric_tiers (code, name, surcharge_paisa, sort_order) VALUES
  ('GOLD', 'Gold', 0, 1),
  ('PLATINUM', 'Platinum', 150000, 2),
  ('DIAMOND', 'Diamond', 300000, 3),
  ('EXCLUSIVE', 'Exclusive', 500000, 4),
  ('SUITING', 'Suiting', 200000, 5);

INSERT INTO fabric_colors (fabric_tier_id, code, name, hex_color)
SELECT id, 'BLK-01', 'Elegant Black', '#111111' FROM fabric_tiers WHERE code = 'GOLD'
UNION ALL
SELECT id, 'WHT-01', 'Crystal White', '#F5F5F5' FROM fabric_tiers WHERE code = 'GOLD'
UNION ALL
SELECT id, 'NVY-01', 'Royal Navy', '#1B2A4A' FROM fabric_tiers WHERE code = 'PLATINUM'
UNION ALL
SELECT id, 'OLV-01', 'Olive Wash & Wear', '#556B2F' FROM fabric_tiers WHERE code = 'DIAMOND';

INSERT INTO products (category_id, name, slug, description, base_price_paisa, supports_custom, active)
SELECT c.id,
       'Elegant Black Kameez Shalwar',
       'elegant-black-kameez-shalwar',
       'Wash & wear made-to-measure kameez shalwar. Feather-light hand finishing.',
       850000,
       TRUE,
       TRUE
FROM categories c WHERE c.slug = 'kameez-shalwar';

INSERT INTO products (category_id, name, slug, description, base_price_paisa, supports_custom, active)
SELECT c.id,
       'Zipper Kurta Pajama',
       'zipper-kurta-pajama',
       'Modern zipper kurta with traditional pajama — forget buttons.',
       720000,
       TRUE,
       TRUE
FROM categories c WHERE c.slug = 'kurta';

INSERT INTO products (category_id, name, slug, description, base_price_paisa, supports_custom, active)
SELECT c.id,
       'Nawabi Gala Pashtun Kameez Shalwar',
       'nawabi-gala-pashtun-kameez-shalwar',
       'Traditional Pashtun nawabi gala cut, custom tailored.',
       950000,
       TRUE,
       TRUE
FROM categories c WHERE c.slug = 'kameez-shalwar';

INSERT INTO products (category_id, name, slug, description, base_price_paisa, supports_custom, active)
SELECT c.id,
       'Classic Waistcoat',
       'classic-waistcoat',
       'Structured waistcoat for formal traditional looks.',
       450000,
       TRUE,
       TRUE
FROM categories c WHERE c.slug = 'waistcoat';

INSERT INTO product_images (product_id, url, sort_order, alt_text)
SELECT id, 'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800', 0, name
FROM products;
