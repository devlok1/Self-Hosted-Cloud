# HomeCloud — Self-Hosted Cloud Storage

HomeCloud is a self-hosted personal cloud storage application designed for running on your own hardware (old laptops, desktops, home servers, or private VPS instances) across your Local Area Network (LAN).

- **Backend**: Java 21, Spring Boot 4.1.1, Spring Security (stateless JWT, BCrypt-12), Spring Data JPA, PostgreSQL 17/18.
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS, Lucide icons.
- **Architecture**: User-isolated storage (`HomeCloudStorage/users/{userId}`), atomic file staging, path traversal prevention, DTO shields concealing filesystem paths.

---

## Quick Start (One-Click Launch)

### 1. Launch Options

#### Option A: Production Multi-Container Docker Stack (Recommended)
Launch the complete stack (PostgreSQL 17, Spring Boot Backend, and Caddy Reverse Proxy with React SPA):
```powershell
docker compose up -d --build
```
- **Web UI & API (via Caddy)**: `http://localhost` (or `http://<YOUR_LAN_IP>`)
- **Health Check**: `http://localhost/api/health`
- **Tailscale HTTPS**: Automatic valid certificates using Tailscale Serve (see [Tailscale Guide](docs/TAILSCALE_REMOTE_ACCESS_GUIDE.md)).

#### Option B: Local Bare-Metal Development (Windows)
1. Initialize local PostgreSQL database:
   ```powershell
   .\setup-local-db.ps1
   ```
2. Start both backend and frontend in dedicated terminals:
   ```powershell
   .\start-homecloud.ps1
   ```
- **Frontend Web UI**: `http://localhost:3000` (or `http://<YOUR_LAN_IP>:3000`)
- **Backend API**: `http://localhost:8081` (or `http://<YOUR_LAN_IP>:8081`)

### 2. Stop HomeCloud
- **Docker Stack**: `docker compose down`
- **Bare-Metal Script**: Run `.\stop-homecloud.ps1` or press `Ctrl + C` in each terminal.

### 3. Automated Backups & Disaster Recovery
- **Create Backup**:
  ```powershell
  .\backup-homecloud.ps1       # Windows PowerShell
  ./backup-homecloud.sh        # Linux / macOS
  ```
  Produces a compressed archive containing a consistent PostgreSQL `database.sql` dump and all physical storage files.
- **Restore from Backup**:
  ```powershell
  .\restore-homecloud.ps1 -BackupFile "backups\homecloud_backup_*.zip"
  ./restore-homecloud.sh backups/homecloud_backup_*.tar.gz
  ```
  See the [Backup and Restoration Guide](docs/BACKUP_AND_RESTORE.md) for automated scheduling and server migration.

### 4. Secure Remote Access (Tailscale)
Access HomeCloud from anywhere in the world on your smartphone or remote laptop with zero port-forwarding:
- **Tailscale Setup**: Install Tailscale on your host and run `tailscale serve --bg 80`.
- **MagicDNS & HTTPS**: Connect directly to `https://<device-name>.<tailnet>.ts.net/` with valid end-to-end encryption.
- See the complete [Tailscale Remote Access Guide](docs/TAILSCALE_REMOTE_ACCESS_GUIDE.md).

---

## Automated Test Suites

Both backend and frontend are backed by comprehensive, real automated test suites:

- **Backend Tests (55 tests, 0 failures)**:
  ```powershell
  cd backend
  mvn test
  ```
  Tests cover JWT auth, password hashing, folder/file management, breadcrumb hierarchy, path traversal security, and the complete Phase 3 resumable transfer suite: chunk store & assembly, duplicate chunk idempotency, chunk/final checksum verification, session cancellation, status recovery, and quota guards.

- **Frontend Tests (30 tests, 0 failures)**:
  ```powershell
  cd frontend
  npm test
  ```
  Tests cover byte formatting, date localization, file type categorization, navigation breadcrumbs, empty state actions, toolbar filtering, browser SHA-256 calculation, and resumable chunked upload modal interactions.

