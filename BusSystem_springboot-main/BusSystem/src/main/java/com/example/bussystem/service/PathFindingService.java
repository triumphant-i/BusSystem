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
 * 负责路径规划（直达+换乘）及数据缓存
 */
@Service
public class PathFindingService {

    @Autowired private StationRepository stationRepo;
    @Autowired private RoadRepository roadRepo;
    @Autowired private LineStationRepository lineStationRepo;

    // --- 内存缓存 ---
    private Map<Integer, Station> stationMap = new HashMap<>();
    private Map<Integer, Road> roadMap = new HashMap<>();
    // 线路ID -> 站点ID有序列表
    private Map<Integer, List<Integer>> lineToStationsMap = new HashMap<>();
    // 站点ID -> 经过该站点的线路ID集合
    private Map<Integer, Set<Integer>> stationToLinesMap = new HashMap<>();

    /**
     * 初始化加载数据到内存
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

        System.out.println("PathFindingService 数据已加载: 站点=" + stationMap.size() + ", 线路=" + roadMap.size());
    }

    /**
     * 核心路径规划入口
     */
    public List<RouteResultDTO> findRoutes(String startStr, String endStr, int maxTransfers) {
        Integer startSid = parseStationId(startStr);
        Integer endSid = parseStationId(endStr);

        // 基本校验
        if (startSid == null || endSid == null) return Collections.emptyList();
        if (startSid.equals(endSid)) return Collections.emptyList();

        List<RouteResultDTO> candidates = new ArrayList<>();

        // 1. 搜索直达方案 (0次换乘)
        findDirectRoutes(startSid, endSid, candidates);

        // 2. 搜索换乘方案 (如果允许)
        if (maxTransfers >= 1) {
            findTransferRoutes(startSid, endSid, maxTransfers, candidates);
        }

        // 去重并排序返回
        return deduplicate(candidates);
    }

    // --- 内部算法实现 ---

    private void findDirectRoutes(Integer start, Integer end, List<RouteResultDTO> results) {
        Set<Integer> startLines = stationToLinesMap.getOrDefault(start, Collections.emptySet());
        for (Integer lid : startLines) {
            List<Integer> seq = lineToStationsMap.get(lid);
            // 如果该线路同时也包含终点
            if (seq.contains(end)) {
                // 构建路径：仅包含这一条线路
                RouteResultDTO route = createRoute(Collections.singletonList(lid), Arrays.asList(start, end));
                if (route != null) results.add(route);
            }
        }
    }

    private void findTransferRoutes(Integer start, Integer end, int maxTransfers, List<RouteResultDTO> results) {
        Set<Integer> startLines = stationToLinesMap.getOrDefault(start, Collections.emptySet());
        Set<Integer> endLines = stationToLinesMap.getOrDefault(end, Collections.emptySet());

        // 限制递归深度：线路数量 = 换乘次数 + 1
        int maxLines = maxTransfers + 1;

        // Queue 中存储的是线路ID的序列
        Queue<List<Integer>> queue = new LinkedList<>();
        for (Integer sl : startLines) {
            List<Integer> path = new ArrayList<>();
            path.add(sl);
            queue.add(path);
        }

        while (!queue.isEmpty()) {
            List<Integer> path = queue.poll();
            if (path.size() >= maxLines) continue;

            Integer lastLineId = path.get(path.size() - 1);
            List<Integer> lastLineStations = lineToStationsMap.get(lastLineId);

            Set<Integer> nextLines = new HashSet<>();
            for (Integer sid : lastLineStations) {
                Set<Integer> linesAtStation = stationToLinesMap.getOrDefault(sid, Collections.emptySet());
                nextLines.addAll(linesAtStation);
            }

            for (Integer nextLineId : nextLines) {
                if (path.contains(nextLineId)) continue;

                List<Integer> newPath = new ArrayList<>(path);
                newPath.add(nextLineId);

                if (endLines.contains(nextLineId)) {
                    buildAndAddRoutes(newPath, start, end, results);
                } else {
                    if (newPath.size() < maxLines) {
                        queue.add(newPath);
                    }
                }
            }
        }
    }

    private void buildAndAddRoutes(List<Integer> linePath, Integer start, Integer end, List<RouteResultDTO> results) {
        List<Integer> transferStations = new ArrayList<>();
        transferStations.add(start);

        for (int i = 0; i < linePath.size() - 1; i++) {
            Integer l1 = linePath.get(i);
            Integer l2 = linePath.get(i+1);

            Set<Integer> stations1 = new HashSet<>(lineToStationsMap.get(l1));
            Set<Integer> stations2 = new HashSet<>(lineToStationsMap.get(l2));

            stations1.retainAll(stations2); // 取交集

            if (stations1.isEmpty()) return;

            // 取第一个交点作为换乘站
            transferStations.add(stations1.iterator().next());
        }
        transferStations.add(end);

        RouteResultDTO route = createRoute(linePath, transferStations);
        if (route != null) results.add(route);
    }

