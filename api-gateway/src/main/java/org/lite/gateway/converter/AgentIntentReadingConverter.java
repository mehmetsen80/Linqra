package org.lite.gateway.converter;

import org.lite.gateway.enums.AgentIntent;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class AgentIntentReadingConverter implements Converter<String, AgentIntent> {

    @Override
    public AgentIntent convert(@NonNull String source) {
        try {
            return AgentIntent.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // Or a default value if appropriate
        }
    }
}
