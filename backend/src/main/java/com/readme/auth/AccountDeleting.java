package com.readme.auth;

/**
 * 탈퇴로 계정(users 행)을 지우기 바로 전에 낸다. 받는 쪽은 같은 트랜잭션 안에서 자기 테이블을 정리한다
 * (예: group 의 LeaderHandover 가 그룹장을 넘긴다). 받는 쪽이 예외를 던지면 탈퇴가 모두 취소된다.
 *
 * <p>auth 는 group 을 불러오지 않는다. group 이 이미 auth(CurrentUser, LoginUser)를 불러와서, 직접 부르면 패키지가 서로를 부른다.
 * 이벤트는 Spring 의 기본대로 내는 쪽의 스레드와 트랜잭션에서 바로 돈다({@code @EventListener}).
 *
 * @param userId 지울 사람의 users.id
 */
public record AccountDeleting(long userId) {
}
