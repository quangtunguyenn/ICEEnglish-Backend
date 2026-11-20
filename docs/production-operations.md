# Vận hành production: VPS, Cloudflare và giám sát

Tài liệu này dành cho một backend instance trên Linux VPS. Các cấu hình chỉ là
điểm khởi đầu; chưa có thay đổi nào được áp dụng tự động lên VPS, Cloudflare
hoặc database production.

## 1. Cấu hình backend

Đặt các biến sau trong secret manager hoặc file môi trường chỉ tài khoản dịch
vụ đọc được; không commit, không gửi secret qua chat:

| Biến | Nội dung |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | JDBC URL PostgreSQL production |
| `DB_USERNAME`, `DB_PASSWORD` | Tài khoản database với quyền tối thiểu cần thiết |
| `JWT_SECRET` | Secret ngẫu nhiên mã hóa Base64, ít nhất 32 byte sau giải mã |
| `APP_CORS_ALLOWED_ORIGINS` | Danh sách origin frontend HTTPS chính xác, phân cách bằng dấu phẩy |

Có thể tạo JWT secret mới trên máy quản trị bằng `openssl rand -base64 48`.
Lưu secret ổn định qua các lần restart; đổi secret sẽ làm token cũ không còn
hợp lệ. Mật khẩu DB và JWT mặc định trong source đã được bỏ: cả local cũng cần
cung cấp `DB_PASSWORD` và `JWT_SECRET`. Nếu các giá trị cũ từng bị chia sẻ,
cần đổi credential thực tế và thu hồi token cũ.

Profile `prod` đặt `ddl-auto=validate`, tắt Swagger/OpenAPI, không khởi tạo
user Spring mặc định và chỉ bind backend vào `127.0.0.1`. Cần reverse proxy
trên cùng host chuyển tiếp đến backend; không đổi bind thành public để khắc
phục lỗi proxy. Tài khoản dịch vụ không cần quyền sửa schema khi chạy.

### Database và rollout

Ứng dụng chưa tích hợp Flyway/Liquibase: các file SQL không tự chạy và không có
đảm bảo migration history. Trước khi deploy, sao lưu và thử khôi phục bản sao;
kiểm tra schema và migration đã áp dụng trên **đúng** database, bao gồm V16.
Chỉ chạy phần migration còn thiếu theo thứ tự sau khi kiểm tra phụ thuộc và
tình trạng dữ liệu; không chạy lại toàn bộ SQL một cách mù quáng. Chạy bằng
tài khoản migration riêng, sau đó khởi động profile `prod`; `validate` sẽ báo
nếu mapping Hibernate không khớp schema. V16 mới chỉ được áp dụng vào DB local,
không đồng nghĩa đã áp dụng ở production. Chuẩn bị rollback ứng dụng và kế
hoạch khôi phục DB trước rollout.

`409 Conflict` hiện được trả cho một số trạng thái lượt làm đã nộp/hết hạn,
không hỗ trợ tracking hoặc thi thử đã dùng; frontend cần xử lý trạng thái này.
Lỗi hệ thống không được chuyển chung thành 409.
Đăng nhập sai tên hoặc mật khẩu trả cùng thông báo `401`; response tạo học sinh
không còn trả `passwordHash`.

## 2. Prometheus + Grafana

Các file trong `monitoring/` cung cấp Prometheus, Grafana và node exporter bằng
Docker Compose **Linux host networking**; không dùng trực tiếp trên Docker
Desktop hoặc triển khai backend ở network namespace khác nếu chưa chỉnh lại
kết nối. Cần Docker Compose plugin và ba port `3001`, `9090`, `9100` còn trống
trên loopback. Backend management bind `127.0.0.1:9091`: chỉ `health` và
`prometheus` được expose. Không reverse-proxy các port quản trị ra Internet.

Đặt `GRAFANA_ADMIN_PASSWORD` mạnh trong môi trường bảo mật, rồi chạy từ thư mục
`monitoring`:

```sh
docker compose config --quiet
docker compose up -d
docker compose ps
curl -fsS http://127.0.0.1:9091/actuator/health
curl -fsS http://127.0.0.1:9091/actuator/prometheus | head
```

