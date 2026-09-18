-- Orthiva baseline schema.
-- Redesign of the 2019 "alignerplus" MySQL model:
--   * binaries live in object storage (media_asset.storage_key), never in the DB
--   * passwords live in Keycloak, never here
--   * multi-tenant from day one (tenant = laboratory as SaaS customer), enforced by
--     application code AND PostgreSQL row-level security (see bottom)
--   * explicit order state machine with audit history
--   * clinical data is never physically deleted (deleted_at soft delete)

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- ---------------------------------------------------------------- enums
CREATE TYPE person_type AS ENUM ('ADMIN', 'DOCTOR', 'PATIENT', 'LAB', 'PLANNER', 'PRODUCTION', 'ACCOUNTING', 'REPRESENTATIVE');
CREATE TYPE gender AS ENUM ('F', 'M', 'X');
CREATE TYPE arch AS ENUM ('UPPER', 'LOWER', 'BOTH');
CREATE TYPE order_status AS ENUM (
    'DRAFT', 'SUBMITTED', 'DIAGNOSIS_PAID', 'IN_PLANNING', 'PLAN_SENT', 'CHANGES_REQUESTED',
    'APPROVED', 'TREATMENT_PAID', 'IN_PRODUCTION', 'SHIPPED', 'IN_FOLLOW_UP', 'CLOSED',
    'REJECTED', 'CANCELLED');
CREATE TYPE media_kind AS ENUM (
    'PHOTO_FRONTAL', 'PHOTO_PROFILE', 'PHOTO_SMILE',
    'PHOTO_INTRAORAL_UPPER', 'PHOTO_INTRAORAL_LOWER', 'PHOTO_INTRAORAL_RIGHT', 'PHOTO_INTRAORAL_LEFT', 'PHOTO_INTRAORAL_FRONTAL',
    'XRAY_PANORAMIC', 'XRAY_LATERAL',
    'VIDEO', 'STL', 'PDF',
    'MODEL3D_BEFORE_LEFT', 'MODEL3D_BEFORE_FRONTAL', 'MODEL3D_BEFORE_RIGHT',
    'MODEL3D_AFTER_LEFT', 'MODEL3D_AFTER_FRONTAL', 'MODEL3D_AFTER_RIGHT',
    'UPPER_BEFORE', 'UPPER_AFTER', 'UPPER_MOVEMENT', 'LOWER_BEFORE', 'LOWER_AFTER', 'LOWER_MOVEMENT',
    'FOLLOW_UP_PHOTO', 'AVATAR', 'OTHER');
CREATE TYPE payment_purpose AS ENUM ('DIAGNOSIS', 'TREATMENT');
CREATE TYPE payment_status AS ENUM ('PENDING', 'APPROVED', 'DECLINED', 'REFUNDED', 'ERROR');

-- ---------------------------------------------------------------- helpers
-- Tenant of the current request. The application runs
--   SET LOCAL app.tenant_id = '<uuid>'
-- inside every transaction; platform-level operations (admin, migrations) run
--   SET LOCAL app.bypass_rls = 'on'
CREATE OR REPLACE FUNCTION app_tenant_id() RETURNS UUID
    LANGUAGE sql STABLE AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

