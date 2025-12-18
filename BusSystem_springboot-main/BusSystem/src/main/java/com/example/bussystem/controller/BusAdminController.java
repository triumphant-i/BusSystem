package com.example.bussystem.controller;

import com.example.bussystem.entity.Station;
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

    // ==========================================
    //               站点管理 (Station)
    // ==========================================

    // 查询站点 (支持关键字搜索)
    // GET /api/admin/station?keyword=天安门
    @GetMapping("/station")
    public Map<String, Object> searchStations(@RequestParam(required = false) String keyword) {
        List<Station> list = dataService.searchStations(keyword);
        return Map.of("code", 200, "data", list);
    }

    // 添加站点 (自动获取经纬度)
    // POST /api/admin/station?id=999&name=测试站
    @PostMapping("/station")
    public Map<String, Object> addStation(@RequestParam Integer id, @RequestParam String name) {
        String msg = dataService.addStation(id, name);
        boolean success = msg.startsWith("成功");
        return Map.of("success", success, "message", msg);
    }

    // 修改站点名称
    // PUT /api/admin/station?id=999&name=新站名
    @PutMapping("/station")
    public Map<String, Object> updateStation(@RequestParam Integer id, @RequestParam String name) {
        String msg = dataService.updateStation(id, name);
        boolean success = msg.startsWith("成功");
        return Map.of("success", success, "message", msg);
    }

    // 删除站点 (级联检查)
    // DELETE /api/admin/station/999
    @DeleteMapping("/station/{id}")
    public Map<String, Object> deleteStation(@PathVariable Integer id) {
        String msg = dataService.deleteStation(id);
        boolean success = msg.startsWith("成功");
        return Map.of("success", success, "message", msg);
    }

    // ==========================================
    //               线路管理 (Line)
    // ==========================================

    // 添加线路 (接收JSON Body)
    // POST /api/admin/line
    // Body: { "lineOrder": 100, "lineName": "测试线", "direction": "上", "st": "06:00", "ft": "22:00", "interval": 10, "stationIds": [1, 2, 3] }
    @PostMapping("/line")
    public Map<String, Object> addLine(@RequestBody Map<String, Object> payload) {
        return handleLineSave(payload, false);
    }

    // 修改线路 (接收JSON Body)
    // PUT /api/admin/line
    // Body: { "lineOrder": 100, "lineName": "测试线", "direction": "上", "st": "06:00", "ft": "22:00", "interval": 15, "stationIds": [1, 2, 3] }
    @PutMapping("/line")
    public Map<String, Object> updateLine(@RequestBody Map<String, Object> payload) {
        return handleLineSave(payload, true);
    }

    // 删除线路
    // DELETE /api/admin/line/100
    @DeleteMapping("/line/{id}")
    public Map<String, Object> deleteLine(@PathVariable Integer id) {
        String msg = dataService.deleteLine(id);
        boolean success = msg.startsWith("成功");
        return Map.of("success", success, "message", msg);
    }

    // ==========================================
    //               内部辅助方法
    // ==========================================

    /**
     * 处理线路的保存逻辑（新增或更新）
     * 包含健壮的参数类型转换，防止前端传字符串导致的类型错误
     */
    private Map<String, Object> handleLineSave(Map<String, Object> payload, boolean isUpdate) {
        try {
            // 使用 toString() 再转类型，防止前端传过来的是 String 导致的 ClassCastException
            Integer lineOrder = Integer.valueOf(payload.get("lineOrder").toString());
            String lineName = payload.get("lineName").toString();
            String direction = payload.get("direction").toString();
            String st = payload.get("st").toString();
            String ft = payload.get("ft").toString();
            Integer interval = Integer.valueOf(payload.get("interval").toString());
            List<Integer> stationIds = (List<Integer>) payload.get("stationIds");

            String msg;
            if (isUpdate) {
                msg = dataService.updateLine(lineOrder, lineName, direction, st, ft, interval, stationIds);
            } else {
                msg = dataService.addLine(lineOrder, lineName, direction, st, ft, interval, stationIds);
            }

            boolean success = msg.startsWith("成功");
            return Map.of("success", success, "message", msg);
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "数据格式错误: " + e.getMessage());
        }
    }
}