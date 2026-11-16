package com.chungnam.parking.place;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceParkingLinkRepository extends JpaRepository<PlaceParkingLink, Long> {

    @EntityGraph(attributePaths = {"parkingLot", "walkingSteps"})
    List<PlaceParkingLink> findByPlace_IdOrderBySortOrderAsc(String placeId);
}
