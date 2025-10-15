package com.livelihoodcoupon.parkinglot.service;

import com.livelihoodcoupon.common.dto.Coordinate;
import com.livelihoodcoupon.common.exception.BusinessException;
import com.livelihoodcoupon.common.service.KakaoApiService;
import com.livelihoodcoupon.parkinglot.dto.NearbySearchRequest;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotDetailResponse;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotNearbyResponse;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotWithDistance;
import com.livelihoodcoupon.parkinglot.entity.ParkingLot;
import com.livelihoodcoupon.parkinglot.repository.ParkingLotRepository;
import com.livelihoodcoupon.place.repository.PlaceRepository;
import com.livelihoodcoupon.search.dto.PageResponse;
import com.livelihoodcoupon.search.dto.SearchRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLotServiceTest {

    @InjectMocks
    private ParkingLotService parkingLotService;

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private PlaceRepository placeRepository; // 의존성 주입을 위해 추가

    @Mock
    private KakaoApiService kakaoApiService; // 테스트를 위해 추가

    @Test
    @DisplayName("좌표 기반 주차장 검색 성공")
    void searchByQueryOrCoord_withCoords_success() {
        // given
        SearchRequestDto request = SearchRequestDto.builder()
                .lat(37.5665)
                .lng(126.9780)
                .radius(1.0)
                .build();

        // findNearby 메소드가 호출될 때의 Mock 설정
        ParkingLotWithDistance projection = mock(ParkingLotWithDistance.class);
        Page<ParkingLotWithDistance> page = new PageImpl<>(Collections.singletonList(projection));
        given(parkingLotRepository.findNearbyParkingLots(anyDouble(), anyDouble(), anyDouble(), any(Pageable.class)))
                .willReturn(page);

        // when
        PageResponse<ParkingLotNearbyResponse> response = parkingLotService.searchByQueryOrCoord(request);

        // then
        assertThat(response).isNotNull();
        verify(kakaoApiService, never()).getCoordinatesFromAddress(anyString()); // 카카오 API가 호출되지 않았는지 확인
    }

    @Test
    @DisplayName("검색어 기반 주차장 검색 성공")
    void searchByQueryOrCoord_withQuery_success() {
        // given
        SearchRequestDto request = SearchRequestDto.builder().query("강남역").radius(1.0).build();
        request.setLat(null);
        request.setLng(null);
        Coordinate mockCoordinate = Coordinate.builder().lat(37.4979).lng(127.0276).build();

        // KakaoApiService Mock 설정
        given(kakaoApiService.getCoordinatesFromAddress("강남역")).willReturn(Mono.just(mockCoordinate));

        // findNearby 메소드가 호출될 때의 Mock 설정
        ParkingLotWithDistance projection = mock(ParkingLotWithDistance.class);
        Page<ParkingLotWithDistance> page = new PageImpl<>(Collections.singletonList(projection));
        given(parkingLotRepository.findNearbyParkingLots(anyDouble(), anyDouble(), anyDouble(), any(Pageable.class)))
                .willReturn(page);

        // when
        PageResponse<ParkingLotNearbyResponse> response = parkingLotService.searchByQueryOrCoord(request);

        // then
        assertThat(response).isNotNull();
        verify(kakaoApiService, times(1)).getCoordinatesFromAddress("강남역"); // 카카오 API가 1번 호출되었는지 확인
        assertThat(response.getSearchCenterLat()).isEqualTo(mockCoordinate.getLat());
        assertThat(response.getSearchCenterLng()).isEqualTo(mockCoordinate.getLng());
    }

    @Test
    @DisplayName("입력값(좌표, 검색어)이 모두 없을 때 예외 발생")
    void searchByQueryOrCoord_withNoInput_throwsException() {
        // given
        SearchRequestDto request = new SearchRequestDto();
        request.setQuery(""); // 빈 검색어
        request.setLat(null); // 좌표 없음
        request.setLng(null);

        // when & then
        assertThrows(BusinessException.class, () -> {
            parkingLotService.searchByQueryOrCoord(request);
        });
    }

    @Test
    @DisplayName("주변 주차장 검색 서비스 성공")
    void findNearby_success() {
        // given
        NearbySearchRequest request = new NearbySearchRequest();
        request.setLat(37.5665);
        request.setLng(126.9780);
        request.setRadius(1000.0); // Double 타입으로 수정
        request.setPage(1);
        request.setSize(10);

        ParkingLotWithDistance projection = mock(ParkingLotWithDistance.class);
        given(projection.getId()).willReturn(1L);
        given(projection.getParkingLotNm()).willReturn("서울시청 주차장");
        given(projection.getRoadAddress()).willReturn("some address");
        given(projection.getLotAddress()).willReturn("some address");
        given(projection.getParkingChargeInfo()).willReturn("유료");
        given(projection.getLat()).willReturn(37.5665);
        given(projection.getLng()).willReturn(126.9780);
        given(projection.getDistance()).willReturn(100.0);

        Page<ParkingLotWithDistance> page = new PageImpl<>(Collections.singletonList(projection));
        // radius 파라미터에 대해 anyDouble() 사용
        given(parkingLotRepository.findNearbyParkingLots(anyDouble(), anyDouble(), anyDouble(), any(Pageable.class)))
                .willReturn(page);

        // when
        PageResponse<ParkingLotNearbyResponse> response = parkingLotService.findNearby(request);

        // then
        assertThat(response.getContent()).hasSize(1);
        ParkingLotNearbyResponse resultDto = response.getContent().get(0);
        assertThat(resultDto.getParkingLotName()).isEqualTo("서울시청 주차장");
        assertThat(resultDto.getDistance()).isEqualTo(100.0);
        assertThat(resultDto.getLat()).isEqualTo(37.5665);
        assertThat(resultDto.getLng()).isEqualTo(126.9780);
    }

    @Test
    @DisplayName("주차장 상세 정보 조회 서비스 성공")
    void getParkingLotDetails_success() {
        // given
        Long parkingLotId = 1L;
        GeometryFactory geometryFactory = new GeometryFactory();
        Point point = geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(126.9780, 37.5665));

        ParkingLot parkingLot = ParkingLot.builder()
                .id(parkingLotId)
                .parkingLotNm("테스트 주차장")
                .location(point)
                .build();

        given(parkingLotRepository.findById(parkingLotId)).willReturn(Optional.of(parkingLot));

        // when
        ParkingLotDetailResponse response = parkingLotService.getParkingLotDetails(parkingLotId);

        // then
        assertThat(response.getParkingLotName()).isEqualTo("테스트 주차장");
        assertThat(response.getLat()).isEqualTo(37.5665);
    }

    @Test
    @DisplayName("주차장 상세 정보 조회 시 ID가 없으면 실패")
    void getParkingLotDetails_fail_when_not_found() {
        // given
        Long parkingLotId = 999L;
        given(parkingLotRepository.findById(parkingLotId)).willReturn(Optional.empty());

        // when & then
        assertThrows(BusinessException.class, () -> {
            parkingLotService.getParkingLotDetails(parkingLotId);
        });
    }
}
