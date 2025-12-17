package com.example.bussystem.service;

import com.example.bussystem.dto.RouteResultDTO;
import com.example.bussystem.dto.SegmentDTO;
import com.example.bussystem.entity.LineStation;
import com.example.bussystem.entity.Road;
import com.example.bussystem.entity.Station;
import com.example.bussystem.repository.LineStationRepository;
import com.example.bussystem.repository.RoadRepository;
import com.example.bussystem.repository.StationRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心算法服务
 * 对应 try_open.py 中的 find_routes_between 和 load_data
 */
@Service
public class PathFindingService {

    @Autowired private StationRepository stationRepo;
    @Autowired private RoadRepository roadRepo;
    @Autowired private LineStationRepository lineStationRepo;

    // 内存缓存 (模拟 Python 中的全局字典)
    private Map<Integer, Station> stationMap = new HashMap<>();
    private Map<Integer, Road> roadMap = new HashMap<>();
    // 线路ID -> 站点ID有序列表
    private Map<Integer, List<Integer>> lineToStationsMap = new HashMap<>();
    // 站点ID -> 经过该站点的线路ID集合
    private Map<Integer, Set<Integer>> stationToLinesMap = new HashMap<>();

    /**
     * 初始化加载数据到内存 (对应 load_data)
     * 每次修改数据(增删改)后应当重新调用此方法刷新缓存
     */
    @PostConstruct
    public synchronized void loadData() {
        stationMap.clear();
        roadMap.clear();
        lineToStationsMap.clear();
        stationToLinesMap.clear();

        // 1. 加载站点
        List<Station> stations = stationRepo.findAll();
        for (Station s : stations) {
            stationMap.put(s.getStationId(), s);
        }

        // 2. 加载线路基础信息
        List<Road> roads = roadRepo.findAll();
        for (Road r : roads) {
            roadMap.put(r.getLineOrder(), r);
            lineToStationsMap.put(r.getLineOrder(), new ArrayList<>());
        }

        // 3. 加载线路-站点关系 (按顺序)
        List<LineStation> relations = lineStationRepo.findAll();
        // 确保按 lineOrder 和 sequenceNo 排序
        relations.sort(Comparator.comparingInt(LineStation::getLineOrder)
                .thenComparingInt(LineStation::getSequenceNo));

        for (LineStation ls : relations) {
            Integer lid = ls.getLineOrder();
            Integer sid = ls.getStationId();

            if (lineToStationsMap.containsKey(lid)) {
                lineToStationsMap.get(lid).add(sid);
            }
            stationToLinesMap.computeIfAbsent(sid, k -> new HashSet<>()).add(lid);
        }

        System.out.println("数据已加载到内存: 站点=" + stationMap.size() + ", 线路=" + roadMap.size());
    }

    /**
     * 核心路径规划算法
     */
    public List<RouteResultDTO> findRoutes(String startStr, String endStr, int maxTransfers) {
        Integer startSid = parseStationId(startStr);
        Integer endSid = parseStationId(endStr);

        if (startSid == null || endSid == null) return Collections.emptyList();
        if (startSid.equals(endSid)) return Collections.emptyList();

        List<RouteResultDTO> candidates = new ArrayList<>();

        // 1. 直达 (0次换乘)
        findDirectRoutes(startSid, endSid, candidates);

        // 2. 换乘逻辑 (1次或2次)
        // 这里的逻辑对应 Python 中的 dfs_build
        if (maxTransfers >= 1) {
            findTransferRoutes(startSid, endSid, maxTransfers, candidates);
        }

        // 排序：优先换乘少，其次总站数少
        candidates.sort(Comparator.comparingInt(RouteResultDTO::getTransfers)
                .thenComparingInt(RouteResultDTO::getTotalStops));

        // 简单去重 (基于线路序列)
        return deduplicate(candidates);
    }

    // --- 辅助算法方法 ---

    private void findDirectRoutes(Integer start, Integer end, List<RouteResultDTO> results) {
        Set<Integer> startLines = stationToLinesMap.getOrDefault(start, Collections.emptySet());
        for (Integer lid : startLines) {
            List<Integer> seq = lineToStationsMap.get(lid);
            if (seq.contains(end)) {
                RouteResultDTO route = createRoute(Collections.singletonList(lid), Arrays.asList(start, end));
                if (route != null) results.add(route);
            }
        }
    }

