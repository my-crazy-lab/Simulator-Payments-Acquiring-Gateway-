# 🔒 PCI DSS Compliant

PCI DSS (Payment Card Industry Data Security Standard) là tiêu chuẩn bảo mật bắt buộc cho mọi hệ thống xử lý, truyền hoặc lưu trữ dữ liệu thẻ thanh toán.

---

## 1. 🔐 Mã hóa dữ liệu thẻ (Cardholder Data Encryption)

PCI DSS yêu cầu:

### Bạn **không được lưu trữ** số thẻ thô:
- PAN  
- CVV/CVC  
- Track1/Track2  
- PIN  

Nếu *bắt buộc* phải lưu PAN → **phải mã hóa** bằng AES-256 hoặc RSA, key phải được quản lý tách biệt.

### Cụ thể:
- Dữ liệu thẻ khi **truyền** → phải dùng TLS ≥ 1.2  
- Dữ liệu thẻ khi **lưu** → phải mã hóa AES-256  
- Key chia thành 2 loại:  
  - **DEK** (Data Encryption Key): mã hóa PAN  
  - **KEK** (Key Encryption Key): mã hóa DEK  
- Nên sử dụng **HSM (Hardware Security Module)** để quản lý key an toàn

### Ví dụ kiến trúc:
```
App → API Gateway → HSM → (Trả về Token)
Hệ thống chỉ lưu token, không lưu PAN.
```

---

## 2. 🛡 Bảo vệ network (Network Security & Segmentation)

PCI DSS yêu cầu **phân tách mạng rõ ràng**:

### Vùng xử lý dữ liệu thẻ = **CDE (Cardholder Data Environment)**
Chỉ các server cần thiết mới được nằm trong CDE.

### Phải làm:
- Firewall chặn inbound/outbound không cần thiết  
- Không expose database ra Internet  
- Chỉ mở port cần thiết (vd: 443)  
- Dùng **WAF** để chặn SQL injection, XSS, bot, DDoS  

### Ví dụ mạng chuẩn:
```
Internet → WAF → API Gateway → App Servers (CDE)
                                |
                              Database (Internal Only)
                                |
                               HSM
```

---

## 3. 🚪 Giới hạn truy cập (Access Control)

### Nguyên tắc: **Least Privilege**
Ai không cần → CẤM truy cập.

### Yêu cầu chính:
- Tất cả tài khoản phải có **MFA**
- Không dùng tài khoản chung  
- Nhân viên không được xem PAN — chỉ hệ thống được phép  
- Dùng **RBAC** để phân quyền  
- Database phải bật audit và hạn chế quyền SELECT  

### Ví dụ:
- Developer không bao giờ thấy full PAN trong log  
- DBA không xem được PAN vì nó được mã hóa bằng DEK trong HSM  

---

## 4. 📜 Logging & Audit Trail

PCI DSS yêu cầu **ghi toàn bộ lịch sử truy cập và hành vi nhạy cảm**.

### Cần log:
- Ai truy cập hệ thống  
- Truy cập endpoint nào  
- Query nào tới database  
- Thay đổi firewall/security rule  
- Failed login, brute-force attempt  

### Log phải:
- Không được sửa (immutable)  
- Lưu >= **1 năm**  
- Có cảnh báo real-time khi có bất thường  

### Ví dụ:
- Failed login 5 lần → alert  
- Developer query bảng chứa PAN → alert  
- API trả về 3DS → log nội dung nhưng **không log PAN**  

---

## 5. 🚫 Không được lưu PAN/CVV thô (Card Data Storage Rules)

### PCI DSS cấm:
- ❌ CVV/CVC  
- ❌ Full PAN không mã hóa  
- ❌ Track1/Track2  
- ❌ PIN & PIN Block  

### Được lưu:
- PAN đã **mask** dạng:  
  `4111 11xx xxxx 1111`  
- Token (tokenized PAN)  
- Metadata:  
  - BIN  
  - Card brand  
  - Last 4 digits  

---

# 💳 Payment Acquiring Gateway

**Payment Acquiring Gateway** là *cổng xử lý giao dịch thẻ* dành cho các merchant (website/app bán hàng).  
Nó là thành phần trung gian giữa **merchant ↔ ngân hàng/PSP** để thực hiện giao dịch an toàn, tuân thủ chuẩn PCI DSS.

