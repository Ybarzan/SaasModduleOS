package com.fleethub.integration;

import com.fleethub.integration.dto.GpsPositionDto;

import java.util.List;

public interface GpsSource {

    List<GpsPositionDto> fetchPositions();
}
