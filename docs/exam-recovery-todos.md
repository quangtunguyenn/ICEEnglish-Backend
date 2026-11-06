# TODO: Lưu và khôi phục bài làm

## Mục tiêu và phạm vi

- [ ] Luyện đề (`GENERAL_EXAM`): học sinh chủ động bấm **Lưu**, chỉ lưu đáp án trên trình duyệt bằng `localStorage`; không giới hạn thời gian và không hỗ trợ đồng bộ giữa thiết bị.
- [ ] Thi thử (`MOCK`): học sinh chủ động bấm **Lưu**, lưu bản chụp vào `localStorage` và đồng bộ lên DB. DB là nguồn chính thức; bản chưa đồng bộ chỉ khôi phục được trên cùng trình duyệt và thiết bị.
- [ ] Đồng hồ thi thử tiếp tục chạy khi học sinh mất kết nối hoặc đóng tab. Admin xem xét sự cố và can thiệp vào thời gian của từng lượt thi.
- [ ] Frontend tự nộp khi hết giờ; backend có cơ chế dự phòng tự nộp bản nháp đã đồng bộ thành công gần nhất trên DB khi frontend không nộp được.
- [ ] Đáp án thay đổi sau lần bấm Lưu gần nhất có thể bị mất khi xảy ra sự cố. Bản chỉ lưu cục bộ có thể mất nếu xóa dữ liệu trình duyệt hoặc đổi thiết bị.

> Đây là danh sách việc cần triển khai, không phải mô tả các API đã tồn tại. Các việc frontend cần thực hiện trong dự án frontend tương ứng.

## 1. Luyện đề — frontend

- [ ] Khi bấm **Lưu**, lưu toàn bộ đáp án vào `localStorage` theo tài khoản và `submissionId`; thông báo đã lưu trên trình duyệt này.
- [ ] Xử lý lỗi lưu cục bộ rõ ràng, ví dụ trình duyệt từ chối lưu hoặc hết dung lượng; không thông báo thành công nếu lưu thất bại.
- [ ] Khi quay lại lượt làm bài chưa nộp, lấy bản lưu tương ứng và khôi phục đáp án; không dùng bản lưu của tài khoản hoặc lượt làm bài khác.
- [ ] Khi bấm **Nộp**, gửi toàn bộ đáp án đang hiển thị, kể cả thay đổi sau lần lưu gần nhất.
- [ ] Chỉ xóa bản lưu cục bộ khi xác nhận nộp thành công; nếu kết quả chưa rõ, giữ lại và cho phép kiểm tra hoặc thử nộp lại.
- [ ] Thông báo giới hạn: dữ liệu chỉ có trên cùng trình duyệt và thiết bị, có thể mất khi xóa dữ liệu trình duyệt.

## 2. Thi thử — lưu và khôi phục bản nháp

### Backend

- [ ] Tạo migration cho bảng đáp án nháp, liên kết với lượt thi và câu hỏi; bảo đảm duy nhất theo `(submission_id, question_id)`. Không dùng `sub_details` làm bản nháp vì bảng này hiện lưu kết quả đã chấm.
- [ ] Bổ sung API lưu toàn bộ bản nháp theo `submissionId`. Quy định rõ câu không có trong danh sách là đã bỏ chọn; thay thế bản nháp trong một transaction.
- [ ] Kiểm tra quyền học sinh và quyền sở hữu lượt thi; chỉ chấp nhận lượt `MOCK` chưa nộp, ở trạng thái và trong thời hạn cho phép. Kiểm tra câu hỏi thuộc đề, đáp án hợp lệ và không trùng câu hỏi.
- [ ] Bổ sung API lấy bản nháp của lượt thi cho đúng học sinh; trả phiên bản, thông tin lưu gần nhất và trạng thái lượt thi cần thiết cho việc khôi phục.
- [ ] Bổ sung kiểm soát phiên bản: chỉ chấp nhận bản lưu dựa trên phiên bản server phù hợp; báo xung đột khi server đã thay đổi, không âm thầm ghi đè.
- [ ] Đảm bảo gửi lại cùng một yêu cầu lưu an toàn cả khi server đã xử lý nhưng phản hồi bị mất; dùng định danh yêu cầu hoặc cơ chế tương đương để phân biệt lần thử lại với xung đột thực sự.
- [ ] Khi nộp, chấm toàn bộ đáp án trong request và ghi kết quả chính thức; phối hợp khóa/transaction giữa lưu nháp, nộp bài và tự nộp để không lưu sau khi đã nộp hoặc chấm trùng.
- [ ] Quy định và thực hiện việc giữ hoặc xóa bản nháp sau khi nộp, có tính đến quy trình xử lý sự cố và mở lại bài nếu được hỗ trợ.

