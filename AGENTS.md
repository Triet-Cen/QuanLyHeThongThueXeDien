# Quy tắc làm việc với Codex — QuanLyChoThueXeDien

Mục tiêu: hoàn thành đúng yêu cầu hiện tại bằng thay đổi gọn, rõ, có kiểm chứng; bảo toàn chức năng đang chạy. Áp dụng các quy tắc này trong phạm vi chỉ dẫn và quyền của phiên làm việc.

## 1. Hiểu đúng yêu cầu và giới hạn công việc

- Xác định kết quả người dùng cần trước khi sửa. Nếu yêu cầu “chỉ tìm kiếm”, chỉ làm tìm kiếm cùng các phần phụ thuộc tối thiểu; không tự làm đặt xe, thanh toán hoặc quản trị.
- Thực hiện đầy đủ cả giao diện, xử lý và dữ liệu trong phạm vi được giao. Không xem UI có nút bấm là chức năng đã hoàn tất.
- Trước thay đổi đáng kể, thông báo ngắn: sẽ làm gì, phần dự kiến chạm tới và cách kiểm tra. Sau đó thực hiện, không dừng ở kế hoạch để xin xác nhận việc đã được giao.
- Tự giải quyết lựa chọn kỹ thuật thông thường dựa trên code hiện có. Chỉ hỏi khi thiếu thông tin quyết định hành vi hoặc có mâu thuẫn nghiệp vụ không thể suy ra; tiếp tục phần độc lập trong lúc chờ.
- Lỗi ngoài phạm vi: ghi nhận ngắn trong kết quả, không tranh thủ sửa. Nếu lỗi chặn nhiệm vụ, sửa tối thiểu phần chặn và giải thích liên quan.

## 2. Đọc đúng phần cần đọc

- Đọc `AGENTS.md` áp dụng cho thư mục đang sửa, nếu có. Đọc `rule.md` này khi được yêu cầu áp dụng.
- Kiểm tra trạng thái Git và diff hiện có trước khi sửa. Xác định phần người dùng đang làm để không ghi đè.
- Tìm bằng `rg`, `rg --files` hoặc công cụ tương đương. Tìm đường dẫn/tên liên quan trước, đọc đoạn cần thiết rồi mới mở rộng.
- Chỉ đọc tài liệu `/docs` và code liên quan tới chức năng. Không đọc lại toàn bộ repository hoặc mọi file Markdown ở mỗi lượt.
- Với một chức năng, lần theo controller → DTO/form → service → repository/entity → template/JS/CSS và kiểm thử liên quan. Tìm chức năng tương tự đã chạy để tái sử dụng cách làm.
- Không quét/in `target`, `.git`, thư mục thư viện, file build và log lớn để lấy ngữ cảnh không cần thiết.
- Không in giá trị bí mật từ `.env`, cấu hình, token, mật khẩu hoặc thông tin đăng nhập vào đầu ra. Khi cần kiểm tra cấu hình, chỉ kiểm tra tên biến/sự hiện diện và che giá trị nhạy cảm.

## 3. Dùng đúng nguồn nghiệp vụ

- Yêu cầu mới được người dùng chốt là căn cứ điều chỉnh phạm vi. Tài liệu nghiệp vụ báo cáo Nguyễn Minh Triết và đặc tả hiện hành trong `/docs` là căn cứ cho luồng thuê xe.
- Repo PRJ301 là nguồn tham khảo giao diện và cách làm, không tự thay thế nghiệp vụ dự án hiện tại.
- Phân biệt quy tắc đã chốt, đề xuất kỹ thuật và mục chưa chốt như “hỏi cô”/XN. Không biến phương án chưa chốt thành chính sách tự động.
- Không tự thêm phí, tiền cọc, ví, giới hạn số đơn, điều kiện GPLX, quyền truy cập hoặc cạnh chuyển trạng thái ngoài yêu cầu/tài liệu đã xác định.
- Khi có mâu thuẫn ảnh hưởng tiền, quyền lợi, lịch hoặc trạng thái: nêu đúng điểm mâu thuẫn và căn cứ. Không âm thầm chọn theo repo tham khảo.
- Chỉ áp dụng những quy tắc thuê xe liên quan đến nhiệm vụ. Không lấy lý do “đúng toàn bộ nghiệp vụ” để triển khai thêm module chưa được giao.

