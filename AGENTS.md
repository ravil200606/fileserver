# Project Overview

## Purpose

`fileserver` is a lightweight self-hosted file server for managing an electronic book collection.

The application provides a web interface for browsing books stored in a filesystem directory and supports downloading, archiving, restoring, and deleting books.

The project is primarily intended for use with legacy devices and browsers, including Safari on iOS 5.1. Therefore, compatibility with the existing legacy frontend environment is an explicit project requirement.

The filesystem is the application's data store. There is no database.

## Technology Stack

- Java 17
- Spring Boot
- Spring MVC
- Gradle
- HTML/CSS/JavaScript frontend
- ES5-compatible JavaScript
- Docker / Docker Compose
- Eclipse Temurin JRE 17 for the runtime container

## Project Structure

The project is a single Gradle/Spring Boot module.

Main components:

- `FileserverApplication` — Spring Boot application entry point.
- `controller/` — HTTP API controllers.
- `service/` — application and business logic.
- `dto/` — objects exchanged between the backend and HTTP layer.
- `src/main/resources/static/` — frontend resources.
- `application.yaml` — application configuration.
- `Dockerfile` and `docker-compose.yml` — containerized deployment.

The main backend flow is:

`HTTP request → Dashboard → BookService → filesystem`

The frontend communicates with the backend through the HTTP API exposed by `Dashboard`.

## Runtime and Configuration

The main application data directory is configured through:

`BOOKS_DIR`

The default value is the user's `~/books` directory.

The frontend refresh interval is configured through:

`FILESERVER_REFRESH_INTERVAL_SECONDS`

The application is designed to run either directly as a Spring Boot application or inside Docker.

When running in Docker, the host book directory is mounted into the container as the application's data directory.

## Main User Scenarios

### Browse books

A user opens the web interface and receives a list of available books.

The list contains book metadata such as:

- filename;
- author;
- title;
- file size;
- modification time.

The frontend supports filtering the displayed books.

### Download a book

A user selects a book and downloads the corresponding file through the HTTP API.

### Archive books

A user selects one or more books and archives them.

Archiving changes the filename by adding the `.arch` suffix.

The `.arch` suffix represents the application's archived state. It is not an archive file format.

### Restore archived books

A user selects archived books and restores them.

Restoring removes the `.arch` suffix and returns the file to its original name.

### Delete archived books

A user can permanently delete archived books.

Normal, non-archived books must not be deleted through the archive deletion operation.

### Automatic refresh

The frontend periodically refreshes the current book or archive list using the interval supplied by the backend.

## Development and Verification

The project uses the Gradle wrapper for build and test operations.

Before considering a backend change complete, the implementation should be built and the available automated tests should be executed.

For frontend changes, verification must also take into account compatibility with the project's legacy browser target.

Changes must preserve the existing HTTP API and documented user-visible behavior unless the task explicitly requires changing them.

# Architecture and Invariants

## Architectural Boundaries

### Controller

`Dashboard` is the HTTP API layer.

It is responsible for:

- mapping HTTP requests to application operations;
- receiving request parameters;
- invoking `BookService`;
- converting service results into HTTP responses;
- returning files for download.

Business rules and filesystem safety rules must not be moved into the controller.

### Service

`BookService` contains the application's business logic for working with books and archived books.

Changes to file discovery, archiving, restoring, deleting, or downloading must be implemented through the service layer rather than duplicating the logic in controllers.

### DTO

DTO classes represent the data exchanged between the backend and the frontend/API.

Do not add business logic to DTOs.

### Frontend

The frontend in `src/main/resources/static/` is a legacy-compatible client.

The frontend communicates with the backend exclusively through the existing HTTP API.

Do not introduce a frontend framework or a build tool unless the task explicitly requires an architectural migration.

## Filesystem Safety

The filesystem is the application's data store.

All operations on book files must remain restricted to the configured `booksDir`.

`BookService.findBook(String name)` is the security boundary for resolving a user-supplied book name to a filesystem path.

The resolved path must remain a regular file directly inside `booksDir`.

Do not weaken or bypass this restriction.

In particular, user-supplied names must not be allowed to access:

- parent directories;
- absolute paths;
- files outside `booksDir`;
- directories instead of regular files.

Changes affecting `findBook()` must preserve these security properties.

## Archive State

The `.arch` suffix represents the application's logical archived state.

It is not an archive format.

For a book:

`book.zip` → `book.zip.arch`

means that the book has been archived.

Restoring reverses this operation:

`book.zip.arch` → `book.zip`

Do not replace this mechanism with ZIP creation, compression, or another archive format unless the task explicitly requests such a change.

Archive, restore, and delete operations must preserve their existing semantics.

## Delete Restrictions

The archive deletion operation is intended to permanently delete archived books.

A normal, non-archived book must not become deletable through the archive deletion API as a side effect of refactoring or code reuse.

## Book Name and Metadata

The filename is the primary identifier of a book in the application.

The current filename convention can be used to derive author and title metadata, but the convention is not a mandatory filename validation rule.

Files that do not follow the convention must remain usable and must continue to be displayed with the existing fallback metadata behavior.

Do not turn metadata parsing into a requirement for storing or displaying a book unless the task explicitly requests such a change.

