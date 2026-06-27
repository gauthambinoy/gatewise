package com.auvex.gateway.scim;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Teaches Spring to read and write the SCIM content type.
 *
 * <p>SCIM clients use {@code application/scim+json}, which the default Jackson converter doesn't
 * advertise, so without this an IdP's request would be a 415. We add it to the existing JSON
 * converter's supported types so the same converter handles both it and {@code application/json}.
 */
@Configuration
public class ScimWebConfig implements WebMvcConfigurer {

  static final MediaType SCIM_JSON = MediaType.valueOf("application/scim+json");

  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
        List<MediaType> types = new ArrayList<>(jackson.getSupportedMediaTypes());
        if (!types.contains(SCIM_JSON)) {
          types.add(SCIM_JSON);
          jackson.setSupportedMediaTypes(types);
        }
      }
    }
  }
}
