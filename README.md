# File Storage Socket

## Mô tả dự án

**File Storage Socket** là một ứng dụng lưu trữ và quản lý tập tin sử dụng WebSocket để truyền tải dữ liệu theo thời gian thực. Dự án kết hợp frontend và backend để cung cấp trải nghiệm mượt mà cho người dùng.

## Tính năng chính

- 📁 Quản lý tập tin (upload, download, delete)
- 🔄 Truyền tải dữ liệu real-time thông qua WebSocket
- 🎨 Giao diện người dùng hiện đại
- 🔐 Hỗ trợ xác thực người dùng
- 📊 Theo dõi trạng thái tập tin

## Stack công nghệ

### Frontend
- **HTML/CSS/JavaScript** - Giao diện người dùng
- **Socket.io Client** - Kết nối WebSocket với server

### Backend
- **Node.js** - Runtime JavaScript phía server
- **Express.js** - Framework web
- **Socket.io** - Thư viện WebSocket
- **MongoDB** hoặc **File System** - Lưu trữ dữ liệu

## Cài đặt

### Yêu cầu
- Node.js >= 14.x
- npm hoặc yarn

### Bước cài đặt

1. **Clone repository:**
   ```bash
   git clone https://github.com/justinbiahoi05/file-storage-socket.git
   cd file-storage-socket
   ```

2. **Cài đặt dependencies:**
   ```bash
   npm install
   ```

3. **Cấu hình environment:**
   ```bash
   cp .env.example .env
   ```
   Chỉnh sửa file `.env` với cấu hình của bạn

4. **Khởi động server:**
   ```bash
   npm start
   ```

5. **Truy cập ứng dụng:**
   Mở trình duyệt và truy cập: `http://localhost:3000`

## Cách sử dụng

### Upload tập tin
1. Nhấn nút "Upload" trên giao diện
2. Chọn tập tin từ máy tính
3. Chờ quá trình upload hoàn thành

### Download tập tin
1. Chọn tập tin từ danh sách
2. Nhấn nút "Download"
3. Tập tin sẽ được tải về máy tính

### Xóa tập tin
1. Chọn tập tin cần xóa
2. Nhấn nút "Delete"
3. Xác nhận thao tác xóa

## Cấu trúc thư mục

```
file-storage-socket/
├── client/              # Frontend code
│   ├── index.html
│   ├── css/
│   └── js/
├── server/              # Backend code
│   ├── index.js
│   ├── routes/
│   └── controllers/
├── package.json
├── .env.example
├── .gitignore
└── README.md
```

## API Documentation

### WebSocket Events

#### Client → Server
- `upload-file` - Gửi tập tin lên server
- `delete-file` - Xóa tập tin
- `list-files` - Lấy danh sách tập tin

#### Server → Client
- `file-uploaded` - Xác nhận upload thành công
- `file-deleted` - Xác nhận xóa thành công
- `files-list` - Gửi danh sách tập tin
- `error` - Thông báo lỗi

## Đóng góp

Chúng tôi rất hoan nghênh những đóng góp từ cộng đồng!

### Các bước đóng góp:
1. Fork repository này
2. Tạo branch feature của bạn (`git checkout -b feature/AmazingFeature`)
3. Commit thay đổi (`git commit -m 'Add some AmazingFeature'`)
4. Push lên branch (`git push origin feature/AmazingFeature`)
5. Mở Pull Request

## License

Dự án này được cấp phép dưới license MIT. Xem file `LICENSE` để chi tiết.

## Tác giả

- **justinbiahoi05** - Initial work

## Support

Nếu bạn gặp vấn đề, vui lòng [mở một issue](https://github.com/justinbiahoi05/file-storage-socket/issues) trên GitHub.

---

**Last Updated:** 28/04/2026
