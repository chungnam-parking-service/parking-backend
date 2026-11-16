package com.chungnam.parking.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "public-data.parking")
public record PublicParkingProperties(
        String baseUrl,
        String serviceKey,
        int pageSize,
        String syncCron,
        boolean syncEnabled
) {
    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
