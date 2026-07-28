# Branch 16 - Bounded Media Upload

## Branch metadata

- **Branch:** `feat/media-upload`
- **Base dependency:** Branch 15 merged
- **Use case:** optional photo attachment for food and drink records
- **BE item:** BE-030
- **Database objects:** `media_asset` from the schema foundation
- **Postman folder:** `15 - Media Upload`
- **Delivery gate:** implement only after the P0/P1 record, permission, and
  integration paths are stable

## Scope

Implement a server-controlled object-storage upload workflow. The Backend
creates a bounded pending asset, issues a short-lived upload instruction,
verifies the uploaded object before finalisation, and permits only the owner to
attach or delete it. Clients never choose an arbitrary object key and never
receive general storage credentials.

### Explicit non-scope

- image editing, transcoding, moderation, OCR, or face recognition;
- accepting remote image URLs;
- public buckets or permanent unauthenticated download URLs;
- client-supplied bucket names or object keys;
- multiple photos per record unless the frozen public contract is revised; and
- asynchronous malware scanning infrastructure unless it becomes an explicit
  deployment requirement.

## Public contract to freeze first

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/media/uploads` | Create a pending asset and bounded upload instruction |
| `POST` | `/api/v1/media/{mediaAssetId}/finalise` | Verify storage metadata and mark the asset ready |
| `DELETE` | `/api/v1/media/{mediaAssetId}` | Soft-delete an owned unattached or attached asset according to policy |

`POST /media/uploads` accepts only:

```json
{
  "contentType": "image/jpeg",
  "byteSize": 245761,
  "checksumSha256": "lowercase-64-character-hex"
}
```

The response returns `mediaAssetId`, `status`, a short expiry timestamp, and
either a presigned `PUT` URL plus an allow-listed header map or a presigned
multipart form. It must not return bucket credentials. The public schema must
document that the upload URL is sensitive and short-lived.

## Target files

```text
pom.xml
.env.example
compose.yaml
src/main/java/com/foodmind/foodmindbackend/media/api/MediaController.java
src/main/java/com/foodmind/foodmindbackend/media/api/request/CreateMediaUploadRequest.java
src/main/java/com/foodmind/foodmindbackend/media/api/response/MediaUploadInstructionResponse.java
src/main/java/com/foodmind/foodmindbackend/media/api/response/MediaAssetResponse.java
src/main/java/com/foodmind/foodmindbackend/media/application/CreateMediaUploadUseCase.java
src/main/java/com/foodmind/foodmindbackend/media/application/FinaliseMediaUploadUseCase.java
src/main/java/com/foodmind/foodmindbackend/media/application/DeleteMediaAssetUseCase.java
src/main/java/com/foodmind/foodmindbackend/media/application/port/MediaAssetRepository.java
src/main/java/com/foodmind/foodmindbackend/media/application/port/ObjectStoragePort.java
src/main/java/com/foodmind/foodmindbackend/media/domain/model/MediaAsset.java
src/main/java/com/foodmind/foodmindbackend/media/domain/policy/MediaPolicy.java
src/main/java/com/foodmind/foodmindbackend/media/infrastructure/persistence/
src/main/java/com/foodmind/foodmindbackend/media/infrastructure/storage/S3ObjectStorageAdapter.java
src/main/java/com/foodmind/foodmindbackend/media/infrastructure/storage/S3StorageProperties.java
src/main/java/com/foodmind/foodmindbackend/record/application/MediaAttachmentPolicy.java
src/main/resources/application.properties
src/main/resources/application-local.properties
src/main/resources/application-staging.properties
src/main/resources/application-production-demo.properties
src/main/resources/openapi/openapi.yaml
src/test/java/com/foodmind/foodmindbackend/media/
src/test/java/com/foodmind/foodmindbackend/record/MediaAttachmentPermissionTest.java
src/test/resources/fixtures/media/
docs/operations/media-storage.md
```

## Detailed implementation steps

1. **Freeze limits and configuration**
   - Define allow-listed content types, maximum byte size, upload TTL, bucket,
     server-owned key prefix, and optional checksum requirement in validated
     `@ConfigurationProperties`.
   - Use conservative image-only defaults and keep all secret/configuration
     values outside Git.
   - Fail startup in a profile where media is enabled but required storage
     settings are missing.

2. **Define the storage port**
   - `createUploadInstruction(objectKey, contentType, byteSize, checksum, ttl)`
     returns a typed URL/form instruction and expiry.
   - `headObject(objectKey)` returns only trusted metadata needed for
     finalisation.
   - `deleteObject(objectKey)` is idempotent.
   - Keep AWS SDK types inside the infrastructure adapter.

3. **Create a pending asset**
   - Derive `ownerUserId` from the authenticated principal.
   - Validate content type, declared size, checksum syntax, and request bounds.
   - Generate the asset UUID and object key on the server. Use a non-guessable,
     environment-scoped key such as
     `media/{ownerUuid}/{assetUuid}/original`.
   - Persist `PENDING` before issuing the upload instruction.
   - Do not log the presigned URL or signed query string.

4. **Issue a bounded upload**
   - Bind the signature to the exact object key, content type, maximum size,
     checksum/header set, and short expiry supported by the chosen S3 method.
   - Disable public ACLs and never permit a caller-selected ACL.
   - Return only headers the client must send. Redact the instruction from
     tracing/error bodies.

5. **Finalise safely**
   - Lock/read the asset by both ID and authenticated owner.
   - Permit only `PENDING -> READY`; make a repeated identical finalisation
     idempotent.
   - Fetch object metadata through `HEAD`.
   - Compare exact key, content type, actual byte size, and checksum where the
     storage provider exposes a trustworthy checksum. Do not treat an ETag as a
     SHA-256 checksum.
   - On mismatch, keep the asset unusable, return a stable safe error, and
     schedule/delete the unexpected object according to the runbook.
   - Store `finalised_at` only after verification succeeds.
   - V5's guard is the final defence against metadata rewrite, READY/PENDING
     reversal, DELETED resurrection, and physical deletion.

6. **Integrate with records**
   - A food/drink create or patch may reference only an owned `READY`,
     non-deleted asset.
   - Prevent attaching another user's asset and prevent reusing one asset in
     conflicting records if the frozen cardinality is one-to-one.
   - Record reads return a short-lived read URL or a Backend download route,
     never the raw bucket/key contract. Confirm which option is used in the
     OpenAPI examples.

7. **Deletion and stale cleanup**
   - Authorise by owner and use non-disclosing `404` for foreign IDs.
   - Mark the database row `DELETED` before/with an out-of-transaction,
     retry-safe object deletion workflow.
   - Define recovery for `PENDING` assets older than the TTL and for storage
     deletion failures. Retrying cleanup must not affect a different object.

8. **Operational protections**
   - Apply per-user request limits to upload creation/finalisation.
   - Record safe audit metadata: asset ID, action, outcome, content-type class,
     and byte-size band; never URL signatures or image content.
   - Emit counts for pending, ready, verification failure, and stale cleanup.

9. **Documentation and fixtures**
   - Add OpenAPI schemas and examples for create/finalise/delete and all stable
     error codes.
   - Provide a local storage stub or LocalStack/MinIO profile only if its
     behaviour is documented as an S3 test double.
   - Keep the Postman flow two-stage: obtain an instruction, upload the fixture
     through the returned URL, then finalise.

## Commit plan

1. At repository root:

   ```text
   chore(media): add validated object-storage configuration
   ```

   Commit the dependency, configuration binding, environment variable names,
   and storage runbook. Do not commit credentials.

2. In the `media` feature:

   ```text
   feat(media): create bounded pending upload instructions
   ```

   Commit domain model, ports, persistence mapping, create use case, controller,
   and matching tests/OpenAPI contract.

3. In the `media` feature:

   ```text
   feat(media): verify and finalise owned uploads
   ```

   Commit the storage adapter, finalisation/deletion paths, record attachment
   validation, and tests.

4. In tests and operations:

   ```text
   test(media): cover upload ownership limits and recovery
   ```

   Commit storage stub/fixture tests, negative permission matrix, stale cleanup
   tests, and final runbook evidence instructions.

Commit from the repository root and stage only:

| Commit | Paths to stage |
| --- | --- |
| 1 | `pom.xml`, `.env.example`, `compose.yaml` when its documented storage test double changes, `src/main/resources/application.properties`, `src/main/resources/application-local.properties`, `src/main/resources/application-staging.properties`, `src/main/resources/application-production-demo.properties`, `src/main/java/com/foodmind/foodmindbackend/media/infrastructure/storage/S3StorageProperties.java`, and `docs/operations/media-storage.md` |
| 2 | `src/main/java/com/foodmind/foodmindbackend/media/api/`, `src/main/java/com/foodmind/foodmindbackend/media/application/CreateMediaUploadUseCase.java`, `src/main/java/com/foodmind/foodmindbackend/media/application/port/MediaAssetRepository.java`, `src/main/java/com/foodmind/foodmindbackend/media/application/port/ObjectStoragePort.java`, `src/main/java/com/foodmind/foodmindbackend/media/domain/model/MediaAsset.java`, `src/main/java/com/foodmind/foodmindbackend/media/domain/policy/MediaPolicy.java`, `src/main/java/com/foodmind/foodmindbackend/media/infrastructure/persistence/`, `src/main/resources/openapi/openapi.yaml`, and matching focused tests |
| 3 | `src/main/java/com/foodmind/foodmindbackend/media/infrastructure/storage/S3ObjectStorageAdapter.java`, `src/main/java/com/foodmind/foodmindbackend/media/application/FinaliseMediaUploadUseCase.java`, `src/main/java/com/foodmind/foodmindbackend/media/application/DeleteMediaAssetUseCase.java`, `src/main/java/com/foodmind/foodmindbackend/record/application/MediaAttachmentPolicy.java`, `src/test/java/com/foodmind/foodmindbackend/record/MediaAttachmentPermissionTest.java`, `src/main/resources/openapi/openapi.yaml`, and matching focused tests |
| 4 | `src/test/java/com/foodmind/foodmindbackend/media/`, `src/test/resources/fixtures/media/`, and final recovery/evidence updates to `docs/operations/media-storage.md` only |

## Required tests

- accepted content type and maximum-size boundary;
- rejected MIME type, zero/negative/oversized byte count, and invalid checksum;
- generated key cannot be supplied or influenced by the client;
- upload instruction has the configured short expiry and required headers;
- URL/signature is absent from logs and safe error bodies;
- owner finalises matching metadata;
- missing object and size/type/checksum mismatch cannot become `READY`;
- repeated finalisation is safe;
- database rejects metadata mutation, READY/PENDING reversal, DELETED
  resurrection, and physical deletion;
- foreign user receives non-disclosing `404`;
- only `READY`, owned assets attach to food/drink records;
- deleted/stale assets cannot attach;
- object deletion and cleanup retry are idempotent;
- storage timeout maps to a stable `503`/dependency error without leaking the
  upstream response; and
- disabled-media profile does not expose a half-configured workflow.

## Verification

```powershell
.\mvnw.cmd test -Dtest="*Media*"
.\mvnw.cmd test
docker compose up -d
```

Start the Backend in terminal A and leave it running:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

After health is ready, run the media folder in terminal B:

```powershell
newman run postman/FoodMind-Backend.postman_collection.json `
  -e postman/FoodMind-Local.postman_environment.json `
  --folder "15 - Media Upload"
