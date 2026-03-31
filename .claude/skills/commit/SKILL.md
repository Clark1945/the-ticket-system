---
name: commit
description: 執行提交前檢查（build、linting、測試、code review）並建立 git commit。當使用者說「commit」、「提交」、「幫我提交」、「git commit」時，一定要使用這個 Skill 執行完整的提交前 SOP 流程，不要直接執行 git commit。
allowed-tools: Bash, Read, Grep, Glob, Edit
---

# Git Commit Skill（Java / Spring Boot）

建立 Git commit 前，針對 Java / Spring Boot 專案執行完整的檢查流程。

---

## 0. 偵測建置工具

在執行任何指令前，先判斷專案使用 Maven 還是 Gradle：

- 若存在 `pom.xml` → 使用 `./mvnw`（或 `mvn`）
- 若存在 `build.gradle` → 使用 `./gradlew`（或 `gradle`）
- 若兩者皆存在 → 優先用 Maven

後續所有指令以偵測結果為準，以下以 `<BUILD>` 代替。

---

## 執行流程

### 階段 1：編譯與靜態分析

#### 1-1. 編譯確認
```bash
# Maven
./mvnw compile -q

# Gradle
./gradlew compileJava --quiet
```

- 若編譯失敗 → 立即停止，回報錯誤並請使用者修復

#### 1-2. Checkstyle（若專案有設定）
```bash
# Maven
./mvnw checkstyle:check -q

# Gradle
./gradlew checkstyleMain --quiet
```

- 若插件不存在，跳過此步驟
- 若有違規 → 列出問題，詢問是否修復後再繼續

#### 1-3. SpotBugs（若專案有設定）
```bash
# Maven
./mvnw spotbugs:check -q

# Gradle
./gradlew spotbugsMain --quiet
```

- 同上，若插件不存在則跳過

---

### 階段 2：執行測試
```bash
# Maven
./mvnw test -q

# Gradle
./gradlew test
```

- 測試全部通過 → 繼續
- 若有失敗 → 立即停止，顯示失敗的測試名稱與錯誤訊息，請使用者修復
- 若專案沒有任何測試，顯示警告但繼續流程

---

### 階段 3：Code Review

#### 3-1. 檢視變更範圍
```bash
git diff --stat
git diff
```

#### 3-2. Code Review 檢查清單

針對每個變更的 `.java` 檔案逐一確認：

**通用品質**
- [ ] 程式碼邏輯是否正確，邊界情況（null、空集合、負數）是否處理
- [ ] 命名是否清晰、符合 Java 慣例（camelCase、PascalCase）
- [ ] 是否有重複程式碼可以抽出 private method 或 utility
- [ ] 錯誤處理是否完整（try-catch 範圍是否過大或遺漏）
- [ ] Log 層級是否正確（不該用 e.printStackTrace()，應用 SLF4J）

**Spring Boot 專項**
- [ ] @Transactional 使用是否正確（是否在 public 方法、是否有 self-invocation 問題）
- [ ] 是否有潛在的 N+1 Query 問題（@OneToMany / @ManyToOne 是否應加 FETCH JOIN）
- [ ] Bean 注入方式是否統一（建議 Constructor Injection，避免 @Autowired field injection）
- [ ] @RestController 回傳值是否符合預期格式（DTO 而非直接回傳 Entity）
- [ ] 是否有硬編碼的設定值應移至 application.properties / application.yml
- [ ] 非同步方法（@Async）是否正確設定 Executor 與例外處理

**安全性**
- [ ] 是否有 SQL Injection 風險（應使用 JPA named parameter）
- [ ] 是否有硬編碼的密碼、Token、Secret Key
- [ ] 敏感資料是否不小心輸出至 Log

#### 3-3. 若發現問題

- 整理成清單，說明問題位置與建議修法
- 詢問使用者：「以上問題是否要先修復再提交？」
- 等待使用者確認後再繼續

---

### 階段 4：準備 Commit

#### 4-1. 確認 Git 狀態
```bash
git status
git log --oneline -5
```

#### 4-2. 建立 Commit
```bash
git add <相關檔案>
git commit -F- <<'EOF'
<type>: <簡短描述>

<詳細說明>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
```

---

## Commit Message 格式

| Type | 說明 |
|------|------|
| feat | 新功能 |
| fix | Bug 修復 |
| refactor | 重構（不改變功能） |
| docs | 文件更新 |
| test | 新增或修改測試 |
| chore | 雜項（設定、依賴更新等） |
| perf | 效能優化 |
| style | 格式調整（不影響邏輯） |

---

## 完成後回報
```
### ✅ 檢查結果
- [ ] 編譯通過
- [ ] Checkstyle 通過（或跳過）
- [ ] SpotBugs 通過（或跳過）
- [ ] 測試通過（共 N 個測試）
- [ ] Code Review 完成（無問題 / 已修復 N 項）

### 📝 Commit 資訊
- Commit hash: xxxxxxx
- 變更檔案數: N
- 變更摘要: <type>: <描述>
```

---

## 注意事項

- 不要執行 `git push`，除非使用者明確要求
- 若有未追蹤的重要檔案，詢問是否加入
- 若使用者已提供 commit 訊息說明，優先採用
- target/ 和 build/ 目錄下的檔案不加入 git
- 若 pom.xml 或 build.gradle 有變動，一併說明依賴異動內容