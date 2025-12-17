package com.example.bussystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RouteResultDTO {
    @Schema(description = "路线唯一ID") // 或者是 @ApiModelProperty("路线唯一ID")
    private String routeId;

    @Schema(description = "预计耗时(分钟)")
    private Integer duration;

    private List<SegmentDTO> segments; // 这里改用了独立的 SegmentDTO 类
    private Integer transfers;


    private Integer totalStops;
}