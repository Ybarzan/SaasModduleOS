package com.fleethub.service.tracking;

import java.util.List;

public interface TrackingProvider {

    String getProviderType();

    String getName();

    List<TrackingUpdate> getTrackingInfo(String trackingNumber, Long companyId);

    LivePosition getCurrentPosition(String trackingNumber, Long companyId);

    boolean isAvailable(Long companyId);
}
