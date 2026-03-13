# 水电 Excel 导入测试数据

目录文件说明:
- 01_seed_buildings_rooms.sql: 测试楼栋和房间初始化 SQL
- 02_valid_multi_sheet_multi_date.xlsx: 正常导入样例（多楼栋、多日期）
- 03_overwrite_same_room_date.xlsx: 覆盖规则样例（同房间同日期）
- 04_error_sheet_not_match.xlsx: 错误样例（Sheet 不匹配）
- 05_error_missing_required_column.xlsx: 错误样例（缺少必需列）
- 06_error_duplicate_date_room.xlsx: 错误样例（同日期+房间重复）
- 07_error_invalid_date_room_number_reading.xlsx: 错误样例（日期/房间/读数非法）
- 08_perf_5000_records.xlsx: 性能样例（5000 条）

建议验证顺序:
1) 先执行 01_seed_buildings_rooms.sql
2) 上传 02 验证成功导入
3) 上传 03 验证覆盖更新
4) 上传 04~07 验证错误提示和错误报告下载
5) 上传 08 验证 5000 条性能
