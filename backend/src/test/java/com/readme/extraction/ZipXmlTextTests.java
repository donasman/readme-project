package com.readme.extraction;

import static com.readme.extraction.HwpTextTests.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * zip 안의 XML 로 된 문서(DOCX, PPTX, HWPX)와 그 방어(압축 폭탄, XXE). 시험 파일은 테스트 안에서 zip 으로 만든다.
 */
class ZipXmlTextTests {

	private static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

	// ── 형식 ─────────────────────────────────────────────────────────

	@Test
	void pptxSlidesAreInNumberOrderWithABlankLineBetween() {
		// jobfit tests/test_exp_extract.py 의 test_read_any_formats 와 같은 파일(p: 접두어는 선언되지 않았다)
		Map<String, String> entries = new LinkedHashMap<>();
		for (String[] slide : new String[][] { { "10", "열번째" }, { "2", "두번째" }, { "1", "첫번째" } }) {
			entries.put("ppt/slides/slide" + slide[0] + ".xml",
					"<p:sld xmlns:a=\"x\"><a:p><a:r><a:t>" + slide[1] + "</a:t></a:r></a:p></p:sld>");
		}

		SourceFiles.Read read = SourceFiles.read("deck.pptx", zip(entries));

		assertThat(read.format()).isEqualTo(SourceFiles.Format.PPTX);
		assertThat(read.text()).isEqualTo("첫번째\n\n두번째\n\n열번째");
	}

	@Test
	void docxParagraphsTablesAndEntities() {
		String document = """
				<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
				<w:document %s><w:body>
				<w:p/>
				<w:p><w:r><w:t>주문 조회</w:t></w:r><w:r><w:t xml:space="preserve"> API 개선</w:t></w:r></w:p>
				<w:p><w:pPr/></w:p>
				<w:p><w:r><w:t>p95 1.8초 &amp; 0.4초</w:t><w:tab/></w:r></w:p>
				<w:tbl><w:tr><w:tc><w:p><w:r><w:t>표 안의 글</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
				<w:sectPr/></w:body></w:document>
				""".formatted(W);

		SourceFiles.Read read = SourceFiles.read("이력서.docx", zip(Map.of("word/document.xml", document,
				"word/styles.xml", "<w:styles " + W + "><w:t>스타일은 읽지 않는다</w:t></w:styles>")));

		assertThat(read.format()).isEqualTo(SourceFiles.Format.DOCX);
		assertThat(read.text()).isEqualTo("주문 조회 API 개선\n\np95 1.8초 & 0.4초\n표 안의 글");
	}

	@Test
	void hwpxSectionsAreInNumberOrder() {
		Map<String, String> entries = new LinkedHashMap<>();
		entries.put("mimetype", "application/hwp+zip");
		for (String section : new String[] { "10", "2", "0", "1" }) {
			entries.put("Contents/section" + section + ".xml",
					"<hs:sec xmlns:hs=\"s\" xmlns:hp=\"p\"><hp:p><hp:run><hp:t>구역 " + section
							+ "</hp:t></hp:run></hp:p></hs:sec>");
		}

		SourceFiles.Read read = SourceFiles.read("포트폴리오.hwpx", zip(entries));

		assertThat(read.format()).isEqualTo(SourceFiles.Format.HWPX);
		assertThat(read.text()).isEqualTo("구역 0\n\n구역 1\n\n구역 2\n\n구역 10");
	}

	@Test
	void theFormatComesFromTheEntriesNotTheExtension() {
		byte[] docx = zip(Map.of("word/document.xml", "<w:document " + W + "><w:p><w:t>글</w:t></w:p></w:document>"));

		assertThat(SourceFiles.read("resume.pdf", docx).format()).isEqualTo(SourceFiles.Format.DOCX);
		assertThat(SourceFiles.read(null, docx).format()).isEqualTo(SourceFiles.Format.DOCX);
	}

	@Test
	void otherZipsAreNotAccepted() {
		assertCode(() -> SourceFiles.read("표.xlsx", zip(Map.of("xl/workbook.xml", "<workbook/>"))),
				ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
	}

	@Test
	void brokenXmlIsUnreadable() {
		assertCode(() -> SourceFiles.read("깨진.docx", zip(Map.of("word/document.xml", "<w:document><w:body>"))),
				ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	// ── XXE, 엔티티 폭탄 ────────────────────────────────────────────────

	@Test
	void externalEntitiesAreNeverResolved(@TempDir Path dir) throws IOException {
		Path secret = Files.writeString(dir.resolve("secret.txt"), "서버의 비밀");
		String document = """
				<?xml version="1.0"?>
				<!DOCTYPE w:document [<!ENTITY xxe SYSTEM "%s">]>
				<w:document %s><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>
				""".formatted(secret.toUri(), W);

		assertCode(() -> SourceFiles.read("xxe.docx", zip(Map.of("word/document.xml", document))),
				ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	@Test
	void entityExpansionIsRefused() {
		String document = """
				<?xml version="1.0"?>
				<!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
				<!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">]>
				<w:document %s><w:p><w:t>&lol3;</w:t></w:p></w:document>
				""".formatted(W);

		assertCode(() -> SourceFiles.read("lol.docx", zip(Map.of("word/document.xml", document))),
				ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	// ── 압축 폭탄 ──────────────────────────────────────────────────────

	@Test
	void anEntryThatInflatesPastTheLimitIsRefused() {
		// 100MB 가 넘는 0 을 그림처럼 넣는다. 압축하면 100KB 남짓이라 20MB 제한에 걸리지 않는다.
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("ppt/slides/slide1.xml"));
			zip.write("<p:sld><a:p><a:t>슬라이드</a:t></a:p></p:sld>".getBytes(StandardCharsets.UTF_8));
			zip.putNextEntry(new ZipEntry("ppt/media/image1.png"));
			byte[] zeros = new byte[1 << 20];
			for (long written = 0; written <= SourceFiles.MAX_INFLATED_BYTES; written += zeros.length) {
				zip.write(zeros);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

		assertThat(bytes.size()).isLessThan((int) SourceInput.MAX_BYTES);
		assertCode(() -> SourceFiles.read("bomb.pptx", bytes.toByteArray()), ExtractionErrorCode.SOURCE_TOO_LARGE);
	}

	@Test
	void tooManyEntriesAreRefused() {
		Map<String, String> entries = new LinkedHashMap<>();
		entries.put("word/document.xml", "<w:document " + W + "><w:p><w:t>글</w:t></w:p></w:document>");
		for (int i = 0; i < ZipXmlText.MAX_ENTRIES; i++) {
			entries.put("word/media/" + i, "");
		}

		assertCode(() -> SourceFiles.read("many.docx", zip(entries)), ExtractionErrorCode.SOURCE_TOO_LARGE);
	}

	static byte[] zip(Map<String, String> entries) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return bytes.toByteArray();
	}

}
