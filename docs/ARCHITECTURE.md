# Arquitectura del backend Orthiva

## 1. Estilo: monolito modular por capas

Un solo despliegue (`core`, Spring Boot 4) dividido en **módulos de negocio** verificados por
[Spring Modulith](https://spring.io/projects/spring-modulith). Cada módulo es un paquete bajo
`com.orthiva.core` y se comunica con los demás **solo** a través de su API pública (interfaz de
servicio, DTOs, enums y eventos) o mediante eventos de dominio. Entidades JPA, repositorios,
clientes externos y controladores son internos al módulo.

```
com.orthiva.core.<modulo>/
├── package-info.java        @ApplicationModule
├── <Modulo>Service.java      INTERFAZ pública (puerto de entrada)
├── *Dto.java, *Input.java    contratos de entrada/salida
├── enums y eventos           OrderStatus, Arch, MediaKind, OrderStatusChanged, PlanSent…
├── domain/                   entidades JPA y reglas de negocio        (interno)
├── application/              <Modulo>ServiceImpl y servicios de caso de uso (interno)
├── infrastructure/           persistence/ (repositorios JPA), keycloak/, storage/ (interno)
└── web/                      controladores REST                       (interno)
```

Regla que hace cumplir `ModularityTests` (`ApplicationModules.verify()`): **solo el paquete raíz
del módulo es visible desde otros módulos**. Importar `com.orthiva.core.order.domain.TreatmentOrder`
desde `planning` rompe el build. Tampoco se permiten ciclos entre módulos.

## 2. Módulos y dependencias

```mermaid
flowchart LR
  shared[(shared\nconfig · tenant · web · persistence)]
  identity[identity\nIdentityService]
  file[file\nMediaService]
  patient[patient\nPatientService]
  order[order\nOrderService]
  planning[planning\nPlanningService]
  payment[payment\nPaymentService]
  notification[notification\n(1.8)]

  patient --> identity
  order --> identity
  order --> patient
  order --> file
  planning --> order
  planning --> file
  planning --> identity
  payment --> order
  payment -. escucha OrderStatusChanged .-> order
  notification -. escucha eventos .-> order
  notification -. escucha eventos .-> planning
  identity --> shared
  patient --> shared
  order --> shared
  planning --> shared
  payment --> shared
  file --> shared
```

| Módulo | Responsabilidad | API pública (raíz del paquete) |
|---|---|---|
| `shared` (OPEN) | Seguridad OIDC, contexto de tenant + RLS, manejo de errores, `Address`/`BaseEntity` | Todo lo que contiene |
| `identity` | Personas (doctor, paciente, staff, admin), tenants, integración Keycloak, provisión al primer login | `IdentityService`, `PersonDto`, `TenantDto`, `ProfileInput`, `PersonType`, `Gender` |
| `patient` | Vínculo doctor↔paciente con historial, clínicas | `PatientService`, `PatientDto`, `PatientInput`, `ClinicDto`, `ClinicInput` |
| `file` | Archivos en object storage (MinIO/S3), compresión y miniaturas, URLs firmadas | `MediaService`, `MediaDto`, `MediaKind`, `MediaOwner` |
| `order` | Prescripciones y **máquina de estados** de la orden | `OrderService`, `OrderDto`, `OrderInput`, `OrderStatus`, `Arch`, evento `OrderStatusChanged` |
| `planning` | Planes de tratamiento del laboratorio (versiones, etapas, precios, archivos) | `PlanningService`, `PlanDto`, `PlanInput`, evento `PlanSent` |
| `payment` | Cobros (diagnóstico, tratamiento), pasarelas | `PaymentService`, `PaymentDto`, `PaymentPurpose`, `PaymentStatus` |
| `notification` | Correos por hitos (entrega 1.8) | — |

Los diagramas generados por Modulith (C4/PlantUML) están en `docs/modulith/` y se regeneran con
`mvnw test` (`target/spring-modulith-docs`).

## 3. Reglas de diseño

1. **Un módulo nunca importa `domain/`, `application/`, `infrastructure/` ni `web/` de otro.** Si
   necesita algo, se agrega un método a la interfaz pública del módulo dueño (p. ej.
   `IdentityService.namesOf(ids)` en lugar de usar `PersonRepository`).
2. **Los servicios devuelven DTOs, nunca entidades.** El mapeo se hace dentro de la transacción
   (`@Transactional` en la implementación) para que las asociaciones perezosas ya estén resueltas.
3. **Cambios de estado de la orden solo vía `OrderWorkflow`** (interno de `order`), que valida la
   transición contra `OrderStatus`, escribe `order_status_history` y publica `OrderStatusChanged`.
4. **Reacciones entre módulos por eventos** (`@ApplicationModuleListener`): se ejecutan tras el
   commit, en otro hilo, usando el registro de publicaciones (`event_publication`) para no perder
   eventos. Como no hay petición HTTP, corren dentro de `PlatformScope` (RLS desactivado).
5. **Multi-tenant**: toda entidad clínica tiene `tenant_id`; `TenantAwareTransactionManager`
   ejecuta `set_config('app.tenant_id')` al abrir cada transacción y Postgres aplica Row-Level
   Security. Las operaciones de plataforma (provisión, admin, webhooks) usan `PlatformScope`.
6. **Autorización en dos niveles**: `@PreAuthorize` por rol en el controlador + reglas de
   propiedad en el servicio (doctor solo ve sus pacientes/órdenes; paciente solo las suyas;
   laboratorio todo el tenant).
7. **Errores**: `DomainException` → `problem+json` con `code` estable que el frontend traduce.

## 4. Flujo de la orden (máquina de estados)

```
DRAFT ─(DOCTOR)→ SUBMITTED ─(SYSTEM: pago)→ DIAGNOSIS_PAID ─(LAB)→ IN_PLANNING ─(LAB)→ PLAN_SENT
PLAN_SENT ─(DOCTOR)→ CHANGES_REQUESTED ─(LAB)→ IN_PLANNING | PLAN_SENT
PLAN_SENT ─(DOCTOR)→ APPROVED ─(SYSTEM: pago)→ TREATMENT_PAID ─(LAB)→ IN_PRODUCTION ─(LAB)→ SHIPPED
SHIPPED ─(DOCTOR)→ IN_FOLLOW_UP ─(DOCTOR|LAB)→ CLOSED        ·  REJECTED / CANCELLED
```

## 5. Cómo agregar un módulo nuevo

1. Crear `com.orthiva.core.<nombre>/package-info.java` con `@ApplicationModule`.
2. Definir en la raíz la interfaz `<Nombre>Service`, sus DTOs/inputs y eventos.
3. Implementar en `application/`, persistir en `infrastructure/persistence/`, exponer en `web/`.
4. Correr `mvnw test`: `ModularityTests` valida fronteras y regenera los diagramas.
