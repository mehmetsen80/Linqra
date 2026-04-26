package org.lite.gateway.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class CaseInsensitiveEnumConverterFactory implements ConverterFactory<String, Enum<?>> {

    @Override
    @NonNull
    public <T extends Enum<?>> Converter<String, T> getConverter(@NonNull Class<T> targetType) {
        return new CaseInsensitiveEnumConverter<>(targetType);
    }

    private static class CaseInsensitiveEnumConverter<T extends Enum<?>> implements Converter<String, T> {
        private final Class<T> enumType;

        CaseInsensitiveEnumConverter(Class<T> enumType) {
            this.enumType = enumType;
        }

        @Override
        public T convert(@NonNull String source) {
            if (source.isEmpty()) {
                return null;
            }
            try {
                // Try exact match first
                for (T constant : enumType.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(source)) {
                        return constant;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            return null;
        }
    }
}
