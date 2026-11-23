"use strict";
document.querySelectorAll("[data-booking-submit]").forEach(form => {
    form.addEventListener("submit", () => { form.querySelector("button[type=submit]").disabled = true; });
});
const countdown = document.querySelector("[data-hold-countdown]");
if (countdown) {
    const serverNow = Number(countdown.dataset.serverNow);
    const holdUntil = Number(countdown.dataset.holdUntil);
    const started = performance.now();
    let timer;
    const update = () => {
        const remaining = Math.max(0, Math.ceil((holdUntil - serverNow - (performance.now() - started)) / 1000));
        countdown.textContent = `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}`;
        if (remaining === 0) {
            document.querySelector("[data-active-hold]").hidden = true;
            const message = document.querySelector("[data-expired-message]");
            message.hidden = false;
            message.textContent = "Đã đến hạn giữ lịch. Vui lòng cập nhật trạng thái từ hệ thống hoặc tìm xe lại.";
            clearInterval(timer);
        }
    };
    timer = setInterval(update, 1000);
    update();
}