CREATE OR REPLACE FUNCTION app_bypass_rls() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$ SELECT COALESCE(current_setting('app.bypass_rls', true), 'off') = 'on' $$;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------- address
-- Reusable postal address. Rows referenced by plan_approval are snapshots: they are
-- created at approval time and must never be edited afterwards.
CREATE TABLE address (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country         VARCHAR(2)   NOT NULL,          -- ISO 3166-1 alpha-2
    state_province  VARCHAR(80),
    city            VARCHAR(80)  NOT NULL,
    postal_code     VARCHAR(20),
    line1           VARCHAR(200) NOT NULL,
    line2           VARCHAR(200),
    reference       VARCHAR(300),                   -- "second floor, next to the pharmacy"
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- tenant
CREATE TABLE tenant (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    address_id  UUID REFERENCES address(id),
    phone       VARCHAR(30),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- person
CREATE TABLE person (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID REFERENCES tenant(id),   -- null only for platform admins
    keycloak_user_id  UUID UNIQUE,                  -- null for patients that never log in
    person_type       person_type  NOT NULL,
    first_name        VARCHAR(80)  NOT NULL,
    last_name         VARCHAR(80)  NOT NULL,
    gender            gender,
    document_id       VARCHAR(30),                  -- national id; text, never int
    birth_date        DATE,
    email             VARCHAR(160),
    phone_country     VARCHAR(6),
    phone_number      VARCHAR(20),
    address_id        UUID REFERENCES address(id),
    specialty         VARCHAR(80),                  -- doctors
    license_number    VARCHAR(40),                  -- doctors
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, document_id),
    CONSTRAINT chk_person_doctor_license
        CHECK (person_type <> 'DOCTOR' OR license_number IS NOT NULL),
    CONSTRAINT chk_person_admin_tenant
        CHECK (person_type = 'ADMIN' OR tenant_id IS NOT NULL),
    CONSTRAINT chk_person_staff_login
        CHECK (person_type = 'PATIENT' OR keycloak_user_id IS NOT NULL)
);
CREATE INDEX idx_person_tenant ON person(tenant_id);
CREATE INDEX idx_person_type   ON person(tenant_id, person_type);
CREATE INDEX idx_person_email  ON person(email);

-- Doctor <-> patient relationship with history (a patient may change doctor or be
-- treated by several). Every treatment_order also records its own doctor.
CREATE TABLE doctor_patient (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenant(id),
    doctor_id   UUID NOT NULL REFERENCES person(id),
    patient_id  UUID NOT NULL REFERENCES person(id),
    since       DATE NOT NULL DEFAULT CURRENT_DATE,
    until       DATE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_doctor_patient_dates CHECK (until IS NULL OR until >= since)
);
CREATE UNIQUE INDEX uq_doctor_patient_active ON doctor_patient(doctor_id, patient_id) WHERE active;
CREATE INDEX idx_doctor_patient_patient ON doctor_patient(patient_id);
CREATE INDEX idx_doctor_patient_doctor  ON doctor_patient(doctor_id) WHERE active;

CREATE TABLE clinic (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    doctor_id       UUID NOT NULL REFERENCES person(id),
    name            VARCHAR(120) NOT NULL,
    website         VARCHAR(160),
    phone_country   VARCHAR(6),
    phone_number    VARCHAR(20),
    address_id      UUID REFERENCES address(id),
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_clinic_doctor ON clinic(doctor_id);

-- ---------------------------------------------------------------- orders
CREATE TABLE treatment_order (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL REFERENCES tenant(id),
    order_number           BIGSERIAL UNIQUE,           -- human-friendly consecutive
    doctor_id              UUID NOT NULL REFERENCES person(id),
    patient_id             UUID NOT NULL REFERENCES person(id),
    clinic_id              UUID REFERENCES clinic(id),
    status                 order_status NOT NULL DEFAULT 'DRAFT',
    first_time             BOOLEAN NOT NULL DEFAULT TRUE,
    reevaluation           BOOLEAN NOT NULL DEFAULT FALSE,
    arch                   arch NOT NULL,
    treatment_goal         TEXT,
    submitted_at           TIMESTAMPTZ,
    deleted_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_order_distinct_people CHECK (doctor_id <> patient_id)
);
CREATE INDEX idx_order_tenant_status ON treatment_order(tenant_id, status);
CREATE INDEX idx_order_doctor        ON treatment_order(doctor_id);
CREATE INDEX idx_order_patient       ON treatment_order(patient_id);

CREATE TABLE order_status_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    order_id      UUID NOT NULL REFERENCES treatment_order(id),
    from_status   order_status,
    to_status     order_status NOT NULL,
    changed_by    UUID REFERENCES person(id),
    note          TEXT,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_history_order ON order_status_history(order_id, changed_at);

-- Prescription details per tooth movement (torque, rotation, ...). One row per tooth (FDI).
CREATE TABLE tooth_movement_detail (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenant(id),
    order_id             UUID NOT NULL REFERENCES treatment_order(id),
    tooth_fdi            SMALLINT,                  -- 11..48, null = general instruction
    torque               VARCHAR(80),
    rotation             VARCHAR(80),
    buccolingual         VARCHAR(80),
    mesiodistal          VARCHAR(80),
    intrusion_extrusion  VARCHAR(80),
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_tooth_fdi CHECK (tooth_fdi IS NULL OR tooth_fdi BETWEEN 11 AND 48)
);
CREATE INDEX idx_tooth_movement_order ON tooth_movement_detail(order_id);

-- ---------------------------------------------------------------- planning
CREATE TABLE treatment_plan (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    order_id         UUID NOT NULL REFERENCES treatment_order(id),
    version          INT  NOT NULL DEFAULT 1,       -- doctor may request changes -> new version
    planner_id       UUID REFERENCES person(id),
    diagnosis        TEXT,
    additional_info  TEXT,
    upper_stages     INT,
    lower_stages     INT,
    price_upper      NUMERIC(12,2),
    price_lower      NUMERIC(12,2),
    price_total      NUMERIC(12,2),
    currency         VARCHAR(3) NOT NULL DEFAULT 'COP',
    stl_uploaded_at  TIMESTAMPTZ,
    sent_at          TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, version),
    CONSTRAINT chk_plan_version CHECK (version > 0),
    CONSTRAINT chk_plan_stages CHECK (
        (upper_stages IS NULL OR upper_stages >= 0) AND (lower_stages IS NULL OR lower_stages >= 0)),
    CONSTRAINT chk_plan_prices CHECK (
        (price_upper IS NULL OR price_upper >= 0) AND (price_lower IS NULL OR price_lower >= 0)
        AND (price_total IS NULL OR price_total >= 0))
);

CREATE TABLE treatment_stage (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    plan_id       UUID NOT NULL REFERENCES treatment_plan(id),
    stage_number  INT  NOT NULL,
    arch          arch NOT NULL,
    description   TEXT,
    cost          NUMERIC(12,2),
    UNIQUE (plan_id, stage_number, arch),
    CONSTRAINT chk_stage_number CHECK (stage_number > 0),
    CONSTRAINT chk_stage_arch CHECK (arch <> 'BOTH'),   -- one row per arch
    CONSTRAINT chk_stage_cost CHECK (cost IS NULL OR cost >= 0)
);

CREATE TABLE plan_approval (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL REFERENCES tenant(id),
    plan_id                UUID NOT NULL UNIQUE REFERENCES treatment_plan(id),
    approved_by            UUID NOT NULL REFERENCES person(id),
    ship_to_clinic_name    VARCHAR(120) NOT NULL,
    ship_address_id        UUID NOT NULL REFERENCES address(id),  -- snapshot row, immutable
    shipping_instructions  TEXT,
    agreement_text         TEXT NOT NULL,           -- snapshot of the accepted agreement
    agreement_accepted     BOOLEAN NOT NULL,
    approved_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_approval_accepted CHECK (agreement_accepted)
);

-- Doctor comments on a plan version (change requests) — replaces free-form emails.
CREATE TABLE plan_comment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenant(id),
    plan_id     UUID NOT NULL REFERENCES treatment_plan(id),
    author_id   UUID NOT NULL REFERENCES person(id),
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_plan_comment_plan ON plan_comment(plan_id, created_at);

-- ---------------------------------------------------------------- follow-up
CREATE TABLE follow_up (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    order_id         UUID NOT NULL REFERENCES treatment_order(id),
    visit_date       DATE NOT NULL,
    treatment_month  INT  NOT NULL,
    notes            TEXT,
    recorded_by      UUID REFERENCES person(id),
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_follow_up_month CHECK (treatment_month > 0)
);
CREATE INDEX idx_follow_up_order ON follow_up(order_id, treatment_month);

-- ---------------------------------------------------------------- media
-- One nullable FK per possible owner; exactly one must be set (no polymorphic FK).
CREATE TABLE media_asset (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    order_id      UUID REFERENCES treatment_order(id),
    plan_id       UUID REFERENCES treatment_plan(id),
    follow_up_id  UUID REFERENCES follow_up(id),
    person_id     UUID REFERENCES person(id),
    kind          media_kind NOT NULL,
    storage_key   VARCHAR(400) NOT NULL UNIQUE,     -- object key in the bucket
    file_name     VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(120) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    width_px      INT,
    height_px     INT,
    uploaded_by   UUID REFERENCES person(id),
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_media_single_owner CHECK (
        (order_id IS NOT NULL)::int + (plan_id IS NOT NULL)::int
        + (follow_up_id IS NOT NULL)::int + (person_id IS NOT NULL)::int = 1),
    CONSTRAINT chk_media_size CHECK (size_bytes > 0)
);
CREATE INDEX idx_media_order     ON media_asset(order_id)     WHERE order_id IS NOT NULL;
CREATE INDEX idx_media_plan      ON media_asset(plan_id)      WHERE plan_id IS NOT NULL;
CREATE INDEX idx_media_follow_up ON media_asset(follow_up_id) WHERE follow_up_id IS NOT NULL;
CREATE INDEX idx_media_person    ON media_asset(person_id)    WHERE person_id IS NOT NULL;

-- ---------------------------------------------------------------- payments
CREATE TABLE payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenant(id),
    order_id            UUID NOT NULL REFERENCES treatment_order(id),
    plan_id             UUID REFERENCES treatment_plan(id),
    purpose             payment_purpose NOT NULL,
    status              payment_status  NOT NULL DEFAULT 'PENDING',
    amount              NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    gateway             VARCHAR(40) NOT NULL,       -- WOMPI, PAYU, STRIPE, MOCK...
    gateway_reference   VARCHAR(120),
    gateway_payload     JSONB,                      -- raw webhook for audit
    payer_id            UUID REFERENCES person(id),
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_treatment_has_plan CHECK (purpose <> 'TREATMENT' OR plan_id IS NOT NULL)
);
CREATE INDEX idx_payment_order ON payment(order_id);
CREATE UNIQUE INDEX uq_payment_gateway_ref ON payment(gateway, gateway_reference) WHERE gateway_reference IS NOT NULL;
-- At most one approved payment per order and purpose (no double charging).
CREATE UNIQUE INDEX uq_payment_approved ON payment(order_id, purpose) WHERE status = 'APPROVED';

