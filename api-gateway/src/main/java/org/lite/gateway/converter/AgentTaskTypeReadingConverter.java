package org.lite.gateway.converter;

import org.lite.gateway.enums.AgentTaskType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class AgentTaskTypeReadingConverter implements Converter<String, AgentTaskType> {

    @Override
    public AgentTaskType convert(@NonNull String source) {
        try {
            return AgentTaskType.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AgentTaskType.WORKFLOW_EMBEDDED;
        }
    }
}
