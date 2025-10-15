package com.livelihoodcoupon.parkinglot.controller;

import com.livelihoodcoupon.common.response.CustomApiResponse;
import com.livelihoodcoupon.parkinglot.dto.NearbySearchRequest;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotDetailResponse;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotNearbyResponse;
import com.livelihoodcoupon.parkinglot.service.ParkingLotService;
import com.livelihoodcoupon.search.dto.PageResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/parkinglots")
@RequiredArgsConstructor
public class ParkingLotController {

    private final ParkingLotService parkingLotService;

    /**
     * 지정된 좌표와 반경 내의 주변 주차장을 검색하는 API 엔드포인트
     * @param request 위도(lat), 경도(lng), 반경(radius)을 포함하는 요청 객체
     * @return 거리순으로 정렬된 주차장 목록
     */
    @GetMapping("/nearby")
    public ResponseEntity<CustomApiResponse<PageResponse<ParkingLotNearbyResponse>>> getNearbyParkingLots(
            @Valid @ModelAttribute NearbySearchRequest request) {

        PageResponse<ParkingLotNearbyResponse> nearbyParkingLots = parkingLotService.findNearby(request);
        return ResponseEntity.ok(CustomApiResponse.success(nearbyParkingLots));
    }

    /**
     * 특정 주차장의 상세 정보를 조회하는 API 엔드포인트
     * @param id 주차장 고유 ID
     * @return 표준 API 응답 형식에 담긴 주차장 상세 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomApiResponse<ParkingLotDetailResponse>> getLotDetails(@PathVariable Long id){
        ParkingLotDetailResponse parkingLotDetails = parkingLotService.getParkingLotDetails(id);
        return ResponseEntity.ok(CustomApiResponse.success(parkingLotDetails));
    }
}