-- ---------------------------------------------------------------- catalogs
-- tenant_id NULL = global catalog; a tenant may add its own values.
CREATE TABLE domain_value (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID REFERENCES tenant(id),
    domain       VARCHAR(40)  NOT NULL,
    code         VARCHAR(40)  NOT NULL,
    label        VARCHAR(120) NOT NULL,
    parent_id    UUID REFERENCES domain_value(id),
    sort_order   INT NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_domain_value ON domain_value(COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'), domain, code);

-- ---------------------------------------------------------------- RAG
-- Chunks of clinical protocols, product docs and anonymised case notes used by the
-- AI assistant. Embedding size is FIXED at 768 (nomic-embed-text via Ollama).
-- Changing the embedding model to one with a different dimension requires a migration
-- that alters this column and a full re-index of the content.
CREATE TABLE knowledge_chunk (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID REFERENCES tenant(id),         -- null = shared/global knowledge
    source      VARCHAR(200) NOT NULL,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    metadata    JSONB,
    embedding   vector(768),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_embedding ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);

-- ---------------------------------------------------------------- updated_at triggers
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['tenant','person','clinic','treatment_order','treatment_plan','payment']
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
    END LOOP;
END $$;

-- ---------------------------------------------------------------- row-level security
-- Second barrier behind the application filter. FORCE makes it apply even to the table
-- owner (the app user). Rows with tenant_id NULL are global and visible to everyone.
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'person','doctor_patient','clinic','treatment_order','order_status_history',
        'tooth_movement_detail','treatment_plan','treatment_stage','plan_approval','plan_comment',
        'follow_up','media_asset','payment','domain_value','knowledge_chunk']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I FOR ALL
               USING (app_bypass_rls() OR tenant_id IS NULL OR tenant_id = app_tenant_id())
               WITH CHECK (app_bypass_rls() OR tenant_id IS NULL OR tenant_id = app_tenant_id())', t);
    END LOOP;
END $$;
