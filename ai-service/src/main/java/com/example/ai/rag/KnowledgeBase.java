package com.example.ai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 지식베이스
 * - 실제 프로덕션에서는 Pinecone, Weaviate, pgvector 등 벡터 DB 사용
 * - 포트폴리오용으로 키워드 매칭 방식의 인메모리 구현
 */
@Slf4j
@Component
public class KnowledgeBase {

    private final List<Document> documents = new ArrayList<>();

    @PostConstruct
    public void init() {
        // 쇼핑몰 FAQ 문서 로드
        loadFaqDocuments();
        log.info("[KnowledgeBase] {}개 문서 로드 완료", documents.size());
    }

    /**
     * 키워드 기반 유사 문서 검색
     * (실제 RAG에서는 임베딩 벡터 코사인 유사도 사용)
     */
    public String search(String query) {
        String lowerQuery = query.toLowerCase();

        List<Document> relevant = documents.stream()
                .filter(doc -> doc.getKeywords().stream()
                        .anyMatch(kw -> lowerQuery.contains(kw.toLowerCase())))
                .sorted(Comparator.comparingLong(doc ->
                        -doc.getKeywords().stream()
                                .filter(kw -> lowerQuery.contains(kw.toLowerCase()))
                                .count()))
                .limit(3)
                .collect(Collectors.toList());

        if (relevant.isEmpty()) {
            return null;
        }

        return relevant.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
    }

    private void loadFaqDocuments() {
        documents.add(Document.of(
                "배송 정책",
                List.of("배송", "배달", "언제", "얼마나", "며칠", "도착"),
                """
                배송 정책:
                - 일반 배송: 주문 후 2~3 영업일 이내 출고
                - 빠른 배송: 오전 11시 이전 주문 시 당일 출고
                - 배송비: 30,000원 이상 주문 시 무료 (미만 시 3,000원)
                - 제주/도서산간 지역 추가 배송비 5,000원
                - 배송 조회는 마이페이지 > 주문내역에서 확인 가능
                """
        ));

        documents.add(Document.of(
                "반품/교환 정책",
                List.of("반품", "교환", "환불", "취소", "환급", "돌려"),
                """
                반품/교환 정책:
                - 반품/교환 기간: 수령 후 7일 이내
                - 단순 변심: 반품 배송비 고객 부담 (2,500원)
                - 상품 불량/오배송: 배송비 무료
                - 환불 처리: 반품 확인 후 2~3 영업일 이내
                - 교환 시 재배송 기간: 교환 확인 후 2~3 영업일
                - 반품 신청: 고객센터 또는 마이페이지 > 주문내역
                """
        ));

        documents.add(Document.of(
                "주문 관련",
                List.of("주문", "결제", "취소", "변경", "확인", "조회"),
                """
                주문 관련 안내:
                - 주문 취소: 결제 완료 후 출고 전까지 가능
                - 출고 후 취소: 반품 처리로 진행
                - 주문 변경: 상품 준비 전까지 고객센터 문의
                - 결제 방법: 신용카드, 계좌이체, 카카오페이, 네이버페이
                - 주문 확인: 마이페이지 > 주문내역 또는 이메일 확인
                """
        ));

        documents.add(Document.of(
                "재고/상품 문의",
                List.of("재고", "품절", "입고", "상품", "상태", "구매"),
                """
                재고/상품 안내:
                - 품절 상품: 상품 상세 페이지에서 재입고 알림 신청 가능
                - 재입고 알림: 등록 이메일로 안내
                - 상품 문의: 상품 상세 페이지 > Q&A 게시판 이용
                - 대량 구매 문의: 고객센터 통화 또는 이메일 문의
                """
        ));

        documents.add(Document.of(
                "회원/계정 관련",
                List.of("회원", "계정", "비밀번호", "탈퇴", "로그인", "가입"),
                """
                회원/계정 안내:
                - 비밀번호 변경: 마이페이지 > 회원정보 수정
                - 비밀번호 분실: 로그인 페이지 > 비밀번호 찾기
                - 회원 탈퇴: 마이페이지 > 회원탈퇴 (탈퇴 후 30일간 재가입 불가)
                - 개인정보 변경: 마이페이지 > 회원정보 수정
                """
        ));
    }

    // ── 내부 Document 클래스 ──────────────────────────────────

    @lombok.Getter
    @lombok.AllArgsConstructor(staticName = "of")
    public static class Document {
        private final String title;
        private final List<String> keywords;
        private final String content;
    }
}
