package com.example.bussystem.dto;

import com.example.bussystem.entity.Station;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SegmentDTO {
    private Integer lineOrder;
    private String lineName;
    private Integer fromSid;
    private Integer toSid;
    private List<Integer> stations;
    private List<Station> stationDetails;
    private Integer stopsCount;
    private Integer segmentDuration;
}