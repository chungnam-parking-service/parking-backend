package com.chungnam.parking.controller;

import com.chungnam.parking.dto.RegionStat;
import com.chungnam.parking.service.ParkingLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 프론트 지역 선택 드롭다운용 (충남 15개 시·군 + 주차장 수)
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "지역", description = "충남 시·군 목록")
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final ParkingLotService parkingLotService;

    @io.swagger.v3.oas.annotations.Operation(summary = "시·군 목록",
            description = "충남 15개 시·군 코드·이름·주차장 수. 검색 API 의 region 파라미터에 code 값을 넣으세요.")
    @GetMapping
    public List<RegionStat> regions() {
        return parkingLotService.getRegionStats();
    }
}
