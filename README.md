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
| PostgreSQL | `localhost:5433` (db `orthiva`) — puerto 5433 para no chocar con un PostgreSQL nativo en 5432 | `orthiva` / `orthiva_dev` |
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
| `GET /api/me` | Bearer JWT (Keycloak) | Identidad y roles del token |
| `POST /api/files/test` (multipart `file`) | Bearer JWT | Prueba de subida a MinIO; devuelve key y URL firmada |
| `GET /actuator/modulith` | Bearer JWT | Estructura de módulos |

Obtener un token para pruebas manuales (password grant, solo dev):

```bash
curl -X POST http://localhost:8180/realms/orthiva/protocol/openid-connect/token -d "client_id=orthiva-web" -d "grant_type=password" -d "scope=openid" -d "username=doctor@orthiva.local" -d "password=doctor123"
```

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

**Fase 0 (fundación)**: infraestructura, esquema de datos, seguridad OIDC, storage y esqueletos. Sin lógica de negocio todavía.