---

## 🎯 Nhiệm vụ chính

### 1. 🧾 Nhận thông tin thẻ từ người dùng
- Số thẻ (PAN)  
- Ngày hết hạn  
- CVV  
- Tên chủ thẻ  
- Billing address (nếu cần AVS)

Thông tin này phải được truyền qua TLS và **không được lưu trữ dạng raw**.

---

### 2. 🏦 Gửi dữ liệu đến Ngân hàng hoặc PSP (Payment Service Provider) để xin **Authorization**
Gateway thực hiện:
- Tokenization thẻ  
- Kết nối đến **Acquirer / Bank / Card Network (Visa, MasterCard)**  
- Gửi request để hỏi xem giao dịch có được phép hay không  
- Có thể yêu cầu **3D Secure (3DS / OTP)** nếu cần xác thực chủ thẻ

Kết quả trả về từ ngân hàng:
- Approved  
- Declined  
- Pending / Challenge (3DS)  
- Fraud suspected  
- Insufficient funds  
- Wrong CVV / expired card  
- …  

---

### 3. ✅ Trả kết quả thành công/thất bại cho Merchant
Gateway sẽ:
- Gửi webhook/API callback cho merchant  
- Trả về status chính xác của giao dịch  
- Kèm theo transaction ID, token card, hoặc reference code

Merchant sử dụng thông tin này để:
- Confirm order  
- Yêu cầu capture / refund sau này  
- Lưu record giao dịch  

---

## 🧩 Tóm tắt đơn giản

Payment Acquiring Gateway làm nhiệm vụ:  
👉 **Nhận thẻ** → **Gửi authorization đến ngân hàng/PSP** → **Trả kết quả cho merchant**

Nó đảm bảo:
- Bảo mật dữ liệu thẻ (PCI DSS)  
- Kết nối ổn định đến ngân hàng  
- Hỗ trợ 3D Secure  
- Xử lý fraud  
- Tạo trải nghiệm thanh toán mượt mà cho khách  

---

# 💳 Handling Card Transactions — Hệ thống xử lý đầy đủ flow giao dịch thẻ

Một hệ thống thanh toán/acquiring gateway phải xử lý trọn vẹn toàn bộ **card transaction lifecycle**.  
Các nghiệp vụ chính gồm:

---

## 1. 🟦 Authorization (Ủy quyền – kiểm tra & giữ tiền)
**Mục tiêu:** Kiểm tra giao dịch có hợp lệ và giữ tiền (hold) trên tài khoản thẻ.

Hệ thống gửi yêu cầu đến:
- Acquirer / Bank
- Card Network (Visa/MasterCard)
- Issuer (ngân hàng phát hành)

Issuer sẽ:
- Kiểm tra số dư  
- Kiểm tra chống gian lận  
- Kiểm tra CVV / ngày hết hạn / địa chỉ  
- Giữ một khoản tiền tương ứng (authorization hold)

**Kết quả trả về:**  
- Approved  
- Declined  
- 3DS challenge  
- Suspected fraud  

> Authorization **chưa trừ tiền**, chỉ tạm khóa số tiền.

---

## 2. 🟩 Capture (Ghi nợ thực sự)
**Mục tiêu:** Thực sự trừ tiền từ tài khoản chủ thẻ.

- Merchant gửi request capture sau khi order được xác nhận.  
- Gateway gửi đến Acquirer/Issuer để hoàn tất giao dịch.  
- Tiền được chuyển từ Issuer → Acquirer → Merchant Settlement Account.

**Lưu ý:**  
- Một số merchant chỉ capture khi hàng được giao.  
- Một số làm **Auth+Capture cùng lúc** (sale transaction).

---

## 3. 🟥 Void (Hủy authorization trước khi capture)
**Mục tiêu:** Hủy “authorization hold” nếu chưa capture.

Dùng khi:
- Khách hủy đơn ngay  
- Merchant muốn đổi phương thức thanh toán  
- Authorization fail 3DS  
- Order timeout  

**Lưu ý:**  
- Void chỉ dùng được nếu transaction **chưa capture**.  
- Sau khi void → tiền “hold” được trả lại cho chủ thẻ (thường 1–7 ngày).