## 4. Giữ kiến trúc và tái sử dụng

- Giữ Spring Boot, Java, Thymeleaf, JPA/MySQL và phiên bản đang cấu hình trong project; không tự nâng cấp hoặc đổi stack.
- Giữ cấu trúc feature, tên package, cách đặt tên và quy ước hiện có. Không di chuyển/đổi tên hàng loạt để đồng bộ sở thích cá nhân.
- Trước tạo file/class/method/entity mới, tìm thành phần cùng trách nhiệm. Ưu tiên mở rộng đúng chỗ thay vì tạo một phiên bản song song.
- Controller nhận request, validate và điều phối; service giữ nghiệp vụ/transaction; repository truy vấn; template hiển thị. Theo cách tổ chức thực tế của dự án, không ép tái cấu trúc toàn bộ để áp dụng quy tắc này.
- DTO/form chỉ chứa dữ liệu cần nhận/trả. Không bind entity trực tiếp theo cách cho client sửa role, chủ sở hữu, giá chốt hoặc trạng thái bảo vệ.
- Không thêm interface chỉ để bọc một implementation khi không có nhu cầu thật. Không tạo BaseService, generic CRUD framework, factory hoặc lớp trung gian chỉ phục vụ một thao tác đơn giản.
- Chỉ tách helper/component khi có trách nhiệm rõ hoặc tái sử dụng thực tế; không tách vụn thành nhiều file khó lần theo.
- Chỉ thêm dependency khi công cụ sẵn có không đáp ứng hợp lý. Nêu lý do và giữ thay đổi tối thiểu; không thêm thư viện lớn cho tác vụ nhỏ.

## 5. Sửa gọn nhưng xử lý tận gốc

- Sửa nguyên nhân trong luồng đang dùng. Không tạo route, template, service hoặc hàm mới cạnh bản cũ để né sửa lỗi.
- Không viết lại cả file khi chỉ cần sửa một phần; tránh format toàn repository, đổi import/tên biến hoặc dọn code không liên quan.
- Không tạo nhiều biến thể “new”, “fixed”, “v2”, “final” của cùng chức năng trong mã nguồn. Chỉ giữ một luồng có thẩm quyền cho hành vi đó.
- Thay đổi ít nhất cần thiết để hoàn thành đúng; không giới hạn cứng số file nếu chức năng thực sự cần sửa nhiều lớp.
- Giữ tương thích các caller đang dùng. Nếu phải đổi hợp đồng dùng chung, rà các nơi gọi bị ảnh hưởng và kiểm tra chúng.
- Không để TODO, placeholder hoặc dữ liệu giả thay cho phần được giao. Nếu phụ thuộc chưa có, báo rõ giới hạn; không giả lập thành công trong code production.
- Xóa import, biến, log debug và code thử do chính thay đổi này tạo ra. Không xóa dữ liệu/file cũ chưa hiểu hoặc phần sửa của người dùng.
- Comment giải thích lý do hoặc ràng buộc khó thấy; tránh comment lặp lại từng dòng code. Dùng tên dễ hiểu và logic đủ đơn giản để người học theo dõi.

## 6. Quy tắc backend và dữ liệu

- Validate ở server, kiểm tra quyền theo vai trò và quyền sở hữu/phạm vi trạm khi liên quan. Ẩn nút không thay thế phân quyền.
- Tái sử dụng bảng/entity/khóa đang có. Kiểm tra ánh xạ tên bảng/cột và kiểu dữ liệu thật trước khi thêm migration.
- Không tự drop/truncate/reset DB, xóa dữ liệu hoặc dùng thay đổi schema tự động để né migration. Thay đổi schema cần script/migration phù hợp và giải thích tác động tới dữ liệu cũ.
- Không thay dữ liệu thật bằng seed mẫu. Dữ liệu demo chỉ ở môi trường kiểm thử được xác định, không chạy tự động vào DB người dùng.
- Dùng `BigDecimal` cho tiền, ngày giờ đầy đủ khi nghiệp vụ có giờ, và quy ước múi giờ nhất quán với dự án.
- Thao tác cần đồng bộ nhiều bản ghi phải có transaction phù hợp. Với tranh chấp lịch/số lượng hoặc callback lặp, thêm cơ chế khóa/idempotency đúng nghiệp vụ; `@Transactional` đơn thuần chưa chứng minh chống race.
- Không cập nhật trạng thái tùy ý ở controller hoặc template. Dùng service nghiệp vụ chung để kiểm tra điều kiện, ghi lịch sử và cập nhật liên quan.
- Truy vấn đủ cho màn hình; tránh lấy toàn bộ bảng rồi lọc bằng Java hoặc phát sinh N+1 khi có thể xử lý hợp lý tại repository.
- Không bắt mọi exception rồi trả success, nuốt lỗi hoặc vô hiệu hóa validation/security để làm chức năng “chạy được”.