Truy cập Grafana bằng SSH tunnel tới `127.0.0.1:3001`, đăng nhập `admin` bằng
mật khẩu đã cấu hình, mở dashboard **IceEnglish - Four Golden Signals**.
Prometheus scrape app và node exporter mỗi 15 giây; có thể mất vài phút để đồ
thị `rate` có đủ dữ liệu. Kiểm tra Prometheus Targets và panel scrape health:
`up=1` cho cả `iceenglish` và `node`. Các metric chủ yếu:

- Traffic: request/giây. Latency: p95/p99 theo histogram.
- Errors: tỷ lệ 4xx và 5xx riêng; 4xx không nhất thiết là lỗi backend. Khi không
  có traffic, tỷ lệ có thể không có dữ liệu.
- Saturation: CPU/RAM của host, network theo interface và Hikari connection pool.
  Biểu đồ network có thể gồm cả interface Docker; lọc interface phù hợp VPS.

Các volume giữ dữ liệu sau restart; Prometheus mặc định giữ 15 ngày. Theo dõi
dung lượng đĩa, quyền truy cập và sao lưu cấu hình/volume theo chính sách vận
hành. Dashboard **chưa cấu hình cảnh báo hoặc thông báo**: cần thiết lập
Grafana alerting/contact point cho `up`, tỷ lệ 5xx, latency và saturation với
ngưỡng được xác định từ baseline thực tế. Prometheus không phải bộ chống DDoS.

## 3. Cloudflare và bảo vệ origin

1. Kiểm tra DNS của domain API được proxy (biểu tượng mây cam), bật HTTPS
   Cloudflare với **Full (strict)** và certificate hợp lệ tại origin. Kiểm tra
   WAF/DDoS rules phù hợp gói đang dùng; không tắt các rule đã có mà chưa xem xét.
2. Tại firewall/VPS security group, chỉ cho Cloudflare IP ranges chính thức
   truy cập các port origin HTTP/HTTPS; cập nhật cả IPv4/IPv6 khi danh sách thay
   đổi. Cho phép IP quản trị tin cậy đối với SSH và các hệ thống hợp lệ khác.
   Thử rule trên kết nối quản trị dự phòng trước khi áp dụng để tránh tự khóa.
   Kiểm tra DNS-only records không để lộ origin IP; IP đã lộ có thể cần đổi.
3. Reverse proxy chỉ chấp nhận Host dự kiến và không route `/actuator` hoặc các
   port Prometheus, Grafana, node exporter ra ngoài. Kiểm tra direct-origin
   từ một mạng ngoài bị chặn, còn truy cập qua Cloudflare vẫn hoạt động.
4. Thiết lập rate limiting tại Cloudflare cho login và các endpoint công khai
   dễ bị lạm dụng, sau khi xem traffic baseline và khả năng của gói. Test bằng
   log/simulate trước khi chặn; học sinh có thể cùng NAT một IP. Không áp
   challenge/rate limit tùy tiện lên nộp bài, chuyển Part hoặc tải đề, tránh
   gián đoạn lớp học. Giám sát 429 và false positives.
5. Nếu cần IP client thực ở reverse proxy, chỉ tin header từ IP Cloudflare đã
   xác thực; không tin `X-Forwarded-For` từ Internet. Cấu hình và kiểm thử proxy
   trước khi dùng IP client cho bảo mật. Cloudflare không thay thế backup,
   cập nhật hệ điều hành hoặc xử lý abuse ở tầng ứng dụng.

Nguồn: [Cloudflare - Protect your origin](https://developers.cloudflare.com/fundamentals/security/protect-your-origin-server/),
[Cloudflare IP ranges](https://www.cloudflare.com/ips/).

## 4. Checklist nghiệm thu trước mở traffic

- Kiểm tra prod profile, TLS, CORS đúng domain, Swagger tắt và 4 port quản trị
  không truy cập trực tiếp từ Internet.
- Trên môi trường staging với DB riêng: chạy test toàn bộ và integration test,
  thử migration, kiểm tra đăng nhập/role, tạo đề, nộp/xem lại, khách và homework.
  Lượt khách đang lưu trong RAM nên mất khi restart; không phù hợp nếu cần
  lịch sử bền vững.
- Xem targets và dashboard có dữ liệu thật, tạo cảnh báo kèm kênh nhận, test
  khôi phục backup và kịch bản rollback; đo tải ở mức dự kiến.
- Xác nhận cấu hình Cloudflare/firewall/TLS, giám sát và người trực vận hành.
  Chưa hoàn thành các bước này không thể kết luận hệ thống sẵn sàng production.
