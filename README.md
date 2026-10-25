# HomeCloud Backend — MVP File Management

HomeCloud backend using Java 21, Spring Boot 4.1.1, PostgreSQL 17, Spring Data JPA and JWT authentication.

## Included MVP features

- User registration and login
- JWT authentication with stateless Spring Security
- Secure ownership checks for every file/folder operation
- Upload files to local `HomeCloudStorage`
- List files in the root or a folder
- Download files
- Rename files
- Move files between folders or back to root
- Delete files
- Create folders
- List folders
- Rename folders
- Delete empty folders
- File metadata stored in PostgreSQL
- Folder metadata stored in PostgreSQL
- Safe generated storage filenames to avoid collisions
- Path traversal protection for stored files
- 1 GB multipart upload limit
- CORS for local React development and private-LAN clients
- LAN-ready server binding on `0.0.0.0:8080`

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

Form parameters:

```text
email=your@email.com
password=yourPassword
```

### Login

`POST /api/auth/login`

Form parameters:

```text
email=your@email.com
password=yourPassword
```

Copy the `token` from the login response.

For every protected request use:

```text
Authorization: Bearer YOUR_TOKEN_HERE
```

Do not include the word `Bearer` twice and do not paste quotes around the token.

## File API

All file endpoints require a valid JWT.

### Upload to root

`POST /api/files/upload`

Body: `multipart/form-data`

```text
file = <select a file>
```

### Upload to a folder

`POST /api/files/upload?folderId=1`

Body: `multipart/form-data`

```text
file = <select a file>
```

### List root files

`GET /api/files`

### List files inside a folder

`GET /api/files?folderId=1`

### Download

`GET /api/files/{fileId}/download`

### Rename

`PUT /api/files/{fileId}?filename=new-name.txt`

### Move to a folder

`PUT /api/files/{fileId}/move?folderId=1`

### Move back to root

`PUT /api/files/{fileId}/move`

### Delete

`DELETE /api/files/{fileId}`

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

### Rename folder

`PUT /api/folders/{folderId}?name=NewName`

### Delete folder

`DELETE /api/folders/{folderId}`

A folder must be empty before it can be deleted. This prevents accidental deletion of stored files and child folders.

## Storage model

PostgreSQL stores metadata such as:

- file ID
- original filename
- generated stored filename
- storage path
- size
- content type
- owner
- folder
- timestamps

The actual file bytes remain on disk under `HomeCloudStorage`.

The generated storage filename is UUID-based, so the physical filename is independent of the user-visible filename.

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