```

Retain a redacted example of the request/finalisation response, storage metadata
verification, negative-owner test, and stale-object cleanup result. Expected:
both Maven commands exit `0`, Newman reports zero failed assertions, and no
signed URL/object key becomes readable across owners.

## Merge and forward-fix note

Squash-merge this branch only after required CI/review, its focused and full
Maven suites exit `0`, and the mapped Newman folder reports zero failed
assertions. Treat V1–V11 as immutable after Branch 01 merges: if a schema
correction is required, add the next unused V12+ forward migration and document
upgrade, compatibility, and rollback impact in the same PR. Roll back application
behaviour with the previous image/commit; do not destructively reverse a
migration already applied to a shared environment.

## Postman mapping

Folder `15 - Media Upload`:

1. Create an upload for the primary user; capture `mediaAssetId`, `uploadUrl`,
   required upload headers, and expiry.
2. Upload the bundled small JPEG fixture to `uploadUrl`. This request must omit
   the Backend bearer token.
3. Finalise the primary user's matching asset; expect `READY`.
4. Repeat finalisation; expect the documented idempotent result.
5. Try to finalise the same asset as the secondary user; expect
   non-disclosing `404`.
6. Create an unsupported/oversized upload; expect `400` with stable field code.
7. Create but do not upload an asset, then finalise; expect the safe storage
   mismatch/not-found error.
8. Attach the ready asset to a food-record request and retrieve the record.
9. Delete the asset as owner; assert it can no longer be attached.

The directly importable collection includes request/test scripts. Because a
presigned upload goes to a dynamic storage URL, the runner must allow outbound
access to the configured local/staging object store.

## Pull Request document

**Title**

```text
feat(media): add bounded owner-controlled upload workflow
```

**Body**

```markdown
## Summary

