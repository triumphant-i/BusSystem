# 长沙公交查询系统 - 接口文档

## 一、核心文件

### `try_open.py` - 主要查询和路径规划文件

这是系统的核心文件，包含所有查询、路径规划和管理功能。

---

## 二、函数接口说明

### 1. 初始化

#### `load_data()`
从数据库加载所有数据到内存，必须在使用其他查询功能前调用。

```python
import try_open as db
db.load_data()
```

---

### 2. 基础查询接口

#### `get_all_stations()`
获取所有站点列表。

**返回值：** 站点字典列表
```python
[
    {'Station_ID': 1, 'Station_NAME': '火车站', 'Longitude': 113.087559, 'Latitude': 28.251818},
    ...
]
```

#### `get_all_routes()`
获取所有线路列表。

**返回值：** 线路字典列表
```python
[
    {
        'Line_order': 1,
        'Line_name': '1路',
        'Direction': '',
        'St': '06:30:00',
        'Ft': '22:30:00',
        'Interval_time': 5,
        'stations': [1, 2, 3, ...]  # 站点ID列表
    },
    ...
]
```

---

### 3. 站点查询

#### `find_stations(query)`
模糊搜索站点。

**参数：**
- `query`: 站点ID（整数）或站点名称（字符串）

**返回值：** 匹配的站点列表

**示例：**
```python
# 按ID查询
stations = db.find_stations(1)  # 返回 [{'Station_ID': 1, 'Station_NAME': '火车站', ...}]

# 按名称模糊查询
stations = db.find_stations('火车')  # 返回所有包含"火车"的站点
```

---

### 4. 关联查询

#### `get_lines_by_station(station_identifier)`
查询某站点的所有线路。

**参数：**
- `station_identifier`: 站点ID（整数）或站点名称（字符串）

**返回值：** 线路字典列表

**示例：**
```python
lines = db.get_lines_by_station(1)        # 按ID查询
lines = db.get_lines_by_station('火车站')  # 按名称查询
```

#### `get_stations_by_line(line_identifier)`
查询某线路的所有站点（按顺序）。

**参数：**
- `line_identifier`: 线路编号（整数）或线路名称（字符串）

**返回值：** 站点字典列表（按线路顺序）

**示例：**
```python
stations = db.get_stations_by_line(1)      # 按编号查询
stations = db.get_stations_by_line('1路')  # 按名称查询
```

---

### 5. 路径规划（核心功能）

#### `find_routes_between(start, end, max_transfers=2)`
查找从起点到终点的乘车方案。

**参数：**
- `start`: 起点站点ID或名称
- `end`: 终点站点ID或名称
- `max_transfers`: 最多换乘次数（默认2次）

**返回值：** 按(换乘次数, 总停数)排序的方案列表

**方案结构：**
```python
{
    'segments': [  # 乘车段列表
        {
            'line_order': 1,
            'line_name': '1路',
            'from_sid': 1,
            'to_sid': 5,
            'stations': [1, 2, 3, 4, 5],  # 经过的站点ID
            'stops_count': 4  # 停靠站数
        },
        ...
    ],
    'transfers': 1,      # 换乘次数
    'total_stops': 10    # 总停靠站数
}
```

**示例：**
```python
# 查询从火车站到东塘的路线
routes = db.find_routes_between('火车站', '东塘')

# 查询最多换乘1次的路线
routes = db.find_routes_between(1, 15, max_transfers=1)

# 打印结果
for route in routes:
    print(f"换乘{route['transfers']}次，共{route['total_stops']}站")
    for seg in route['segments']:
        print(f"  乘坐 {seg['line_name']}: {seg['from_sid']} -> {seg['to_sid']}")
```

---

### 6. 管理员功能

#### `add_station(station_id, station_name)`
添加新站点。

**参数：**
- `station_id`: 站点ID（整数）
- `station_name`: 站点名称（字符串）

**返回值：** `(success: bool, message: str)`

**限制：** 站点名不能重复

#### `delete_station(station_identifier)`
删除站点（级联删除相关记录）。

**参数：**
- `station_identifier`: 站点ID或名称

**返回值：** `(success: bool, message: str)`

