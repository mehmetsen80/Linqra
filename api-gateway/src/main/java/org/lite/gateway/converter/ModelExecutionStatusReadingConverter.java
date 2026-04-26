package org.lite.gateway.converter;

import org.lite.gateway.model.ExecutionStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class ModelExecutionStatusReadingConverter implements Converter<String, ExecutionStatus> {

    @Override
    public ExecutionStatus convert(@NonNull String source) {
        try {
            return ExecutionStatus.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExecutionStatus.FAILED;
        }
    }
}
