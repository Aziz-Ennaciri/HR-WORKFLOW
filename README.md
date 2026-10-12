# HR Workflow

A visual workflow-automation platform for HR teams. Users design workflows on a drag-and-drop canvas by chaining nodes — read CVs from a drive folder, analyse them with an LLM, build an Excel report, ask a manager for approval, and email the result — then run and monitor them in real time.

## Features

- **Visual workflow editor** built on React Flow: add, connect, configure and reorder nodes
- **Node types**
  | Node | What it does |
  |------|--------------|
  | **Drive** | Reads files (PDF, text…) from a folder, or writes the previous step's output to one |
  | **GPT / LLM** | Runs a prompt over the input with Ollama (local), OpenAI or Anthropic |
  | **Excel** | Generates an `.xlsx` report from structured data, or reads an existing spreadsheet |
  | **Approval** | Pauses the run until the assigned approver approves or rejects |
  | **Email** | Sends an HTML email with the results / a download link |
- **Execution engine** that runs asynchronously, tracks per-node status, inputs and outputs, and supports *retry from a failed node*
- **Approvals inbox** for assigned approvers
- **Authentication & authorization**: JWT, role-based access (`ROLE_ADMIN`, `ROLE_RH`), per-owner access to workflows and executions
- **Account settings**: profile, email/password change, preferences

## Tech stack

| Layer | Technologies |
|-------|--------------|
| Backend | Java 17, Spring Boot 3, Spring Security (JWT), Spring Data JPA, Flyway, MapStruct, Lombok, Apache POI, PDFBox |
| Database | PostgreSQL 15 |
| Frontend | Next.js 14, React 18, TypeScript, Tailwind CSS, React Flow, Recharts |
| Testing | JUnit 5, Mockito, Testcontainers (real PostgreSQL), MockMvc, JaCoCo |
| DevOps | Docker / Docker Compose, Jenkins, SonarQube |

## Architecture

```
┌──────────────────┐   REST + JWT   ┌──────────────────────────────────────────┐
│  Next.js (3000)  │ ─────────────▶ │  Spring Boot API (8080)                  │
│  workflow editor │                │                                          │
│  dashboard       │                │  controllers → services → repositories   │
│  approvals       │                │                 │                        │
└──────────────────┘                │  Execution engine ── NodeHandler per type│
                                    │     (Drive, GPT, Excel, Approval, Email) │
                                    └──────┬────────────┬───────────┬──────────┘
                                           │            │           │
                                     PostgreSQL     Ollama/LLM     SMTP
```

The backend is organised by feature: `user`, `workflow` (definitions: workflows + nodes), `execution` (runs: workflow instances + node instances, handlers), and `integration` (Drive, Email, Excel, GPT services).
A more detailed diagram is in [`docs/architecture.excalidraw`](docs/architecture.excalidraw).

## Getting started

### Prerequisites

- Docker & Docker Compose
- For local development without Docker: Java 17 and Node.js 20
- Optional: [Ollama](https://ollama.com) running locally with `llama3.2:3b` for the LLM node

### 1. Configure secrets

```bash
cp .env.example .env
# then edit .env: DB_PASSWORD, JWT_SECRET (openssl rand -base64 64),
# CONFIG_ENCRYPTION_KEY (openssl rand -base64 32), MAIL_USERNAME / MAIL_PASSWORD
```

`.env` is git-ignored — never commit real credentials. Keep `CONFIG_ENCRYPTION_KEY` stable: it encrypts the API keys stored in workflow steps, and changing it makes them unreadable.

**First admin:** self-registration always creates an HR user. Promote the first administrator in the database, after which admins can create other admins through `POST /users/register`:

```sql
UPDATE user_roles SET role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN')
WHERE user_id = (SELECT id FROM users WHERE email = 'you@company.com');
```

### 2a. Run everything with Docker

```bash
docker volume create hr-workflow_postgres-data   # first time only
docker compose up -d --build
```

- Frontend: http://localhost:3000
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html

### 2b. Run locally (dev mode)

```bash
# database only
docker compose up -d postgres

# backend (reads ../.env automatically)
cd hr-workflow-backend
./mvnw spring-boot:run

# frontend
cd hr-workflow-frontend
npm install
npm run dev
npm run check   # type-check + lint (no warnings allowed)
```

## Configuration

All settings are environment variables (see [`.env.example`](.env.example)):

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/hr_workflow_db` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | `admin` / *(required)* |
| `JWT_SECRET` | HS512 signing key (≥ 64 bytes) | *(required)* |
| `CONFIG_ENCRYPTION_KEY` | AES-256 key (base64, 32 bytes) for secrets in step settings | *(required)* |
| `JWT_EXPIRATION_MS` | Access-token lifetime (renewed automatically with the refresh token) | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | Refresh-token lifetime | `604800000` (7 days) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials (Gmail app password) | — |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:3000` |
| `DRIVE_STORAGE_PATH` | Folder used by the Drive node and for generated reports | `~/hr-workflow-files` |
| `DRIVE_PROVIDER` | `local` (storage folder) or `google` (Google Drive) | `local` |
| `PUBLIC_API_URL` | How email recipients reach the API (report links) | `http://localhost:8080` |
| `NEXT_PUBLIC_API_URL` | API base URL used by the frontend (build time) | `http://localhost:8080/api/v1` |

## Testing

```bash
cd hr-workflow-backend
./mvnw verify        # unit + integration tests (needs Docker for Testcontainers)
```

The integration tests start a real PostgreSQL container, apply the Flyway migrations and exercise the repositories, services and HTTP endpoints, including authentication and ownership rules. A JaCoCo coverage report is written to `target/site/jacoco/`.

## API overview

All endpoints are under `/api/v1` and require `Authorization: Bearer <token>` except register, login, refresh and logout.
Lists of executions and users are paginated (`?page=0&size=20`, max 100) and return `{ content, page, size, totalElements, totalPages }`.
Errors use the [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json` format.

| Area | Endpoints |
|------|-----------|
| Auth & users | `POST /users/register`, `POST /users/login`, `POST /users/refresh`, `POST /users/logout`, `GET/PATCH/DELETE /users/me`, … |
| Workflows | `GET/POST /workflows`, `GET/PUT/PATCH/DELETE /workflows/{id}`, `PUT /workflows/{id}/graph` (save the editor canvas atomically), `POST /workflows/{id}/duplicate` |
| Nodes | `/nodes/workflow/{workflowId}`, `/nodes/{id}` |
| Executions | `POST /executions/trigger`, `GET /executions` (filters: `status`, `workflowId`), `GET /executions/stats`, `GET /executions/{id}/detail`, `POST /executions/nodes/{stepId}/retry` |
| Approvals | `GET /executions/nodes/my-approvals`, `POST /executions/nodes/{id}/approve` · `/reject` |

Full, interactive documentation is available in Swagger UI.

## CI/CD

The [`Jenkinsfile`](Jenkinsfile) builds the backend, runs the full test suite, runs SonarQube analysis, builds the Docker images and deploys with Docker Compose. Secrets are provided by a Jenkins *Secret file* credential (`hr-workflow-env`) containing the `.env` contents. See [`SONARQUBE.md`](SONARQUBE.md) for the code-quality setup.
