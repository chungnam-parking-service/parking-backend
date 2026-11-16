package com.chungnam.parking.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 주차장 OpenAPI 호출
 */
@Slf4j
@Component
public class PublicParkingApiClient {

    private final RestClient restClient;
    private final PublicParkingProperties props;

    public PublicParkingApiClient(RestClient.Builder builder, PublicParkingProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restClient = builder.requestFactory(factory).build();
        this.props = props;
    }

    public PublicParkingApiResponse.Body fetchPage(int pageNo) {
        // 인증키는 "Decoding" 키를 넣는다. URI 변수로 넘기면 RestClient 가 +, / 등을 안전하게 인코딩한다.
        PublicParkingApiResponse response = restClient.get()
                .uri(props.baseUrl() + "?serviceKey={key}&pageNo={page}&numOfRows={rows}&type=json",
                        props.serviceKey(), pageNo, props.pageSize())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(PublicParkingApiResponse.class);

        if (response == null) {
            throw new IllegalStateException("공공데이터 API 응답이 비어 있습니다. page=" + pageNo);
        }
        PublicParkingApiResponse.Header header = response.header();
        if (header == null || !header.isSuccess()) {
            // "03" = NODATA_ERROR (마지막 페이지 이후)
            if (header != null && "03".equals(header.resultCode())) {
                return null;
            }
            throw new IllegalStateException("공공데이터 API 오류: "
                    + (header == null ? "no header" : header.resultCode() + " " + header.resultMsg()));
        }
        return response.body();
    }
}
