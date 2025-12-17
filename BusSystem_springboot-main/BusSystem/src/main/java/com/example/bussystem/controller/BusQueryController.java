package com.example.bussystem.controller;

import com.example.bussystem.dto.RouteResultDTO;
import com.example.bussystem.entity.Road;
import com.example.bussystem.entity.Station;
import com.example.bussystem.service.PathFindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Tag(name = "公交查询模块", description = "包含站点查询、线路查询及路径规划")
@CrossOrigin // 你的这个注解保留，这对前端本地调试很有用
public class BusQueryController {

    @Autowired
    private PathFindingService busService;

    @GetMapping("/stations")
    @Operation(summary = "获取所有站点", description = "返回地图上所有的站点列表")
    public List<Station> getAllStations() {
        return new ArrayList<>(busService.getStationMap().values());
    }

    @GetMapping("/routes")
    @Operation(summary = "获取所有线路", description = "返回所有公交线路信息")
    public List<Road> getAllRoutes() {
        List<Road> roads = new ArrayList<>(busService.getRoadMap().values());
        for (Road r : roads) {
            // 注意：确保 Road 实体里的 setStationIds 字段不会导致数据库写入异常（如果是JPA）
            r.setStationIds(busService.getLineToStationsMap().get(r.getLineOrder()));
        }
        return roads;
    }

    @GetMapping("/stations/search")
    @Operation(summary = "搜索站点", description = "支持 ID 精确查询 或 名称模糊查询")
    public List<Station> findStations(
            @Parameter(description = "站点ID(数字) 或 站点名称(中文)") @RequestParam String query
    ) {
        List<Station> res = new ArrayList<>();
        if (query.matches("\\d+")) {
            Integer id = Integer.parseInt(query);
            if (busService.getStationMap().containsKey(id)) {
                res.add(busService.getStationMap().get(id));
            }
        }
        for (Station s : busService.getStationMap().values()) {
            if (s.getStationName().contains(query)) {
                if (!res.contains(s)) res.add(s);
            }
        }
        return res;
    }

    @GetMapping("/station/{identifier}/lines")
    @Operation(summary = "查询某站点的经过线路", description = "点击某个站点时，显示经过该站的所有公交线")
    public List<Road> getLinesByStation(
            @Parameter(description = "站点ID 或 站点名称") @PathVariable String identifier
    ) {
        Integer sid = busService.parseStationId(identifier);
        if (sid == null) return new ArrayList<>();

        return busService.getStationToLinesMap().getOrDefault(sid, java.util.Collections.emptySet())
                .stream()
                .map(lid -> busService.getRoadMap().get(lid))
                .collect(Collectors.toList());
    }

    @GetMapping("/line/{identifier}/stations")
    @Operation(summary = "查询某线路的所有站点", description = "查看某条公交线（如1路）具体经过哪些站")
    public List<Station> getStationsByLine(
            @Parameter(description = "线路ID 或 线路名称") @PathVariable String identifier
    ) {
        Integer lid = null;
        if (identifier.matches("\\d+")) {
            lid = Integer.parseInt(identifier);
        } else {
            for (Road r : busService.getRoadMap().values()) {
                if (r.getLineName().equals(identifier)) {
                    lid = r.getLineOrder();
                    break;
                }
            }
        }

        if (lid == null || !busService.getLineToStationsMap().containsKey(lid)) return new ArrayList<>();

        return busService.getLineToStationsMap().get(lid).stream()
                .map(sid -> busService.getStationMap().get(sid))
                .collect(Collectors.toList());
    }

    @GetMapping("/routes/plan")
    @Operation(summary = "🚀 路径规划核心接口", description = "前端输入起点终点，后端计算最优换乘")
    public List<RouteResultDTO> planRoute(
            @Parameter(description = "起点（名称或ID）") @RequestParam String start,
            @Parameter(description = "终点（名称或ID）") @RequestParam String end,
            @Parameter(description = "最大换乘次数") @RequestParam(defaultValue = "2") int maxTransfers) {
        return busService.findRoutes(start, end, maxTransfers);
    }
}