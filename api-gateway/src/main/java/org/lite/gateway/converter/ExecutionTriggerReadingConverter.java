package org.lite.gateway.converter;

import org.lite.gateway.enums.ExecutionTrigger;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class ExecutionTriggerReadingConverter implements Converter<String, ExecutionTrigger> {

    @Override
    public ExecutionTrigger convert(@NonNull String source) {
        try {
            return ExecutionTrigger.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExecutionTrigger.MANUAL;
        }
    }
}
