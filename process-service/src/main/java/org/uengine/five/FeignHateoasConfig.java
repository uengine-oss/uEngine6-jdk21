package org.uengine.five;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

import feign.codec.Decoder;

/**
 * Feign 클라이언트가 Spring HATEOAS 응답(_links 포함)을 정상적으로
 * 역직렬화할 수 있도록, Spring이 관리하는 ObjectMapper(Jackson2HalModule 포함)를
 * Feign Decoder에 주입한다.
 */
@Configuration
public class FeignHateoasConfig {

    @Bean
    public Decoder feignDecoder(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        ObjectFactory<HttpMessageConverters> factory =
                () -> new HttpMessageConverters(jacksonConverter);
        return new ResponseEntityDecoder(new SpringDecoder(factory));
    }
}
