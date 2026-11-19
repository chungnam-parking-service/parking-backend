package com.chungnam.parking.report;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 주차 정보 제보 → 관리자 승인 → 목적지 응답 반영. 시연 목적지(place-demo-cafe)로 확인 */
@SpringBootTest(properties = {"admin.api-key=test-admin-key", "uploads.dir=build/test-uploads"})
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {

    static final String PLACE = "place-demo-cafe";
    static final String LOT = "lot-demo-public";   // 시연 카페의 주변 공영주차장 (입구 사진·혜택 없음)
    static final String ADMIN = "test-admin-key";
    /** PNG 파일 앞부분 (형식 판단은 앞 8바이트로 한다) */
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired
    MockMvc mvc;

    private String submitJson(String body, String ip) throws Exception {
        return mvc.perform(post("/places/{id}/reports", PLACE).contentType(MediaType.APPLICATION_JSON)
                        .content(body).header("X-Forwarded-For", ip))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static MockMultipartFile reportPart(String json) {
        return new MockMultipartFile("report", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 제보는_승인_전에는_화면에_나오지_않고_승인하면_반영된다() throws Exception {
        String receipt = submitJson("""
                {"lotId": "lot-demo-public",
                 "entranceGuide": "공영주차장 정문 옆 차단기로 들어가세요",
                 "benefitDescription": "카페 영수증 제시 시 30분 무료",
                 "walkingSteps": ["출구로 나와 왼쪽", "횡단보도 건너 카페"],
                 "entranceLatitude": 36.8151, "entranceLongitude": 127.1079,
                 "comment": "어제 직접 가봤어요"}
                """, "10.1.0.1");
        assertThat(JsonPath.<String>read(receipt, "$.status")).isEqualTo("pending");
        Integer id = JsonPath.read(receipt, "$.id");

        // 승인 전: 원래 값 그대로
        mvc.perform(get("/places/{id}/parking", PLACE))
                .andExpect(jsonPath("$.alternatives[0].lot.id").value(LOT))
                .andExpect(jsonPath("$.alternatives[0].lot.entranceGuide").value("큰길에서 파란 P 표지판을 따라 진입하세요."))
                .andExpect(jsonPath("$.alternatives[0].benefitDescription").value(nullValue()));

        mvc.perform(post("/api/admin/reports/{id}/approve", id).header("X-Admin-Key", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.autoApplies").value(true));

        // 승인 후: 보낸 칸만 바뀌고, 입구는 확인된 입구로, 확인 시각이 생긴다
        mvc.perform(get("/places/{id}/parking", PLACE))
                .andExpect(jsonPath("$.alternatives[0].lot.entranceGuide").value("공영주차장 정문 옆 차단기로 들어가세요"))
                .andExpect(jsonPath("$.alternatives[0].lot.entranceCoordinates.latitude").value(36.8151))
                .andExpect(jsonPath("$.alternatives[0].lot.coordinateType").value("entrance"))
                .andExpect(jsonPath("$.alternatives[0].lot.verifiedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.alternatives[0].lot.feeDescription").value("최초 30분 500원 · 이후 10분당 200원"))
                .andExpect(jsonPath("$.alternatives[0].benefitDescription").value("카페 영수증 제시 시 30분 무료"))
                .andExpect(jsonPath("$.alternatives[0].walkingSteps", contains("출구로 나와 왼쪽", "횡단보도 건너 카페")))
                // 관리자 메모는 사용자 화면에 나오지 않는다
                .andExpect(content().string(not(containsString("어제 직접 가봤어요"))))
                // 다른 주차장은 그대로
                .andExpect(jsonPath("$.primaryParking[0].lot.entranceGuide").value("매장 정문 반대편, 건물 오른쪽 골목의 지하 입구로 들어가세요."));
    }

    @Test
    void 사진을_함께_올리면_승인_후_입구_사진_URL이_생긴다() throws Exception {
        String receipt = mvc.perform(multipart("/places/{id}/reports", PLACE)
                        .file(reportPart("{\"lotId\": \"lot-demo-public\"}"))
                        .file(new MockMultipartFile("photo", "entrance.png", "image/png", PNG))
                        .header("X-Forwarded-For", "10.1.0.2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photoUrl", matchesPattern("http://localhost/uploads/[0-9a-f-]{36}\\.png")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Integer id = JsonPath.read(receipt, "$.id");
        String photoUrl = JsonPath.read(receipt, "$.photoUrl");

        // 사진 파일이 공개 경로로 내려간다
        mvc.perform(get(photoUrl.replace("http://localhost", "")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        mvc.perform(post("/api/admin/reports/{id}/approve", id).header("X-Admin-Key", ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/places/{id}/parking", PLACE))
                .andExpect(jsonPath("$.alternatives[0].lot.entrancePhotoUrl").value(photoUrl))
                // 사진만 보냈으므로 입구 좌표 종류는 그대로
                .andExpect(jsonPath("$.alternatives[0].lot.coordinateType").value("entrance"));
    }

    @Test
    void 반려하면_반영되지_않고_사진도_지운다() throws Exception {
        String receipt = mvc.perform(multipart("/places/{id}/reports", PLACE)
                        .file(reportPart("{\"lotId\": \"lot-demo-public\", \"feeDescription\": \"무료예요\"}"))
                        .file(new MockMultipartFile("photo", "x.png", "image/png", PNG))
                        .header("X-Forwarded-For", "10.1.0.3"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Integer id = JsonPath.read(receipt, "$.id");
        String fileName = JsonPath.<String>read(receipt, "$.photoUrl").replaceAll(".*/", "");
        assertThat(Files.exists(Path.of("build/test-uploads", fileName))).isTrue();

        mvc.perform(post("/api/admin/reports/{id}/reject", id).header("X-Admin-Key", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"확인 불가\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.reviewNote").value("확인 불가"))
                .andExpect(jsonPath("$.photoUrl").value(nullValue()));
        assertThat(Files.exists(Path.of("build/test-uploads", fileName))).isFalse();

        mvc.perform(get("/places/{id}/parking", PLACE))
                .andExpect(jsonPath("$.alternatives[0].lot.feeDescription").value("최초 30분 500원 · 이후 10분당 200원"));
        // 이미 처리한 제보는 다시 처리할 수 없다
        mvc.perform(post("/api/admin/reports/{id}/approve", id).header("X-Admin-Key", ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));
    }

    @Test
    void 사진이_아닌_파일은_400_INVALID_PHOTO() throws Exception {
        mvc.perform(multipart("/places/{id}/reports", PLACE)
                        .file(reportPart("{\"lotId\": \"lot-demo-public\"}"))
                        .file(new MockMultipartFile("photo", "evil.png", "image/png", "<script>".getBytes()))
                        .header("X-Forwarded-For", "10.1.0.4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO"));
    }

    @Test
    void 빈_제보_위도만_있는_제보_없는_주차장은_거절() throws Exception {
        mvc.perform(post("/places/{id}/reports", PLACE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotId\": \"lot-demo-public\", \"entranceGuide\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_REPORT"));
        mvc.perform(post("/places/{id}/reports", PLACE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotId\": \"lot-demo-public\", \"entranceLatitude\": 36.81}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/places/{id}/reports", PLACE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotId\": \"lot-demo-public\", \"entranceLatitude\": 10.0, \"entranceLongitude\": 127.1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/places/{id}/reports", PLACE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotId\": \"lot-unknown\", \"feeDescription\": \"무료\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOT_NOT_FOUND"));
    }

    @Test
    void lotId_없는_제보는_접수되지만_자동_반영되지_않는다() throws Exception {
        String receipt = submitJson("{\"comment\": \"길 건너에 새 공영주차장이 생겼어요\"}", "10.1.0.5");
        assertThat((Object) JsonPath.read(receipt, "$.lotId")).isNull();
        mvc.perform(get("/api/admin/reports").header("X-Admin-Key", ADMIN))
                .andExpect(jsonPath("$[0].comment").value("길 건너에 새 공영주차장이 생겼어요"))
                .andExpect(jsonPath("$[0].autoApplies").value(false));
    }

    @Test
    void 한_사람은_하루_10건까지() throws Exception {
        for (int i = 0; i < ReportService.DAILY_LIMIT; i++) {
            submitJson("{\"comment\": \"제보 " + i + "\"}", "10.1.0.6");
        }
        mvc.perform(post("/places/{id}/reports", PLACE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\": \"한 번 더\"}").header("X-Forwarded-For", "10.1.0.6"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REPORTS"));
    }

    @Test
    void 관리자_API는_키가_필요하다() throws Exception {
        mvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/reports/1/approve")).andExpect(status().isUnauthorized());
    }
}
