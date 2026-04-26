package org.lite.gateway.converter;

import org.lite.gateway.enums.ExecutionType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class ExecutionTypeReadingConverter implements Converter<String, ExecutionType> {

    @Override
    public ExecutionType convert(@NonNull String source) {
        try {
            return ExecutionType.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Fallback for any unknown values
            return ExecutionType.MANUAL;
        }
    }
}
