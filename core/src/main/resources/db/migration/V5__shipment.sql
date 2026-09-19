-- Phase 1.7: the laboratory ships the aligners. One row per order (re-shipments come later).

CREATE TABLE shipment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    order_id         UUID NOT NULL UNIQUE REFERENCES treatment_order(id),
    carrier          VARCHAR(80),                 -- Servientrega, Coordinadora, DHL...
    tracking_number  VARCHAR(120),
    notes            TEXT,
    shipped_by       UUID REFERENCES person(id),
    shipped_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE shipment ENABLE ROW LEVEL SECURITY;
ALTER TABLE shipment FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON shipment FOR ALL
    USING (app_bypass_rls() OR tenant_id = app_tenant_id())
    WITH CHECK (app_bypass_rls() OR tenant_id = app_tenant_id());
