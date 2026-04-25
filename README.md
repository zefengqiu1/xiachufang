# Recipe Agent

一个基于 Spring Boot 的本地菜谱问答服务，前端提供最小聊天界面。

## 后端

- 启动端口：`8091`
- 主入口：`com.webcrawler.recipe.app.RecipeApplication`
- 数据文件：`caipu.txt`

常用接口：

- `POST /api/chat`
- `POST /api/recipes/ask`
- `GET /api/recipes`
- `GET /api/recipes/{id}`
- `GET /api/health`

运行：

```bash
mvn spring-boot:run
```

## 前端

前端目录：`chat-ui`

运行：

```bash
cd chat-ui
npm install
npm run dev
```

前端页面会直接调用后端 `/api/chat`，输入例如 `宫保鸡丁怎么做？` 即可返回菜谱。