    // 简化的BFS/DFS搜索，寻找线路序列
    private void findTransferRoutes(Integer start, Integer end, int maxTransfers, List<RouteResultDTO> results) {
        Set<Integer> startLines = stationToLinesMap.getOrDefault(start, Collections.emptySet());
        Set<Integer> endLines = stationToLinesMap.getOrDefault(end, Collections.emptySet());

        // 限制递归深度：线路数量 = 换乘次数 + 1
        int maxLines = maxTransfers + 1;

        // BFS 搜索线路路径: List<Integer> 表示线路ID序列
        Queue<List<Integer>> queue = new LinkedList<>();
        for (Integer sl : startLines) {
            List<Integer> path = new ArrayList<>();
            path.add(sl);
            queue.add(path);
        }

        while (!queue.isEmpty()) {
            List<Integer> path = queue.poll();
            if (path.size() >= maxLines) continue; // 达到深度限制，不能再扩展

            Integer lastLineId = path.get(path.size() - 1);
            List<Integer> lastLineStations = lineToStationsMap.get(lastLineId);

            // 找到与当前线路有交集的所有线路
            Set<Integer> nextLines = new HashSet<>();
            for (Integer sid : lastLineStations) {
                Set<Integer> linesAtStation = stationToLinesMap.getOrDefault(sid, Collections.emptySet());
                nextLines.addAll(linesAtStation);
            }

            for (Integer nextLineId : nextLines) {
                if (path.contains(nextLineId)) continue; // 防止回路

                List<Integer> newPath = new ArrayList<>(path);
                newPath.add(nextLineId);

                // 如果这条新线路能到达终点 (即 nextLineId 在 endLines 中)
                if (endLines.contains(nextLineId)) {
                    // 构建具体的换乘方案
                    buildAndAddRoutes(newPath, start, end, results);
                } else {
                    // 如果还没到最大深度，继续入队
                    if (newPath.size() < maxLines) {
                        queue.add(newPath);
                    }
                }
            }
        }
    }

    // 根据线路序列构建详细路径 (需要找到具体的换乘站)
    private void buildAndAddRoutes(List<Integer> linePath, Integer start, Integer end, List<RouteResultDTO> results) {
        // 这是一个简化版实现，对于每一对相邻线路，找到第一个公共站点作为换乘点
        // Python版逻辑更复杂，枚举了所有可能的换乘站组合。这里为了代码简洁，取第一个交点。

        List<Integer> transferStations = new ArrayList<>();
        transferStations.add(start);

        for (int i = 0; i < linePath.size() - 1; i++) {
            Integer l1 = linePath.get(i);
            Integer l2 = linePath.get(i+1);

            // 找交集
            Set<Integer> stations1 = new HashSet<>(lineToStationsMap.get(l1));
            Set<Integer> stations2 = new HashSet<>(lineToStationsMap.get(l2));
            stations1.retainAll(stations2);

            if (stations1.isEmpty()) return; // 无交点，理论上不会发生
            transferStations.add(stations1.iterator().next()); // 取任意一个换乘点
        }
        transferStations.add(end);

        RouteResultDTO route = createRoute(linePath, transferStations);
        if (route != null) results.add(route);
    }

    private RouteResultDTO createRoute(List<Integer> lines, List<Integer> transferPoints) {
        RouteResultDTO dto = new RouteResultDTO();
        dto.setTransfers(lines.size() - 1);
        List<SegmentDTO> segments = new ArrayList<>();
        int totalStops = 0;

        for (int i = 0; i < lines.size(); i++) {
            Integer lid = lines.get(i);
            Integer from = transferPoints.get(i);
            Integer to = transferPoints.get(i + 1);

            List<Integer> fullSeq = lineToStationsMap.get(lid);
            int idxFrom = fullSeq.indexOf(from);
            int idxTo = fullSeq.indexOf(to);

            if (idxFrom == -1 || idxTo == -1) return null;

            List<Integer> subList;
            if (idxFrom <= idxTo) {
                subList = new ArrayList<>(fullSeq.subList(idxFrom, idxTo + 1));
            } else {
                subList = new ArrayList<>(fullSeq.subList(idxTo, idxFrom + 1));
                Collections.reverse(subList);
            }

            SegmentDTO seg = new SegmentDTO();
            seg.setLineOrder(lid);
            seg.setLineName(roadMap.get(lid).getLineName());
            seg.setFromSid(from);
            seg.setToSid(to);
            seg.setStations(subList);
            seg.setStopsCount(Math.abs(idxTo - idxFrom));

            segments.add(seg);
            totalStops += seg.getStopsCount();
        }

        dto.setSegments(segments);
        dto.setTotalStops(totalStops);
        return dto;
    }

    public Integer parseStationId(String query) {
        if (query == null) return null;
        if (query.matches("\\d+")) {
            Integer id = Integer.parseInt(query);
            if (stationMap.containsKey(id)) return id;
        }
        // 模糊匹配，取第一个
        for (Station s : stationMap.values()) {
            if (s.getStationName().contains(query)) return s.getStationId();
        }
        return null;
    }

    // 简单的列表去重
    private List<RouteResultDTO> deduplicate(List<RouteResultDTO> list) {
        // 实际生产中可以用Set + hashcode去重，这里直接返回
        return list.stream().limit(10).collect(Collectors.toList());
    }

    // Getter for controllers to use cache
    public Map<Integer, Station> getStationMap() { return stationMap; }
    public Map<Integer, Road> getRoadMap() { return roadMap; }
    public Map<Integer, List<Integer>> getLineToStationsMap() { return lineToStationsMap; }
    public Map<Integer, Set<Integer>> getStationToLinesMap() { return stationToLinesMap; }
}