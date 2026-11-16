/**
 * 메일 자동화(메일 설계): 받은 채용 메일을 읽어 내 공고와 지원 상태에 반영한다.
 *
 * <p>테이블: mail_inboxes, mail_senders, inbound_mails. API: /api/inbound-mail(Worker 가 서명해 보낸다, 로그인 없이 연다),
 * /api/me/mail-settings(수신 주소와 보내는 주소), /api/me/mailbox(메일 확인함).
 * <p>받기: InboundMailController 가 서명(MailSignature)을 보고, MailReceiver 가 받는 주소와 보낸 사람(SenderCheck)을 확인해
 * 본문 글만 저장한다. 커밋 뒤 MailClassifier 가 다른 스레드에서 llm 의 Llm 으로 분류하고 MailRules 로 반영할 것을 정한다.
 * 새 공고는 posting 의 {@link com.readme.posting.UserPostings#addFromMail} 로 바로 넣고, 지원 상태와 전형 일정은 확인함에서 반영할 때
 * {@link com.readme.posting.UserPostings#setStatus}, {@link com.readme.posting.UserPostings#setSchedule} 로 바꾼다.
 * 근거: docs/리드미_설계_메일.md.
 */
package com.readme.inboundmail;
