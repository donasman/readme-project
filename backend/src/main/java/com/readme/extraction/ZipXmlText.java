package com.readme.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.readme.common.error.ApiException;

/**
 * zip 안의 XML 로 된 문서(DOCX, PPTX, HWPX)에서 글자를 꺼낸다. jobfit 의 textio.py {@code zip_text} 와 같은 규칙이다:
 * 글자 요소(w:t, a:t, hp:t)를 문단(w:p, a:p, hp:p)마다 이어 붙이고 문단은 줄바꿈으로, 파일(슬라이드, 구역) 사이는 빈 줄로 나눈다.
 *
 * <p>사용자가 올린 파일이므로 믿지 않는다. 압축을 푼 크기와 항목 수에 상한을 두고(압축 폭탄),
 * DTD 가 든 XML 은 읽지 않으며(외부 엔티티, 엔티티 폭탄), 요소를 너무 깊이 겹친 XML 도 읽지 않는다.
 * Office·한글 문서의 XML 에는 DTD 가 없다.
 */
final class ZipXmlText {

	/** zip 안 항목 수의 상한. 슬라이드가 수백 장인 PPTX 도 수천 개를 넘지 않는다 */
	static final int MAX_ENTRIES = 10_000;

	/** XML 요소를 겹칠 수 있는 깊이. 문서는 수십 단계를 넘지 않는다 */
	static final int MAX_ELEMENT_DEPTH = 1000;

	private static final String DOCX_BODY = "word/document.xml";

	private static final Pattern PPTX_SLIDE = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");

	private static final Pattern HWPX_SECTION = Pattern.compile("Contents/section(\\d+)\\.xml");

	private ZipXmlText() {
	}

	/** 읽은 형식과 글. 문서가 아닌 zip(XLSX, 그냥 압축 파일 등)은 SOURCE_UNSUPPORTED_TYPE. */
	static SourceFiles.Read read(byte[] bytes) {
		Map<String, String> parts = new LinkedHashMap<>();
		SourceFiles.InflateBudget budget = new SourceFiles.InflateBudget();
		int textLength = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			int entries = 0;
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (++entries > MAX_ENTRIES) {
					throw SourceFiles.InflateBudget.tooLarge();
				}
				InputStream in = budget.guard(zip);
				if (isTextPart(entry.getName())) {
					String part = lines(in, SourceText.READ_LIMIT - textLength);
					textLength += part.length();
					parts.put(entry.getName(), part);
				}
				else {
					in.transferTo(OutputStream.nullOutputStream());
				}
			}
		}
		catch (IOException | XMLStreamException ex) {
			throw SourceFiles.unreadable(ex);
		}
		catch (IllegalArgumentException ex) {
			// 항목 이름이 UTF-8 이 아니다(한국어 윈도우에서 만든 zip 은 CP949). Office·한글 문서는 늘 UTF-8 이다.
			throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
		}
		if (parts.containsKey(DOCX_BODY)) {
			return new SourceFiles.Read(SourceFiles.Format.DOCX, join(List.of(parts.get(DOCX_BODY))));
		}
		List<String> slides = numbered(parts, PPTX_SLIDE);
		if (!slides.isEmpty()) {
			return new SourceFiles.Read(SourceFiles.Format.PPTX, join(slides));
		}
		List<String> sections = numbered(parts, HWPX_SECTION);
		if (!sections.isEmpty()) {
			return new SourceFiles.Read(SourceFiles.Format.HWPX, join(sections));
		}
		throw new ApiException(ExtractionErrorCode.SOURCE_UNSUPPORTED_TYPE);
	}

	private static boolean isTextPart(String name) {
		return name.equals(DOCX_BODY) || PPTX_SLIDE.matcher(name).matches() || HWPX_SECTION.matcher(name).matches();
	}

	/** 번호 순서의 슬라이드·구역(slide2 가 slide10 보다 앞). */
	private static List<String> numbered(Map<String, String> parts, Pattern pattern) {
		Map<BigInteger, String> byNumber = new TreeMap<>();
		parts.forEach((name, part) -> {
			Matcher matcher = pattern.matcher(name);
			if (matcher.matches()) {
				byNumber.put(new BigInteger(matcher.group(1)), part);
			}
		});
		return new ArrayList<>(byNumber.values());
	}

	/** 파일(슬라이드, 구역)들을 줄바꿈으로 잇는다. 앞쪽의 빈 줄은 strip 이 지운다(jobfit 의 {@code if text or paras} 와 같다). */
	private static String join(List<String> parts) {
		return String.join("\n", parts).strip();
	}

	/**
	 * 한 XML 의 글. 문단이 끝날 때(&lt;/…:p&gt;)마다 줄을 바꾼다. 마지막 문단 뒤에 남은 것(대개 빈 글)도 한 줄이라 파일 끝에 줄바꿈이
	 * 남는다. 그래서 파일을 줄바꿈으로 이으면 슬라이드 사이가 빈 줄이 된다. 글이 limit 를 넘으면 멈춘다.
	 */
	private static String lines(InputStream in, int limit) throws XMLStreamException {
		XMLStreamReader xml = factory().createXMLStreamReader(in);
		try {
			StringBuilder text = new StringBuilder();
			int insideText = 0;
			// 바로 앞이 <…:p> 의 시작이었는지. 빈 문단(<w:p/>)은 jobfit 의 정규식이 </…:p> 를 찾지 못해 줄을 내지 않는다
			boolean paragraphJustOpened = false;
			while (xml.hasNext()) {
				int event = xml.next();
				switch (event) {
					case XMLStreamConstants.DTD -> throw new XMLStreamException("DTD 가 든 XML 은 읽지 않는다");
					case XMLStreamConstants.START_ELEMENT -> {
						if (localName(xml).equals("t")) {
							insideText++;
						}
					}
					case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
						if (insideText > 0) {
							text.append(xml.getText());
						}
					}
					case XMLStreamConstants.END_ELEMENT -> {
						if (localName(xml).equals("t")) {
							insideText--;
						}
						else if (localName(xml).equals("p") && !paragraphJustOpened) {
							text.append('\n');
						}
					}
					default -> {
					}
				}
				if (text.length() > limit) {
					throw SourceText.tooLong();
				}
				paragraphJustOpened = event == XMLStreamConstants.START_ELEMENT && localName(xml).equals("p");
			}
			return text.toString();
		}
		finally {
			xml.close();
		}
	}

	/** 접두어를 뗀 요소 이름(w:t → t). jobfit 의 정규식처럼 접두어가 무엇이든, 선언되지 않았어도 본다. */
	private static String localName(XMLStreamReader xml) {
		String name = xml.getLocalName();
		return name.substring(name.indexOf(':') + 1);
	}

	/**
	 * JDK 기본 StAX. DTD 와 외부 엔티티를 끄고(DTD 가 있으면 위에서 거부한다), 요소의 깊이를 MAX_ELEMENT_DEPTH 로 막는다.
	 * 이름공간은 보지 않는다(접두어는 localName 이 뗀다).
	 */
	private static XMLInputFactory factory() {
		XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
		factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setProperty("jdk.xml.maxElementDepth", MAX_ELEMENT_DEPTH);
		return factory;
	}

}
