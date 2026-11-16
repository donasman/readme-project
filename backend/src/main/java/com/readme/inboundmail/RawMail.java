package com.readme.inboundmail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.jsoup.Jsoup;

/**
 * 메일 원문(RFC 822)에서 쓰는 칸만 읽는다. Spring 없이 돈다. 원문은 저장하지 않는다(메일 설계 M9).
 *
 * <p>본문 글: 첨부가 아닌 text/plain 이 있으면 그것, 없으면 text/html 의 글자다. 첨부로 전달한 메일(message/rfc822)도 들여다본다.
 * 문자 집합 euc-kr, ks_c_5601-1987 은 CP949 로 읽는다(한국 메일이 이 이름으로 CP949 글자를 보낸다).
 *
 * @param messageId   Message-ID. 없으면 null
 * @param fromAddress From 헤더의 주소(소문자). 없거나 읽지 못하면 null
 * @param fromName    From 헤더의 이름. 없으면 null
 * @param subject     제목. 없으면 ""
 * @param text        본문 글. 없으면 ""
 */
record RawMail(String messageId, String fromAddress, String fromName, String subject, String text) {

	private static final Session SESSION = Session.getInstance(new Properties());

	private static final Charset CP949 = Charset.forName("x-windows-949");

	static RawMail parse(byte[] raw) {
		try {
			MimeMessage message = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
			InternetAddress from = firstFrom(message);
			String subject = message.getSubject();
			String text = text(message, false);
			if (text == null) {
				text = text(message, true);
			}
			return new RawMail(message.getMessageID(), (from != null) ? SenderCheck.normalize(from.getAddress()) : null,
					(from != null) ? blankToNull(from.getPersonal()) : null, (subject != null) ? subject.strip() : "",
					(text != null) ? text.strip() : "");
		}
		catch (MessagingException | IOException ex) {
			return new RawMail(null, null, null, "", "");
		}
	}

	/** 보낸 곳으로 보일 글자: 이름이 있으면 이름, 없으면 주소. */
	String sender() {
		if (this.fromName != null) {
			return this.fromName;
		}
		return (this.fromAddress != null) ? this.fromAddress : "";
	}

	private static InternetAddress firstFrom(MimeMessage message) {
		try {
			Address[] from = message.getFrom();
			return (from != null && from.length > 0 && from[0] instanceof InternetAddress address) ? address : null;
		}
		catch (MessagingException ex) {
			return null;
		}
	}

	/**
	 * html 이 false 면 text/plain 만, true 면 text/html 만 찾는다. 빈 글은 건너뛴다. 없으면 null.
	 * 첨부로 전달한 메일(message/rfc822)은 첨부여도 들여다보고, 그 밖의 첨부는 건너뛴다.
	 */
	private static String text(Part part, boolean html) throws MessagingException, IOException {
		if (part.isMimeType("message/rfc822")) {
			return (part.getContent() instanceof Part inner) ? text(inner, html) : null;
		}
		if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
			return null;
		}
		if (part.isMimeType("text/plain")) {
			return html ? null : nonBlank(decode(part));
		}
		if (part.isMimeType("text/html")) {
			return html ? nonBlank(Jsoup.parse(decode(part)).text()) : null;
		}
		if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				String found = text(multipart.getBodyPart(i), html);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static String nonBlank(String text) {
		return text.isBlank() ? null : text;
	}

	private static String decode(Part part) throws MessagingException, IOException {
		try (InputStream in = part.getInputStream()) {
			return new String(in.readAllBytes(), charset(part.getContentType()));
		}
	}

	private static Charset charset(String contentType) {
		try {
			String name = new ContentType(contentType).getParameter("charset");
			if (name == null) {
				return StandardCharsets.UTF_8;
			}
			String lower = name.trim().toLowerCase(Locale.ROOT);
			if (lower.equals("euc-kr") || lower.equals("euc_kr") || lower.startsWith("ks_c_5601") || lower.equals("ksc5601")
					|| lower.equals("cp949")) {
				return CP949;
			}
			return Charset.forName(name.trim());
		}
		catch (RuntimeException | jakarta.mail.internet.ParseException ex) {
			return StandardCharsets.UTF_8;
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
