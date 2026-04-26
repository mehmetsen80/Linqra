package org.lite.gateway.converter;

import org.lite.gateway.enums.ExecutionResult;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class ExecutionResultReadingConverter implements Converter<String, ExecutionResult> {

    @Override
    public ExecutionResult convert(@NonNull String source) {
        try {
            return ExecutionResult.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExecutionResult.UNKNOWN;
        }
    }
}
