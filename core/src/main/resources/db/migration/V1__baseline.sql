-- Orthiva baseline schema.
-- Redesign of the 2019 "alignerplus" MySQL model:
--   * binaries live in object storage (media_asset.storage_key), never in the DB
--   * passwords live in Keycloak, never here
--   * multi-tenant from day one (tenant = laboratory as SaaS customer)
--   * explicit order state machine with audit history

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
    'FOLLOW_UP_PHOTO', 'OTHER');
CREATE TYPE media_owner AS ENUM ('ORDER', 'PLAN', 'FOLLOW_UP', 'PERSON');
CREATE TYPE payment_purpose AS ENUM ('DIAGNOSIS', 'TREATMENT');
CREATE TYPE payment_status AS ENUM ('PENDING', 'APPROVED', 'DECLINED', 'REFUNDED', 'ERROR');

-- ---------------------------------------------------------------- tenant
CREATE TABLE tenant (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    country     VARCHAR(2)   NOT NULL,          -- ISO 3166-1 alpha-2
    city        VARCHAR(80),
    address     VARCHAR(200),
    phone       VARCHAR(30),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- person
CREATE TABLE person (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID REFERENCES tenant(id),
    keycloak_user_id  UUID UNIQUE,                 -- null for patients that never log in
    person_type       person_type  NOT NULL,
    first_name        VARCHAR(80)  NOT NULL,
    last_name         VARCHAR(80)  NOT NULL,
    gender            gender,
    document_id       VARCHAR(30),                 -- national id; text, never int
    birth_date        DATE,
    email             VARCHAR(160),
    phone_country     VARCHAR(6),
    phone_number      VARCHAR(20),
    country           VARCHAR(2),
    state_province    VARCHAR(80),
    city              VARCHAR(80),
    postal_code       VARCHAR(20),
    specialty         VARCHAR(80),                 -- doctors
    license_number    VARCHAR(40),                 -- doctors
    doctor_id         UUID REFERENCES person(id),  -- patients: treating doctor
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, document_id)
);
CREATE INDEX idx_person_tenant  ON person(tenant_id);
CREATE INDEX idx_person_doctor  ON person(doctor_id);
CREATE INDEX idx_person_type    ON person(person_type);

CREATE TABLE clinic (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID REFERENCES tenant(id),
    doctor_id       UUID NOT NULL REFERENCES person(id),
    name            VARCHAR(120) NOT NULL,
    website         VARCHAR(160),
    phone_country   VARCHAR(6),
    phone_number    VARCHAR(20),
    country         VARCHAR(2),
    state_province  VARCHAR(80),
    city            VARCHAR(80),
    postal_code     VARCHAR(20),
    address         VARCHAR(200),
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
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_tenant_status ON treatment_order(tenant_id, status);
CREATE INDEX idx_order_doctor        ON treatment_order(doctor_id);
CREATE INDEX idx_order_patient       ON treatment_order(patient_id);

CREATE TABLE order_status_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES treatment_order(id) ON DELETE CASCADE,
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
    order_id             UUID NOT NULL REFERENCES treatment_order(id) ON DELETE CASCADE,
    tooth_fdi            SMALLINT,                  -- 11..48, null = general instruction
    torque               VARCHAR(80),
    rotation             VARCHAR(80),
    buccolingual         VARCHAR(80),
    mesiodistal          VARCHAR(80),
    intrusion_extrusion  VARCHAR(80),
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tooth_movement_order ON tooth_movement_detail(order_id);

-- ---------------------------------------------------------------- planning
CREATE TABLE treatment_plan (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL REFERENCES treatment_order(id) ON DELETE CASCADE,
    version          INT  NOT NULL DEFAULT 1,       -- doctor may request changes → new version
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
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, version)
);

CREATE TABLE treatment_stage (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id       UUID NOT NULL REFERENCES treatment_plan(id) ON DELETE CASCADE,
    stage_number  INT  NOT NULL,
    arch          arch NOT NULL,
    description   TEXT,
    cost          NUMERIC(12,2),
    UNIQUE (plan_id, stage_number, arch)
);

CREATE TABLE plan_approval (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id                UUID NOT NULL UNIQUE REFERENCES treatment_plan(id) ON DELETE CASCADE,
    approved_by            UUID NOT NULL REFERENCES person(id),
    ship_to_clinic_name    VARCHAR(120) NOT NULL,
    ship_country           VARCHAR(2)   NOT NULL,
    ship_state_province    VARCHAR(80),
    ship_city              VARCHAR(80)  NOT NULL,
    ship_postal_code       VARCHAR(20),
    ship_address           VARCHAR(200) NOT NULL,
    shipping_instructions  TEXT,
    agreement_text         TEXT NOT NULL,           -- snapshot of the accepted agreement
    agreement_accepted     BOOLEAN NOT NULL,
    approved_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Doctor comments on a plan version (change requests) — replaces free-form emails.
CREATE TABLE plan_comment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id     UUID NOT NULL REFERENCES treatment_plan(id) ON DELETE CASCADE,
    author_id   UUID NOT NULL REFERENCES person(id),
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_plan_comment_plan ON plan_comment(plan_id, created_at);

-- ---------------------------------------------------------------- follow-up
CREATE TABLE follow_up (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL REFERENCES treatment_order(id) ON DELETE CASCADE,
    visit_date       DATE NOT NULL,
    treatment_month  INT  NOT NULL,
    notes            TEXT,
    recorded_by      UUID REFERENCES person(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_follow_up_order ON follow_up(order_id, treatment_month);

-- ---------------------------------------------------------------- media
CREATE TABLE media_asset (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    owner_type    media_owner NOT NULL,
    owner_id      UUID NOT NULL,                    -- order / plan / follow_up / person id
    kind          media_kind NOT NULL,
    storage_key   VARCHAR(400) NOT NULL UNIQUE,     -- object key in the bucket
    file_name     VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(120) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    width_px      INT,
    height_px     INT,
    uploaded_by   UUID REFERENCES person(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_media_owner ON media_asset(owner_type, owner_id);

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
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_order ON payment(order_id);
CREATE INDEX idx_payment_gateway_ref ON payment(gateway, gateway_reference);

-- ---------------------------------------------------------------- catalogs
CREATE TABLE domain_value (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain       VARCHAR(40)  NOT NULL,
    code         VARCHAR(40)  NOT NULL,
    label        VARCHAR(120) NOT NULL,
    parent_id    UUID REFERENCES domain_value(id),
    sort_order   INT NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (domain, code)
);

-- ---------------------------------------------------------------- RAG
-- Chunks of clinical protocols, product docs and anonymised case notes used by the
-- AI assistant. Embedding size 768 matches nomic-embed-text (Ollama).
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

-- ---------------------------------------------------------------- updated_at trigger
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['tenant','person','clinic','treatment_order','treatment_plan','payment']
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
    END LOOP;
END $$;
