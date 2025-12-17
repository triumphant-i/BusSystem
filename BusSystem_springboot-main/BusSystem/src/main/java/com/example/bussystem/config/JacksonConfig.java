package com.example.bussystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // 设置全局属性命名策略为：蛇形命名（Snake Case）
        // 例如：stationId -> station_id (注意：原Python代码是 Station_ID，Spring默认蛇形是小写)
        // 为了完美适配原API的大写字段（Station_ID），我们通常建议在Entity上保留 @JsonProperty
        // 但这个配置能处理大部分常规字段的转换
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return objectMapper;
    }
}