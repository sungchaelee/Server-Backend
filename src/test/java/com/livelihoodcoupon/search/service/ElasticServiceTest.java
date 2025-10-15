package com.livelihoodcoupon.search.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.mockito.ArgumentCaptor;
import java.util.Collections;

import com.livelihoodcoupon.common.dto.Coordinate;
import com.livelihoodcoupon.common.service.KakaoApiService;
import com.livelihoodcoupon.parkinglot.dto.NearbySearchRequest;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotNearbyResponse;
import com.livelihoodcoupon.parkinglot.service.ParkingLotService;
import com.livelihoodcoupon.search.dto.PageResponse;
import com.livelihoodcoupon.search.dto.AnalyzedAddress;
import com.livelihoodcoupon.search.dto.PlaceSearchResponseDto;
import com.livelihoodcoupon.search.dto.SearchRequestDto;
import com.livelihoodcoupon.search.dto.SearchServiceResult;
import com.livelihoodcoupon.search.dto.SearchToken;
import com.livelihoodcoupon.search.entity.PlaceDocument;
import com.livelihoodcoupon.search.repository.SearchRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import kr.co.shineware.nlp.komoran.model.Token;
import reactor.core.publisher.Mono;

@DisplayName("SearchService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ElasticServiceTest {

	@Mock
	ElasticPlaceService elasticPlaceService;
	@Mock
	SearchResponse<PlaceDocument> mockResponse;
	@Mock
	private RedisService redisService;
	@Mock
	private SearchRepository searchRepository;
	@Mock
	private KakaoApiService kakaoApiService;
	@Mock
	private QueryService queryService;
	@Mock
	private SearchService searchService;
	@Mock
	private ElasticService elasticService;
	@Mock
	private AnalyzerTest analyzerTest;
	@Mock
	private ElasticsearchClient client;
	@Mock
	private ParkingLotService parkingLotService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		elasticService = new ElasticService(elasticPlaceService, searchService, kakaoApiService,
			analyzerTest, redisService, parkingLotService);
	}

	@Test
	@DisplayName("지도검색 ServiceService 테스트 성공")
	void elasticSearch() throws IOException {
		// Given
		String query = "서울시 종로구 카페";

		SearchRequestDto req = new SearchRequestDto();
		req.setQuery(query);
		req.initDefaults();

		List<SearchToken> resultList = new ArrayList<>();

		SearchToken searchToken1 = new SearchToken("address",
			new Token("서울시", "NNP", 0, 3));
		resultList.add(searchToken1);

		SearchToken searchToken2 = new SearchToken("address",
			new Token("종로구", "NNP", 4, 6));
		resultList.add(searchToken2);

		SearchToken searchToken3 = new SearchToken("category",
			new Token("카페", "NNG", 8, 10));
		resultList.add(searchToken3);

		when(redisService.getWordInfo(anyString())).thenReturn("address");
		when(kakaoApiService.getCoordinatesFromAddress(anyString()))
			.thenReturn(Mono.just(new Coordinate(37.57104033689386, 127.0019782463416)));

		PlaceDocument doc = new PlaceDocument();
		doc.setPlaceName("카페1");
		doc.setLocation(new Coordinate(37.51, 127.01));

		Hit<PlaceDocument> hit = new Hit.Builder<PlaceDocument>()
			.source(doc)
			.index("places")
			.id("1")
			.build();
		TotalHits totalHits = new TotalHits.Builder().value(1).relation(TotalHitsRelation.Eq).build();
		HitsMetadata<PlaceDocument> hitsMetadata = new HitsMetadata.Builder<PlaceDocument>()
			.hits(List.of(hit))
			.total(totalHits)
			.build();
		SearchResponse<PlaceDocument> mockResponse = new SearchResponse.Builder<PlaceDocument>()
			.hits(hitsMetadata)
			.took(10L)
			.timedOut(false)
			.shards(new ShardStatistics.Builder().total(1).successful(1).failed(0).build())
			.build();

		when(elasticPlaceService.searchPlace(any(AnalyzedAddress.class), any(), any(), any(Double.class),
			any(Double.class), any(Double.class), any(Double.class))).thenReturn(
			mockResponse);
		when(searchService.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
			.thenReturn(1.23);
		Coordinate mockCoordinate = new Coordinate(37.5, 127.0);

		//when
		SearchServiceResult result = elasticService.elasticSearch(req, 10, 100);
		Page<PlaceSearchResponseDto> page = result.getPage();

		//then
		assertThat(page.getContent())
			.hasSize(1)
			.extracting(PlaceSearchResponseDto::getPlaceName)
			.containsExactly("카페1");
		assertThat(page.getContent().get(0).getDistance()).isEqualTo(1.23);
	}

	@Test
	@DisplayName("검색한 위치해서 상가 위치까지 거리계산")
	void toSearchPosition() {
		// given
		Coordinate location = new Coordinate(127.0, 37.5); // 위도/경도
		PlaceDocument doc = new PlaceDocument();
		doc.setLocation(location);
		doc.setPlaceName("테스트 카페");

		double refLat = 37.6;
		double refLng = 127.1;
		double mockDistance = 1.0;
		when(searchService.calculateDistance(refLat, refLng, 37.5, 127.0)).thenReturn(mockDistance);

		// when
		PlaceSearchResponseDto result = elasticService.toSearchPosition(doc, refLat, refLng);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getPlaceName()).isEqualTo("테스트 카페");
		assertThat(result.getDistance()).isEqualTo(mockDistance);

		verify(searchService, times(1)).calculateDistance(refLat, refLng, 37.5, 127.0);
	}

	@Test
	@DisplayName("주소로 위도, 경도 재탐색 성공")
	void handleAddressPosition_success() {
		// given
		String address = "서울 종로구";
		SearchRequestDto request = new SearchRequestDto();
		Coordinate coord = Coordinate.builder().lat(37.5).lng(127.0).build();

		when(kakaoApiService.getCoordinatesFromAddress(address)).thenReturn(Mono.just(coord));

		// when
		ResponseEntity<SearchRequestDto> response =
			elasticService.handleAddressPosition(address, request).block();

		// then
		assertEquals(37.5, response.getBody().getLat());
		assertEquals(127.0, response.getBody().getLng());
	}

	@Test
	@DisplayName("주소로 위도, 경도 재탐색 실패")
	void handleAddressPosition_failed() {
		// given
		String address = "서울 종로구";
		SearchRequestDto request = new SearchRequestDto();
		when(kakaoApiService.getCoordinatesFromAddress(address))
			.thenReturn(Mono.error(new RuntimeException("API 실패")));

		// when
		ResponseEntity<SearchRequestDto> response =
			elasticService.handleAddressPosition(address, request).block();

		// then
		assertEquals(request.getLat(), response.getBody().getLat());
		assertEquals(request.getLng(), response.getBody().getLng());
	}

	@Test
	@DisplayName("단어 형태 분리 테스트 성공")
	void testAnalysisChat_success() throws IOException {
		// Given
		String query = "서울시 강남구 카페";

		when(redisService.getWordInfo(anyString())).thenReturn("address");

		// When
		AnalyzedAddress analyzedAddress = elasticService.analysisChat(query);

		// Then
		assertNotNull(analyzedAddress);
		assertEquals(3, analyzedAddress.getResultList().size());
		assertTrue(analyzedAddress.getResultList().stream().anyMatch(token -> "address".equals(token.getFieldName())));
	}

	@Test
	@DisplayName("주소여부 테스트 성공")
	void testIsAddress() throws IOException {
		// Given
		String morph = "서울시";
		String pos = "NNP";
		when(redisService.getWordInfo(morph)).thenReturn("address");

		// When
		String result = elasticService.isAddress(morph);

		// Then
		assertEquals("address", result);
	}

	@Test
	@DisplayName("장소 기반 주변 주차장 검색 성공")
	void searchParkingLotsNearPlace_success() throws IOException {
		// given
		ElasticService spyElasticService = spy(elasticService);

		SearchRequestDto request = new SearchRequestDto();
		request.setQuery("강남역");

		// 1. elasticSearch 모의 결과 설정
		double centerLat = 37.4979;
		double centerLng = 127.0276;
		Page<PlaceSearchResponseDto> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 1), 0);
		SearchServiceResult mockPlaceSearchResult = new SearchServiceResult(emptyPage, centerLat, centerLng);

		doReturn(mockPlaceSearchResult).when(spyElasticService).elasticSearch(any(SearchRequestDto.class), eq(1), eq(1));

		// 2. parkingLotService.findNearby 모의 결과 설정
		PageResponse<ParkingLotNearbyResponse> mockParkingLotResponse = new PageResponse<>(new PageImpl<>(Collections.emptyList()), 10, centerLat, centerLng);
		when(parkingLotService.findNearby(any(NearbySearchRequest.class))).thenReturn(mockParkingLotResponse);

		// when
		spyElasticService.searchParkingLotsNearPlace(request);

		// then
		// elasticSearch가 호출되었는지 검증
		verify(spyElasticService, times(1)).elasticSearch(request, 1, 1);

		// parkingLotService.findNearby가 올바른 좌표로 호출되었는지 검증
		ArgumentCaptor<NearbySearchRequest> captor = ArgumentCaptor.forClass(NearbySearchRequest.class);
		verify(parkingLotService, times(1)).findNearby(captor.capture());
		NearbySearchRequest capturedRequest = captor.getValue();
		assertThat(capturedRequest.getLat()).isEqualTo(centerLat);
		assertThat(capturedRequest.getLng()).isEqualTo(centerLng);
	}
}