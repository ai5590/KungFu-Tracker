# KungFu Exercises

## Overview
Web application for managing kung fu exercises with a tree-based navigation, file uploads (including large videos), and user authentication. Built with Java 17, Spring Boot 3.x, Maven, and vanilla HTML/CSS/JS frontend.

## Project Architecture
- **Backend**: Java Spring Boot 3.2.5 with Spring Security
- **Frontend**: Static HTML/CSS/JS served from `src/main/resources/static/`
- **Storage**: File-based (no database), data stored in `./data/` directory
- **Authentication**: File-based user management via `./users.txt`
- **Build Tool**: Maven

## Key Directories
- `src/main/java/com/kungfu/` - Java source code
  - `config/` - Spring Security configuration
  - `controller/` - REST API controllers
  - `service/` - Business logic services
  - `model/` - DTOs and data models
  - `util/` - Path validation utilities
- `src/main/resources/static/` - Frontend (HTML, CSS, JS)
- `src/test/java/com/kungfu/` - Test classes
- `./data/` - Runtime data directory (created automatically)
- `./users.txt` - User credentials file (created automatically with ai:1)

## Running
```bash
mvn spring-boot:run
```
Application runs on port 5000.

## Recent Changes
- Initial creation: Full v1+v2+v3 implementation
- Tree navigation with sections/exercises
- File upload/download with Range request streaming for video
- CRUD operations for sections, exercises, files
- Path traversal protection
- Form-based authentication
