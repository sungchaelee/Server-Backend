package com.livelihoodcoupon.search.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.livelihoodcoupon.common.config.SearchProperties;
import com.livelihoodcoupon.common.exception.BusinessException;
import com.livelihoodcoupon.common.exception.ErrorCode;
import com.livelihoodcoupon.place.entity.Place;
import com.livelihoodcoupon.search.dto.AutocompleteDto;
import com.livelihoodcoupon.search.dto.AutocompleteResponseDto;
import com.livelihoodcoupon.search.dto.PlaceSearchResponseDto;
import com.livelihoodcoupon.search.dto.SearchRequestDto;
import com.livelihoodcoupon.search.dto.SearchResponseDto;
import com.livelihoodcoupon.search.dto.SearchServiceResult;
import com.livelihoodcoupon.search.repository.SearchRepository;
import com.livelihoodcoupon.search.service.ElasticPlaceService;
import com.livelihoodcoupon.search.service.ElasticService;
import com.livelihoodcoupon.search.service.RedisWordRegister;
import com.livelihoodcoupon.parkinglot.dto.ParkingLotNearbyResponse;
import com.livelihoodcoupon.parkinglot.service.ParkingLotService;
import com.livelihoodcoupon.search.dto.PageResponse;
import com.livelihoodcoupon.search.service.SearchService;

@DisplayName("Search 통합테스트")
@WebMvcTest(SearchController.class)
@Import(SearchProperties.class)
public class SearchControllerTest {

	List<Place> places = null;
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SearchProperties searchProperties;

	@MockitoBean
	private SearchService searchService;

	@MockitoBean
	private ElasticService elasticService;

	@MockitoBean
	private ParkingLotService parkingLotService;

	@Mock
	private RedisWordRegister redisWordRegister;

	@Mock
	private SearchRepository searchRepository;

	@Mock
	private ElasticPlaceService elasticPlaceService;

	@BeforeEach
	void setUp() {
		//elasticService = mock(ElasticService.class); // Komoran 필요 없음
		//searchController = new SearchController(search, elasticService);
	}

	@Test
	@DisplayName("한단어 테스트 성공")
	void testSearch_OneWord_Success() throws Exception {

		//given
		String query = "음식점";
		SearchRequestDto req = new SearchRequestDto();
		req.setQuery(query);
		req.initDefaults();

		SearchResponseDto place1 = SearchResponseDto.builder()
			.placeId("650685922")    //.category("음식점 > 일식 > 일식집")
			.placeName("타마고").roadAddress("서울 중구 을지로 지하 88")
			.lotAddress("서울 중구 을지로2가 149-10").lat(37.56610453).lng(126.9862248)
			.phone("070-8805-0922").categoryGroupName("음식점").build();

		SearchResponseDto place2 = SearchResponseDto.builder()
			.placeId("853125114")    //.category("음식점 > 카페 > 테마카페 > 디저트카페")
			.placeName("짜드라").roadAddress("서울 중구 을지로 지하 88")
			.lotAddress("서울 중구 을지로2가 149-10").lat(37.56614425).lng(126.9868644)
			.phone("010-8715-6111").categoryGroupName("카페").build();
		List<SearchResponseDto> searchResponses = List.of(place1, place2);
		Pageable pageable = PageRequest.of(req.getPage() - 1, 10, Sort.unsorted());
		Page<SearchResponseDto> pageList = new PageImpl<>(searchResponses, pageable, 2);
		given(searchService.search(req, searchProperties.getPageSize(), searchProperties.getMaxResults())).willReturn(
			pageList);

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/search")
				.param("query", query)
		);