## Frontend Compatibility

The frontend is intentionally implemented using legacy JavaScript compatible with the project's target environment, including Safari on iOS 5.1.

Preserve ES5 compatibility.

Do not introduce language or browser features that are unavailable in the supported legacy environment.

In particular, do not replace the existing approach with modern APIs such as:

- `let` / `const`;
- arrow functions;
- `fetch`;
- Promises;
- modules;
- modern browser APIs;

unless the task explicitly changes the supported browser environment.

Avoid introducing external frontend dependencies unless explicitly requested.

## HTTP API Compatibility

Existing endpoints are part of the application's public interface.

Changes to endpoint paths, HTTP methods, request parameters, response structures, or user-visible semantics require explicit justification and must not be made as an incidental part of refactoring.

When changing an API contract, inspect and update all affected frontend callers and documentation.

## Configuration Compatibility

Existing environment variables and configuration properties are part of the application's runtime contract.

In particular:

- `BOOKS_DIR` controls the book storage directory;
- `FILESERVER_REFRESH_INTERVAL_SECONDS` controls the frontend refresh interval.

Do not silently rename, remove, or change the meaning of existing configuration properties.

## Scope of Changes

Prefer the smallest change that correctly solves the requested task.

Do not introduce architectural migrations, new frameworks, databases, or unrelated refactoring unless they are required by the task.

Before changing a protected architectural boundary or invariant, explain:

1. why the existing design is insufficient;
2. which invariant is affected;
3. how the proposed change preserves or intentionally changes the existing behavior.

If the task is ambiguous and different interpretations could change architecture, behavior, security, or API compatibility, ask for clarification instead of making an assumption.

# AI Development Workflow

## General Principle

Treat the existing project behavior as the default contract.

Do not change existing behavior, architecture, API contracts, or compatibility requirements unless the task explicitly requires such a change.

Prefer a small, focused change over a broad refactoring.

Do not introduce technologies, dependencies, frameworks, or architectural patterns only because they are considered modern or generally preferable.

## Before Making Changes

Before editing code:

1. Identify the files and components affected by the task.
2. Read the relevant existing implementation.
3. Trace the execution flow far enough to understand the existing behavior.
4. Identify existing tests, if any.
5. Identify project invariants that may be affected.
6. Determine whether the task changes:
   - public HTTP API;
   - filesystem behavior;
   - security boundaries;
   - frontend browser compatibility;
   - configuration;
   - user-visible behavior.

Do not modify code based only on filenames or assumptions about how the project works.

## Clarifications and Assumptions

Ask a clarifying question when the task is ambiguous and different interpretations could affect:

- architecture;
- security;
- filesystem access;
- HTTP API compatibility;
- persistent behavior;
- supported browser compatibility;
- user-visible behavior.

For minor ambiguities that do not affect these areas, choose the most conservative interpretation and explicitly state the assumption.

Never silently make a significant architectural or behavioral assumption.

## Planning

For non-trivial tasks, provide a short implementation plan before making changes.

The plan should identify:

- affected files/components;
- intended changes;
- relevant invariants;
- tests or verification to be performed.

Do not create a large speculative plan for a small change.

## Implementation

During implementation:

- preserve existing behavior unless a behavior change is part of the task;
- reuse existing project patterns where appropriate;
- avoid unrelated cleanup;
- avoid broad refactoring mixed with a feature or bug fix;
- keep changes localized;
- do not duplicate business rules across layers;
- keep filesystem operations inside the service layer;
- preserve the existing API unless explicitly changing it.

When a requested change conflicts with an existing invariant, stop and explain the conflict instead of silently weakening the invariant.

## Testing

Add or update automated tests when the changed behavior is testable.

For changes to `BookService`, prefer unit tests covering the relevant business behavior and edge cases.

For security-sensitive filesystem behavior, include tests for invalid paths and attempts to access files outside the configured books directory where practical.

For HTTP API changes, verify both the backend behavior and affected frontend callers.

For frontend changes, verify that the resulting JavaScript remains compatible with the supported legacy browser environment.

Do not claim that tests passed unless they were actually executed successfully.

If a verification command cannot be executed because of the environment, report the exact limitation.

## Verification

Before considering a task complete, perform the applicable checks:

1. Compile/build the project.
2. Run automated tests.
3. Verify changed API behavior where applicable.
4. Inspect the final diff for unrelated changes.
5. Confirm that project invariants remain intact.

If a check cannot be performed, explicitly report:

- which check was not performed;
- why it could not be performed;
- whether the limitation is environmental or caused by the implementation.

## Completion Criteria

A task is complete only when:

- the requested behavior is implemented;
- existing relevant behavior is preserved;
- relevant tests have been added or updated;
- applicable checks have been executed;
- no known security or compatibility invariant has been weakened;
- the final change is limited to the task scope.

## Response Format

For non-trivial tasks, structure the final response as:

### Plan

Brief description of the intended approach.

### Changes

Files changed and what was changed in each.

### Rationale

Important implementation decisions and assumptions.

### Verification

Commands executed and their results.

### Remaining Issues

Any checks that could not be performed, known limitations, or follow-up work.

For simple tasks, keep the response concise while still reporting the changes and verification performed.