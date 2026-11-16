package com.readme.inboundmail;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 메일 원문에서 쓰는 칸 읽기(메일 설계 4.7의 본문 글). */
class RawMailTests {

	@Test
	void plainMailWithEncodedSubjectAndName() {
		RawMail mail = RawMail.parse(bytes("""
				Message-ID: <abc@company.example>
				From: %s <Recruit@Company.example>
				Subject: %s
				MIME-Version: 1.0
				Content-Type: text/plain; charset=UTF-8
				Content-Transfer-Encoding: base64

				%s
				""".formatted(word("예시 보안 채용팀"), word("[예시 보안] 2차 면접 안내"), base64("2차 면접은 11월 17일입니다."))));

		assertThat(mail.messageId()).isEqualTo("<abc@company.example>");
		assertThat(mail.fromAddress()).isEqualTo("recruit@company.example");
		assertThat(mail.fromName()).isEqualTo("예시 보안 채용팀");
		assertThat(mail.sender()).isEqualTo("예시 보안 채용팀");
		assertThat(mail.subject()).isEqualTo("[예시 보안] 2차 면접 안내");
		assertThat(mail.text()).isEqualTo("2차 면접은 11월 17일입니다.");
	}

	@Test
	void plainTextIsPreferredOverHtml() {
		RawMail mail = RawMail.parse(bytes("""
				From: hr@company.example
				Subject: hi
				MIME-Version: 1.0
				Content-Type: multipart/alternative; boundary="b1"

				--b1
				Content-Type: text/html; charset=UTF-8

				<p>html <b>text</b></p>
				--b1
				Content-Type: text/plain; charset=UTF-8

				plain text
				--b1--
				"""));

		assertThat(mail.text()).isEqualTo("plain text");
		assertThat(mail.sender()).isEqualTo("hr@company.example");
		assertThat(mail.messageId()).isNull();
	}

	@Test
	void htmlOnlyMailGivesItsText() {
		RawMail mail = RawMail.parse(bytes("""
				From: hr@company.example
				Subject: hi
				MIME-Version: 1.0
				Content-Type: text/html; charset=UTF-8

				<html><body><p>서류 전형에 <b>합격</b>하셨습니다.</p></body></html>
				"""));

		assertThat(mail.text()).isEqualTo("서류 전형에 합격하셨습니다.");
	}

	@Test
	void koreanLegacyCharsetsAreReadAsCp949() {
		Charset cp949 = Charset.forName("x-windows-949");
		String body = "똠방각하 면접 안내"; // '똠'은 EUC-KR 에 없고 CP949 에만 있다
		RawMail mail = RawMail.parse(bytes("""
				From: hr@company.example
				Subject: hi
				MIME-Version: 1.0
				Content-Type: text/plain; charset="ks_c_5601-1987"
				Content-Transfer-Encoding: base64

				%s
				""".formatted(Base64.getMimeEncoder().encodeToString(body.getBytes(cp949)))));

		assertThat(mail.text()).isEqualTo(body);
	}

	@Test
	void attachmentsAreSkippedButForwardedMailsAreRead() {
		RawMail mail = RawMail.parse(bytes("""
				From: name@gmail.com
				Subject: Fwd: 결과
				MIME-Version: 1.0
				Content-Type: multipart/mixed; boundary="b1"

				--b1
				Content-Type: text/plain; charset=UTF-8

				\s
				--b1
				Content-Type: text/plain; charset=UTF-8
				Content-Disposition: attachment; filename="resume.txt"

				이력서 첨부
				--b1
				Content-Type: message/rfc822
				Content-Disposition: attachment

				From: hr@company.example
				Subject: 결과
				Content-Type: text/plain; charset=UTF-8

				최종 합격을 축하드립니다.
				--b1--
				"""));

		assertThat(mail.text()).isEqualTo("최종 합격을 축하드립니다.");
	}

	@Test
	void brokenMailGivesEmptyValues() {
		RawMail mail = RawMail.parse(new byte[] { 0, 1, 2 });

		assertThat(mail.subject()).isEmpty();
		assertThat(mail.text()).isEmpty();
		assertThat(mail.sender()).isEmpty();
	}

	/** 줄바꿈을 CRLF 로 */
	private static byte[] bytes(String text) {
		return text.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
	}

	/** RFC 2047 의 UTF-8 B 인코딩 */
	private static String word(String text) {
		return "=?UTF-8?B?" + base64(text) + "?=";
	}

	private static String base64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

}