    private RouteResultDTO createRoute(List<Integer> lines, List<Integer> transferPoints) {
        RouteResultDTO dto = new RouteResultDTO();
        // dto.setTransfers(lines.size() - 1);

        List<SegmentDTO> segments = new ArrayList<>();
        int totalStops = 0;
        int totalDuration = 0;

        // 获取最终的目的地ID
        Integer finalDestination = transferPoints.get(transferPoints.size() - 1);

        for (int i = 0; i < lines.size(); i++) {
            Integer lid = lines.get(i);
            Integer from = transferPoints.get(i);
            Integer to = transferPoints.get(i + 1);

            List<Integer> fullSeq = lineToStationsMap.get(lid);
            int idxFrom = fullSeq.indexOf(from);
            int idxTo = fullSeq.indexOf(to);

            if (idxFrom == -1 || idxTo == -1) return null;

            if (idxFrom == idxTo) {
                continue;
            }

            // ============================================================
            // 拒绝“多此一举”的换乘
            // 如果这不是最后一段路（即我们正准备在这里下车换乘），
            // 但如果你不下的车，这辆车其实后面就能到终点，那你下车干嘛？-> 废弃
            // ============================================================
            if (i < lines.size() - 1) {
                int idxFinal = fullSeq.indexOf(finalDestination);
                if (idxFinal != -1) {
                    // 判断终点是否在当前行驶方向的“前方”
                    boolean canReachDirectly = false;
                    if (idxFrom < idxTo) {
                        // 正向行驶 (1->5)，终点在更后面 (10)，即 idxFinal > idxTo
                        if (idxFinal > idxTo) canReachDirectly = true;
                    } else {
                        // 反向行驶 (5->1)，终点在更后面 (0)，即 idxFinal < idxTo
                        if (idxFinal < idxTo) canReachDirectly = true;
                    }

                    if (canReachDirectly) {
                        // 发现当前线路直达终点，却被安排了中途换乘，视为不合理，丢弃。
                        return null;
                    }
                }
            }
            // ============================================================

            List<Integer> subList;
            if (idxFrom <= idxTo) {
                subList = new ArrayList<>(fullSeq.subList(idxFrom, idxTo + 1));
            } else {
                subList = new ArrayList<>(fullSeq.subList(idxTo, idxFrom + 1));
                Collections.reverse(subList);
            }

            // ============================================================
            // 检查是否“坐过站”或“多余换乘”
            // 如果这一段路径中包含终点站，那么它必须是最后一段，且终点必须是这一段的最后一站。
            // ============================================================
            if (subList.contains(finalDestination)) {
                boolean isLastSegment = (i == lines.size() - 1);
                Integer lastStationInSegment = subList.get(subList.size() - 1);

                // 如果包含终点，但不是最后一段，或者不是在这一段的末尾下车 -> 说明路过终点没下车 -> 废弃
                if (!isLastSegment || !lastStationInSegment.equals(finalDestination)) {
                    return null;
                }
            }
            // ============================================================

            Road road = roadMap.get(lid);
            SegmentDTO seg = new SegmentDTO();
            seg.setLineOrder(lid);
            seg.setLineName(road.getLineName());
            seg.setFromSid(from);
            seg.setToSid(to);
            seg.setStations(subList);
            seg.setStopsCount(Math.abs(idxTo - idxFrom));

            // 填充 stationDetails 供前端地图绘制使用，让前端能画出折线和换乘点名
            List<Station> details = subList.stream()
                    .map(sid -> stationMap.get(sid))
                    .collect(Collectors.toList());
            seg.setStationDetails(details);

            int interval = (road.getIntervalTime() != null && road.getIntervalTime() > 0) ? road.getIntervalTime() : 5;
            int segTime = seg.getStopsCount() * interval;
            seg.setSegmentDuration(segTime);

            segments.add(seg);
            totalStops += seg.getStopsCount();
            totalDuration += segTime;
        }
        if (segments.isEmpty()) return null;

        // 重新计算换乘次数
        dto.setTransfers(segments.size() - 1);

        int transferPenalty = dto.getTransfers() * 10;
        dto.setDuration(totalDuration + transferPenalty);
        dto.setSegments(segments);
        dto.setTotalStops(totalStops);
        dto.setRouteId(UUID.randomUUID().toString()); // 生成一个唯一ID
        return dto;
    }

    public Integer parseStationId(String query) {
        if (query == null) return null;
        if (query.matches("\\d+")) {
            Integer id = Integer.parseInt(query);
            if (stationMap.containsKey(id)) return id;
        }
        for (Station s : stationMap.values()) {
            if (s.getStationName().contains(query)) return s.getStationId();
        }
        return null;
    }

    /**
     * [逻辑修复] 真正的去重逻辑
     * 防止出现两个完全一样的方案
     */
    private List<RouteResultDTO> deduplicate(List<RouteResultDTO> list) {
        if (list == null || list.isEmpty()) return list;

        // 使用 Set 记录已经出现过的“路线签名”
        Set<String> signatures = new HashSet<>();
        List<RouteResultDTO> uniqueList = new ArrayList<>();

        for (RouteResultDTO route : list) {
            String sig = generateSignature(route);
            if (!signatures.contains(sig)) {
                signatures.add(sig);
                uniqueList.add(route);
            }
        }

        // 排序：时间短 > 换乘少
        return uniqueList.stream()
                .sorted(Comparator.comparingInt(RouteResultDTO::getDuration)
                        .thenComparingInt(RouteResultDTO::getTransfers))
                .limit(10) // 只取前10
                .collect(Collectors.toList());
    }

    /**
     * 生成路线签名：线路名 + 经过的站点序列
     * 如果两个方案线路名一样，且经过的站点ID序列也完全一样，则视为重复
     */
    private String generateSignature(RouteResultDTO route) {
        StringBuilder sb = new StringBuilder();
        if (route.getSegments() != null) {
            for (SegmentDTO seg : route.getSegments()) {
                sb.append(seg.getLineName()).append(":"); // 线路名
                sb.append(seg.getStations().toString()).append("|"); // 站点ID序列
            }
        }
        return sb.toString();
    }

    // --- Getters ---
    public Map<Integer, Station> getStationMap() { return stationMap; }
    public Map<Integer, Road> getRoadMap() { return roadMap; }
    public Map<Integer, List<Integer>> getLineToStationsMap() { return lineToStationsMap; }
    public Map<Integer, Set<Integer>> getStationToLinesMap() { return stationToLinesMap; }
}