package com.chungnam.parking.domain;

/**
 * 주차장 데이터 출처
 * - PUBLIC_DATA: 공공데이터 동기화로 들어온 주차장 (기존 행은 null 이어도 PUBLIC_DATA 로 취급)
 * - MANUAL: 건물 주차장 등 팀이 직접 등록한 주차장 (공공데이터 동기화가 건드리지 않음)
 */
public enum LotSource {
    PUBLIC_DATA,
    MANUAL
}
