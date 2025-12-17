package com.example.bussystem.controller;

import com.example.bussystem.service.BusDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin
public class BusAdminController {

    @Autowired
    private BusDataService dataService;

    // 添加站点
    // POST /api/admin/station?id=999&name=测试站
    @PostMapping("/station")
    public Map<String, Object> addStation(@RequestParam Integer id, @RequestParam String name) {
        String msg = dataService.addStation(id, name);
        boolean success = msg.startsWith("成功");
        return Map.of("success", success, "message", msg);
    }

    // 删除站点
    // DELETE /api/admin/station/999
    @DeleteMapping("/station/{id}")
    public Map<String, Object> deleteStation(@PathVariable Integer id) {
        String msg = dataService.deleteStation(id);
        boolean success = msg.startsWith("成功");
        return Map.of("success", success, "message", msg);
    }

    // 添加线路 (接收JSON Body)
    // POST /api/admin/line
    // Body: { "lineOrder": 100, "lineName": "测试线", "direction": "上", "st": "06:00:00", "ft": "22:00:00", "interval": 10, "stationIds": [1, 2, 3] }
    @PostMapping("/line")
    public Map<String, Object> addLine(@RequestBody Map<String, Object> payload) {
        try {
            Integer lineOrder = (Integer) payload.get("lineOrder");
            String lineName = (String) payload.get("lineName");
            String direction = (String) payload.get("direction");
            String st = (String) payload.get("st");
            String ft = (String) payload.get("ft");
            Integer interval = (Integer) payload.get("interval");
            List<Integer> stationIds = (List<Integer>) payload.get("stationIds");

            String msg = dataService.addLine(lineOrder, lineName, direction, st, ft, interval, stationIds);
            return Map.of("success", true, "message", msg);
        } catch (Exception e) {
            return Map.of("success", false, "message", "参数错误: " + e.getMessage());
        }
    }

    // 删除线路
    // DELETE /api/admin/line/100
    @DeleteMapping("/line/{id}")
    public Map<String, Object> deleteLine(@PathVariable Integer id) {
        String msg = dataService.deleteLine(id);
        boolean success = msg.startsWith("成功");
        return Map.of("success", success, "message", msg);
    }
}