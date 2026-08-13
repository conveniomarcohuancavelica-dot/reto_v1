INSERT INTO inventory_items (id, product_id, product_name, stock, version, updated_at)
SELECT UUID(), 'PROD-001', 'Laptop 14 pulgadas', 15, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM inventory_items WHERE product_id = 'PROD-001');

INSERT INTO inventory_items (id, product_id, product_name, stock, version, updated_at)
SELECT UUID(), 'PROD-002', 'Mouse inalámbrico', 50, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM inventory_items WHERE product_id = 'PROD-002');

INSERT INTO inventory_items (id, product_id, product_name, stock, version, updated_at)
SELECT UUID(), 'PROD-003', 'Teclado mecánico', 0, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM inventory_items WHERE product_id = 'PROD-003');
