# Privacy

## Current behavior

DeepFind indexes selected filesystem metadata into a local Lucene directory. The default data directory is `${user.home}/.deepfind`, and `DEEPFIND_DATA_DIRECTORY` can override it. Search queries are processed in memory and are not persisted or logged by application code. The application does not send telemetry, load remote fonts, call cloud APIs, or upload indexed metadata.

## Product policy

- File metadata and supported content will be processed locally.
- The search index, database, settings, and logs will remain on the device.
- Core features will work without login or an internet connection.
- Extracted content and private queries will not be logged by default.
- No analytics, tracking, crash-upload, or external AI service will be silently introduced.
- Any future online feature must be explicit, optional, disabled by default, and clearly disclosed.

## Network behavior

The runtime backend binds to loopback only. Development tools may contact package repositories while installing dependencies; that is build-time behavior, not application telemetry.

## Future storage disclosure

The Lucene metadata index is stored under `<data-directory>/index`. Database, settings, application-log, and extraction temporary-file paths will be documented before those stores are introduced. DeepFind will not claim its index is encrypted until encryption is actually implemented and verified.
