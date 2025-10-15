package com.livelihoodcoupon.parkinglot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livelihoodcoupon.parkinglot.dto.NearbySearchRequest;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotDetailResponse;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotNearbyResponse;
import com.livelihoodcoupon.parkinglot.service.ParkingLotService;
import com.livelihoodcoupon.search.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParkingLotController.class)
class ParkingLotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParkingLotService parkingLotService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("주변 주차장 검색 성공")
    void getNearbyParkingLots_success() throws Exception {
        // given
        NearbySearchRequest request = new NearbySearchRequest();
        request.setLat(37.5665);
        request.setLng(126.9780);
        request.setRadius(1.0);
        request.setPage(1);
        request.setSize(10);

        ParkingLotNearbyResponse responseItem = ParkingLotNearbyResponse.builder()
                .id(1L)
                .parkingLotName("서울시청 주차장")
                .roadAddress("서울 중구 세종대로 110")
                .lat(37.5665)
                .lng(126.9780)
                .distance(100.0)
                .build();

        PageRequest pageRequest = PageRequest.of(0, 10);
        PageImpl<ParkingLotNearbyResponse> page = new PageImpl<>(Collections.singletonList(responseItem), pageRequest, 1);
        PageResponse<ParkingLotNearbyResponse> pageResponse = new PageResponse<>(page, 10, request.getLat(), request.getLng());


        given(parkingLotService.findNearby(any(NearbySearchRequest.class))).willReturn(pageResponse);

        // when & then
        mockMvc.perform(get("/api/parkinglots/nearby")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("radius", "1000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].parkingLotName").value("서울시청 주차장"));
    }

    @Test
    @DisplayName("주차장 상세 정보 조회 성공")
    void getLotDetails_success() throws Exception {
        // given
        Long parkingLotId = 1L;
        ParkingLotDetailResponse detailResponse = ParkingLotDetailResponse.builder()
                .id(parkingLotId)
                .parkingLotName("테스트 주차장")
                .roadAddress("테스트 도로명 주소")
                .build();

        given(parkingLotService.getParkingLotDetails(parkingLotId)).willReturn(detailResponse);

        // when & then
        mockMvc.perform(get("/api/parkinglots/{id}", parkingLotId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.parkingLotName").value("테스트 주차장"));
    }

    @Test
    @DisplayName("주변 주차장 검색 시 위도값이 없을 때 실패")
    void getNearbyParkingLots_fail_without_lat() throws Exception {
        // when & then
        mockMvc.perform(get("/api/parkinglots/nearby")
                        .param("lng", "126.9780")
                        .param("radius", "1000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}