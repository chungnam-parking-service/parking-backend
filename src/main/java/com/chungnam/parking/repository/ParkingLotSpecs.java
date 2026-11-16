package com.chungnam.parking.repository;

import com.chungnam.parking.domain.ChungnamRegion;
import com.chungnam.parking.domain.FeeType;
import com.chungnam.parking.domain.LotSource;
import com.chungnam.parking.domain.ParkingCategory;
import com.chungnam.parking.domain.ParkingLot;
import com.chungnam.parking.dto.ParkingSearchCondition;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 동적 검색 조건 (JPA Specification)
 */
public final class ParkingLotSpecs {

    private ParkingLotSpecs() {
    }

    public static Specification<ParkingLot> of(ParkingSearchCondition cond) {
        List<Specification<ParkingLot>> specs = new ArrayList<>();
        specs.add(publicDataOnly());
        if (cond.region() != null) specs.add(region(cond.region()));
        if (cond.category() != null) specs.add(category(cond.category()));
        if (cond.feeType() != null) specs.add(feeType(cond.feeType()));
        if (cond.keyword() != null && !cond.keyword().isBlank()) specs.add(keyword(cond.keyword().trim()));
        if (cond.minSpaces() != null) specs.add(minSpaces(cond.minSpaces()));
        return Specification.allOf(specs);
    }

    /** 직접 등록한 건물 주차장(MANUAL)은 일반 주차장 검색에서 제외 */
    static Specification<ParkingLot> publicDataOnly() {
        return (root, q, cb) -> cb.or(
                cb.isNull(root.get("source")),
                cb.equal(root.get("source"), LotSource.PUBLIC_DATA));
    }

    static Specification<ParkingLot> region(ChungnamRegion region) {
        return (root, q, cb) -> cb.equal(root.get("region"), region);
    }

    static Specification<ParkingLot> category(ParkingCategory category) {
        return (root, q, cb) -> cb.equal(root.get("category"), category);
    }

    static Specification<ParkingLot> feeType(FeeType feeType) {
        return (root, q, cb) -> cb.equal(root.get("feeType"), feeType);
    }

    static Specification<ParkingLot> minSpaces(int min) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("totalSpaces"), min);
    }

    /** 주차장명 / 도로명주소 / 지번주소 부분 일치 */
    static Specification<ParkingLot> keyword(String keyword) {
        String like = "%" + keyword + "%";
        return (root, q, cb) -> cb.or(
                cb.like(root.get("name"), like),
                cb.like(root.get("roadAddress"), like),
                cb.like(root.get("jibunAddress"), like)
        );
    }
}
