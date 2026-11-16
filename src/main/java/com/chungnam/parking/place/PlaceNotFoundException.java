package com.chungnam.parking.place;

public class PlaceNotFoundException extends RuntimeException {
    public PlaceNotFoundException(String placeId) {
        super("목적지를 찾을 수 없습니다. placeId=" + placeId);
    }
}
