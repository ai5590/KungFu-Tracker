# KungFu Exercises

## Overview
Web application for managing kung fu exercises with tree-based navigation, file uploads (including large videos), role-based access control, and user management. Built with Java 17, Spring Boot 3.x, Maven, and vanilla HTML/CSS/JS frontend. Full Russian UI.

## Project Architecture
- **Backend**: Java Spring Boot 3.2.5 with Spring Security + RBAC
- **Frontend**: Static HTML/CSS/JS served from `src/main/resources/static/`
- **Storage**: File-based (no database), data stored in `./data/` directory
- **Users**: JSON-based user management via `./users.json` (migrated from users.txt on first run)
- **Build Tool**: Maven
- **Docker**: Dockerfile available for standalone deployment

## Key Directories
- `src/main/java/com/kungfu/` - Java source code
  - `config/` - Spring Security configuration with RBAC
  - `controller/` - REST API controllers (Tree, Exercise, File, Section, Me, Admin)
  - `service/` - Business logic services (UserService with caching, ExerciseService with files.json sync)
  - `model/` - DTOs and data models (UserEntry, UsersData, FileMeta, FilesData, etc.)
  - `util/` - Path validation utilities
- `src/main/resources/static/` - Frontend (HTML, CSS, JS) — all in Russian
- `src/test/java/com/kungfu/` - 17 test cases
- `./data/` - Runtime data directory (created automatically)
- `./users.json` - User credentials + roles file (created automatically, migrated from users.txt)

## Running
```bash
mvn spring-boot:run
```
Application runs on port 5000.

## Docker Deployment
```bash
docker build -t kungfu .
docker run -p 5000:5000 -v ./data:/app/data kungfu
```
On first run with empty data folder, default user `admin:admin` is created.

## User Roles
- **Admin** (admin=true): Sees "Админка" button, can manage users
- **Editor** (canEdit=true): Can create/edit/delete sections, exercises, files
- **Viewer** (canEdit=false): Read-only, can only view and download

## Login
- Case-insensitive: "Admin", "ADMIN", "admin" are all the same user
- Default fresh install user: admin / admin

## Color Themes
4 themes stored per-user in users.json (`theme` field), also cached in localStorage (key: kf_theme_v1) for instant page load:
- **Полночь** (Midnight) — default, dark navy blue
- **Светлая** (Light) — white/gray
- **Тёмная** (Dark) — pure dark with purple accent
- **Сакура** (Sakura) — warm rose/purple
Server is source of truth; theme synced from server on login via GET /api/me, saved via POST /api/me/theme.

## File Metadata
Each exercise directory can have a `files.json` alongside `exercise.json`, `notes.md`, and `media/`. The `files.json` tracks file descriptions and timestamps, auto-syncs with actual files in `media/`.

## Recent Changes
- Case-insensitive login (all usernames normalized to lowercase)
- Default user changed to admin:admin for fresh installs
- Settings modal redesigned as multi-level menu (main menu → sub-pages with back navigation)
- Per-user color theme storage in users.json (theme field), server-validated whitelist
- 4 color themes: Светлая, Полночь, Тёмная, Сакура
- Dockerfile for standalone deployment with external data volume
- RBAC: 3-level user roles (admin, editor, viewer) with users.json and migration from users.txt
- Admin panel for user management (add/remove users, toggle flags, change passwords)
- File metadata: files.json per exercise with descriptions, auto-sync
- FAB button (bottom-right) for adding sections/exercises
- Resizable sidebar with drag handle, width persisted in localStorage
- Full Russian UI localization (login, main app, admin, settings)
- Root view on startup (shows top-level sections)
- Removed action buttons from tree (navigation only)
- RBAC-aware frontend: edit controls hidden for read-only users
- Breadcrumbs with "Главная" home link
- UI/UX overhaul: responsive mobile layout, collapsible sidebar drawer, dark theme
