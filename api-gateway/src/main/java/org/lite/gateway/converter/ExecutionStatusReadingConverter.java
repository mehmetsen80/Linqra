package org.lite.gateway.converter;

import org.lite.gateway.enums.ExecutionStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class ExecutionStatusReadingConverter implements Converter<String, ExecutionStatus> {

    @Override
    public ExecutionStatus convert(@NonNull String source) {
        try {
            return ExecutionStatus.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExecutionStatus.RUNNING;
        }
    }
}
