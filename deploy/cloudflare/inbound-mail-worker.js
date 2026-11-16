// 리드미 수신 전용 도메인의 Email Worker(docs/리드미_설계_메일.md 4.1). 아직 실제 메일로 돌려 본 적이 없다(같은 문서 6절).
// Cloudflare Email Routing 의 모든 주소 받기(catch-all) → 이 Worker → 서버 POST /api/inbound-mail.
// 메일을 풀지 않고 원문을 서명과 함께 넘기기만 한다. 푸는 일은 서버가 한다(무료 Worker 의 CPU 한도).
//
// 비밀 값 INBOUND_MAIL_SECRET: 서버의 app.inbound-mail.secret(INBOUND_MAIL_SECRET)과 같은 값
// 변수 INBOUND_MAIL_URL: 서버의 받는 주소. 예: https://api.<서비스 도메인>/api/inbound-mail

const MAX_BYTES = 10 * 1024 * 1024; // 서버의 InboundMailController.MAX_BYTES

export default {
  async email(message, env) {
    if (message.rawSize > MAX_BYTES) {
      message.setReject("Message too large");
      return;
    }
    const raw = new Uint8Array(await new Response(message.raw).arrayBuffer());
    const timestamp = String(Math.floor(Date.now() / 1000));
    // Cloudflare 가 맨 앞에 붙인 Authentication-Results. 같은 이름이 여럿이면 ", " 로 이어진다. 서버가 맨 앞의 것만 믿는다(메일 설계 M3).
    // HTTP 헤더 값은 줄바꿈을 담지 못하고 앞뒤 빈칸이 지워질 수 있어, 서명 전에 같은 꼴로 맞춘다.
    const clean = (value) => (value ?? "").replace(/[\r\n]+/g, " ").trim();
    const from = clean(message.from);
    const to = clean(message.to);
    const auth = clean(message.headers.get("authentication-results"));

    // 서명할 바이트: 시각\n봉투 From\n봉투 To\nAuthentication-Results\n + 원문(메일 설계 M2, 서버의 MailSignature)
    const head = new TextEncoder().encode([timestamp, from, to, auth].join("\n") + "\n");
    const signed = new Uint8Array(head.length + raw.length);
    signed.set(head);
    signed.set(raw, head.length);
    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(env.INBOUND_MAIL_SECRET),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, signed));
    const signature = Array.from(mac, (b) => b.toString(16).padStart(2, "0")).join("");

    const response = await fetch(env.INBOUND_MAIL_URL, {
      method: "POST",
      headers: {
        "Content-Type": "message/rfc822",
        "X-Readme-Timestamp": timestamp,
        "X-Readme-Envelope-From": from,
        "X-Readme-Envelope-To": to,
        "X-Readme-Auth-Results": auth,
        "X-Readme-Signature": signature,
      },
      body: raw,
    });
    if (!response.ok) {
      // 던지면 보낸 쪽이 나중에 다시 보낸다고 보고 짰다(메일 설계 6절 5번에서 확인할 것). 서버는 받지 않고 버린 메일에도 204 를 준다.
      throw new Error(`readme server answered ${response.status}`);
    }
  },
};
