# KungFu Exercises

Web application for managing kung fu exercises with tree-based navigation, video playback, and file management.

## Quick Start

```bash
mvn spring-boot:run
```

Open http://localhost:5000 and log in with username `ai`, password `1`.

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
        media/
```

## Users

Users are stored in `./users.txt` (created automatically with default user `ai:1`).

Format: one user per line, `login:password`

```
ai:1
admin:secret123
```

Changes to users.txt are picked up without restart.

## Adding a User

Simply add a new line to `./users.txt`:
```
newuser:newpassword
```

## Backup

To back up all data, simply copy the `./data/` directory:
```bash
cp -r ./data ./data-backup
```

## Running Tests

```bash
mvn test
```

## Features

- Tree navigation with sections and exercises
- Create, rename, delete sections and exercises
- Edit exercise descriptions and notes
- Upload files (including videos up to 1GB+)
- Video playback with seeking (HTTP Range support)
- Image preview
- File download
- User authentication
- Path traversal protection
