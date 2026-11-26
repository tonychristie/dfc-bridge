# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.0.0] - 2026-11-23

### Changed
- The bridge listens on `127.0.0.1` by default. It has no authentication, so it no longer accepts connections from other hosts unless `SERVER_ADDRESS` or `--server.address` is set, for example to `0.0.0.0`
- `POST /api/v1/connect` connects through the docbroker and port in the request. The `dfc.docbroker.*` entries of `dfc.properties` no longer choose the docbroker
- The bridge loads one DFC instance per docbroker host and port, each with its own `dfc.properties` and data folder under `dfc-instances/`. One bridge can hold sessions to several environments at the same time, including two environments with the same repository name and ID
- DFC is no longer on the application classpath. The start scripts pass `--dfc.home` and `--dfc.config` in DFC mode
- Each error returns the HTTP status that matches it: 400 for bad input, DQL, ids, attributes and commands, 401 for authentication errors, 403 for access errors, 404 for objects and sessions not found, 503 for docbroker and repository errors, 502 for any other DFC error and 500 for a bridge error with no DFC cause
- A missing or invalid request parameter, an unreadable body, an unsupported method or content type and an unknown path return 400, 404, 405, 406 or 415 with a JSON error body, in place of 500. Every error body is `application/json`
- Error messages carry the DFC exception message, in place of `null`
- The folder listings return `objectId`, `type`, `name` and `a_content_type` for each child. `GET /api/v1/types` returns `name`, `superType` and `systemType` for each type; `GET /api/v1/types/{typeName}` still returns the full definition
- Name patterns for types, users and groups take `_` and `%` as literal characters, so `dm_*` no longer matches `dmc_` names or `dmadmin`
- Group responses carry `isPrivate` in place of `private`, and add `isDynamic`, `ownerName`, `groupAddress` and `groupSource`
- `POST /api/v1/dmapi` takes the dmAPISet value from the `value` field, in place of the text after the last comma of the command
- Attribute writes set every value as text. A null value writes the empty value for the data type, a null for a repeating attribute removes all values, and an object, or a list for a single-value attribute, fails with 400
- `POST /api/v1/api` calls only a method whose parameters accept every argument, converts string arguments for `IDfTime` and `IDfId` parameters, and returns any result that is not null, a string, a boolean or a number as its text
- The OpenAPI document lists every status each endpoint returns
- Upgraded to Spring Boot 4.1.2 and springdoc 3.2.0. The HTTP API is unchanged by the upgrade
- `mvn verify` runs SpotBugs, which fails the build on any item that `spotbugs-exclude.xml` does not cover, and writes the JaCoCo coverage report

### Added
- Windows launcher: `start-dfc-bridge.cmd`, which runs `start-dfc-bridge.ps1`
- The start scripts run from the unzipped release bundle as well as from the build folder

### Fixed
- Concurrent requests on one session no longer use the DFC session at the same time. Each session is locked while a request runs, and cleanup and disconnect no longer release a session that a request is using
- A request on a DFC session that is no longer connected returns 404 `SESSION_NOT_FOUND`, `GET /api/v1/session/{sessionId}` included, and `GET /api/v1/session/{sessionId}/valid` returns `false`. The bridge then removes the session
- An update with `save=false`, or one that fails, no longer leaves its changes on the cached object for a later save to write
- A value the bridge rejects (a list or an object for a single attribute, a time DFC cannot parse, an `/api` method with no overload that accepts the arguments) returns 400 in place of 500
- `POST /api/v1/objects` for a type that is not a sysobject, such as `dm_relation`, returns 400 `CREATE_ERROR` in place of 500 when the request has `objectName` or `folderPath`
- `GET /api/v1/users` no longer lists groups, and `GET /api/v1/users/{userName}` returns 404 `USER_NOT_FOUND` for a group name
- Listing the contents of an object that is not a folder returns 404 in place of an empty list
- Listing a folder by path no longer fails with "wrong number of arguments"
- Not-found messages for folders and types no longer carry a second `Object not found: ` prefix
- dmAPI commands recognise `SESSION` under any default locale
- A missing file under `/swagger-ui/` returns 404 `NOT_FOUND` in place of 500
- The instance `dfc.properties` keeps the global `dfc.docbroker.*` settings, such as `dfc.docbroker.search_order`, and the settings of each `#include` file. Only the indexed docbroker entries and `dfc.data.dir` are replaced
- A line at the end of a `dfc.properties` file or an `#include` file that ends in a backslash no longer runs on into the next line of the instance `dfc.properties`
- Every file of the DFC config folder, such as `log4j2.properties`, is copied into each DFC instance
- `servlet-api.jar` in `DFC_HOME` is left off the DFC classpath
- The start script extracts the jar again whenever it has changed, refuses a second start while the bridge is running, keeps its files and logs in `run/`, and `--verify` reports every missing item
- A DQL result column of type ID, such as `object_created` from `create ... object`, returns the object ID text in place of the DFC id object

### Removed
- `dfcProfile` on connect and `namedArgs` on `/api`, which had no effect. Clients that still send them are not affected
- The `dfc.session.auto-reconnect` and `max-sessions-per-profile` settings, which nothing read

## [1.1.0] - 2026-01-24

### Added
- User and group endpoints for REST API parity (#265)
  - `GET /api/v1/users` - List all users
  - `GET /api/v1/users/{name}` - Get user details
  - `GET /api/v1/users/{name}/groups` - Get groups containing user
  - `GET /api/v1/groups` - List all groups
  - `GET /api/v1/groups/{name}` - Get group details including members
- Direct `/health` endpoint returning `{"status": "ok"}` (#268)

### Fixed
- `/api/v1/objects/{objectId}` now correctly resolves names for dm_group, dm_user, and dm_relation objects (#266)
- `/api/v1/groups/{name}` endpoint now properly returns all values of repeating attributes (`users_names`, `groups_names`) (#274)
- `/api/v1/objects/{id}` endpoint now correctly extracts repeating attributes as arrays (#275)
- `setObjectAttribute` now supports repeating attributes (#294)
