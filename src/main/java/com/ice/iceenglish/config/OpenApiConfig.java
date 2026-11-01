package com.ice.iceenglish.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Project không có sẵn ObjectMapper (Jackson 2) do Spring Boot cấu hình,
     * nên tự khai báo ở đây cho ExamService và các class khác inject.
     * - @ConditionalOnMissingBean: nếu sau này có bean khác thì không bị trùng.
     * - findAndRegisterModules(): tự nạp JavaTimeModule (LocalDateTime...) nếu có trên classpath.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ICE English API System")
                        .version("1.0")
                        .description("Hệ thống API quản lý học tập ICE English"))
                // Yêu cầu bảo mật chung cho tất cả API trên Swagger UI
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                // Khai báo kiểu xác thực Bearer JWT
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}