- **Frontend Production Build**:
  ```powershell
  cd frontend
  npm run build
  ```

---

## Included Features

### Web Frontend
- Modern glassmorphism UI with responsive mobile/desktop layouts
- Sign In & User Registration with validation
- Breadcrumb navigation for nested folders
- Grid and List view switcher
- File and folder upload with real-time progress indicator
- Direct secure file downloads with correct MIME types and UTF-8 filenames
- File renaming, moving between folders, and deletion
- Empty folder deletion safety guard
- Live search filtering across files
- Real-time storage meter (bytes used, file and folder count)
- **Reliable Large-File Resumable Transfers**:
  - Chunked uploads with per-chunk SHA-256 verification
  - In-browser pause and resume controls
  - Transient network retry with exponential backoff
  - Survives browser refreshes and tab closures (server-side status recovery)
  - Explicit cancellation with immediate disk cleanup

### Backend API
- User registration and login
- JWT authentication with stateless Spring Security
- Secure ownership checks for every file/folder operation
- Upload files to user-isolated `HomeCloudStorage/users/{userId}/`
- List files in the root or a folder
- Download files with RFC 5987 UTF-8 Content-Disposition
- Rename and move files between folders or back to root
- Delete files with storage rollback protection
- Create, rename, and delete folders
- Folder breadcrumb hierarchy resolution
- Fast search across user files
- Storage statistics (`/api/storage/stats`)
- Safe generated storage filenames and path traversal protection
- 1 GB multipart upload limit
- CORS for local React development and private-LAN clients
- LAN-ready server binding on `0.0.0.0:8081`


## Project structure

```text
HomeCloud/
└── backend/
    ├── src/main/java/com/homecloud/backend/
    │   ├── controller/
    │   │   ├── ApiExceptionHandler.java
    │   │   ├── AuthController.java
    │   │   ├── FileController.java
    │   │   ├── FolderController.java
    │   │   ├── HomeController.java
    │   │   └── TestController.java
    │   ├── dto/
    │   ├── entity/
    │   │   ├── File.java
    │   │   ├── Folder.java
    │   │   └── User.java
    │   ├── repository/
    │   │   ├── FileRepository.java
    │   │   ├── FolderRepository.java
    │   │   └── UserRepository.java
    │   ├── security/
    │   └── service/
    │       ├── AuthService.java
    │       ├── FileService.java
    │       ├── FileStorageService.java
    │       └── FolderService.java
    ├── src/main/resources/application.properties
    └── HomeCloudStorage/
```

## PostgreSQL

The supplied `docker-compose.yml` creates:

```text
Database: homecloud
User:     homecloud
Password: <set in your .env or docker-compose.override.yml>
Port:     5432
```

If you are using a manually installed PostgreSQL server instead of Docker, make sure the `homecloud` role exists, has the correct password, and can connect to the `homecloud` database.

The password in your `.env` (or environment variables) must match your PostgreSQL role password.

## Run

1. Start PostgreSQL (e.g., `docker compose up -d`).
2. Copy `.env.example` to `.env` and configure your local passwords.
3. Open the `backend` folder in IntelliJ IDEA.
4. Run `HomecloudBackendApplication`.
5. The server listens on `0.0.0.0:8080`, which makes it reachable from other devices on the same LAN.
6. On Windows, allow inbound TCP port `8080` through Windows Defender Firewall (see the LAN section below).

Hibernate uses `ddl-auto=update`, so the new `folders` table and `folder_id` column are created/updated automatically when the application starts.

## LAN setup

The backend is configured to listen on all network interfaces. The PostgreSQL database remains on the HomeCloud host PC; LAN clients do **not** connect directly to PostgreSQL. They connect only to the Spring Boot API.

### 1. Find the HomeCloud host PC's LAN IPv4 address

On Windows PowerShell or Command Prompt run:

```powershell
ipconfig
```

Find the active Wi-Fi/Ethernet adapter and note its `IPv4 Address`, for example:

```text
192.168.1.105
```