---

## 4. 🟧 Refund (Hoàn tiền sau khi capture)
**Mục tiêu:** Hoàn tiền lại cho khách sau khi đã capture.

Có hai loại:
1. **Full refund** – hoàn toàn bộ số tiền  
2. **Partial refund** – hoàn một phần (ví dụ trả lại 1 item trong đơn)

Refund đi qua:
- Acquirer  
- Card Network  
- Issuer  

Tiền được trả về tài khoản thẻ của khách, thời gian tùy ngân hàng (1–10 ngày).

**Lưu ý:**  
- Refund chỉ dùng sau khi **capture**.  
- Refund tạo giao dịch mới, không chạm vào authorization.

---

## 🧩 Tóm tắt ngắn gọn
| Flow | Khi nào dùng? | Tiền có bị trừ? |
|------|----------------|-----------------|
| **Authorization** | Kiểm tra & giữ tiền | ❌ Không (chỉ hold) |
| **Capture** | Thực sự trừ tiền | ✅ Có |
| **Void** | Hủy hold khi chưa capture | ❌ Không |
| **Refund** | Hoàn tiền sau capture | 🔄 Trả lại vào thẻ |

---

# 🔑 Tokenization — Cơ chế thay thế PAN bằng Token an toàn

**Tokenization** là kỹ thuật thay thế **số thẻ thật (PAN)** bằng một chuỗi ký tự không nhạy cảm gọi là **token**.  
Token không mang ý nghĩa tài chính và **không thể đảo ngược** để lấy lại PAN, giúp hệ thống không phải lưu trữ dữ liệu thẻ thô.

---

## 🎯 Mục tiêu của Tokenization

### 1. 🔒 Tăng bảo mật
- Token không thể sử dụng ngoài hệ thống phát hành token.  
- Hacker lấy token cũng không thể rút ra PAN.  
- Giảm phạm vi PCI DSS (PCI Scope) vì nội bộ không lưu dữ liệu nhạy cảm.

---

### 2. 🛡 Hệ thống nội bộ không phải động vào PAN
- Microservices, database, logs, analytics… **không cần biết số thẻ thật**.  
- Chỉ **Token Service / HSM** mới giữ (hoặc mã hóa) PAN.  
- Giảm bề mặt tấn công – mọi phần còn lại của hệ thống không cần PCI compliance.

---

### 3. ⚡ Reuse token cho lần thanh toán sau (One-click Payment)
- Người dùng thanh toán 1 lần → sinh token  
- Lần sau chỉ gửi token để thanh toán → không cần nhập PAN/CVV  
- Tạo trải nghiệm kiểu:
  - Grab / Shopee / Tiki: “Dùng lại thẻ này”  
  - Apple Pay style: lưu token thay vì PAN

---

### 4. 🧪 Ví dụ minh họa

**PAN thật:**  
`4098 5321 2345 6789`  

**Token sau khi tokenize:**  
`TOKEN=abc123xyz987`  

**Format masking cho UI:**  
`4098 53•• •••• 6789`

---

## 🧩 Tóm tắt kỹ thuật

- Token → được generate từ HSM / Tokenization Service  
- PAN thật → được mã hóa AES-256 + lưu ở PCI Vault  
- Token không thể reverse để lấy lại PAN  
- Chỉ 1 service có quyền “detokenize” (lấy lại PAN để gửi đến ngân hàng)  
- Token có thể là:
  - Random GUID  
  - Deterministic token (để nhận diện cùng thẻ)  
  - Network token (Visa/Mastercard provisioning)

---

# 🕵️‍♂️ Fraud Mitigation — Cơ chế chống gian lận trong hệ thống thanh toán thẻ

Hệ thống thanh toán/acquiring gateway phải tích hợp **Fraud Mitigation** để ngăn chặn các hành vi gian lận, không hợp lệ hoặc đánh cắp thẻ.  
Fraud system giúp giảm chargeback, bảo vệ merchant và giảm thiểu rủi ro tài chính.

---

## 1. ⚙️ Tự động đánh giá rủi ro (Rules Engine)

Hệ thống cần một **Rules Engine** để tự động phân tích và đưa ra quyết định như:
- Approve  
- Decline  
- Require 3DS challenge  
- Require manual review  