Adds the optional, server-controlled media upload/finalisation workflow after
the core record and integration paths are stable.

## Implements

- BE-030
- owned pending asset creation
- bounded presigned upload instructions
- trusted metadata verification and `PENDING -> READY` finalisation
- record attachment permission checks and stale/deletion recovery

## Not included

- public bucket access
- arbitrary client object keys
- image transformation/moderation
- general cloud credentials

## Contract

- OpenAPI paths/schemas: [link]
- content types and byte limit: [values]
- upload TTL: [value]
- record-to-media cardinality: [decision]
- read delivery method: [short-lived URL / Backend route]

## Database / forward fix

- Uses immutable V5 `media_asset` metadata and record-owner foreign keys.
- New migration: [None / V12+ filename and reason]
- Object/database cleanup and rollback impact: [describe]

## Security

- owner/non-owner matrix: [PASS + evidence]
- public-access-block/storage policy: [PASS + evidence]
- URL/signature log-redaction test: [PASS]
- metadata mismatch tests: [PASS]

## Verification

- media tests: [PASS]
- full Maven tests: [PASS]
- Postman `15 - Media Upload`: [PASS]
- stale cleanup demonstration: [PASS]

## Configuration

Added variable names only: [list]. No credentials or signed URLs are committed.

## Risks / follow-up

