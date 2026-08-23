package com.fleethub.integration;

import com.fleethub.integration.dto.TachographDayDto;

import java.time.LocalDate;
import java.util.List;

public interface TachographSource {

    List<TachographDayDto> fetchDrivingDays(LocalDate since);
}
