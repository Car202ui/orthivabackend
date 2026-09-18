# Orthiva — Backend

Plataforma de planeación y gestión de alineadores dentales. Este repositorio contiene:

| Carpeta | Qué es | Tecnología |
|---|---|---|
| `infra/` | Servicios de infraestructura local | Docker Compose: PostgreSQL 16 + pgvector, MinIO, Redis, Keycloak |
| `core/` | API principal (monolito modular) | Java 21, Spring Boot 4, Spring Modulith, Flyway |
| `ai-service/` | Servicio de IA: visión, RAG, mallas 3D | Python 3.12, FastAPI, Ollama |

## Requisitos

- Docker Desktop (con WSL 2)
- Java 21 (Maven se descarga solo vía `mvnw`)
- Python 3.12
- [Ollama](https://ollama.com) instalado y corriendo (`ollama serve`)

## 1. Infraestructura

```bash
cd infra
copy .env.example .env
docker compose up -d
```

| Servicio | URL | Credenciales (dev) |
|---|---|---|
| PostgreSQL (admin) | `localhost:5433` (db `orthiva`) — puerto 5433 para no chocar con un PostgreSQL nativo en 5432 | `orthiva` / `orthiva_dev` (superusuario, solo administración) |
| PostgreSQL (app) | misma BD | `orthiva_app` / `orthiva_app_dev` — rol **no** superusuario que usa el core; obligatorio para que el RLS multi-tenant aplique |
| MinIO API / consola | `http://localhost:9000` / `http://localhost:9001` | `orthiva` / `orthiva_dev_minio` |
| Redis | `localhost:6379` | — |
| Keycloak | `http://localhost:8180` | admin: `admin` / `admin` |

El realm `orthiva` se importa automáticamente con estos usuarios de prueba:

| Usuario | Clave | Roles |
|---|---|---|
| `admin@orthiva.local` | `admin123` | ADMIN |
| `doctor@orthiva.local` | `doctor123` | DOCTOR |
| `lab@orthiva.local` | `lab123` | LAB, PLANNER |
| `patient@orthiva.local` | `patient123` | PATIENT |

## 2. Core (Spring Boot)

```bash
cd core
mvnw spring-boot:run
```

Arranca en `http://localhost:8080`. Flyway aplica las migraciones de `src/main/resources/db/migration` al iniciar.

> **Windows:** si `%TEMP%` es una ruta corta (p. ej. `C:\Users\DELL-P~1\...`), Tomcat falla con
> "Unable to establish loopback connection". El perfil Maven `windows` (automático) ya pasa
> `-Djdk.net.unixdomain.tmpdir=target`. Para ejecutar el jar directamente:
> `java -Djdk.net.unixdomain.tmpdir=C:\Temp -jar target\core-0.0.1-SNAPSHOT.jar`

| Endpoint | Auth | Descripción |
|---|---|---|
| `GET /api/health` | pública | Liveness |
| `GET /api/me` | Bearer JWT | Identidad (token + `person` + tenant); `onboardingRequired=true` si el usuario aún no eligió DOCTOR/PATIENT |
| `POST /api/me/onboarding` | Bearer JWT | Usuario auto-registrado elige tipo y completa perfil; el core asigna el rol en Keycloak |
| `GET/PUT /api/me/profile` | Bearer JWT | Perfil de la persona (doctores: licencia obligatoria) |
| `GET/POST /api/admin/users` | rol ADMIN | Lista / crea usuarios internos (LAB, PLANNER, PRODUCTION, ACCOUNTING, REPRESENTATIVE) vía Keycloak Admin API |
| `GET/POST /api/clinics`, `PUT/DELETE /api/clinics/{id}` | rol DOCTOR | Clínicas del doctor (borrado lógico) |
| `GET /api/patients?q=`, `POST`, `GET/PUT /api/patients/{id}` | rol DOCTOR | Pacientes del doctor; un email ya existente en el tenant se **vincula** en vez de duplicarse |
| `GET /api/portal/doctors` | rol PATIENT | Equipo tratante del paciente |
| `GET/POST /api/orders`, `GET/PUT /api/orders/{id}`, `POST …/media?kind=`, `DELETE …/media/{mediaId}`, `POST …/submit`, `POST …/cancel` | DOCTOR (lectura también LAB/PATIENT) | Prescripciones: borrador → archivos (fotos comprimidas a JPG + miniatura, STL/PDF/video) → envío al laboratorio |
| `GET /api/media/{id}` | Bearer JWT | URLs firmadas frescas de un archivo (tenant) |
| `GET /api/payments?orderId=` | Bearer JWT | Pagos de una orden (el de diagnóstico se crea en PENDING al enviar la orden) |

### Máquina de estados de la orden (`OrderStatus`)

`DRAFT → SUBMITTED → DIAGNOSIS_PAID → IN_PLANNING → PLAN_SENT → (CHANGES_REQUESTED ↔) APPROVED → TREATMENT_PAID → IN_PRODUCTION → SHIPPED → IN_FOLLOW_UP → CLOSED`, más `REJECTED` / `CANCELLED`.
Cada transición tiene los roles que pueden ejecutarla (o `SYSTEM` para las que disparan los pagos). Solo `OrderWorkflowService` cambia el estado: valida, escribe `order_status_history` y publica `OrderStatusChanged` (registro de eventos de Modulith, `event_publication`), al que reaccionan `payment` (y luego `notification`).
| `GET /actuator/modulith` | Bearer JWT | Estructura de módulos |

### Identidad y multi-tenant (cómo funciona)

1. Keycloak autentica; el core valida el JWT (`SecurityConfig`).
2. `TenantFilter` → `IdentityService.resolve()` convierte el token en un `TenantContext.Actor` (persona + tenant + roles). Al primer login con rol, la persona se **provisiona** en el tenant por defecto (`orthiva.default-tenant-slug`).
3. `TenantAwareTransactionManager` ejecuta `set_config('app.tenant_id', …, true)` al abrir cada transacción, de modo que el **RLS** de Postgres filtra por tenant sin que los repositorios lo sepan. `PlatformScope.run()` ejecuta trabajo con RLS desactivado (provisión, admin).
4. Un usuario que se registra solo no tiene rol: el frontend lo lleva a **onboarding**, elige DOCTOR/PATIENT, y el core (service account `orthiva-core`, permisos `manage-users`/`view-realm`) le asigna el rol. El frontend renueva el token en silencio.
5. Los usuarios internos los crea un ADMIN con clave temporal; Keycloak exige cambiarla en el primer ingreso.

Servicio SMTP de desarrollo: **Mailpit** en `http://localhost:8025` (los correos llegan en la entrega 1.8).

Obtener un token para pruebas manuales (password grant, solo dev):

```bash
curl -X POST http://localhost:8180/realms/orthiva/protocol/openid-connect/token -d "client_id=orthiva-web" -d "grant_type=password" -d "scope=openid" -d "username=doctor@orthiva.local" -d "password=doctor123"
```

### Esquema de datos (Flyway `V1__baseline.sql`)

- Multi-tenant: toda tabla clínica tiene `tenant_id`; **Row-Level Security** activo (`FORCE`) con la política
  `tenant_id IS NULL OR tenant_id = app_tenant_id()`. La aplicación debe ejecutar `SET LOCAL app.tenant_id = '<uuid>'`
  en cada transacción; operaciones de plataforma usan `SET LOCAL app.bypass_rls = 'on'`.
- Direcciones normalizadas en `address`; las de envío (`plan_approval.ship_address_id`) son snapshots inmutables.
- Doctor ↔ paciente N:M con historial (`doctor_patient`).
- `media_asset` con una FK por dueño (orden, plan, seguimiento, persona) y `CHECK` de un solo dueño.
- Un solo pago aprobado por orden y propósito (`uq_payment_approved`).
- Borrado lógico (`deleted_at`) en tablas clínicas.
- Embeddings fijos a `vector(768)` (nomic-embed-text); cambiar de modelo implica migración y re-indexado.

### Módulos (`com.orthiva.core.*`)

`shared` (config, seguridad, errores) · `identity` · `patient` · `order` · `planning` · `payment` · `file` · `notification`

Cada módulo es un paquete con `package-info.java` anotado con `@ApplicationModule`; Spring Modulith verifica que no haya dependencias cíclicas.

## 3. Servicio de IA (FastAPI)

```bash
cd ai-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8000
```

Documentación interactiva en `http://localhost:8000/docs`.

Modelos de Ollama esperados (descargar con `ollama pull <modelo>`):

| Uso | Modelo | Tamaño aprox. |
|---|---|---|
| Chat / RAG | `llama3.2:3b` | 2 GB |
| Visión (fotos) | `qwen2.5vl:7b` | 6 GB |
| Embeddings (pgvector, 768 dims) | `nomic-embed-text` | 270 MB |

`GET /v1/llm/health` indica cuáles están disponibles.

## Estado

- **Fase 0 (fundación)** ✅: infraestructura, esquema de datos, seguridad OIDC, storage y esqueletos.
- **Fase 1 (MVP transaccional)** en curso:
  - 1.1 Identidad, tenant y menús ✅
  - 1.2 Doctores, clínicas y pacientes ✅
  - 1.3 Prescripción y órdenes ✅ (máquina de estados, archivos con miniaturas, pago de diagnóstico PENDING vía evento)
  - 1.4 Planeación · 1.4 Planeación · 1.5 Aprobación · 1.6 Pagos · 1.7 Producción y seguimiento · 1.8 Notificaciones · 1.9 Calidad