		//String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		//System.out.println("API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.status().isOk())  // 상태 코드가 200 OK이어야 한다
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content").isArray())  // JSON path로 배열이 맞는지 검증
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content.length()").value(2))  // 배열의 길이
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[0].placeId").value("650685922"))  // 첫 번째 항목의 placeId 확인
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[1].placeName").value("짜드라"));  // 첫 번째 항목의 placeName 확인
	}

	@Test
	@DisplayName("두단어 테스트 성공")
	void testSearch_TwoWord_Success() throws Exception {

		//given
		String query = "서울시 종로구";
		SearchRequestDto req = new SearchRequestDto();
		req.setQuery(query);
		req.initDefaults();

		SearchResponseDto place1 = SearchResponseDto.builder()
			.placeId("22318916")    //.category("음식점 > 일식 > 참치회")
			.placeName("종로참치").roadAddress("서울 종로구 청계천로 97")
			.lotAddress("서울 종로구 관철동 37-2").lat(37.56836267).lng(126.9884894)
			.phone("02-2265-3737").categoryGroupName("음식점").build();

		SearchResponseDto place2 = SearchResponseDto.builder()
			.placeId("161332969")    //.category("음식점 > 술집 > 호프,요리주점")
			.placeName("청계뷰호프").roadAddress("서울 종로구 청계천로 97")
			.lotAddress("서울 종로구 관철동 37-2").lat(37.56840862).lng(126.9884419)
			.phone("010-8715-6111").categoryGroupName("음식점").build();
		List<SearchResponseDto> searchResponses = List.of(place1, place2);
		Pageable pageable = PageRequest.of(req.getPage() - 1, 10, Sort.unsorted());
		Page<SearchResponseDto> pageList = new PageImpl<>(searchResponses, pageable, 2);
		given(searchService.search(req, searchProperties.getPageSize(), searchProperties.getMaxResults())).willReturn(
			pageList);

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/search")
				.param("query", query)
		);

		//then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.status().isOk())  // 상태 코드가 200 OK이어야 한다
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content").isArray())  // JSON path로 배열이 맞는지 검증
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content.length()").value(2))  // 배열의 길이
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[0].placeId").value("22318916"))  // 첫 번째 항목의 placeId 확인
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[1].placeName").value("청계뷰호프"));  // 첫 번째 항목의 placeName 확인
	}

	@Test
	@DisplayName("세단어 테스트 성공")
	void testSearch_ThreeWord_Success() throws Exception {

		//given
		String query = "서울시 종로구 참치";
		SearchRequestDto req = new SearchRequestDto();
		req.setQuery(query);
		req.initDefaults();

		SearchResponseDto place1 = SearchResponseDto.builder()
			.placeId("22318916")    //.category("음식점 > 일식 > 참치회")
			.placeName("종로참치").roadAddress("서울 종로구 청계천로 97")
			.lotAddress("서울 종로구 관철동 37-2").lat(37.56836267).lng(126.9884894)
			.phone("02-2265-3737").categoryGroupName("음식점").build();
		List<SearchResponseDto> searchResponses = List.of(place1);
		Pageable pageable = PageRequest.of(req.getPage() - 1, 10, Sort.unsorted());
		Page<SearchResponseDto> pageList = new PageImpl<>(searchResponses, pageable, 2);
		given(searchService.search(req, searchProperties.getPageSize(), searchProperties.getMaxResults())).willReturn(
			pageList);

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/search")
				.param("query", query)
		);
		String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		System.out.println("API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.status().isOk())  // 상태 코드가 200 OK이어야 한다
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content").isArray())  // JSON path로 배열이 맞는지 검증
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content.length()").value(1))  // 배열의 길이
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[0].placeId").value("22318916"))  // 첫 번째 항목의 placeId 확인
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[0].placeName").value("종로참치"));  // 첫 번째 항목의 placeName 확인
	}

	@Test
	@DisplayName("엘라스틱 목록 테스트 성공")
	void searchElastic_success() throws Exception {

		//given
		String query = "서울시 종로구 참치";
		SearchRequestDto req = SearchRequestDto.builder().query(query).build();
		req.initDefaults();

		PlaceSearchResponseDto place1 = PlaceSearchResponseDto.builder()
			.placeId("22318916")    //.category("음식점 > 일식 > 참치회")
			.placeName("종로참치").roadAddress("서울 종로구 청계천로 97")
			.lotAddress("서울 종로구 관철동 37-2").lat(37.56836267).lng(126.9884894)
			.phone("02-2265-3737").categoryGroupName("음식점").build();
		List<PlaceSearchResponseDto> searchResponses = List.of(place1);
		Pageable pageable = PageRequest.of(req.getPage() - 1, 10, Sort.unsorted());
		Page<PlaceSearchResponseDto> pageList = new PageImpl<>(searchResponses, pageable, 1);
		SearchServiceResult result = new SearchServiceResult(pageList, req.getLat(), req.getLng());
		when(elasticService.elasticSearch(req, searchProperties.getPageSize(),
			searchProperties.getMaxResults())).thenReturn(result);

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/searches")
				.param("query", query)
		);
		String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		System.out.println("API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.status().isOk())  // 상태 코드가 200 OK이어야 한다
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content").isArray())  // JSON path로 배열이 맞는지 검증
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.content.length()").value(1))  // 배열의 길이
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[0].placeId").value("22318916"))  // 첫 번째 항목의 placeId 확인
			.andExpect(
				MockMvcResultMatchers.jsonPath("$.data.content[0].placeName").value("종로참치"));  // 첫 번째 항목의 placeName 확인
	}

	@Test
	@DisplayName("엘라스틱 상세내용 테스트 성공")
	void searchElasticDetail_success() throws Exception {

		//given
		String id = "22318916";
		String place_name = "테스트 카페";
		SearchRequestDto req = SearchRequestDto.builder()
			.lat(37.56836267).lng(126.9884894).query(id).build();
		req.initDefaults();

		PlaceSearchResponseDto place1 = PlaceSearchResponseDto.builder()
			.placeId(id)    //.category("음식점 > 일식 > 참치회")
			.placeName(place_name).roadAddress("서울 종로구 청계천로 97")
			.lotAddress("서울 종로구 관철동 37-2").lat(req.getLat()).lng(req.getLng())
			.phone("02-2265-3737").categoryGroupName("음식점").build();

		when(elasticService.elasticSearchDetail(id, req)).thenReturn(place1);

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/searches/{id}", id)
				.param("lat", String.valueOf(req.getLat()))
				.param("lng", String.valueOf(req.getLng()))
				.param("query", req.getQuery()));
		String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		System.out.println("===>API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.placeId").value(id))
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.placeName").value(place_name));
	}

	@Test
	@DisplayName("엘라스틱 상세내용 테스트 실패")
	void searchElasticDetail_failed404() throws Exception {

		//given
		String id = "22318916";
		String place_name = "테스트 카페";
		SearchRequestDto req = SearchRequestDto.builder()
			.lat(37.56836267).lng(126.9884894).query(id).build();
		req.initDefaults();

		PlaceSearchResponseDto place1 = PlaceSearchResponseDto.builder()
			.placeId(id)    //.category("음식점 > 일식 > 참치회")
			.placeName(place_name).roadAddress("서울 종로구 청계천로 97")
			.lotAddress("서울 종로구 관철동 37-2").lat(req.getLat()).lng(req.getLng())
			.phone("02-2265-3737").categoryGroupName("음식점").build();

		when(elasticService.elasticSearchDetail(id, req)).thenThrow(
			new BusinessException(ErrorCode.NOT_FOUND, "검색 결과가 없습니다."));

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/searches/{id}", id)
				.param("lat", String.valueOf(req.getLat()))
				.param("lng", String.valueOf(req.getLng()))
				.param("query", req.getQuery()));
		String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		System.out.println("===>API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isNotFound())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.jsonPath("$.success").value(false))
			.andExpect(MockMvcResultMatchers.jsonPath("$.error.message").value("검색 결과가 없습니다."));
	}

	@Test
	@DisplayName("엘라스틱 자동완성 테스트 성공")
	void searchElasticAutocomplete_success() throws Exception {

		//given
		String word = "강";
		int maxRecordSize = 10;
		AutocompleteDto req = AutocompleteDto.builder().word(word).build();

		AutocompleteResponseDto dto = AutocompleteResponseDto.builder().word("강남").build();
		AutocompleteResponseDto dto2 = AutocompleteResponseDto.builder().word("강남구청").build();
		List<AutocompleteResponseDto> list = List.of(dto, dto2);
		when(elasticService.elasticSearchAutocomplete(refEq(req), eq(maxRecordSize)))
			.thenReturn(list);

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/suggestions")
				.param("word", word)
		);
		String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		System.out.println("API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
			.andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray())
			.andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
			.andExpect(MockMvcResultMatchers.jsonPath("$.data[0].word").value("강남"))
			.andExpect(MockMvcResultMatchers.jsonPath("$.data[1].word").value("강남구청"));
	}

	@Test
	@DisplayName("엘라스틱 자동완성 테스트 실패(400)")
	void searchElasticAutocomplete_failed400() throws Exception {

		//given
		String word = "";
		int maxRecordSize = 10;
		AutocompleteDto req = AutocompleteDto.builder().word(word).build();

		List<AutocompleteResponseDto> list = List.of();
		when(elasticService.elasticSearchAutocomplete(refEq(req), eq(maxRecordSize)))
			.thenReturn(list);

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/suggestions")
				.param("word", word)
		);
		String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		System.out.println("API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isBadRequest())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.jsonPath("$.success").value(false))
			.andExpect(MockMvcResultMatchers.jsonPath("$.error.message").value("word: 검색어는 필수입니다."));
	}

	@Test
	@DisplayName("엘라스틱 자동완성 테스트 실패(404)")
	void searchElasticAutocomplete_failed404() throws Exception {

		//given
		String word = "asdfsafd";
		int maxRecordSize = 10;
		AutocompleteDto req = AutocompleteDto.builder().word(word).build();

		List<AutocompleteResponseDto> list = List.of();
		when(elasticService.elasticSearchAutocomplete(refEq(req), eq(maxRecordSize)))
			.thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "검색 결과가 없습니다."));

		//when
		ResultActions resultActions = mockMvc.perform(
			get("/api/suggestions")
				.param("word", word)
		);
		String responseContent = resultActions.andReturn().getResponse().getContentAsString();
		System.out.println("API 응답 내용: " + responseContent);

		//then
		resultActions.andExpect(status().isNotFound())
			.andDo(print())
			.andExpect(MockMvcResultMatchers.jsonPath("$.success").value(false))
			.andExpect(MockMvcResultMatchers.jsonPath("$.error.message").value("검색 결과가 없습니다."));
	}

	@Test
	@DisplayName("주변 주차장 검색 테스트 성공")
	void searchNearbyParkingLots_success() throws Exception {
		// given
		String query = "강남역";
		Page<ParkingLotNearbyResponse> page = new PageImpl<>(List.of(ParkingLotNearbyResponse.builder().id(1L).parkingLotName("테스트 주차장").build()));
		PageResponse<ParkingLotNearbyResponse> mockResponse = new PageResponse<>(page, 10, 37.5, 127.0);

		given(parkingLotService.searchByQueryOrCoord(any(SearchRequestDto.class))).willReturn(mockResponse);

		// when
		ResultActions resultActions = mockMvc.perform(
			get("/api/search/parkinglots")
				.param("query", query)
		);

		// then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.content[0].parkingLotName").value("테스트 주차장"));
	}

	@Test
	@DisplayName("주변 주차장 검색 파라미터 없을 시 실패")
	void searchNearbyParkingLots_badRequest() throws Exception {
		// given
		given(parkingLotService.searchByQueryOrCoord(any(SearchRequestDto.class)))
			.willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "좌표 또는 검색어가 필요합니다."));

		// when
		ResultActions resultActions = mockMvc.perform(
			get("/api/search/parkinglots")
		);

		// then
		resultActions.andExpect(status().isBadRequest())
			.andDo(print())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.message").value("좌표 또는 검색어가 필요합니다."));
	}

	@Test
	@DisplayName("장소 기반 주변 주차장 검색 성공")
	void searchParkingLotsNearPlace_success() throws Exception {
		// given
		SearchRequestDto request = SearchRequestDto.builder()
			.query("강남역")
			.build();

		ParkingLotNearbyResponse parkingLotResponse = ParkingLotNearbyResponse.builder()
			.id(1L)
			.parkingLotName("강남역 근처 주차장")
			.build();
		Page<ParkingLotNearbyResponse> page = new PageImpl<>(List.of(parkingLotResponse));
		PageResponse<ParkingLotNearbyResponse> mockResponse = new PageResponse<>(page, 10, 37.4979, 127.0276);

		given(elasticService.searchParkingLotsNearPlace(any(SearchRequestDto.class))).willReturn(mockResponse);

		// when
		ResultActions resultActions = mockMvc.perform(
			get("/api/searches/parkinglots")
				.param("query", "강남역")
		);

		// then
		resultActions.andExpect(status().isOk())
			.andDo(print())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.content").isArray())
			.andExpect(jsonPath("$.data.content[0].parkingLotName").value("강남역 근처 주차장"));
	}

}
