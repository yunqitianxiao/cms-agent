# M1-S3 · 登录与身份：验收判据落位

S3 交付的 16 个类（`security/auth/*`）与 main 上已有的实现是**同一套功能的两种写法**：
main 的 `security.LoginInterceptor` / `security.UserContext` / `security.LoginUser` /
`security.user.service.AuthService` / `security.user.controller.AuthController` 已经覆盖了 S3 的全部能力，
而且往后走了一步（`PermissionAspect` 判权、`RoleService` 角色管理）。

因此本模块**不新增 S3 的类**：两个 `@RequestMapping("/api/auth")` 会让 Spring 报
`Ambiguous mapping` 启动失败，重复的拦截器还会把每个请求拦两遍。
这里只把 S3 的验收判据逐条落到 main 的现有实现上，并补掉唯一一处行为差异。

## 判据对照

| S3 的判据 | main 的实现 | 状态 |
|---|---|---|
| 未登录访问 `/api/**` → 401 + `40100` | `LoginInterceptor.preHandle` 无 userId 时 `reject(UNAUTHORIZED)` | 已有 |
| 账号已停用 → 401 + `40102`，Session 立即失效 | **本次补齐**：`AuthService.loadLoginUser` 对停用账号抛 `ACCOUNT_DISABLED`，拦截器捕获后作废 Session 并回 `40102` | 本次修复 |
| Session 里只存 `userId` | `SessionKeys.LOGIN_USER_ID`，登录成功写一次新 Session（防会话固定） | 已有 |
| 登录成功返回 `userId` / `username` | `AuthController.login` 返回 `LoginUser` | 已有 |
| 口令错与账号不存在都回 `40101` | `AuthService.authenticate` 两条路径都抛 `LOGIN_FAILED` | 已有 |
| 注册落库的是 BCrypt 密文 | `AuthService.register` 走 `PasswordEncoder.encode` | 已有 |
| 重名回 `40902` | `AuthService.register` 先查重抛 `DUPLICATE`，唯一索引并发兜底 | 已有 |

唯一的差异是"停用账号"这一格：原先 `loadLoginUser` 对"已删除"和"已停用"都返回 `null`，
拦截器统一回 `40100`。结果是用户拿正确口令反复登录也只会看到"未登录"，
真正的原因（被管理员停用）永远露不出来；前端也没法决定该跳登录页还是该提示"账号已停用"。

## 本次改动

1. `security/user/service/AuthService#loadLoginUser`
   - 用户不存在 → 仍返回 `null`（对应 `40100`）
   - 用户存在但 `status != 1` → 抛 `BizException(ACCOUNT_DISABLED)`（对应 `40102`）
2. `security/LoginInterceptor#preHandle`
   - 捕获 `BizException`，按异常里的 `ResultCode` 回响应，并作废旧 Session
   - `reject` 改为接收 `ResultCode`，`/api/**` 回 JSON（状态码取错误码自带的 HTTP 状态），
     其余路径仍跳 `/login?next=...`
3. 新增 `src/test/.../AccountDisabledBoundaryTest`（2 条，能失败）

## 怎么验

```bash
mvn -B test -Dtest=AccountDisabledBoundaryTest   # 2 条
mvn -B test                                      # 全量
```

两条红验证都实测过：

- 删掉拦截器里 `catch (BizException e)` 那段 → `disabledAccountReturns40102` 报错（异常直接冒泡）；
- 把 `loadLoginUser` 改回"停用也返回 null" → `serviceSeparatesDisabledFromDeleted` 失败
  （`assertThrows` 拿不到 `BizException`）。

## 手工复验（需要库）

1. 用 `admin` 登录，进用户管理把 `author` 停用；
2. 拿 `author` 的旧会话（另开一个浏览器标签）请求任意 `/api/**`；
3. 期望：HTTP 401，响应体 `{"code":40102,"message":"账号已被停用"}`，再次请求已无 Session（跳登录页）。
