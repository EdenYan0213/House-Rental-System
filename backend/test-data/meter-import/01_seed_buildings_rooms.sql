USE house_db;

-- Seed buildings for meter import tests
INSERT INTO building (name, code, address, total_floors)
SELECT 'A栋', 'A', '测试园区A栋', 3
WHERE NOT EXISTS (SELECT 1 FROM building WHERE name = 'A栋');

INSERT INTO building (name, code, address, total_floors)
SELECT 'B栋', 'B', '测试园区B栋', 3
WHERE NOT EXISTS (SELECT 1 FROM building WHERE name = 'B栋');

-- Seed rooms for A栋
INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 1, '101', '一室一厅', 35.0, 'IDLE', 100101, 200101
FROM building b WHERE b.name = 'A栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 1 AND r.room_number = '101');

INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 1, '102', '一室一厅', 36.0, 'IDLE', 100102, 200102
FROM building b WHERE b.name = 'A栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 1 AND r.room_number = '102');

INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 2, '201', '两室一厅', 52.0, 'IDLE', 100201, 200201
FROM building b WHERE b.name = 'A栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 2 AND r.room_number = '201');

INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 2, '202', '两室一厅', 54.0, 'IDLE', 100202, 200202
FROM building b WHERE b.name = 'A栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 2 AND r.room_number = '202');

-- Seed rooms for B栋
INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 1, '101', '一室一厅', 34.0, 'IDLE', 110101, 210101
FROM building b WHERE b.name = 'B栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 1 AND r.room_number = '101');

INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 1, '102', '一室一厅', 34.5, 'IDLE', 110102, 210102
FROM building b WHERE b.name = 'B栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 1 AND r.room_number = '102');

INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 2, '201', '两室一厅', 51.0, 'IDLE', 110201, 210201
FROM building b WHERE b.name = 'B栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 2 AND r.room_number = '201');

INSERT INTO room (building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id)
SELECT b.id, 2, '202', '两室一厅', 53.5, 'IDLE', 110202, 210202
FROM building b WHERE b.name = 'B栋'
AND NOT EXISTS (SELECT 1 FROM room r WHERE r.building_id = b.id AND r.floor = 2 AND r.room_number = '202');