Ví dụ rule:
- Country mismatch (VN card → login từ Mỹ)  
- CVV fail nhiều lần  
- Amount quá cao so với lịch sử  
- IP → proxy/VPN/tor exit node  

Rules Engine có thể là:
- Custom engine (Drools, custom DSL…)  
- Machine learning (fraud scoring model)

---

## 2. ⏱ Check Velocity (kiểm tra tần suất giao dịch)

Phát hiện khi một thẻ bị thử **liên tục trong thời gian ngắn** — dấu hiệu thẻ bị đánh cắp.

Ví dụ:
- 5 giao dịch thất bại trong 1 phút  
- 3 lần nhập sai CVV  
- 10 attempts từ cùng 1 IP trong 30s  
- Nhiều giao dịch cùng thẻ nhưng khác merchant  

Nếu vượt ngưỡng → block hoặc yêu cầu 3DS.

---

## 3. 🛑 Check Blacklist / Whitelist

### Blacklist:
- Thẻ bị đánh cắp  
- IP gian lận  
- Device fingerprint đáng ngờ  
- Email bị abuse  
- Merchant risk cao  

### Whitelist:
- Khách hàng VIP  
- Tài khoản có lịch sử giao dịch tốt  
- Thẻ đã xác thực 3DS nhiều lần  

Cơ chế này giúp giảm false positive và tăng tỷ lệ duyệt.

---

## 4. 🔐 Yêu cầu 3DS với giao dịch đáng ngờ

Khi phát hiện bất thường, hệ thống kích hoạt:
- **3DS 2.0 Challenge Flow**  
- Yêu cầu chủ thẻ xác thực bằng OTP / biometric  

Dùng cho:
- Country mismatch  
- High-risk merchant  
- First-time transaction  
- High amount  
- Suspicious device  

3DS giúp chuyển trách nhiệm (liability shift) về phía ngân hàng.

---

## 5. 🌐 Tích hợp dịch vụ Fraud External

Hệ thống thường kết nối với các dịch vụ fraud chuyên nghiệp để tăng độ chính xác:

### **Sift**
- Machine learning scoring  
- Device fingerprint  
- Behavioral analytics  

### **ThreatMetrix (LexisNexis)**
- Device ID  
- IP intelligence  
- Digital identity network  

### **ClearSale**
- Manual review team  
- Tập trung vào e-commerce fraud  

### **Visa / Mastercard Fraud Tools**
- Visa Risk Manager (VRM)  
- Mastercard Decision Intelligence  
- BIN-level risk scoring  
- Network-level detection  

Các dịch vụ này tạo **fraud score**, dùng để:
- auto-approve  
- auto-decline  
- trigger 3DS  
- route to manual review

---

## 🧩 Tóm tắt Fraud Mitigation

| Cơ chế | Mục tiêu |
|--------|----------|
| Rules Engine | Tự động phân tích rủi ro |
| Velocity Check | Phát hiện spam/brute-force thẻ |
| Blacklist/Whitelist | Chặn thẻ xấu, ưu tiên thẻ tốt |
| 3DS | Xác thực chủ thẻ thật |
| External Fraud Services | ML scoring + device profiling |

---

# 3D Secure Authentication (3DS1 / 3DS2)

3D Secure (3DS) là lớp xác thực bổ sung giúp giảm gian lận và chargeback trong giao dịch thẻ online.

---

## 🎯 Mục đích
- Xác minh chủ thẻ thật sự.
- Giảm rủi ro gian lận → giảm chargeback.
- Đáp ứng yêu cầu của Visa/Mastercard/AMEX (SCA – Strong Customer Authentication).

---

## 🔵 3DS1 (Legacy – Redirect)
3DS1 hoạt động bằng cách **redirect** người dùng sang trang ACS của ngân hàng để nhập OTP/password.

## **Flow 3DS1**
1. Gateway gửi **PAReq** → Issuer ACS.  
2. Trình duyệt **redirect** user sang ACS.  
3. User nhập OTP/mật khẩu.  
4. ACS trả về **PARes** → Gateway.  
5. Gateway gửi kết quả đến PSP/Acquirer.

