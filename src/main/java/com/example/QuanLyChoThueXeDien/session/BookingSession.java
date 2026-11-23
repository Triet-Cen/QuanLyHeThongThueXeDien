package com.example.QuanLyChoThueXeDien.session;

import com.example.QuanLyChoThueXeDien.rental.dtos.BookingForm;
import com.example.QuanLyChoThueXeDien.rental.dtos.BookingQuote;
import jakarta.servlet.http.HttpSession;
import java.util.*;


public class BookingSession {
    private static final String SELECTION = "bookingSelection";
    //Đây là hằng số tên khóa, dùng để lưu và lấy BookingForm trong HttpSession:
    //Khi khách đăng nhập để xác nhận đơn thuê đó, session này có thể lưu lại để kháCH tiếp tục đặt chiếc xe đó

    private final Map<String, BookingQuote> quotes = new LinkedHashMap<>();
    private final Map<String, Integer> createdOrders = new HashMap<>();
    private final String owner;

    public BookingSession(String owner) { this.owner = owner; }

    public static BookingForm getSelection(HttpSession session) {
        return session.getAttribute(SELECTION) instanceof BookingForm form ? form : null;
    }

    public static void saveSelection(HttpSession session, BookingForm form) {
        session.setAttribute(SELECTION, form.copy());
    }

    public synchronized String add(BookingQuote quote) {
        String key = UUID.randomUUID().toString(); //tạo mã mới cho báo giá
        quotes.put(key, quote); //lưu báo giá trên server
        if (quotes.size() > 30) {
            String oldest = quotes.keySet().iterator().next();
            quotes.remove(oldest);
            createdOrders.remove(oldest);
        }
        return key;
    }

    public synchronized BookingQuote get(String key) { return quotes.get(key); }

    public synchronized Integer createdOrder(String key) { return createdOrders.get(key); }

    public synchronized void rememberCreated(String key, Integer orderId) { createdOrders.put(key, orderId); }

    public static BookingSession of(HttpSession session, String email) {
        synchronized (session) {
            Object existing = session.getAttribute("bookingQuotes");
            if (existing instanceof BookingSession state && state.owner.equals(email)) return state;
            BookingSession state = new BookingSession(email);
            session.setAttribute("bookingQuotes", state);
            return state;
        } /*1.Tìm đối tượng đang lưu dưới khóa "bookingQuotes" trong HTTP session.
            2.Nếu đối tượng là BookingSession và có owner trùng email hiện tại, dùng lại.
            3.Nếu chưa có hoặc thuộc tài khoản khác, tạo đối tượng mới và lưu vào session.
            Vì vậy, các báo giá được truy cập thông qua hàm này phải thuộc tài khoản hiện tại.*/
    }
}
