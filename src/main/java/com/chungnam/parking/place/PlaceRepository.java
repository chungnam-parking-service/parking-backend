package com.chungnam.parking.place;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, String> {

    /** term 은 Place.normalize() 로 공백 제거·소문자 처리된 값 */
    @Query("select p from Place p where p.searchText like concat('%', :term, '%') order by p.name")
    List<Place> search(@Param("term") String term);
}
