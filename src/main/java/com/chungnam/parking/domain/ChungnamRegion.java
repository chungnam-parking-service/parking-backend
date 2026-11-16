package com.chungnam.parking.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 충청남도 15개 시·군
 */
public enum ChungnamRegion {
    CHEONAN("천안시"),
    GONGJU("공주시"),
    BORYEONG("보령시"),
    ASAN("아산시"),
    SEOSAN("서산시"),
    NONSAN("논산시"),
    GYERYONG("계룡시"),
    DANGJIN("당진시"),
    GEUMSAN("금산군"),
    BUYEO("부여군"),
    SEOCHEON("서천군"),
    CHEONGYANG("청양군"),
    HONGSEONG("홍성군"),
    YESAN("예산군"),
    TAEAN("태안군");

    public static final String PROVINCE_NAME = "충청남도";

    private final String label;

    ChungnamRegion(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 주소 문자열에서 시·군을 추출한다. ("충청남도 천안시 동남구 ..." → CHEONAN)
     */
    public static Optional<ChungnamRegion> fromAddress(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(r -> address.contains(r.label))
                .findFirst();
    }

    public static boolean isChungnam(String address) {
        return address != null && (address.startsWith(PROVINCE_NAME) || address.startsWith("충남"));
    }
}
