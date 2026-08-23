package com.incokalk.service.tracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LivePosition {
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double course;
    private String heading;
    private LocalDateTime timestamp;
    private String source;
    private String vesselName;
}
