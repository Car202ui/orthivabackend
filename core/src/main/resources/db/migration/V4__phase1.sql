-- Phase 1 additions: user preferences, doctor licensing, tenant commercial settings, media thumbnails.

ALTER TABLE person
    ADD COLUMN locale            VARCHAR(5)  NOT NULL DEFAULT 'es',
    ADD COLUMN license_country   VARCHAR(2),
    ADD COLUMN verified_at       TIMESTAMPTZ,            -- lab marked the doctor's license as checked
    ADD COLUMN verified_by       UUID REFERENCES person(id),
    ADD COLUMN profile_completed BOOLEAN NOT NULL DEFAULT FALSE;

-- A doctor row is provisioned at first login before the profile is filled, so the
-- license is mandatory only once the profile is marked complete.
ALTER TABLE person DROP CONSTRAINT chk_person_doctor_license;
ALTER TABLE person ADD CONSTRAINT chk_person_doctor_license
    CHECK (person_type <> 'DOCTOR' OR NOT profile_completed OR license_number IS NOT NULL);

ALTER TABLE tenant
    ADD COLUMN currency         VARCHAR(3)    NOT NULL DEFAULT 'COP',
    ADD COLUMN diagnosis_price  NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN agreement_text   TEXT;                     -- current responsibility agreement shown at approval

ALTER TABLE treatment_order
    ADD COLUMN diagnosis_price  NUMERIC(12,2),            -- snapshot of tenant.diagnosis_price at submission
    ADD COLUMN currency         VARCHAR(3);

ALTER TABLE media_asset
    ADD COLUMN thumbnail_key VARCHAR(400);

UPDATE tenant SET diagnosis_price = 150000, agreement_text =
  'El doctor tratante declara que ha revisado el plan de tratamiento propuesto, que asume la responsabilidad clínica del tratamiento y que la información suministrada es veraz.'
WHERE slug = 'orthiva-dev';