### Frontend

- [ ] Khi bấm **Lưu**, ghi bản chụp toàn bộ đáp án vào `localStorage` theo tài khoản và `submissionId`, rồi gửi bản chụp lên backend; không tự lưu các thay đổi sau đó nếu học sinh chưa bấm Lưu.
- [ ] Lưu cùng bản local phiên bản server làm cơ sở, trạng thái đồng bộ và định danh yêu cầu để có thể đối chiếu và gửi lại an toàn.
- [ ] Chỉ báo **Đã lưu và đồng bộ** khi backend xác nhận. Nếu đã lưu local nhưng chưa được server xác nhận, báo **Đã lưu trên máy, chưa xác nhận đồng bộ**; khi có mạng, thử đồng bộ lại bản đã bấm Lưu nếu lượt thi vẫn đủ điều kiện.
- [ ] Xử lý riêng lỗi `localStorage`: không khẳng định đã lưu trên máy; nếu lưu lên server thành công thì thông báo đúng trạng thái, nếu cả hai thất bại thì báo chưa lưu được.
- [ ] Khi quay lại và có mạng, kiểm tra trạng thái lượt thi và bản nháp trên DB cùng bản local. Nếu local đã đồng bộ, dùng bản DB; nếu local chưa đồng bộ và phiên bản server chưa thay đổi, gửi lại bản local để backend kiểm tra và chấp nhận.
- [ ] Nếu bản local chưa đồng bộ và server đã thay đổi, giữ cả hai bản, thông báo xung đột và xử lý theo chính sách được duyệt; không tự ghi đè. Nếu chưa kết nối được server, cho biết chưa kiểm tra được trạng thái và chưa đồng bộ; không khẳng định lượt thi vẫn hợp lệ.
- [ ] Nếu bài hết hạn, tạm dừng hoặc đã nộp, tuân theo trạng thái và quyền thao tác do backend xác định; không tự coi bản local là bản server đã chấp nhận hoặc dùng nó để mở lại bài.
- [ ] Khi bấm **Nộp**, gửi toàn bộ đáp án đang hiển thị, không chỉ bản lưu gần nhất; xử lý trường hợp nộp lại sau khi mất phản hồi.
- [ ] Chỉ dọn bản local khi đã xác nhận nộp thành công và theo chính sách xử lý sự cố; không xóa bản chưa đồng bộ khi nộp thất bại hoặc chưa rõ kết quả.
- [ ] Nhắc học sinh lưu thường xuyên; phân biệt bản lưu trên máy và bản đã đồng bộ. Bản chỉ ở local không được backend dùng để tự nộp.

## 3. Đồng hồ và xử lý sự cố thi thử

### Backend và quản trị

- [ ] Thiết kế trạng thái và dữ liệu thời gian cho từng lượt `MOCK`: đang làm, tạm dừng, tiếp tục và đã nộp; backend là nguồn xác định hạn nộp và thời gian còn lại.
- [ ] Cập nhật các luồng bắt đầu/quay lại, lưu nháp, nộp và tự nộp để dùng thống nhất trạng thái và hạn mới. Không cho học sinh tự sửa thời gian.
- [ ] Bổ sung chức năng để admin xem lượt thi và xử lý sự cố: tạm dừng, tiếp tục với thời gian còn lại được duyệt, hoặc gia hạn.
- [ ] Hỗ trợ admin ghi nhận mốc sự cố đã xác minh và điều chỉnh thời gian tương ứng; không mặc định coi thời điểm admin bấm xử lý là thời điểm xảy ra sự cố.
- [ ] Kiểm tra quyền `ADMIN` ở backend; kiểm tra giới hạn và tính hợp lệ của thời gian điều chỉnh.
- [ ] Ghi lịch sử thao tác: lượt thi, admin, thời điểm thực hiện, lý do, mốc sự cố được duyệt và trạng thái/thời gian trước và sau điều chỉnh.
- [ ] Đồng bộ các thao tác admin với lưu nháp, nộp và tự nộp để tránh trạng thái hoặc thời gian bị ghi đè khi thao tác đồng thời.

