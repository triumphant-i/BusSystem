import os
import sys
import pymysql

# 配置：修改下面的变量或通过环境变量覆盖
DB_HOST = os.getenv('DB_HOST', 'localhost')
# 请根据你的 MySQL 用户修改，常见为 'root'
DB_USER = os.getenv('DB_USER', 'root')
# 你提供的密码
DB_PASSWORD = os.getenv('DB_PASSWORD', '20050410Ky6!')
# 请把数据库名改为你实际使用的数据库
DB_NAME = os.getenv('DB_NAME', 'bus')


def get_connection():
    try:
        return pymysql.connect(
            host=DB_HOST,
            user=DB_USER,
            password=DB_PASSWORD,
            database=DB_NAME,
            charset='utf8mb4',
            cursorclass=pymysql.cursors.DictCursor,
            autocommit=True
        )
    except pymysql.MySQLError as e:
        print('无法连接到 MySQL：')
        print(f'  host={DB_HOST} user={DB_USER} database={DB_NAME}')
        print('请检查 MySQL 服务是否已启动，用户/密码/数据库名是否正确，或网络访问是否被阻止。')
        print('错误详情:', e)
        sys.exit(1)


def create_tables_and_sample_data():
    """创建三张表（如果不存在）并插入示例数据。可运行 `python try_open.py init` 执行。"""
    stmts = [
        """
        CREATE TABLE IF NOT EXISTS stations (
            Station_ID INT PRIMARY KEY,
            Station_NAME CHAR(20),
            Longitude DECIMAL(10, 7),
            Latitude DECIMAL(10, 7),
            INDEX idx_station_name (Station_NAME)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """,
        """
        CREATE TABLE IF NOT EXISTS roads (
            Line_order INT PRIMARY KEY,
            Line_name CHAR(4),
            Direction CHAR(2),
            St TIME,
            Ft TIME,
            Interval_time INT,
            INDEX idx_line_name (Line_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """,
        """
        CREATE TABLE IF NOT EXISTS line_stations (
            Line_order INT NOT NULL,
            Station_ID INT NOT NULL,
            Sequence_No INT NOT NULL,
            PRIMARY KEY (Line_order, Sequence_No),
            FOREIGN KEY (Line_order) REFERENCES roads(Line_order),
            FOREIGN KEY (Station_ID) REFERENCES stations(Station_ID),
            INDEX idx_station_lookup (Station_ID)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """
    ]

    sample_inserts = [
        # stations
        ("INSERT INTO stations (Station_ID, Station_NAME) VALUES (%s, %s)", [(1, '中山站'), (2, '人民广场'), (3, '市政府')]),
        # roads
        ("INSERT INTO roads (Line_order, Line_name, Direction, St, Ft, Interval_time) VALUES (%s, %s, %s, %s, %s, %s)",
         [(100, 'L01', '↑', '06:00:00', '23:00:00', 10), (200, 'L02', '→', '05:30:00', '22:30:00', 12)]),
        # line_stations
        ("INSERT INTO line_stations (Line_order, Station_ID, Sequence_No) VALUES (%s, %s, %s)",
         [(100, 1, 1), (100, 2, 2), (200, 2, 1), (200, 3, 2)])
    ]

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            for s in stmts:
                cursor.execute(s)

            # 插入示例数据，使用忽略重复的方式（只在需要时插入）
            for sql, rows in sample_inserts:
                for row in rows:
                    try:
                        cursor.execute(sql, row)
                    except Exception:
                        # 可能因为主键冲突，忽略当前行
                        pass

        print('表已创建（如尚未存在）并尝试插入示例数据。')
    finally:
        conn.close()


def read_table(table_name):
    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            sql = f"SELECT * FROM `{table_name}`"
            cursor.execute(sql)
            results = cursor.fetchall()
            if not results:
                print(f"表 `{table_name}` 为空或不存在。")
                return

            # 打印列名
            cols = list(results[0].keys())
            print('\n表:', table_name)
            print('列名:', cols)
            for row in results:
                print(row)
    finally:
        conn.close()


def read_all_tables():
    for t in ['stations', 'roads', 'line_stations']:
        read_table(t)


