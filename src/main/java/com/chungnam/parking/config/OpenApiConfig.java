package com.chungnam.parking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정
 * - 문서 화면: http://localhost:8080/swagger-ui.html
 * - JSON 스펙: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    public static final String ADMIN_KEY = "AdminKey";

    @Bean
    public OpenAPI parkingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("충남 주차정보 검색 API")
                        .description("""
                                충청남도 15개 시·군의 주차장 정보를 검색하는 API 입니다.
                                데이터 출처: 공공데이터포털 전국주차장정보표준데이터
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(ADMIN_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Key")
                                .description("관리자 API 용 키 (ADMIN_API_KEY 환경변수 값)")));
    }
}
