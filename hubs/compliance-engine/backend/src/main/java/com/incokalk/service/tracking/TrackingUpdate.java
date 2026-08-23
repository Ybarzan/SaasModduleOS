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
public class TrackingUpdate {
    private String status;
    private String location;
    private Double latitude;
    private Double longitude;
    private String description;
    private LocalDateTime eventTime;
    private String source;
}
