package dev.settleup.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Money leaves the API as JSON <em>strings</em> ("33.34"), not numbers. Many JSON clients
 * (JavaScript included) parse numbers into binary floating point, which would reintroduce the
 * exact precision problems BigDecimal avoids on the server. Inputs accept either form.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule bigDecimalAsPlainStringModule() {
        SimpleModule module = new SimpleModule("BigDecimalAsPlainString");
        module.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.toPlainString());
            }
        });
        return module;
    }
}
