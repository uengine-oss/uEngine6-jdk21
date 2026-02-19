-- H2: GenerationType.AUTO 시 Hibernate가 사용하는 시퀀스 생성 (없으면 Sequence not found 오류)
-- in-memory DB는 매 기동 시 새로 만들어지므로 시퀀스가 없음.
CREATE SEQUENCE HIBERNATE_SEQUENCE START WITH 1 INCREMENT BY 1;
