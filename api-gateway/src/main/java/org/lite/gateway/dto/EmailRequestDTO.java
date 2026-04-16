package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standard request DTO for sending emails through the Linqra Gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequestDTO {
    private String to;
    private String from;
    private String subject;
    private String body;
    private List<String> cc;
    private List<String> bcc;
    private boolean html;
}
