# Orthiva — estado del proyecto (2026-09-18, fin de jornada)

## Dónde estamos

**Fase 1 (MVP transaccional): 1.1 → 1.8 completas.** Falta solo **1.9 Calidad y cierre**.

| Entrega | Backend | Frontend | Verificación |
|---|---|---|---|
| Refactor arquitectura (capas + features + reglas) | `e2e7ee9` | `78bac8a` | `ModularityTests`, `tsc`/`lint`/`build` |
| 1.5 Aprobación del plan (comentarios, aprobar, rechazar) | `cd9bf2a` | `131bd25` | `test-approval.ps1` |
| 1.6 Pagos (PaymentGateway: Mock + Wompi, checkout, webhooks) | `0fbf1d0` | `e49cbb2` | `test-payments.ps1` + página mock en navegador |
| 1.7 Producción, envío, controles (módulo `followup`, V5) | `bbd8624` | `1a54d64` | `test-fulfillment.ps1` (orden #4 llegó a CLOSED) |
| 1.8 Notificaciones por correo (módulo `notification`, Mailpit) | `657d5b1` | — | `test-notifications.ps1` (29 correos, orden #9) |

El flujo de negocio completo funciona de punta a punta por API:
`DRAFT → SUBMITTED → DIAGNOSIS_PAID → IN_PLANNING → PLAN_SENT → (CHANGES_REQUESTED → IN_PLANNING → PLAN_SENT) → APPROVED → TREATMENT_PAID → IN_PRODUCTION → SHIPPED → IN_FOLLOW_UP → CLOSED`, con `REJECTED`/`CANCELLED` como terminales.

## Pendiente

1. **Recorrido visual en navegador** de 1.5–1.7 (doctor → laboratorio → paciente). Requiere que el usuario haga login en el panel (yo no escribo contraseñas). Todo lo demás (tipos, lint, build, API) está verificado.
2. **1.9 Calidad y cierre** (plan aprobado en conversación, pendiente de "arranca"):
   - Tests con Testcontainers (Postgres pgvector + Flyway real): máquina de estados por rol, RLS con dos tenants, webhook Wompi (checksum), listener de pagos. JWT simulado con `spring-security-test`.
   - GitHub Actions: backend `mvnw verify` (Docker para Testcontainers); frontend `npm ci`, `lint`, `tsc`, `next build`.
   - READMEs y `docs/ARCHITECTURE.md` con los módulos nuevos (`followup`, `notification`), tag `v0.1.0-mvp` en ambos repos.
3. Cuando el usuario tenga cuenta Wompi sandbox: poner `WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET` en el entorno del core; el gateway se activa solo. Para el webhook real hace falta túnel (ngrok/cloudflared) o probar con `test-payments.ps1` (evento firmado).
4. Después: Fase 2 (IA: visión sobre fotos, LLM+RAG con Ollama, STL) y Fase 3 (multi-laboratorio).

## Cómo retomar

- Infra: `docker compose up -d` en `infra/` (Postgres 5433, Keycloak 8180, MinIO 9000, Redis, Mailpit 8025).
- Core: el usuario lo corre desde IntelliJ en 8080. Para pruebas paralelas: `java -Djdk.net.unixdomain.tmpdir=C:\Temp\orthiva -Dserver.port=8081 -jar core\target\core-0.0.1-SNAPSHOT.jar` (antes `mvnw -o -DskipTests package`).
- Frontend: `npm run dev` en `Orthivafrotend` (`.env.local` apunta a 8080; para probar contra 8081 cambiar `NEXT_PUBLIC_API_URL` y **restaurar** al terminar).
- Usuarios de prueba (realm `orthiva`): `doctor@orthiva.local`, `nuevo.doctor@orthiva.local`, `lab@orthiva.local`, `planner1@…`, `produccion1@…`, `nuevo.paciente@orthiva.local`; paciente con correo `laura.gomez@example.com` está vinculado al doctor de prueba.
- Scripts de prueba API (PowerShell, contra 8081) en el scratchpad de la sesión: `test-identity/patients/orders/planning/approval/payments/fulfillment/notifications.ps1`.
- Git: commits como `Car202ui <caiglesias.desarrollador@gmail.com>`; si el push devuelve 403, `gh auth switch --user Car202ui`.

## Decisiones tomadas hoy (usuario)

- Rechazo del plan incluido, con motivo obligatorio.
- Acuerdo de responsabilidad provisional (ya seed en V4), editable por tenant sin código.
- Sin cuenta Wompi aún → Mock por defecto; Wompi listo y activado por variables de entorno; webhook simulado sin túnel; checkout por redirección.
- Envío con transportadora y guía opcionales; controles con fecha, mes, notas y fotos.
- Regla de fronteras del frontend estricta (una feature no importa otra): `OrderForm` recibe pacientes/clínicas por props desde la página.
