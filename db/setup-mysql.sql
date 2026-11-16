-- 팀원용 템플릿: 비밀번호를 바꾼 뒤 root 로 실행하세요.
CREATE DATABASE IF NOT EXISTS chungnam_parking
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'parking_app'@'localhost' IDENTIFIED BY '여기에_비밀번호';
GRANT ALL PRIVILEGES ON chungnam_parking.* TO 'parking_app'@'localhost';
FLUSH PRIVILEGES;
