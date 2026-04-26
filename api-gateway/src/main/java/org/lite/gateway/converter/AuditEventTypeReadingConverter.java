package org.lite.gateway.converter;

import org.lite.gateway.enums.AuditEventType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;

@ReadingConverter
public class AuditEventTypeReadingConverter implements Converter<String, AuditEventType> {

    @Override
    public AuditEventType convert(@NonNull String source) {
        try {
            return AuditEventType.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Pick a safe default or something that indicates unknown if possible
            // DOCUMENT_ACCESSED is first, but maybe we should have an UNKNOWN
            return AuditEventType.valueOf("DOCUMENT_ACCESSED"); 
        }
    }
}
