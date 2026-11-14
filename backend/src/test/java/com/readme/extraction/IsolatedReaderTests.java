package com.readme.extraction;

import static com.readme.extraction.HwpTextTests.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * PDF·HWP 를 다른 프로세스에서 읽기. 그 프로세스가 메모리를 다 쓰거나 오래 걸리면 그 프로세스만 끝나고 SOURCE_UNREADABLE 이다.
 * 테스트에서는 테스트의 클래스 경로로 띄운다. 실행 jar 로 띄우는 길은 한 번 손으로 확인했다(경험카드 설계 4.3).
 */
class IsolatedReaderTests {

	@Test
	void readsTheTextInAnotherProcess() throws IOException {
		String text = IsolatedReader.read(SourceFiles.Format.PDF,
				PdfTextTests.pdf("Order API p95 1.8s to 0.4s, queries 21 to 1 per order with Redis cache"));

		assertThat(text).contains("21 to 1");
	}

	@Test
	void anErrorInTheOtherProcessComesBackWithItsCode() throws IOException {
		byte[] scan = PdfTextTests.pdf("");

		assertCode(() -> IsolatedReader.read(SourceFiles.Format.PDF, scan), ExtractionErrorCode.SOURCE_NO_TEXT);
	}

	@Test
	void runningOutOfMemoryEndsOnlyTheOtherProcess() throws Exception {
		// hwplib 은 글자마다 객체를 만든다(글자당 20바이트쯤). 100만 자면 압축한 파일은 작아도 힙 16MB 를 넘는다.
		byte[] hwp = HwpTextTests.hwp("가".repeat(1_000_000));

		assertThat(hwp.length).isLessThan(100_000);
		assertCode(() -> IsolatedReader.read(SourceFiles.Format.HWP, hwp, "-Xmx16m", IsolatedReader.TIMEOUT),
				ExtractionErrorCode.SOURCE_UNREADABLE);
		// 실패한 까닭이 메모리다: 보통의 힙에서는 같은 파일을 읽는다
		assertThat(IsolatedReader.read(SourceFiles.Format.HWP, hwp)).contains("가가가");
	}

	@Test
	void aReadThatTakesTooLongIsStopped() throws IOException {
		byte[] pdf = PdfTextTests.pdf("Order API p95 1.8s to 0.4s, queries 21 to 1 per order with Redis cache");

		assertCode(() -> IsolatedReader.read(SourceFiles.Format.PDF, pdf, IsolatedReader.HEAP, Duration.ofMillis(1)),
				ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	@Test
	void theTemporaryFilesAreDeleted() throws IOException {
		List<Path> before = temporaryFiles();

		IsolatedReader.read(SourceFiles.Format.PDF, PdfTextTests.pdf("Order API p95 1.8s to 0.4s, queries 21 to 1 per order"));
		assertCode(() -> IsolatedReader.read(SourceFiles.Format.PDF, PdfTextTests.pdf("")), ExtractionErrorCode.SOURCE_NO_TEXT);

		assertThat(temporaryFiles()).isEqualTo(before);
	}

	private static List<Path> temporaryFiles() throws IOException {
		try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
			return files.filter((file) -> file.getFileName().toString().startsWith("readme-source-")).sorted().toList();
		}
	}

}