### Nhược điểm:
- UX kém (redirect cứng).
- Không tối ưu cho mobile apps.

---

## 🔵 3DS2 (Modern – Frictionless + Challenge)
3DS2 hỗ trợ risk-based authentication, mobile app, biometrics, SDK, và trải nghiệm tốt hơn.

## **Message Types**
- **AReq** – Authentication Request  
- **ARes** – Authentication Response  
- **CReq** – Challenge Request  
- **CRes** – Challenge Response  

## **Flow 3DS2**

### 1) AReq – Authentication Request  
Gateway gửi thông tin giao dịch → Directory Server → ACS.

### 2) ARes – Authentication Response  
ACS trả:
- **Frictionless** (không challenge)
- **Challenge required**

### 3) Challenge (CReq / CRes)  
Nếu challenge:
- Gateway hiển thị challenge window (iframe/app SDK).
- User xác thực bằng OTP/App/Biometric.
- ACS trả **CRes**.

### 4) Gửi kết quả xác thực  
Gateway nhận kết quả, lưu thông tin 3DS (ECI, CAVV, dsTransID…), gửi qua PSP/Acquirer để tiếp tục Authorization.

---

## 🔧 Vai trò của Gateway trong 3DS

Một cổng thanh toán phải:

### ✔ 1. Xử lý redirect/challenge  
- 3DS1 → redirect browser  
- 3DS2 → render iframe/app challenge  

### ✔ 2. Mapping & validate messages  
- PAReq / PARes  
- AReq / ARes  
- CReq / CRes  

### ✔ 3. Lưu dữ liệu xác thực  
- ECI  
- CAVV  
- 3DS Version  
- dsTransID  

### ✔ 4. Gửi kết quả đến PSP/Acquirer  
PSP dùng ECI/CAVV/3DS để đánh giá bảo vệ chargeback.

### ✔ 5. Hỗ trợ fallback  
- 3DS2 → 3DS1  
- 3DS1 → no-3DS (tuỳ config)

---

## 🧠 Tại sao phải hỗ trợ cả 3DS1 và 3DS2?
- Một số ngân hàng cũ vẫn dùng 3DS1.  
- Visa/Mastercard yêu cầu 3DS2 cho PSD2 (EU).  
- Không có 3DS → merchant chịu chargeback liability.

---

# 🏁 Settlement Processing — Bước Cuối Trong Chuỗi Thanh Toán Thẻ

**Settlement** là quá trình cuối cùng trong xử lý thanh toán thẻ, nơi **ngân hàng phát hành/PSP chính thức chuyển tiền** cho merchant sau khi giao dịch đã được **capture**.

---

## 🎯 Mục tiêu của Settlement
- Đảm bảo merchant nhận đúng số tiền từ giao dịch.
- Xác minh số tiền thực tế ngân hàng chuyển có khớp với dữ liệu hệ thống.
- Ghi nhận các lệch (discrepancy), refund, chargeback, dispute.

---

## 🔄 Responsibilities của hệ thống Settlement

## 1. Tự động tổng hợp các giao dịch đã Capture
- Lấy tất cả transaction có trạng thái `CAPTURED` trong kỳ.  
- Gom nhóm theo:
  - Merchant ID  
  - Currency  
  - Settlement cycle  
  - Acquirer  

→ Tạo **settlement batch**.

---

## 2. Tạo & Gửi Settlement File cho Acquirer
Gateway phải tạo 1 file chuẩn theo format của acquirer:

Ví dụ:
- CSV  
- Fixed-width  
- ISO8583 settlement message  
- XML/JSON theo từng ngân hàng  

File gồm:
- Transaction ID  
- Amount  
- Currency  
- Capture date  
- Auth code  
- MID/TID  
- Fees  
- Refunds  

Gateway → SFTP/HTTPS → Acquirer.

---

## 3. Reconciliation (Đối soát)
Nhiệm vụ:
- So sánh số liệu **trong hệ thống** với **file đối soát của ngân hàng**.
- Tự đánh dấu:
  - **Match** – khớp
  - **Mismatch** – lệch  
  - **Missing transaction** – không có trong file ngân hàng
  - **Extra transaction** – ngân hàng trả về giao dịch lạ

Khi lệch → tạo dispute ticket để xử lý.