# -----------------------------
# 内存数据结构与查询/检索接口
# -----------------------------
# 模块级缓存（在调用 `load_data()` 后填充）
stations = {}  # Station_ID -> {'Station_ID', 'Station_NAME'}
lines = {}     # Line_order -> {'Line_order','Line_name','Direction','St','Ft','Interval_time','stations': [Station_ID,...]}
station_to_lines = {}  # Station_ID -> set(Line_order)


def load_data():
    """从数据库读取三张表并构建内存索引。"""
    global stations, lines, station_to_lines
    stations = {}
    lines = {}
    station_to_lines = {}

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute('SELECT Station_ID, Station_NAME, Longitude, Latitude FROM stations')
            for row in cursor.fetchall():
                sid = int(row['Station_ID'])
                stations[sid] = {
                    'Station_ID': sid,
                    'Station_NAME': row['Station_NAME'],
                    'Longitude': float(row['Longitude']) if row['Longitude'] else None,
                    'Latitude': float(row['Latitude']) if row['Latitude'] else None
                }

            cursor.execute('SELECT Line_order, Line_name, Direction, St, Ft, Interval_time FROM roads')
            for row in cursor.fetchall():
                lo = int(row['Line_order'])
                lines[lo] = {
                    'Line_order': lo,
                    'Line_name': row.get('Line_name'),
                    'Direction': row.get('Direction'),
                    'St': row.get('St'),
                    'Ft': row.get('Ft'),
                    'Interval_time': row.get('Interval_time'),
                    'stations': []
                }

            # 按 Sequence_No 排序读取线路对应站点
            cursor.execute('SELECT Line_order, Station_ID, Sequence_No FROM line_stations ORDER BY Line_order, Sequence_No')
            for row in cursor.fetchall():
                lo = int(row['Line_order'])
                sid = int(row['Station_ID'])
                if lo not in lines:
                    # 如果 roads 中没有该线路，先创建占位
                    lines[lo] = {'Line_order': lo, 'Line_name': None, 'Direction': None, 'St': None, 'Ft': None, 'Interval_time': None, 'stations': []}
                lines[lo]['stations'].append(sid)
                station_to_lines.setdefault(sid, set()).add(lo)

    finally:
        conn.close()


def get_all_stations():
    """返回所有站点的列表（每项为 dict，包含 Station_ID 与 Station_NAME）。"""
    return list(stations.values())


def get_all_routes():
    """返回所有线路信息列表（每项为 dict）。"""
    return list(lines.values())


def find_stations(query):
    """模糊搜索站点名或通过精确 ID 查询。
    如果 query 是整数或只含数字，将优先当作 Station_ID 精确匹配。
    返回匹配的站点 dict 列表（可能为空）。"""
    res = []
    if query is None:
        return res
    q = str(query).strip()
    # 尝试按 ID 精确匹配
    if q.isdigit():
        sid = int(q)
        if sid in stations:
            return [stations[sid]]

    # 否则按名称模糊（不区分大小写，子串匹配）
    qlow = q.lower()
    for s in stations.values():
        name = (s.get('Station_NAME') or '')
        if qlow in name.lower():
            res.append(s)
    return res


def get_lines_by_station(station_identifier):
    """传入 Station_ID（int 或 数字字符串）或 Station_NAME（模糊），返回线路 dict 列表。"""
    matches = []
    # 如果是可能的 ID
    sid = None
    if isinstance(station_identifier, int) or (isinstance(station_identifier, str) and station_identifier.isdigit()):
        sid = int(station_identifier)
        if sid not in stations:
            return []
    else:
        found = find_stations(station_identifier)
        if not found:
            return []
        # 站点名称唯一，取第一个
        sid = found[0]['Station_ID']

    for lo in station_to_lines.get(sid, []):
        matches.append(lines.get(lo))
    return matches


