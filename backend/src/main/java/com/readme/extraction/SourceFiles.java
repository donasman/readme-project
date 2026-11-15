package com.readme.extraction;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.readme.common.error.ApiException;

/**
 * 올린 파일에서 글을 읽는다. 형식은 확장자를 믿지 않고 파일의 앞부분(매직 바이트)과 안의 구성으로 정한다.
 * 확장자는 앞부분으로 알 수 없는 글 파일(TXT, MD)에만 쓴다.
 *
 * <p>읽는 형식(정함): PDF(글자 층만), DOCX, HWPX, HWP, PPTX, TXT, MD. 사진과 글자 층이 없는 PDF 는 SOURCE_NO_TEXT 다.
 * PDF 와 HWP 는 다른 프로세스에서 읽는다(IsolatedReader).
 * 파일 이름과 내용은 기록(log)에 남기지 않는다.
 */
final class SourceFiles {

	/** zip(DOCX, PPTX, HWPX)의 압축을 푼 크기 합계의 상한. 압축 폭탄이 메모리와 CPU 를 쓰지 않게 한다 */
	static final long MAX_INFLATED_BYTES = 100L * 1024 * 1024;

	private static final Logger log = LoggerFactory.getLogger(SourceFiles.class);

	private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] ZIP = { 'P', 'K', 3, 4 };

	private static final byte[] OLE = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private SourceFiles() {
	}

	/** 읽은 형식. contentType 은 DB 의 content_type 에 넣는다(브라우저가 보낸 값이 아니라 서버가 알아낸 값). */
	enum Format {

		PDF("application/pdf"),
		DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
		PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
		HWPX("application/hwp+zip"),
		HWP("application/x-hwp"),
		TXT("text/plain"),
		MD("text/markdown");

		final String contentType;

		Format(String contentType) {
			this.contentType = contentType;
		}

	}

	/** 읽은 결과. text 는 아직 다듬지 않은 글이다(SourceText.normalize 전). */
	record Read(Format format, String text) {
	}

	/**
	 * 파일 하나를 읽는다. 읽지 못하면 ApiException(ExtractionErrorCode).
	 *
	 * @param fileName 확장자를 보려고만 쓴다. null 이어도 된다
	 */
	static Read read(String fileName, byte[] bytes) {
		if (bytes.length == 0) {
			throw new ApiException(ExtractionErrorCode.SOURCE_EMPTY);
		}
		if (indexOf(bytes, PDF, 1024) >= 0) {
			return new Read(Format.PDF, IsolatedReader.read(Format.PDF, bytes));
		}
		if (startsWith(bytes, ZIP)) {
			return ZipXmlText.read(bytes);
		}
		if (startsWith(bytes, OLE)) {
			return new Read(Format.HWP, IsolatedReader.read(Format.HWP, bytes));
		}
		if (isImage(bytes)) {
			throw new ApiException(ExtractionErrorCode.SOURCE_NO_TEXT);
		}
		return switch (extension(fileName)) {
			case "txt" -> new Read(Format.TXT, decode(bytes));
			case "md" -> new Read(Format.MD, decode(bytes));
			default -> throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
		};
	}

	/** 형식 라이브러리가 낸 오류. 원인은 디버그 기록에 예외의 종류만 남긴다(메시지에 파일 내용이 섞일 수 있다). */
	static ApiException unreadable(Exception cause) {
		log.debug("자료를 읽지 못함: {}", cause.getClass().getName());
		return new ApiException(ExtractionErrorCode.SOURCE_UNREADABLE);
	}

	/**
	 * 글 파일의 글자 인코딩: BOM 이 있으면 그것(UTF-8, UTF-16), 없으면 UTF-8, 그것도 아니면 CP949(윈도우 메모장의 옛 기본값).
	 * NUL 이 든 파일은 글 파일이 아닌 것으로 본다.
	 */
	static String decode(byte[] bytes) {
		try {
			if (startsWith(bytes, UTF8_BOM)) {
				return strict(StandardCharsets.UTF_8, Arrays.copyOfRange(bytes, 3, bytes.length));
			}
			if (bytes.length >= 2 && ((bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE)
					|| (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF))) {
				return strict(StandardCharsets.UTF_16, bytes);
			}
			for (byte b : bytes) {
				if (b == 0) {
					throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
				}
			}
			try {
				return strict(StandardCharsets.UTF_8, bytes);
			}
			catch (CharacterCodingException notUtf8) {
				return strict(Charset.forName("x-windows-949"), bytes);
			}
		}
		catch (CharacterCodingException ex) {
			throw unreadable(ex);
		}
	}

	private static String strict(Charset charset, byte[] bytes) throws CharacterCodingException {
		return charset.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT)
			.decode(ByteBuffer.wrap(bytes))
			.toString();
	}

	/** PNG, JPEG, GIF, TIFF, WebP, HEIC·AVIF(휴대폰 사진). */
	private static boolean isImage(byte[] bytes) {
		return startsWith(bytes, new byte[] { (byte) 0x89, 'P', 'N', 'G' })
				|| startsWith(bytes, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF })
				|| startsWith(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII))
				|| startsWith(bytes, new byte[] { 'I', 'I', '*', 0 })
				|| startsWith(bytes, new byte[] { 'M', 'M', 0, '*' })
				|| (startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII)) && indexOf(bytes, "WEBP".getBytes(StandardCharsets.US_ASCII), 16) == 8)
				|| isHeif(bytes);
	}

	private static boolean isHeif(byte[] bytes) {
		if (bytes.length < 12 || indexOf(bytes, "ftyp".getBytes(StandardCharsets.US_ASCII), 8) != 4) {
			return false;
		}
		String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
		return brand.equals("heic") || brand.equals("heix") || brand.equals("heif") || brand.equals("mif1")
				|| brand.equals("avif");
	}

	private static String extension(String fileName) {
		if (fileName == null || fileName.lastIndexOf('.') < 0) {
			return "";
		}
		return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
	}

	/** bytes 의 앞 within 바이트 안에서 pattern 이 시작하는 곳. 없으면 -1. PDF 는 머리말 앞에 쓰레기 바이트가 붙기도 한다. */
	private static int indexOf(byte[] bytes, byte[] pattern, int within) {
		int last = Math.min(bytes.length, within) - pattern.length;
		for (int i = 0; i <= last; i++) {
			if (Arrays.equals(bytes, i, i + pattern.length, pattern, 0, pattern.length)) {
				return i;
			}
		}
		return -1;
	}

	/** 압축을 풀며 읽은 바이트 수를 세고, MAX_INFLATED_BYTES 를 넘으면 SOURCE_TOO_LARGE 를 던진다. 파일 하나에 하나씩 쓴다. */
	static final class InflateBudget {

		private long left = MAX_INFLATED_BYTES;

		static ApiException tooLarge() {
			return new ApiException(ExtractionErrorCode.SOURCE_TOO_LARGE, "압축을 풀면 너무 큰 파일입니다");
		}

		/** in 에서 읽는 만큼 센다. 닫아도 in 은 닫지 않는다(zip 의 다음 항목을 읽어야 한다). */
		InputStream guard(InputStream in) {
			return new FilterInputStream(in) {

				@Override
				public int read() throws IOException {
					int b = super.read();
					if (b >= 0) {
						spend(1);
					}
					return b;
				}

				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					int n = super.read(buffer, offset, length);
					if (n > 0) {
						spend(n);
					}
					return n;
				}

				@Override
				public long skip(long n) throws IOException {
					long skipped = super.skip(n);
					spend(skipped);
					return skipped;
				}

				@Override
				public void close() {
				}

			};
		}

		private void spend(long bytes) {
			this.left -= bytes;
			if (this.left < 0) {
				throw tooLarge();
			}
		}

	}

}
