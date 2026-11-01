-- MySQL schema for the Erode District Emergency Response Management System
-- Run once:   mysql -u root -p < database/schema.sql
-- (the Java program can also run this file for you: menu option "Set up MySQL").

CREATE DATABASE IF NOT EXISTS sudar_dispatch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sudar_dispatch;

CREATE TABLE IF NOT EXISTS locations (
    id      VARCHAR(10)  NOT NULL PRIMARY KEY,
    seq     INT          NOT NULL,                 -- keeps the original display order
    name    VARCHAR(120) NOT NULL,
    type    VARCHAR(20)  NOT NULL,                 -- STATION, HOSPITAL, MARKET, INDUSTRIAL, SCHOOL, RESIDENTIAL, JUNCTION
    lat     DOUBLE       NOT NULL,
    lon     DOUBLE       NOT NULL,
    taluk   VARCHAR(40),
    x       INT          NOT NULL,                 -- map position (pixels)
    y       INT          NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS roads (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    from_id  VARCHAR(10) NOT NULL,
    to_id    VARCHAR(10) NOT NULL,
    km       DOUBLE      NOT NULL,
    traffic  DOUBLE      NOT NULL DEFAULT 1.0,     -- travel-time multiplier
    blocked  TINYINT(1)  NOT NULL DEFAULT 0,       -- 1 = road closed
    FOREIGN KEY (from_id) REFERENCES locations(id),
    FOREIGN KEY (to_id)   REFERENCES locations(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS units (
    id       VARCHAR(20) NOT NULL PRIMARY KEY,
    seq      INT         NOT NULL,
    type     VARCHAR(12) NOT NULL,                 -- FIRE, POLICE, AMBULANCE
    station  VARCHAR(10) NOT NULL,
    FOREIGN KEY (station) REFERENCES locations(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS incidents (
    id                VARCHAR(12)  NOT NULL PRIMARY KEY,
    type              VARCHAR(80)  NOT NULL,
    location          VARCHAR(10)  NOT NULL,
    severity          INT          NOT NULL,       -- 1 (low) .. 5 (critical)
    reported_at       DOUBLE       NOT NULL,       -- simulation minute
    status            VARCHAR(14)  NOT NULL DEFAULT 'PENDING',   -- PENDING, DISPATCHED, QUEUED, UNREACHABLE
    response_minutes  DOUBLE       NULL,
    FOREIGN KEY (location) REFERENCES locations(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS incident_needs (        -- which unit types an incident needs
    incident_id VARCHAR(12) NOT NULL,
    seq         INT         NOT NULL,
    need        VARCHAR(12) NOT NULL,
    PRIMARY KEY (incident_id, seq),
    FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS assignments (           -- which unit was sent where, and by which route
    id             INT AUTO_INCREMENT PRIMARY KEY,
    incident_id    VARCHAR(12) NOT NULL,
    unit_id        VARCHAR(20) NOT NULL,
    path           TEXT        NOT NULL,           -- location ids joined by '>'
    km             DOUBLE      NOT NULL,
    minutes        DOUBLE      NOT NULL,
    dispatched_at  DOUBLE      NOT NULL,
    arrival_at     DOUBLE      NOT NULL,
    FOREIGN KEY (incident_id) REFERENCES incidents(id),
    FOREIGN KEY (unit_id)     REFERENCES units(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS dispatch_log (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    line_no  INT          NOT NULL,
    message  VARCHAR(255) NOT NULL,
    created  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Handy queries for the viva / report:
--   SELECT i.id, i.type, l.name, i.severity, i.status, i.response_minutes
--     FROM incidents i JOIN locations l ON l.id = i.location ORDER BY i.reported_at;
--   SELECT a.incident_id, a.unit_id, a.minutes, a.path FROM assignments a ORDER BY a.dispatched_at;
--   SELECT type, COUNT(*) FROM units GROUP BY type;