#### `add_line(line_order, line_name, direction, st, ft, interval_time, station_ids)`
添加新线路。

**参数：**
- `line_order`: 线路编号（整数）
- `line_name`: 线路名称（字符串）
- `direction`: 方向（字符串，如'上'、'下'）
- `st`: 首班时间（字符串，格式'HH:MM:SS'）
- `ft`: 末班时间（字符串，格式'HH:MM:SS'）
- `interval_time`: 发车间隔（整数，分钟）
- `station_ids`: 站点ID列表

**返回值：** `(success: bool, message: str)`

**限制：** 
- 线路名不能重复
- 每个站点最多6条线路

**示例：**
```python
success, msg = db.add_line(
    line_order=100,
    line_name='测试线',
    direction='上',
    st='06:00:00',
    ft='22:00:00',
    interval_time=10,
    station_ids=[1, 2, 3, 4, 5]
)
```

#### `delete_line(line_identifier)`
删除线路。

**参数：**
- `line_identifier`: 线路编号或名称

**返回值：** `(success: bool, message: str)`

#### `add_station_to_line(line_identifier, station_identifier, sequence_no)`
为已有线路添加站点。

**参数：**
- `line_identifier`: 线路编号或名称
- `station_identifier`: 站点ID或名称
- `sequence_no`: 站点在线路中的序号（整数）

**返回值：** `(success: bool, message: str)`

---

## 三、数据库导出

### `exported_data.sql`
包含完整数据的SQL文件，包括：
- 267个站点（含经纬度）
- 77条线路
- 1068条线路-站点关系

**使用方法：**
```sql
-- 在MySQL中执行
SOURCE exported_data.sql;
```

或使用 `insert_data.py` 脚本导入。

---

## 四、完整使用示例

```python
import try_open as db

# 1. 加载数据
db.load_data()

# 2. 查询所有站点
all_stations = db.get_all_stations()
print(f"共有 {len(all_stations)} 个站点")

# 3. 查找站点
stations = db.find_stations('火车')
for s in stations:
    print(f"{s['Station_ID']}: {s['Station_NAME']}")

# 4. 查询站点的线路
lines = db.get_lines_by_station('火车站')
print(f"火车站有 {len(lines)} 条线路")

# 5. 查询线路的站点
stations = db.get_stations_by_line('1路')
print(f"1路共有 {len(stations)} 个站点")

# 6. 路径规划
routes = db.find_routes_between('火车站', '东塘', max_transfers=2)
print(f"找到 {len(routes)} 条路线")

for i, route in enumerate(routes, 1):
    print(f"\n方案{i}: 换乘{route['transfers']}次，共{route['total_stops']}站")
    for seg in route['segments']:
        from_name = db.stations[seg['from_sid']]['Station_NAME']
        to_name = db.stations[seg['to_sid']]['Station_NAME']
        print(f"  {seg['line_name']}: {from_name} -> {to_name} ({seg['stops_count']}站)")

# 7. 管理功能（需要权限）
success, msg = db.add_station(999, '新站点')
print(msg)
```

---

## 五、数据库表结构

### stations 表
```sql
CREATE TABLE stations (
    Station_ID INT PRIMARY KEY,
    Station_NAME CHAR(20),
    Longitude DECIMAL(10, 7),
    Latitude DECIMAL(10, 7),
    INDEX idx_station_name (Station_NAME)
);
```

### roads 表
```sql
CREATE TABLE roads (
    Line_order INT PRIMARY KEY,
    Line_name CHAR(4),
    Direction CHAR(2),
    St TIME,
    Ft TIME,
    Interval_time INT,
    INDEX idx_line_name (Line_name)
);
```

### line_stations 表
```sql
CREATE TABLE line_stations (
    Line_order INT NOT NULL,
    Station_ID INT NOT NULL,
    Sequence_No INT NOT NULL,
    PRIMARY KEY (Line_order, Sequence_No),
    FOREIGN KEY (Line_order) REFERENCES roads(Line_order),
    FOREIGN KEY (Station_ID) REFERENCES stations(Station_ID),
    INDEX idx_station_lookup (Station_ID)
);
```

