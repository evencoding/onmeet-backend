package com.onmeet.email.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequestDto {

    private String to;
    private String subject;
    private String templateName;
    private Map<String, Object> variables;
}