[Storage lifecycle, cost, scanning, cleanup, or provider-specific follow-up.]

## Architecture / data flow

[Describe controller -> application use case -> domain policy -> outbound port /
adapter flow. State where authorization is enforced in SQL and prove every
remote call occurs outside a database transaction. Use `N/A` with a reason only
when a boundary does not exist in this branch.]

## Delivery evidence

- Linked BE/UC issues: [BE-___](https://tracker.example.test/BE-___), [UC-___](https://tracker.example.test/UC-___) — replace both with approved tracker URLs
- Exact commands executed and exit/results: [paste verbatim]
- Postman folder and Newman report: [folder + zero-failure result + report link]
- Redacted response excerpt or screenshot: [link]
- Configuration variable names added/changed (no values): [None / list]
- Explicit non-scope: [list]
- Migration rollback/forward-fix impact: [None / next V12+ migration and impact]
- Risks/follow-ups and owners: [None / linked items]
- Cross-repository actions: [None / linked actions]

## Checklist

- [ ] Client cannot influence object key, bucket, or ACL
- [ ] Only owned `READY` assets can attach
- [ ] Storage metadata is verified before finalisation
- [ ] Presigned data is redacted
- [ ] Failure cleanup is retry-safe
- [ ] Runbook and Postman evidence are attached
```
