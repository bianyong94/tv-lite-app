# 新增接口整理

本文整理当前页面接入时新增的 3 个接口，包含接口地址、参数、返回结构。

## 1. 专题列表

### 接口地址
`GET /movie/topic`

### 参数
- 无业务参数
- 实际请求会由客户端自动附加 `pack` 和 `signature`
- 通用请求头与项目内其他接口一致

### 返回数据格式
```json
{
  "msg": "获取成功",
  "data": {
    "list": [
      {
        "id": 83,
        "name": "粽香端午 · 佳片相伴",
        "cover": "https://...",
        "view": "84",
        "description": "专题描述...",
        "create_time": 1781862976,
        "movie_count": 14
      }
    ],
    "total": 10,
    "page": 1,
    "pageSize": 10
  }
}
```

### 字段说明
- `data.list`：专题列表
- `id`：专题 id
- `name`：专题标题
- `cover`：专题封面图
- `view`：浏览量
- `description`：专题介绍
- `movie_count`：专题包含的影片数量

## 2. 专题详情

### 接口地址
`GET /movie/topic/{id}`

### 参数
- 路径参数 `id`：专题 id
- 例如：`/movie/topic/83`
- 实际请求同样会自动附加 `pack` 和 `signature`

### 返回数据格式
```json
{
  "msg": "获取成功",
  "data": {
    "id": 83,
    "name": "粽香端午 · 佳片相伴",
    "description": "专题描述...",
    "cover": "https://...",
    "view": "83",
    "create_time": 1781862976,
    "update_time": 1781863645,
    "movies": [
      {
        "id": "lz1",
        "name": "风味人间第四季",
        "year": "2022",
        "cover": "https://...",
        "dynamic": "HD中字",
        "type_name": "",
        "collect_count": 3,
        "label": "高分推荐"
      }
    ]
  }
}
```

### 字段说明
- `data.movies`：专题下的资源列表
- `name`：资源名称
- `year`：年份
- `cover`：资源封面图
- `dynamic`：状态文案，例如 `HD中字`、`全集完结`
- `type_name`：资源类型
- `collect_count`：收藏数
- `label`：资源角标

## 3. 榜单资源

### 接口地址
`GET /movie/ranking/data`

### 参数
- 查询参数 `id`：榜单 id
- 例如：`/movie/ranking/data?id=6`
- 实际请求同样会自动附加 `pack` 和 `signature`

### 返回数据格式
```json
{
  "msg": "获取成功",
  "data": [
    {
      "id": "NLgkK",
      "name": "火遮眼2026",
      "cover": "https://...",
      "year": "2026",
      "dynamic": "TC",
      "blurb": "简介...",
      "type_name": "电影",
      "tags": [
        { "tag_id": 1, "name": "剧情" },
        { "tag_id": 3, "name": "动作" }
      ],
      "hot": "0",
      "popularity_score": 1
    }
  ]
}
```

### 字段说明
- `data`：榜单对应的资源数组
- `id`：资源 id
- `name`：资源名称
- `cover`：封面图
- `year`：年份
- `dynamic`：状态文案
- `blurb`：简介
- `type_name`：类型名
- `tags`：标签列表
- `hot`：热度值
- `popularity_score`：排序分值

## 备注

- 以上 3 个接口在当前应用中都属于加密请求，业务参数会被打进 `pack`。
- 如果你还要我把 `GET /movie/ranking/list` 和 `GET /movie/weekly?week_day=` 也整理进去，我可以继续补成完整接口说明。