### 2. Start HomeCloud

Run `HomecloudBackendApplication` from IntelliJ, or from the `backend` directory:

```powershell
./mvnw spring-boot:run
```

If the project does not contain Maven Wrapper files, use:

```powershell
mvn spring-boot:run
```

You should see a message similar to:

```text
Tomcat started on port 8080
```

### 3. Test from another device on the same Wi-Fi

From the second device, open:

```text
http://192.168.1.105:8080/api/health
```

Replace `192.168.1.105` with the host PC's actual LAN IPv4 address. The response should be:

```text
HomeCloud Backend is running
```

### 4. If the second device cannot connect

Windows Firewall may be blocking port 8080. Run PowerShell **as Administrator** on the HomeCloud host PC:

```powershell
New-NetFirewallRule -DisplayName "HomeCloud Spring Boot 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
```

This opens port 8080 only for the Windows **Private** network profile. Do not expose port 8080 to the public Internet as part of this LAN MVP.

### 5. Postman from another LAN device

Use the LAN base URL instead of `localhost`:

```text
http://192.168.1.105:8080
```

For example:

```text
GET  http://192.168.1.105:8080/api/health
POST http://192.168.1.105:8080/api/auth/login
POST http://192.168.1.105:8080/api/files/upload
GET  http://192.168.1.105:8080/api/files
```

Protected endpoints still require the same JWT header:

```text
Authorization: Bearer YOUR_TOKEN_HERE
```

### 6. Frontend/mobile client

Do not use `http://localhost:8080` as the API base URL on a phone. On a phone, `localhost` means the phone itself. Configure the client to use the HomeCloud host PC's LAN IP:

```text
http://192.168.1.105:8080
```

The actual IP can change when the router assigns a new DHCP address. For a stable home setup, reserve the PC's IP address in the router later.

### LAN architecture

```text
Phone / Laptop / Frontend
          |
          | HTTP :8080 over same Wi-Fi/LAN
          v
   HomeCloud Host PC
   Spring Boot API
          |
     +----+----+
     |         |
 PostgreSQL  HomeCloudStorage
 metadata    actual file bytes
```


## Authentication

### Register

`POST /api/auth/register`

Accepts either JSON (`application/json`) or form parameters (`application/x-www-form-urlencoded`):

**JSON Body (Recommended):**
```json
{
  "email": "your@email.com",
  "password": "yourPassword123"
}
```

**Form parameters:**
```text
email=your@email.com
password=yourPassword123
```

Returns `UserResponse` (`id`, `email`).

### Login

`POST /api/auth/login`

Accepts either JSON (`application/json`) or form parameters:

**JSON Body (Recommended):**
```json
{
  "email": "your@email.com",
  "password": "yourPassword123"
}
```

Returns `LoginResponse` containing JWT token, userId, and email.

### Current User Profile

`GET /api/auth/me`

Requires JWT authentication. Returns current user details (`id`, `email`).

---

## File API & Resumable Large-File Transfers

All file endpoints require a valid JWT header (`Authorization: Bearer <token>`).
Responses use `FileResponse` DTOs (`id`, `filename`, `size`, `contentType`, `folderId`, `createdAt`, `updatedAt`), concealing internal server storage paths.

### Resumable Chunked Transfer Endpoints (Phase 3)