## 7. Quy tắc giao diện

- Dùng layout, fragment, component và tài nguyên hiện có trước khi tạo mới.
- CSS có phạm vi theo trang/component, không dùng selector chung làm thay đổi trang khác khi nhiệm vụ chỉ sửa một khu vực.
- Không tự đổi framework CSS, font, màu và toàn bộ bố cục của project khi không được giao.
- Thay đổi UI phải giữ đúng action/method, field binding, validation, CSRF và dữ liệu được truyền từ controller.
- Dùng dữ liệu thật; hỗ trợ trạng thái trống, lỗi và giữ giá trị đã nhập. Không hiển thị success khi backend chưa xác nhận.
- Khi được giao làm responsive, kiểm tra bố cục ở kích thước phù hợp. Nếu chưa mở được trình duyệt thì báo chưa kiểm tra trực quan, không khẳng định đã xem.

## 8. Kiểm tra đúng mức

- Chạy kiểm tra sát phần đã sửa trước: compile/build phù hợp, test có sẵn và test nghiệp vụ liên quan. Dùng Maven Wrapper nếu project có; không tự đổi công cụ build.
- Thêm/sửa test khi thay đổi nghiệp vụ, quyền, dữ liệu, transaction hoặc sửa lỗi cần ngăn tái diễn. Ưu tiên test có giá trị thay vì test chỉ phản chiếu implementation.
- Thay đổi chữ, khoảng cách hoặc CSS nhỏ không cần sinh bộ test mới; kiểm tra giao diện/diff ở mức phù hợp.
- Kiểm tra nhánh thành công, đầu vào sai, không có dữ liệu và quyền truy cập khi liên quan. Nghiệp vụ tiền/lịch cần kiểm tra biên và xử lý lặp/đồng thời phù hợp.
- Test/build thất bại thì đọc lỗi và xử lý nguyên nhân trong phạm vi. Không tắt test, bỏ validation hay đổi assertion để che lỗi.
- Khi kiểm tra đã đủ và đạt, không chạy lại toàn bộ nhiều lần nếu không có thay đổi hoặc nghi ngờ mới.
- Không truy cập cổng thanh toán thật hoặc gây giao dịch/hoàn tiền thật chỉ để kiểm thử. Dùng môi trường/fixture kiểm thử phù hợp.
- Thiếu DB, cấu hình, dependency mạng hoặc công cụ: báo chính xác phần chưa chạy. Phân biệt “đã compile”, “test đã qua” và “đã kiểm tra UI”; không đánh đồng.

## 9. Bảo toàn workspace

- Không dùng `git reset --hard`, `git clean`, checkout ghi đè hoặc thao tác tương đương để xóa thay đổi người dùng.
- Nếu file đã được người dùng sửa, tích hợp phần cần thiết cẩn thận; không hoàn nguyên toàn file để lấy phiên bản tiện sửa.
- Không tự commit, push, merge, deploy hoặc thay cấu hình hệ thống nếu nhiệm vụ chưa bao gồm các thao tác đó.
- Không sửa `rule.md`, `AGENTS.md` hoặc đặc tả nghiệp vụ chỉ để hợp thức hóa code vừa viết. Chỉ cập nhật khi có yêu cầu hoặc thay đổi tài liệu thực sự thuộc nhiệm vụ.
- Tạo script/file tạm khi cần, dọn phần tạm do mình tạo sau khi dùng. Không để dump, screenshot, file build hay log thử lẫn vào source.

### 9.1. Giới hạn lệnh terminal và xử lý lỗi cấu hình

- Chỉ chạy lệnh phục vụ trực tiếp nhiệm vụ: tìm/đọc file, xem Git diff,
  compile và kiểm thử phần liên quan bằng công cụ sẵn có của project.
