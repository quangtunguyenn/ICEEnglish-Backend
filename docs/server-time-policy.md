# Quy ước thời gian phía backend

- Đồng hồ nghiệp vụ là đồng hồ của máy chủ backend; máy chủ triển khai phải được đồng bộ NTP.
- Múi giờ nghiệp vụ cố định là `Asia/Ho_Chi_Minh` (`UTC+07:00`), không lấy timezone máy của học sinh hoặc timezone mặc định tùy môi trường.
- Backend ghi nhận `startedAt` và `submittedAt`, tự kiểm tra thời hạn bài thi bằng `Clock` phía server. Client không được gửi thời gian bắt đầu, thời điểm nộp hoặc thời điểm hết hạn để backend tin cậy.
- Timestamp trả về API là ISO-8601 có offset Việt Nam, ví dụ `2026-11-07T17:00:00+07:00`. Frontend chỉ định dạng để hiển thị, không dùng giờ trên thiết bị để quyết định quyền nộp bài.
- Các cột `started_at`/`submitted_at` hiện tại vẫn dùng `LocalDateTime`. Migration V5 chỉ thêm trạng thái part đang chạy để tính duration phía server; không chuyển đổi dữ liệu thời gian cũ. Những bản ghi cũ không chứa timezone nên khi triển khai cần xác nhận timezone máy chủ đã dùng lúc ghi chúng. Không tự chuyển đổi dữ liệu cũ nếu chưa xác định được timezone nguồn.
- Để tính thời gian từng part cho `MOCK` và `GENERAL_EXAM`, frontend gọi `PUT /api/v1/submissions/{submissionId}/parts/{partId}/time` khi học sinh vào/chuyển sang part (không gửi thời lượng). Backend ghi nhận part hiện tại và mốc bắt đầu theo server; khi chuyển part/nộp bài, backend cộng thời gian đã trôi qua vào part trước. Lặp lại cùng part không khởi động lại đồng hồ. `HOMEWORK` không hỗ trợ tracking theo part.
- `timeSpentSeconds` cũ trong endpoint tracking và `partLogs` cũ gửi kèm lúc nộp được chấp nhận tương thích nhưng bị bỏ qua. Part đang mở chỉ được cộng thời gian khi chuyển part hoặc nộp bài; do đó số trả từ API part logs trong lúc bài còn làm dở chưa bao gồm khoảng thời gian đang chạy.
- Migration `V5__Add_server_part_timing_to_submissions.sql` lưu part hiện tại và mốc bắt đầu do server ghi. Phải áp dụng migration trước khi chạy phiên bản backend mới.