---

## 4. Ghi nhận Chargeback / Dispute
Hệ thống cần:
- Nhận file chargeback từ acquirer (CBK).  
- Update transaction state → `CHARGEBACK`, `REPRESENTMENT`, `ARBITRATION`…  
- Lưu timeline:
  - CBK created  
  - CBK reason code  
  - Document request  
  - Merchant represent evidence  

---

# 🏦 Hệ thống cần tích hợp với

| Loại | Ví dụ |
|------|-------|
| **Ngân hàng acquirer** | ACB, Vietcombank, Techcombank, Citi, Chase… |
| **Card Networks** | Visa, Mastercard, JCB, Amex |
| **PSP (Payment Service Providers)** | Stripe, Adyen, Braintree, CyberSource, PayPal Pro |
| **Fraud Services** | Sift, ThreatMetrix, ClearSale, Accertify |

# 🔁 Settlement End-to-End Flow

Dưới đây là **flow chính xác** cho settlement trong hệ thống thanh toán thẻ. Mình trình bày cả **bản chuẩn (Acquirer-centric)** và **biến thể có PSP** để bạn áp dụng tùy kiến trúc.

---

## 📘 1. Standard Card Settlement (Acquirer → Card Network → Issuer → Acquirer → Merchant)

**(a) Transaction time — Authorization / Capture**  
1. **Cardholder** dùng thẻ trên **Merchant** (checkout).  
2. **Merchant** gửi transaction đến **Gateway** (hoặc PSP).  
3. **Gateway** gửi authorization request tới **Acquirer** (hoặc PSP routing to Acquirer).  
4. **Acquirer** chuyển request qua **Card Network** (Visa/Mastercard) → **Issuer**.  
5. **Issuer** trả auth response (Approved/Declined) qua Card Network → Acquirer → Gateway → Merchant.  
6. Nếu approved và merchant capture → giao dịch trạng thái **CAPTURED**.

**(b) Settlement time — Clearing & Settlement**  
1. Tại kỳ settlement, **Acquirer** tổng hợp các giao dịch `CAPTURED` thành **settlement batch/file**.  
2. **Acquirer** gửi settlement instructions/financial clearing message lên **Card Network** (ví dụ: VisaNet).  
3. **Card Network** chuyển yêu cầu clearing đến các **Issuers** tương ứng.  
4. **Issuers** chuyển tiền (funds) qua Card Network đến **Acquirer** (net settlement via card network/settlement processor).  
5. **Acquirer** nhận funds net (sau fee, interchange) và **credit** vào merchant’s settlement account (hoặc trả qua PSP nếu merchant dùng PSP).  
6. **Merchant** thấy tiền vào tài khoản (hoặc qua PSP payout).  
7. **Reconciliation**: Acquirer vs Issuer vs Card Network vs Gateway đối soát các file, xử lý discrepancy/chargeback.

**Tóm:**  
Card Network là trung gian tài chính để chuyển funds giữa Issuer và Acquirer; Acquirer chịu trách nhiệm trả merchant.

---

## 📗 2. PSP / Aggregator Variation (PSP sits between Merchant and Acquirer)

Nhiều merchant dùng PSP (Stripe, Adyen, Braintree). PSP có thể:  
- Act as merchant’s acquirer (in some regions), or  
- Route to third-party acquirer.

**Flow (PSP-involved):**  
1. Cardholder → Merchant → **PSP/Gateway**.  
2. PSP routes auth to **Acquirer** (or PSP's acquiring partner) → Card Network → Issuer → back.  
3. Capture → transaction marked CAPTURED in PSP.  
4. At settlement, **Acquirer** (or PSP acting as acquirer) initiates settlement via **Card Network** → Issuers.  
5. Funds flow Issuer → Card Network → Acquirer → **PSP** (if PSP is intermediate) → **Merchant** (PSP pays out to merchant according to payout schedule).  
6. Reconciliation: PSP reconciles incoming settlement with merchant payouts; handles fees, refunds, chargebacks.

**Important variants:**  
- PSP may net-settle multiple merchants in one file.  
- PSP could be the acquirer in some markets (simpler flow).  
- Some PSPs use multiple acquirers depending on card/country (dynamic routing).
