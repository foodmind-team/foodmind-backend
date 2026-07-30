# Media object storage

The Backend owns all media object keys. Media APIs are disabled by default and
are exposed only with `MEDIA_ENABLED=true` plus a valid bucket configuration.
The storage service account needs only `PutObject`, `HeadObject`, and
`DeleteObject` for the configured bucket and `MEDIA_S3_KEY_PREFIX`; it must not
grant public ACL or public-bucket permissions.

For local testing, `docker compose up -d minio` starts MinIO as an S3 test
double. Open `http://localhost:9001`, create the `foodmind-media` bucket, and
set the `MEDIA_S3_*` names in `.env`. MinIO credentials in `.env.example` are
local placeholders only and must never be used outside local development.

The upload flow is: create a PENDING asset, PUT using the short-lived returned
instruction without a Backend bearer token, then finalise. Finalisation uses a
trusted storage HEAD and compares the exact content type, byte size, and
SHA-256 checksum. ETags are never treated as checksums.

PENDING assets older than the upload TTL are soft-deleted by scheduled cleanup.
Cleanup retries deletion of soft-deleted objects, so an object-store outage
does not resurrect an asset or affect a different immutable object key. Roll
back application code with the previous image; do not reverse the existing V5
media schema migration.