def get_stations_by_line(line_identifier):
    """传入 Line_order（int/数字字符串）或 Line_name（字符串），返回按顺序的站点 dict 列表。"""
    lo = None
    if isinstance(line_identifier, int) or (isinstance(line_identifier, str) and line_identifier.isdigit()):
        lo = int(line_identifier)
    else:
        # 根据 Line_name 查找第一个匹配
        name = str(line_identifier)
        for v in lines.values():
            if v.get('Line_name') == name:
                lo = v['Line_order']
                break
    if lo is None or lo not in lines:
        return []
    return [stations[sid] for sid in lines[lo]['stations'] if sid in stations]


def _ensure_station_id(x):
    """辅助：将输入（id 或 名称）解析为单个 Station_ID 或返回 None。"""
    if x is None:
        return None
    if isinstance(x, int):
        return x if x in stations else None
    s = str(x).strip()
    if s.isdigit():
        sid = int(s)
        return sid if sid in stations else None
    found = find_stations(s)
    return found[0]['Station_ID'] if found else None


def find_routes_between(start, end, max_transfers=2):
    """查找从 start 到 end 的可行乘车方案，最多支持 `max_transfers` 次换乘（默认 2）。
    返回按 (换乘次数, 总停数) 排序的方案列表。每个方案为 dict：
      { 'segments': [ {line_order, line_name, from_sid, to_sid, stations:[...], stops_count}],
        'transfers': n, 'total_stops': m }
    """
    sid_start = _ensure_station_id(start)
    sid_end = _ensure_station_id(end)
    if sid_start is None or sid_end is None:
        return []
    if sid_start == sid_end:
        return [{'segments': [], 'transfers': 0, 'total_stops': 0}]

    # 候选方案集合
    candidates = []

    # 直接线路（0 次换乘）
    for lo, info in lines.items():
        seq = info['stations']
        if sid_start in seq and sid_end in seq:
            i1 = seq.index(sid_start)
            i2 = seq.index(sid_end)
            stops = abs(i2 - i1)
            seg = {
                'line_order': lo,
                'line_name': info.get('Line_name'),
                'from_sid': sid_start,
                'to_sid': sid_end,
                'stations': seq[i1:i2+1] if i1 <= i2 else list(reversed(seq[i2:i1+1])),
                'stops_count': stops
            }
            candidates.append({'segments': [seg], 'transfers': 0, 'total_stops': stops})

    # 一次换乘与两次换乘
    # 用 BFS 在线路图上搜索，节点为 (line_order, station_index)
    # 简化方法：先枚举起点所属线路，然后枚举终点所属线路，寻找路径最多通过 max_transfers+1 条线路。
    start_lines = station_to_lines.get(sid_start, set())
    end_lines = station_to_lines.get(sid_end, set())

    # 如果 start_lines 或 end_lines 为空，则无解
    if not start_lines or not end_lines:
        # 仍返回已有的直接方案（可能为空）
        return sorted(candidates, key=lambda x: (x['transfers'], x['total_stops']))

    # 枚举线路路径长度 2..max_transfers+1
    from itertools import product

    # precompute transfer stations per pair of lines
    def common_stations(l1, l2):
        return set(lines[l1]['stations']) & set(lines[l2]['stations'])

    # 搜索线路序列（长度 2..max线路数）
    max_lines = max_transfers + 1
    line_list = list(lines.keys())
    # For small graph, brute-force reasonable combinations between start_lines and end_lines
    # 枚举中间线路数从 2 到 max_lines
    for path_len in range(2, max_lines + 1):
        # 枚举起始线路为 start_lines
        # We'll search sequences l0->l1->...->l_{k-1} where l0 in start_lines and last in end_lines
        def dfs_build(path):
            if len(path) == path_len:
                if path[-1] in end_lines:
                    # 验证该线路序列是否可通过站点连接
                    # 找可行的转乘站集合序列
                    transfer_options = [[]]
                    feasible = True
                    for i in range(len(path) - 1):
                        commons = common_stations(path[i], path[i+1])
                        if not commons:
                            feasible = False
                            break
                        # 扩展 transfer options
                        new_opts = []
                        for opt in transfer_options:
                            for s in commons:
                                new_opts.append(opt + [s])
                        transfer_options = new_opts
                    if not feasible:
                        return

                    # 对于每种转乘站选择，构造完整段并计算总停数
                    for trans_stations in transfer_options:
                        # trans_stations length = path_len-1
                        segs = []
                        total_stops = 0
                        # first segment: start -> trans_stations[0]
                        first_line = path[0]
                        seq = lines[first_line]['stations']
                        try:
                            i_start = seq.index(sid_start)
                        except ValueError:
                            feasible = False
                            break
                        i_trans = seq.index(trans_stations[0])
                        stops = abs(i_trans - i_start)
                        segs.append({'line_order': first_line, 'line_name': lines[first_line].get('Line_name'), 'from_sid': sid_start, 'to_sid': trans_stations[0], 'stations': seq[i_start:i_trans+1] if i_start<=i_trans else list(reversed(seq[i_trans:i_start+1])), 'stops_count': stops})
                        total_stops += stops

                        # middle segments
                        ok = True
                        for i in range(1, len(path)-1):
                            l = path[i]
                            seq = lines[l]['stations']
                            s_from = trans_stations[i-1]
                            s_to = trans_stations[i]
                            try:
                                idx_from = seq.index(s_from)
                                idx_to = seq.index(s_to)
                            except ValueError:
                                ok = False
                                break
                            stops = abs(idx_to - idx_from)
                            segs.append({'line_order': l, 'line_name': lines[l].get('Line_name'), 'from_sid': s_from, 'to_sid': s_to, 'stations': seq[idx_from:idx_to+1] if idx_from<=idx_to else list(reversed(seq[idx_to:idx_from+1])), 'stops_count': stops})
                            total_stops += stops
                        if not ok:
                            continue

                        # last segment: trans_stations[-1] -> end
                        last_line = path[-1]
                        seq = lines[last_line]['stations']
                        try:
                            i_trans_last = seq.index(trans_stations[-1])
                            i_end = seq.index(sid_end)
                        except ValueError:
                            continue
                        stops = abs(i_end - i_trans_last)
                        segs.append({'line_order': last_line, 'line_name': lines[last_line].get('Line_name'), 'from_sid': trans_stations[-1], 'to_sid': sid_end, 'stations': seq[i_trans_last:i_end+1] if i_trans_last<=i_end else list(reversed(seq[i_end:i_trans_last+1])), 'stops_count': stops})
                        total_stops += stops

                        candidates.append({'segments': segs, 'transfers': path_len-1, 'total_stops': total_stops})

                return

            # extend path by trying all neighbor lines that share a station with current last line
            last = path[-1]
            # neighbors are lines that share at least one station with last
            neigh = set()
            for s in lines[last]['stations']:
                neigh |= station_to_lines.get(s, set())
            # Avoid immediate duplicates in path
            for n in neigh:
                if len(path) >= 1 and n == path[-1]:
                    continue
                # prevent cycles longer than path_len
                if n in path:
                    continue
                dfs_build(path + [n])

        for sline in start_lines:
            dfs_build([sline])

    # 去重候选（简单方法：基于 segments 描述）
    unique = []
    seen = set()
    for c in candidates:
        key = tuple((seg['line_order'], seg['from_sid'], seg['to_sid']) for seg in c['segments'])
        if key in seen:
            continue
        seen.add(key)
        unique.append(c)

    unique.sort(key=lambda x: (x['transfers'], x['total_stops']))
    return unique


