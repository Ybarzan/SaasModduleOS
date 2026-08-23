package com.incokalk.service.tracking;

import java.util.List;
import java.util.UUID;

public interface TrackingProvider {

    String getProviderType();

    String getName();

    List<TrackingUpdate> getTrackingInfo(String trackingNumber, UUID companyId);

    LivePosition getCurrentPosition(String trackingNumber, UUID companyId);

    boolean isAvailable(UUID companyId);
}
