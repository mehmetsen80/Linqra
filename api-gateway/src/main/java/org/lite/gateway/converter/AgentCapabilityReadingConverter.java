package org.lite.gateway.converter;

import org.lite.gateway.enums.AgentCapability;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class AgentCapabilityReadingConverter implements Converter<String, AgentCapability> {

    @Override
    public AgentCapability convert(@NonNull String source) {
        try {
            return AgentCapability.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
