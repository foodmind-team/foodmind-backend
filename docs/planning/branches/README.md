# Feature-Branch Plan Contract

Each numbered file in this directory is the implementation hand-off for one
branch in `../backend-branch-roadmap.md`.

Every plan must contain:

1. branch metadata and dependencies;
2. scope and explicit non-scope;
3. affected public/private contracts and database objects;
4. exact target files;
5. ordered implementation steps at class/method/query level;
6. required unit, MVC, PostgreSQL, security, architecture, and contract tests;
7. exact commit boundaries and Conventional Commit messages;
8. verification commands and expected results;
9. directly corresponding Postman requests/tests and variables;
10. ready-to-copy Pull Request title and body; and
11. merge and rollback/forward-fix notes.

The code examples in these documents are implementation guidance, not generated
application source. The repository owner writes and reviews the Java/Spring
implementation.

## Ordered hand-offs

1. [PostgreSQL, Flyway, and test foundation](01-backend-test-postgres.md)
2. [Common API errors and correlation](02-common-api-errors.md)
3. [Authentication and sessions](03-auth-session.md)
4. [User profile and preferences](04-user-preferences.md)
5. [Controlled catalogue and seed data](05-catalogue-seed.md)
6. [Food records and Meal Note view](06-food-records.md)
7. [Trusted groups and visibility](07-groups-visibility.md)
8. [Drink records and combined history](08-drink-records-history.md)
9. [Want to Try, Search, and Explore](09-want-to-try-search.md)
10. [Deterministic recommendation fallback](10-recommendation-fallback.md)
11. [Recommendation Agent integration](11-recommendation-agent.md)
12. [Recommendation feedback and ML export](12-recommendation-feedback.md)
13. [Cooking plans](13-cooking-plan.md)
14. [Chat grounding](14-chat-grounding.md)
15. [Dashboard and weekly recap](15-dashboard-recap.md)
16. [Bounded media upload](16-media-upload.md)
17. [Cloud, security, UAT, and release freeze](17-cloud-security-uat.md)

Branch 01 commits the complete V1–V11 SQL foundation. Later branches consume
those immutable objects and create a V12+ forward migration only when
implementation or measured query-plan evidence proves a correction is needed.
