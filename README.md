# 🚌 BusSystem - 城市公交查询与管理系统

> 一个基于 **Spring Boot 3** + **Vue 3** 的前后端分离公交查询系统。支持路径规划（直达/换乘）、站点搜索、地图可视化展示以及后台数据管理。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![SpringBoot](https://img.shields.io/badge/SpringBoot-3.2.0-green.svg)
![Vue](https://img.shields.io/badge/Vue-3.3.4-brightgreen.svg)
![ElementPlus](https://img.shields.io/badge/ElementPlus-2.4.0-blue.svg)

## 📖 项目简介

BusSystem 旨在为用户提供高效的公交换乘方案查询，同时为管理员提供便捷的线路与站点数据维护工具。
核心亮点包括：
- **智能路径规划**：基于 BFS 算法实现的直达与换乘路径搜索，支持“最少换乘”优先。
- **地图可视化**：集成 **百度地图 API**，在地图上绘制完整路线、标注起终点及换乘站点。
- **内存加速**：核心图数据（站点、线路关系）启动时加载至内存，查询响应速度极快。
- **后台管理**：提供简单的 RESTful API 接口用于管理公交网络数据。

## 🛠️ 技术栈

### 后端 (BusSystem_springboot)
- **核心框架**: Spring Boot 3.2.0
- **语言**: Java 17
- **数据库交互**: Spring Data JPA
- **数据库**: MySQL 8.0
- **API文档**: Knife4j (Swagger 3)
- **工具库**: Lombok

### 前端 (BusSystem_vue)
- **核心框架**: Vue 3 (Composition API)
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **网络请求**: Axios
- **地图组件**: 百度地图 (Baidu Map JS API)

## 🧩 功能模块

### 👤 用户端
1. **站点查询**：支持输入 ID 或站点名称（模糊匹配）搜索。
2. **线路详情**：查看某条线路的所有经过站点。
3. **换乘规划**：
   - 输入起点和终点，计算最优路线。
   - 支持 直达 和 一次换乘 方案。
   - 显示详细的换乘策略（乘坐线路、经过站数、预估时间）。
4. **地图展示**：路线高亮显示，自动标注关键节点。

### 🔧 管理端 (API)
1. **站点管理**：增加、删除站点。
2. **线路管理**：增加线路（包含方向、首末班时间、发车间隔及站点序列）、删除线路。

## 🚀 快速开始

### 1. 环境准备
- JDK 17+
- Node.js 16+
- MySQL 8.0+
- Maven 3.6+

### 2. 数据库配置
1. 创建数据库 `bus`。
2. 导入项目根目录下的 `exported_data.sql` 文件（如果有）以初始化表结构和数据。
3. 如果没有 SQL 文件，Spring Boot (JPA) 会在启动时自动创建表结构 (`ddl-auto: update`)。

### 3. 后端启动
1. 进入后端目录：
   ```bash
   cd BusSystem_springboot-main/BusSystem
   ```
2. 修改配置文件 src/main/resources/application.yml，配置你的 MySQL 账号密码：
    ```YAML
    spring:
      datasource:
        url: jdbc:mysql://localhost:3306/bus?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
        username: root       # 你的数据库账号
        password: your_password # 你的数据库密码
    ```
3. 启动项目：
   使用 IDEA 运行 BusSystemApplication.java。
   或者使用命令行：mvn spring-boot:run。
4. 服务默认运行在端口 8080。
   接口文档地址：http://localhost:8080/doc.html

### 4. 前端启动
1. 进入前端目录： 
   ```bash
   cd BusSystem_vue-main/BusSystem
   ```
2. 安装依赖： 
   ```bash
   npm install
   ```
3. 启动开发服务器：
   ```bash
   npm install
   ```
4. 访问控制台输出的地址（通常是 http://localhost:5173）。

## 📂 核心配置说明
### 百度地图 AK 配置
前端地图功能依赖百度地图 AK（Access Key）。 请在 src/views/user/BusQuery.vue 中替换为你自己的 Key（如果当前 Key 失效）：
```JavaScript
const mapAK = '你的百度地图AK'; 
```

### 算法参数
路径规划的最大换乘次数默认为 1。 可在 BusQueryController.java 中修改默认参数：
```Java
@RequestParam(defaultValue = "1") int maxTransfers
```

## 📝 API 示例
后端集成了 Knife4j，启动后访问 http://localhost:8080/doc.html 可在线调试接口。

### 常用接口：
GET /api/routes/plan?start=站点A&end=站点B : 路径规划
GET /api/stations/search?query=中山 : 搜索站点
POST /api/admin/line : 添加线路 (JSON Body)

## 🤝 贡献与反馈
欢迎提交 Issue 或 Pull Request 来改进本项目。
Created by Triumphant-i