# -----------------------------
# 管理员功能
# -----------------------------

def add_station(station_id, station_name):
    """添加站点。
    限制：站点名不能与已有站点名重合。
    返回：(success: bool, message: str)
    """
    if not isinstance(station_id, int) or station_id <= 0:
        return False, '站点ID必须是正整数'

    if not station_name or not isinstance(station_name, str):
        return False, '站点名称不能为空'

    station_name = station_name.strip()

    # 检查站点名是否重合
    for s in stations.values():
        if s['Station_NAME'] == station_name:
            return False, f'站点名称 "{station_name}" 已存在'

    # 检查ID是否已存在
    if station_id in stations:
        return False, f'站点ID {station_id} 已存在'

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute('INSERT INTO stations (Station_ID, Station_NAME) VALUES (%s, %s)',
                         (station_id, station_name))
        # 更新内存
        stations[station_id] = {'Station_ID': station_id, 'Station_NAME': station_name}
        return True, f'站点 {station_id} "{station_name}" 添加成功'
    except Exception as e:
        return False, f'添加站点失败: {e}'
    finally:
        conn.close()


def delete_station(station_identifier):
    """删除站点（级联删除 line_stations 中的相关记录）。
    参数：station_identifier 可以是 Station_ID 或 Station_NAME
    返回：(success: bool, message: str)
    """
    sid = _ensure_station_id(station_identifier)
    if sid is None:
        return False, f'站点 "{station_identifier}" 不存在'

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            # 级联删除 line_stations
            cursor.execute('DELETE FROM line_stations WHERE Station_ID = %s', (sid,))
            # 删除站点
            cursor.execute('DELETE FROM stations WHERE Station_ID = %s', (sid,))

        # 更新内存
        station_name = stations[sid]['Station_NAME']
        del stations[sid]

        # 更新 station_to_lines
        if sid in station_to_lines:
            affected_lines = station_to_lines[sid]
            del station_to_lines[sid]
            # 更新线路中的站点列表
            for lo in affected_lines:
                if lo in lines and sid in lines[lo]['stations']:
                    lines[lo]['stations'].remove(sid)

        return True, f'站点 {sid} "{station_name}" 及其关联记录已删除'
    except Exception as e:
        return False, f'删除站点失败: {e}'
    finally:
        conn.close()


