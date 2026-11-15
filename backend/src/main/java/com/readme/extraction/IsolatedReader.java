package com.readme.extraction;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.readme.common.error.ApiException;

/**
 * PDF 와 HWP 를 서버와 다른 자바 프로세스에서 읽는다. 작은 파일도 풀면 메모리를 다 쓸 수 있어서다(PDFBox 는 스트림 하나를 통째로
 * 메모리에 풀고, hwplib 은 글자마다 객체를 만든다). 그 프로세스는 메모리(HEAP)와 시간(TIMEOUT)에 상한이 있고, 넘으면 그 프로세스만
 * 죽는다. 서버는 422 SOURCE_UNREADABLE 로 답한다.
 *
 * <p>파일은 임시 파일로 넘기고(표준 입력) 글은 임시 파일로 받는다(표준 출력). 둘 다 읽고 바로 지운다. 원본 파일을 두지 않는다는
 * 설계 가정 C2 는 그대로다. 받는 쪽 진입점이 {@link #main} 이라 클래스가 public 이다.
 *
 * <p>띄우는 법: 테스트·bootRun 처럼 클래스 경로가 여럿이면 그 경로로 이 클래스를 띄운다(윈도우의 명령 길이 제한 때문에 인자 파일로
 * 넘긴다). {@code java -jar} 로 띄운 실행 jar 면 그 jar 의 PropertiesLauncher 로 이 클래스를 띄운다(loader.main).
 */
public final class IsolatedReader {

	/** 읽는 프로세스의 힙. 20MB 파일의 글자 층을 읽기에 넉넉하다 */
	static final String HEAP = "-Xmx256m";

	static final Duration TIMEOUT = Duration.ofSeconds(30);

	/** 읽기에 실패해 정해진 오류(ApiException)를 낸 종료 코드. 출력 파일의 첫 줄이 code, 나머지가 message 다 */
	private static final int API_ERROR = 10;

	private static final String BOOT_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";

	private IsolatedReader() {
	}

	/** 서버 쪽. 다른 프로세스에서 읽은 글을 돌려준다. 그 프로세스가 메모리나 시간을 넘으면 SOURCE_UNREADABLE. */
	static String read(SourceFiles.Format format, byte[] bytes) {
		return read(format, bytes, HEAP, TIMEOUT);
	}

	static String read(SourceFiles.Format format, byte[] bytes, String heap, Duration timeout) {
		List<Path> files = new ArrayList<>();
		try {
			Path input = temp(files, ".in");
			Path output = temp(files, ".out");
			Files.write(input, bytes);
			Process process = new ProcessBuilder(command(format, heap, files))
				.redirectInput(input.toFile())
				.redirectOutput(output.toFile())
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly().waitFor();
				throw new ApiException(ExtractionErrorCode.SOURCE_UNREADABLE);
			}
			int exit = process.exitValue();
			if (exit != 0 && exit != API_ERROR) {
				// 메모리 초과(-XX:+ExitOnOutOfMemoryError 는 3), 그 밖의 실패
				throw new ApiException(ExtractionErrorCode.SOURCE_UNREADABLE);
			}
			if (Files.size(output) > 4L * SourceText.READ_LIMIT) { // UTF-8 로 한 글자 4바이트까지
				throw SourceText.tooLong();
			}
			String result = Files.readString(output, StandardCharsets.UTF_8);
			if (exit == API_ERROR) { // 코드, 쪽 수(없으면 빈 줄), 메시지
				String[] parts = result.split("\n", 3);
				ApiException error = new ApiException(ExtractionErrorCode.valueOf(parts[0]), parts[2]);
				throw parts[1].isEmpty() ? error : error.with(PdfText.PAGES, Integer.parseInt(parts[1]));
			}
			return result;
		}
		catch (IOException ex) {
			throw SourceFiles.unreadable(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw SourceFiles.unreadable(ex);
		}
		finally {
			files.forEach(IsolatedReader::deleteQuietly);
		}
	}

	/** 읽는 프로세스 쪽. 인자는 형식(PDF, HWP). 표준 입력의 파일을 읽어 글을 표준 출력에 UTF-8 로 쓴다. */
	public static void main(String[] args) throws IOException {
		PrintStream result = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
		System.setOut(System.err); // 라이브러리가 표준 출력에 쓰는 것이 글에 섞이지 않게 한다
		byte[] bytes = System.in.readAllBytes();
		try {
			result.print(switch (SourceFiles.Format.valueOf(args[0])) {
				case PDF -> PdfText.read(bytes);
				case HWP -> HwpText.read(bytes);
				default -> throw new IllegalArgumentException(args[0]);
			});
			result.flush();
		}
		catch (ApiException ex) {
			Object pages = ex.details().get(PdfText.PAGES);
			result.print(ex.errorCode().name() + "\n" + ((pages != null) ? pages : "") + "\n" + ex.getMessage());
			result.flush();
			System.exit(API_ERROR);
		}
	}

	private static List<String> command(SourceFiles.Format format, String heap, List<Path> files) throws IOException {
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		String classPath = System.getProperty("java.class.path");
		List<String> command = new ArrayList<>(List.of(java, heap, "-XX:+ExitOnOutOfMemoryError", "-XX:+UseSerialGC",
				"-Djava.awt.headless=true",
				// 읽는 프로세스는 기록을 남기지 않는다(파일 내용이 섞일 수 있다)
				"-Dslf4j.provider=org.slf4j.helpers.NOP_SLF4JServiceProvider"));
		if (isExecutableJar(classPath)) {
			command.addAll(List.of("-cp", classPath, "-Dloader.main=" + IsolatedReader.class.getName(), BOOT_LAUNCHER));
		}
		else {
			Path arguments = temp(files, ".args");
			Files.writeString(arguments, "-cp \"" + classPath.replace('\\', '/') + "\"\n" + IsolatedReader.class.getName());
			command.add("@" + arguments);
		}
		command.add(format.name());
		return command;
	}

	/** java -jar 로 띄운 실행 jar 인가. 그때 클래스 경로는 그 jar 하나다. */
	private static boolean isExecutableJar(String classPath) {
		return !classPath.contains(File.pathSeparator) && classPath.endsWith(".jar");
	}

	private static Path temp(List<Path> files, String suffix) throws IOException {
		Path file = Files.createTempFile("readme-source-", suffix);
		files.add(file);
		return file;
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			file.toFile().deleteOnExit();
		}
	}

}
