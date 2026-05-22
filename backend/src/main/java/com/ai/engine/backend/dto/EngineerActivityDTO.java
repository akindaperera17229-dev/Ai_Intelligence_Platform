package com.ai.engine.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EngineerActivityDTO {

    private String engineerName;

    private Long totalEvents;
}