-- Development seed: a default tenant and the global catalogs the UI needs.

INSERT INTO address (id, country, state_province, city, line1)
VALUES ('00000000-0000-0000-0000-00000000a001', 'CO', 'Cundinamarca', 'Bogotá', 'Calle 1 # 1-1');

INSERT INTO tenant (id, name, slug, address_id)
VALUES ('00000000-0000-0000-0000-000000000001', 'Orthiva Lab (dev)', 'orthiva-dev', '00000000-0000-0000-0000-00000000a001');

-- Global catalogs (tenant_id NULL). Tenants may add their own rows later.
INSERT INTO domain_value (domain, code, label, sort_order) VALUES
    ('SPECIALTY', 'ORTHODONTICS',      'Ortodoncia',            1),
    ('SPECIALTY', 'GENERAL_DENTISTRY', 'Odontología general',   2),
    ('SPECIALTY', 'PROSTHODONTICS',    'Rehabilitación oral',   3),
    ('SPECIALTY', 'MAXILLOFACIAL',     'Cirugía maxilofacial',  4),

    ('PAYMENT_GATEWAY', 'WOMPI',  'Wompi',        1),
    ('PAYMENT_GATEWAY', 'PAYU',   'PayU',         2),
    ('PAYMENT_GATEWAY', 'STRIPE', 'Stripe',       3),
    ('PAYMENT_GATEWAY', 'MOCK',   'Mock (dev)',   99),

    ('COUNTRY', 'CO', 'Colombia',       1),
    ('COUNTRY', 'MX', 'México',         2),
    ('COUNTRY', 'PE', 'Perú',           3),
    ('COUNTRY', 'EC', 'Ecuador',        4),
    ('COUNTRY', 'CL', 'Chile',          5),
    ('COUNTRY', 'US', 'Estados Unidos', 6),
    ('COUNTRY', 'ES', 'España',         7);