For large files or unreliable connections, use the persistent transfer session protocol (see [Architecture Guide](file:///c:/ARKSET/Github/Self-Hosted_cloud/docs/TRANSFERS_ARCHITECTURE.md)):

1. **Initiate Transfer Session**:
   `POST /api/files/transfers/initiate`
   - Body: `{"filename": "video.mp4", "fileSize": 524288000, "chunkSize": 5242880, "folderId": 1, "contentType": "video/mp4", "expectedChecksum": "optional-sha256"}`
   - Returns: `201 Created` with `uploadId`, `totalChunks`, `status: "INITIATED"`, and `expiresAt`.

2. **Upload Chunk**:
   `PUT /api/files/transfers/{uploadId}/chunks/{chunkNumber}`
   - Form-data: `chunk` (binary data)
   - Header: `X-Chunk-Checksum: <sha256-hex>`
   - Idempotent: re-transmitting an existing valid chunk returns `200 OK` safely.

3. **Query Status / Resume Discovery**:
   `GET /api/files/transfers/{uploadId}/status`
   - Returns: Current session state and list of confirmed `uploadedChunks: [0, 1, 2, ...]`.

4. **Complete Transfer**:
   `POST /api/files/transfers/{uploadId}/complete`
   - Assembles chunks sequentially, verifies size and checksums outside database transactions, atomically relocates file, and records `File` entity.

5. **Cancel Transfer**:
   `DELETE /api/files/transfers/{uploadId}`
   - Aborts transfer and deletes all temporary chunks from disk.

### Single-Request Upload (Legacy / Small Files)

### Upload to root

`POST /api/files/upload`

Body: `multipart/form-data`
- `file`: `<file bytes>`

### Upload to a folder

`POST /api/files/upload?folderId=1`

Body: `multipart/form-data`
- `file`: `<file bytes>`

### List root files

`GET /api/files`

### List files inside a folder

`GET /api/files?folderId=1`

### Search files

`GET /api/files/search?q=report`

Searches filenames belonging to the authenticated user.

### Storage statistics

`GET /api/storage/stats` (or `GET /api/files/stats`)

Returns storage usage and counts:
```json
{
  "usedBytes": 1048576,
  "fileCount": 12,
  "folderCount": 3
}
```

### Download

`GET /api/files/{fileId}/download`

Returns file bytes with proper `Content-Type` and `Content-Disposition` attachment headers (RFC 5987 UTF-8 encoded).

### Rename

`PUT /api/files/{fileId}?filename=new-name.txt`

### Move to a folder

`PUT /api/files/{fileId}/move?folderId=1`

### Move back to root

`PUT /api/files/{fileId}/move`

### Delete

`DELETE /api/files/{fileId}`

---

## Folder API

All folder endpoints require a valid JWT.

### Create root folder

`POST /api/folders?name=Documents`

### Create subfolder

`POST /api/folders?name=College&parentId=1`

### List root folders

`GET /api/folders`

### List subfolders

`GET /api/folders?parentId=1`

### Get folder details with breadcrumbs

`GET /api/folders/{folderId}`

Returns folder metadata along with ordered breadcrumb navigation path from root.

### Rename folder

`PUT /api/folders/{folderId}?name=NewName`

### Delete folder

`DELETE /api/folders/{folderId}`

A folder must be empty (no subfolders, no files) before it can be deleted. This prevents accidental deletion of stored files and child folders.

---

## Storage model

- PostgreSQL stores file and folder metadata (`id`, `filename`, `size`, `contentType`, `owner`, `folder`, `createdAt`, `updatedAt`).
- Actual file bytes are stored with per-user filesystem isolation under `HomeCloudStorage/users/{userId}/<uuid>_<cleanFilename>`.
- Internal filesystem paths and physical filenames are never exposed through API responses.
- Safe staging prevents incomplete or aborted uploads from leaving orphaned files on disk.


## MVP test order in Postman

1. Register a new user.
2. Login and copy the JWT token.
3. Set `Authorization: Bearer <token>`.
4. Create a folder.
5. Upload a file to the root.
6. List root files.
7. Upload another file using `folderId`.
8. List files using that `folderId`.
9. Download the file.
10. Rename the file.
11. Move the file to another folder or root.
12. Delete the file.
13. Delete empty folders.

## Important JWT troubleshooting

If the API returns `401 Invalid or expired JWT token`:

- Login again and use the newest token.
- Ensure the header is exactly `Authorization: Bearer <token>`.
- Do not send an old token after restarting/changing the JWT secret.
- Do not put the login response JSON in the Authorization header; copy only the `token` value.

A `403` response generally means the request reached Spring Security but was not authorized. Check that the request has the correct JWT and that the endpoint is protected as expected.
