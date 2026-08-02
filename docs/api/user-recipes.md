# User recipe API (C-08)

用户自有菜谱与受控 catalogue 分离，所有权由认证 access token 推导，客户端不能提交 owner ID。

## Endpoints

- `GET /api/v1/recipes?page=0&size=20` — 当前用户的未删除菜谱，按 `updatedAt DESC` 分页。
- `GET /api/v1/recipes/{id}` — 当前用户详情；非本人或已删除资源统一安全 404。
- `POST /api/v1/recipes` — 创建菜谱。
- `PUT /api/v1/recipes/{id}` — 使用 `If-Match: "<version>"` 乐观更新；版本冲突返回 409。
- `DELETE /api/v1/recipes/{id}` — 软删除，返回 204。

请求字段：`name`、`servings`、可选 `imageUrl`、`tags`、`allergenHints`、非空 `ingredients` 与 `steps` 数组。响应额外包含 `id`、时间戳和 `version`。

持久化由 `V13__owner_recipes.sql` 的 `user_recipe` 表提供；历史 Cooking Plan 不引用可变行，而继续读取生成时的不可变 snapshot。已通过 Maven compile、应用层测试及 Testcontainers PostgreSQL 流（迁移、owner 隔离、CRUD、If-Match、软删除）；公开契约已同步到 `src/main/resources/openapi/openapi.yaml`，仍需跨客户端真实登录 E2E 才能关闭 C-08。