- Trước khi chạy, nêu ngắn gọn mục đích lệnh. Không chạy lệnh khi chưa
  hiểu rõ tác dụng hoặc không xác định được ảnh hưởng.
- Không tự cài đặt/gỡ/nâng cấp công cụ hoặc dependency; không chạy script
  tải từ mạng, sửa registry, service, biến môi trường, PATH, JAVA_HOME,
  quyền truy cập, execution policy hoặc cấu hình hệ thống.
- Không tự sửa cấu hình project: application.properties/yml, .env,
  pom.xml, Maven Wrapper, SecurityConfig, database, OAuth, thanh toán.
- Không dùng lệnh terminal, script, tham số khởi động hoặc file mới
  để lách giới hạn sửa cấu hình.
- Nếu build/test lỗi do môi trường hoặc cấu hình: dừng bước kiểm tra đó,
  báo lệnh đã chạy, lỗi chính và đề xuất xử lý; chờ người dùng đồng ý
  trước khi thực hiện thay đổi cấu hình.
- Không thử liên tiếp các lệnh sửa môi trường theo phỏng đoán.
  Chỉ tiếp tục kiểm tra khi có bằng chứng mới và không thay cấu hình.
- Không tự thêm cờ bỏ qua test, validation hoặc security để báo thành công.
- Với lệnh ngoài phạm vi cho phép ở trên, phải trình bày chính xác
  lệnh, mục đích và tác động, rồi chờ người dùng đồng ý.
- Tiếp tục các phần viết code độc lập nếu bước kiểm tra đang bị chặn.

## 10. Tiết kiệm ngữ cảnh và giao tiếp

- Trả lời và báo cáo bằng tiếng Việt, ngắn gọn. Trong code giữ ngôn ngữ đặt tên theo project.
- Batch các tìm kiếm/đọc độc lập, giới hạn đầu ra; không in cả file dài khi một đoạn đủ trả lời.
- Không lặp lại yêu cầu hoặc chép toàn bộ tài liệu `/docs` vào phần trả lời. Tham chiếu đúng file/mục cần dùng.
- Không tạo báo cáo Markdown, checklist dài, nhật ký tác vụ hoặc tài liệu mới sau mọi thay đổi nhỏ. Chỉ tạo/cập nhật tài liệu khi được yêu cầu hoặc cần cho việc dùng chức năng mới.
- Khi công việc kéo dài, báo tiến độ ngắn tại mốc có ý nghĩa: đã xác định nguyên nhân, quyết định thay đổi, kết quả kiểm tra hoặc trở ngại.
- Trước kết thúc, xem diff tổng và kiểm tra file mới: thay đổi có đúng yêu cầu, có code trùng, file thừa, secret, lỗi debug hoặc sửa lan không? Dọn phần thừa do nhiệm vụ tạo ra.

## 11. Mẫu kết quả cuối

Báo ngắn theo bốn ý, bỏ ý không liên quan:

1. Đã làm: kết quả và hành vi cụ thể.
2. File chính: các file/nhóm file quan trọng và migration nếu có; không liệt kê mọi dòng thay đổi.
3. Kiểm tra: lệnh/thao tác thật sự đã chạy và kết quả.
4. Còn thiếu: trở ngại, phụ thuộc hoặc chính sách chưa chốt; ghi rõ nếu chưa kiểm thử được.

Không tuyên bố “hoàn tất” khi chức năng còn placeholder, chưa nối backend hoặc chưa có bằng chứng phù hợp. Không cam kết “không ảnh hưởng phần khác” khi chưa kiểm tra các phần dùng chung đã thay đổi.

## 12. Cách áp dụng trong phiên terminal

Đặt file này ở thư mục gốc dự án. Khi bắt đầu phiên mới, yêu cầu rõ:

```text
Đọc rule.md ở thư mục gốc và tuân thủ trong phiên này.
Nhiệm vụ hiện tại: [mô tả chức năng hoặc lỗi cần xử lý].
Phạm vi: [phần cần làm].
Tiêu chí hoàn thành: [hành vi mong đợi].
Hãy khảo sát phần liên quan, thực hiện và kiểm tra đúng phạm vi.
```

Tên `rule.md` là file quy ước của dự án; không dựa vào việc công cụ sẽ tự đọc nó. Nếu phiên đã đọc, chỉ cần giao nhiệm vụ tiếp theo và nhắc lại khi quy tắc hoặc phạm vi thay đổi.
