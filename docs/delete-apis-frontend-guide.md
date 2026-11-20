# Hướng dẫn frontend: API xóa đề nháp và video

Base URL là `/api/v1`. Cả hai API đều cần JWT:

```http
Authorization: Bearer <accessToken>
```

Không gửi request body. Các thao tác dưới đây xóa dữ liệu vĩnh viễn; chỉ gọi sau
khi người dùng xác nhận.

## 1. Xóa tất cả đề nháp

```http
DELETE /api/v1/exams/drafts
```

- Quyền: `ADMIN` hoặc `TEACHER`.
- Xóa tất cả đề có `status = DRAFT`, bao gồm phần và câu hỏi thuộc đề.
- Không ảnh hưởng đề `PUBLISHED` hoặc `ARCHIVED`.
- Nếu không có đề nháp, request vẫn thành công và `deletedCount` là `0`.

Response `200 OK`:

```json
{
  "deletedCount": 3
}
```

Hiển thị số lượng đề đã xóa từ `deletedCount`, sau đó tải lại danh sách đề.

## 2. Xóa cứng một video

```http
DELETE /api/v1/admin/videos/{videoId}
```

- Quyền: chỉ `ADMIN`.
- Xóa bản ghi video trong hệ thống, từ vựng, các đề liên kết với video, lượt
  làm homework, câu trả lời và log thời gian liên quan.
- Câu hỏi thuộc đề khác vẫn được giữ lại; liên kết `relatedVideo` tới video bị
  xóa sẽ được gỡ.
- Đây là thao tác không thể hoàn tác. API chỉ xóa dữ liệu trong hệ thống, không
  xóa file media/tài liệu ở dịch vụ bên ngoài mà URL của video đang trỏ tới.

Response `204 No Content`; không đọc JSON response body. Sau khi thành công,
đóng hộp thoại xác nhận và tải lại danh sách video cùng dữ liệu màn hình liên quan.

## 3. Ví dụ gọi bằng `fetch`

```ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

async function deleteDraftExams(accessToken: string): Promise<number> {
  const response = await fetch(`${API_BASE_URL}/api/v1/exams/drafts`, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  const result: { deletedCount: number } = await response.json();
  return result.deletedCount;
}

async function deleteVideo(videoId: string, accessToken: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/admin/videos/${encodeURIComponent(videoId)}`,
    {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
}

async function readErrorMessage(response: Response): Promise<string> {
  const body = await response.json().catch(() => null);
  return body?.message ?? `Request failed (${response.status})`;
}
```

## 4. Xử lý lỗi

| HTTP | Ý nghĩa | Gợi ý xử lý |
|---:|---|---|
| `401` | Thiếu, sai hoặc hết hạn token | Yêu cầu đăng nhập lại |
| `403` | Không đủ quyền; API xóa video chỉ dành cho Admin | Hiển thị thông báo không đủ quyền |
| `404` | Không tìm thấy video cần xóa | Tải lại danh sách; có thể video đã bị xóa |

