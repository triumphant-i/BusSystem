package com.example.bussystem.controller;

import com.example.bussystem.entity.Station;
import com.example.bussystem.service.BusDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    //                内部辅助方法
    // ==========================================

    /**
     * 处理线路的保存逻辑（新增或更新）
     */
    private Map<String, Object> handleLineSave(Map<String, Object> payload, boolean isUpdate) {
        try {
            // 安全获取参数
            Integer lineOrder = getInteger(payload, "lineOrder");
            String lineName = getString(payload, "lineName");
            String direction = getString(payload, "direction");

            // 兼容 startTime/st 和 finishTime/ft
            String st = getString(payload, "startTime");
            if (st == null) st = getString(payload, "st");

            String ft = getString(payload, "finishTime");
            if (ft == null) ft = getString(payload, "ft");

            // 兼容 interval/intervalTime
            Integer interval = getInteger(payload, "interval");
            if (interval == null) interval = getInteger(payload, "intervalTime");

            // 安全转换 List
            List<Integer> stationIds = new ArrayList<>(); // 建议显示引入 ArrayList
            Object stationIdsObj = payload.get("stationIds");
            if (stationIdsObj instanceof List) {
                for (Object o : (List<?>) stationIdsObj) {
                    // 修改点：使用 String.valueOf(o) 避免潜在的 toString() 报错
                    if (o != null) {
                        try {
                            stationIds.add(Integer.valueOf(String.valueOf(o)));
                        } catch (NumberFormatException e) {
                            // 忽略无法转为数字的项
                        }
                    }
                }
            }

            // 必填项校验
            if (lineOrder == null || lineName == null) {
                return Map.of("success", false, "message", "错误：线路ID或名称不能为空");
            }

            String msg;
            if (isUpdate) {
                msg = dataService.updateLine(lineOrder, lineName, direction, st, ft, interval, stationIds);
            } else {
                msg = dataService.addLine(lineOrder, lineName, direction, st, ft, interval, stationIds);
            }

            boolean success = msg != null && msg.startsWith("成功");
            return Map.of("success", success, "message", msg != null ? msg : "操作失败，未返回消息");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "系统异常: " + e.getMessage());
        }
    }

    // 辅助：安全获取 Integer
    private Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        // 修改点：使用 String.valueOf() 安全转换，并统一处理 null/undefined/空串
        String strVal = String.valueOf(val);

        // String.valueOf(null) 会返回字符串 "null"，所以要排除
        if (val == null || "null".equals(strVal) || "undefined".equals(strVal) || strVal.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.valueOf(strVal);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 辅助：安全获取 String
    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;

        String strVal = String.valueOf(val);
        if ("undefined".equals(strVal) || "null".equals(strVal)) return null;

        return strVal;
    }
}