def add_line(line_order, line_name, direction, st, ft, interval_time, station_ids):
    """添加线路及其站点序列。
    参数：
        line_order: int, 线路编号
        line_name: str, 线路名称
        direction: str, 方向
        st: str, 首班时间 (格式: 'HH:MM:SS')
        ft: str, 末班时间 (格式: 'HH:MM:SS')
        interval_time: int, 发车间隔（分钟）
        station_ids: list[int], 站点ID序列
    限制：线路名不能与已有线路名重合，每个站点最多6条线路
    返回：(success: bool, message: str)
    """
    if not isinstance(line_order, int) or line_order <= 0:
        return False, '线路编号必须是正整数'

    if line_order in lines:
        return False, f'线路编号 {line_order} 已存在'

    if not line_name or not isinstance(line_name, str):
        return False, '线路名称不能为空'

    line_name = line_name.strip()

    # 检查线路名是否重合
    for l in lines.values():
        if l.get('Line_name') == line_name:
            return False, f'线路名称 "{line_name}" 已存在'

    # 验证站点ID
    if not station_ids or not isinstance(station_ids, list):
        return False, '站点序列不能为空'

    for sid in station_ids:
        if sid not in stations:
            return False, f'站点ID {sid} 不存在'

    # 检查站点线路数限制
    for sid in station_ids:
        current_lines = len(station_to_lines.get(sid, set()))
        if current_lines >= 6:
            return False, f'站点 {sid} 已有6条线路，无法添加更多'

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            # 插入线路
            cursor.execute(
                'INSERT INTO roads (Line_order, Line_name, Direction, St, Ft, Interval_time) VALUES (%s, %s, %s, %s, %s, %s)',
                (line_order, line_name, direction, st, ft, interval_time)
            )
            # 插入站点序列
            for seq_no, sid in enumerate(station_ids, start=1):
                cursor.execute(
                    'INSERT INTO line_stations (Line_order, Station_ID, Sequence_No) VALUES (%s, %s, %s)',
                    (line_order, sid, seq_no)
                )

        # 更新内存
        lines[line_order] = {
            'Line_order': line_order,
            'Line_name': line_name,
            'Direction': direction,
            'St': st,
            'Ft': ft,
            'Interval_time': interval_time,
            'stations': station_ids.copy()
        }
        for sid in station_ids:
            station_to_lines.setdefault(sid, set()).add(line_order)

        return True, f'线路 {line_order} "{line_name}" 添加成功，包含 {len(station_ids)} 个站点'
    except Exception as e:
        return False, f'添加线路失败: {e}'
    finally:
        conn.close()


