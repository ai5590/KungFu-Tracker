# KungFu Exercises

Web application for managing kung fu exercises with tree-based navigation, video playback, file management, and role-based access control.

## Quick Start

```bash
mvn spring-boot:run
```

Open http://localhost:5000 and log in with username `admin`, password `admin` (fresh install default).

## Docker Deployment

```bash
docker build -t kungfu .
docker run -p 5000:5000 -v $(pwd)/data:/app/data kungfu
```

On first run with an empty `data/` folder and no `users.json`, the default user `admin:admin` is created automatically.
To update to a new version, simply rebuild the Docker image — your data in `./data/` is preserved via the volume mount.

## Data Directory

All exercise data is stored in `./data/`. On first launch, demo data is created automatically:
```
data/
  KungFu/
    _section.json
    Basics/
      _section.json
      HorseStance/
        exercise.json
        notes.md
        files.json
        media/
```

## Users & Roles

Users are stored in `./users.json` (created automatically on first launch).

On first startup, if `users.txt` exists it is migrated to `users.json`. If neither file exists, the default user `admin:admin` is created with admin + editor rights.

Login is case-insensitive: `Admin`, `ADMIN`, and `admin` are treated as the same user.

Format (`users.json`):
```json
{
  "users": [
    { "login": "admin", "password": "admin", "admin": true, "canEdit": true }
  ],
  "updatedAt": "2026-02-07T12:00:00Z"
}
```

### Role Levels
- **Admin** (`admin: true`): Can manage users via the admin panel
- **Editor** (`canEdit: true`): Can create, edit, rename, delete sections/exercises/files
- **Viewer** (`canEdit: false`): Can only view and download content

### Managing Users
Use the admin panel (top-right "Админка" button, visible only to admins) to:
- Add/remove users
- Toggle admin and editor flags
- View/change passwords

## File Descriptions (Metadata)

Each exercise can have a `files.json` in its directory with descriptions for each file:
```json
{
  "files": [
    {
      "fileName": "demo.mp4",
      "description": "Front angle, slow motion",
      "createdAt": "2026-02-07T12:00:00Z",
      "updatedAt": "2026-02-07T12:00:00Z"
    }
  ]
}
```
- Descriptions can be edited in the UI via the "Описание" button on each file
- `files.json` auto-syncs with the `media/` folder (adds missing entries, removes stale ones)

## API Endpoints

### Public (authenticated)
- `GET /api/tree` — tree structure
- `GET /api/exercises?path=...` — exercise details including file descriptions
- `GET /api/files/stream?exercisePath=...&fileName=...` — file streaming (Range support)
- `GET /api/me` — current user info (login, admin, canEdit)
- `POST /api/me/change-password` — change own password

### Editor only
- `POST/PUT/DELETE /api/sections` — section CRUD
- `POST/PUT/DELETE /api/exercises` — exercise CRUD
- `POST /api/files/upload` — upload files
- `DELETE /api/files` — delete files
- `PUT /api/files/description` — update file description

### Admin only
- `GET /api/admin/users` — list users
- `GET /api/admin/users/password?login=...` — view user password
- `POST /api/admin/users` — add user
- `PUT /api/admin/users` — update user flags/password
- `DELETE /api/admin/users?login=...` — delete user

## Backup

```bash
cp -r ./data ./data-backup
cp users.json users.json.backup
```

## Running Tests

```bash
mvn test
```

17 tests covering: tree, CRUD, upload, Range streaming, path traversal, authentication, RBAC, admin endpoints, file descriptions, user migration.

## Features

- Tree navigation with sections and exercises
- Create, rename, delete sections and exercises
- Edit exercise descriptions and notes
- Upload files (including videos up to 1GB+)
- Video playback with seeking (HTTP Range support)
- Image preview and file download
- Role-based access control (admin, editor, viewer)
- Admin panel for user management
- Password change (self-service)
- File metadata with descriptions
- Case-insensitive login
- Path traversal protection
- Full Russian UI localization

## UI/UX

- 4 color themes: Светлая (Light), Полночь (Midnight), Тёмная (Dark), Сакура (Sakura)
- Theme selection persisted per user in localStorage
- Settings modal with tabs: password change + color theme picker
- Responsive layout: sidebar becomes slide-out drawer on mobile (<= 900px)
- Resizable sidebar with drag handle (min 220px, max 600px, persisted in localStorage)
- FAB button (bottom-right) for quick section/exercise creation
- Root view shows top-level sections on startup
- Tree state persistence in localStorage
- Breadcrumbs navigation with "Главная" (home) link
- File thumbnails and modal viewer for media
- RBAC-aware UI: edit buttons hidden for read-only users
