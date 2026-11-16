package com.chungnam.parking.place;

import com.chungnam.parking.domain.ParkingLot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 목적지 ↔ 주차장 관계. 혜택·등록 방법·도보 안내는 주차장이 아니라 이 관계에 속한다.
 * 프론트 PlaceParkingOption 에 대응.
 */
@Entity
@Table(name = "place_parking_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceParkingLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parking_lot_id")
    private ParkingLot parkingLot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParkingRelationship relationship;

    @Column(length = 300)
    private String benefitDescription;

    @Column(length = 500)
    private String validationInstructions;

    /** 도보 분. 모르면 null (직선거리로 만들지 않음) */
    private Integer walkingMinutes;

    /** 순서대로 표시할 도보 안내 */
    @ElementCollection
    @CollectionTable(name = "place_parking_walking_step", joinColumns = @JoinColumn(name = "link_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "step", length = 300)
    private List<String> walkingSteps = new ArrayList<>();

    /** 같은 목적지 안에서의 표시 순서 */
    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private PlaceParkingLink(Place place, ParkingLot parkingLot, ParkingRelationship relationship,
                             String benefitDescription, String validationInstructions,
                             Integer walkingMinutes, List<String> walkingSteps, int sortOrder) {
        this.place = place;
        this.parkingLot = parkingLot;
        this.relationship = relationship;
        this.benefitDescription = benefitDescription;
        this.validationInstructions = validationInstructions;
        this.walkingMinutes = walkingMinutes;
        this.walkingSteps = walkingSteps == null ? new ArrayList<>() : new ArrayList<>(walkingSteps);
        this.sortOrder = sortOrder;
    }
}