### Frontend

- [ ] Hiển thị đồng hồ theo trạng thái và hạn do backend trả về; cập nhật lại sau khi quay lại bài hoặc sau thay đổi của admin.
- [ ] Hiển thị rõ khi lượt thi đang tạm dừng, đã hết giờ hoặc đã nộp; không cho tiếp tục làm hoặc nộp trái với trạng thái backend.
- [ ] Bổ sung giao diện quản trị để tìm lượt thi, xem sự cố, thao tác thời gian và xem lịch sử.

## 4. Tự nộp dự phòng ở backend

- [ ] Thiết kế tác vụ xử lý các lượt `MOCK` hết hạn mà chưa nộp; dùng bản nháp đã đồng bộ thành công gần nhất trên DB, không dựa vào tab hoặc `localStorage` của học sinh.
- [ ] Chỉ xử lý lượt thi đủ điều kiện và không đang tạm dừng; dùng cùng nguồn thời gian/trạng thái với luồng nộp và can thiệp của admin.
- [ ] Xử lý nhất quán khoảng ân hạn nộp hiện có: tránh tự nộp bản nháp trước khi request hợp lệ từ frontend còn có thể được chấp nhận.
- [ ] Bảo đảm xử lý an toàn khi tác vụ chạy lại, nhiều instance cùng xử lý, hoặc frontend nộp đồng thời; kết quả chỉ được chấm một lần.
- [ ] Ghi nhận bài được tự nộp và nguồn đáp án để admin có thể kiểm tra.

## 5. Chính sách cần chốt trước khi hoàn thiện

- [ ] **Chưa từng lưu khi hết giờ:** xác nhận tự nộp bài trắng hay chuyển cho admin xử lý.
- [ ] **Sự cố được duyệt sau khi bài đã nộp/tự nộp:** quyết định có cho admin mở lại không. Nếu có, xác định cách lưu lịch sử, xử lý kết quả cũ, đáp án khôi phục và quyền nộp lại.
- [ ] **Báo sự cố:** xác định học sinh báo qua hệ thống hay kênh bên ngoài; nếu offline, xác định cách admin ghi nhận và xác minh thời điểm.
- [ ] **Nhiều thiết bị:** xác định chính sách khi cùng một lượt thi được mở hoặc lưu đồng thời, bao gồm cách thông báo và xử lý xung đột.
- [ ] **Bản local chưa đồng bộ sau khi hết hạn hoặc đã nộp:** xác định admin có được tiếp nhận hay không, cách xác minh và quy trình xử lý; không tự động coi là đáp án chính thức.
- [ ] **Trạng thái tạm dừng:** xác định học sinh có được xem đề, sửa hoặc lưu đáp án trong lúc tạm dừng hay không.

## 6. Kiểm thử và tài liệu

- [ ] Kiểm thử lưu/khôi phục luyện đề, lỗi `localStorage`, đổi tài khoản, nộp thành công và nộp chưa xác nhận.
- [ ] Kiểm thử lưu/khôi phục thi thử trên nhiều thiết bị, bỏ chọn đáp án, dữ liệu không hợp lệ, sai quyền và lỗi mạng.
- [ ] Kiểm thử lưu local thành công nhưng server thất bại, local thất bại, mất phản hồi sau khi server lưu, đồng bộ lại, xung đột phiên bản và bản local chưa đồng bộ khi hết hạn hoặc đã nộp.
- [ ] Kiểm thử tạm dừng, tiếp tục, gia hạn, điều chỉnh theo mốc sự cố và lịch sử admin; kiểm tra cả quyền truy cập.
- [ ] Kiểm thử hết giờ, khoảng ân hạn, bài đang tạm dừng, bài chưa có bản nháp và cơ chế tự nộp dự phòng.
- [ ] Kiểm thử cạnh tranh giữa lưu, nộp, tự nộp và điều chỉnh của admin; bảo đảm không mất dữ liệu đã xác nhận hoặc chấm trùng.
- [ ] Cập nhật tài liệu API, hướng dẫn frontend/admin và thông báo cho học sinh theo chính sách đã chốt.