def delete_line(line_identifier):
    """删除线路（同时删除 line_stations 中该线路的所有关联记录）。
    参数：line_identifier 可以是 Line_order 或 Line_name
    返回：(success: bool, message: str)
    """
    lo = None
    if isinstance(line_identifier, int):
        lo = line_identifier
    elif isinstance(line_identifier, str) and line_identifier.isdigit():
        lo = int(line_identifier)
    else:
        # 根据 Line_name 查找
        name = str(line_identifier)
        for v in lines.values():
            if v.get('Line_name') == name:
                lo = v['Line_order']
                break

    if lo is None or lo not in lines:
        return False, f'线路 "{line_identifier}" 不存在'

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            # 删除 line_stations
            cursor.execute('DELETE FROM line_stations WHERE Line_order = %s', (lo,))
            # 删除线路
            cursor.execute('DELETE FROM roads WHERE Line_order = %s', (lo,))

        # 更新内存
        line_name = lines[lo].get('Line_name')
        affected_stations = lines[lo]['stations']
        del lines[lo]

        # 更新 station_to_lines
        for sid in affected_stations:
            if sid in station_to_lines and lo in station_to_lines[sid]:
                station_to_lines[sid].remove(lo)
                if not station_to_lines[sid]:
                    del station_to_lines[sid]

        return True, f'线路 {lo} "{line_name}" 及其关联记录已删除'
    except Exception as e:
        return False, f'删除线路失败: {e}'
    finally:
        conn.close()


def add_station_to_line(line_identifier, station_identifier, sequence_no):
    """为已有线路添加站点。
    参数：
        line_identifier: Line_order 或 Line_name
        station_identifier: Station_ID 或 Station_NAME
        sequence_no: int, 站点在线路中的序号
    限制：站点最多6条线路
    返回：(success: bool, message: str)
    """
    # 解析线路
    lo = None
    if isinstance(line_identifier, int):
        lo = line_identifier
    elif isinstance(line_identifier, str) and line_identifier.isdigit():
        lo = int(line_identifier)
    else:
        name = str(line_identifier)
        for v in lines.values():
            if v.get('Line_name') == name:
                lo = v['Line_order']
                break

    if lo is None or lo not in lines:
        return False, f'线路 "{line_identifier}" 不存在'

    # 解析站点
    sid = _ensure_station_id(station_identifier)
    if sid is None:
        return False, f'站点 "{station_identifier}" 不存在'

    # 检查站点是否已在该线路中
    if sid in lines[lo]['stations']:
        return False, f'站点 {sid} 已在线路 {lo} 中'

    # 检查站点线路数限制
    current_lines = len(station_to_lines.get(sid, set()))
    if current_lines >= 6:
        return False, f'站点 {sid} 已有6条线路，无法添加更多'

    if not isinstance(sequence_no, int) or sequence_no <= 0:
        return False, '序号必须是正整数'

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute(
                'INSERT INTO line_stations (Line_order, Station_ID, Sequence_No) VALUES (%s, %s, %s)',
                (lo, sid, sequence_no)
            )

        # 更新内存 - 按序号插入
        stations_list = lines[lo]['stations']
        # 简单处理：如果序号超出范围，追加到末尾
        if sequence_no > len(stations_list):
            stations_list.append(sid)
        else:
            stations_list.insert(sequence_no - 1, sid)

        station_to_lines.setdefault(sid, set()).add(lo)

        return True, f'站点 {sid} 已添加到线路 {lo}，序号 {sequence_no}'
    except Exception as e:
        return False, f'添加站点到线路失败: {e}'
    finally:
        conn.close()


def usage_and_exit():
    print('用法:')
    print('  python try_open.py init   # 创建表并插入示例数据')
    print('  python try_open.py read   # 读取并打印三张表的内容')
    print('\n请确保已在正确的数据库中（DB_NAME），并且 MySQL 可通过配置的账号密码访问。')
    sys.exit(1)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        usage_and_exit()

    cmd = sys.argv[1].lower()
    if cmd == 'init':
        create_tables_and_sample_data()
    elif cmd == 'read':
        read_all_tables()
    else:
        usage_and_exit()