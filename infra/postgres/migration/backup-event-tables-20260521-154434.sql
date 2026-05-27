--
-- PostgreSQL database dump
--

\restrict Eeo2MBkJAk8zc9DhepZbk46Sx4PQMmhfSO6TIzfTrjMRnxQXmjCvDi3sLL4T8bB

-- Dumped from database version 16.13
-- Dumped by pg_dump version 16.13

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: bpm_event_inbox; Type: TABLE; Schema: public; Owner: uengine
--

CREATE TABLE public.bpm_event_inbox (
    id bigint DEFAULT nextval('public.bpm_event_inbox_seq'::regclass) NOT NULL,
    created_at timestamp(6) with time zone DEFAULT now() NOT NULL,
    corr_key character varying(64) DEFAULT ('start_'::text || (gen_random_uuid())::text),
    event_type character varying(128),
    payload text NOT NULL,
    processed_at timestamp(6) with time zone,
    try_cnt integer DEFAULT 0 NOT NULL,
    last_error text,
    event_name character varying(128)
);


ALTER TABLE public.bpm_event_inbox OWNER TO uengine;

--
-- Name: bpm_event_mapping; Type: TABLE; Schema: public; Owner: uengine
--

CREATE TABLE public.bpm_event_mapping (
    event_type character varying(255) NOT NULL,
    correlation_key character varying(255),
    definition_id character varying(255),
    is_start_event boolean,
    tracing_tag character varying(255)
);


ALTER TABLE public.bpm_event_mapping OWNER TO uengine;

--
-- Data for Name: bpm_event_inbox; Type: TABLE DATA; Schema: public; Owner: uengine
--

COPY public.bpm_event_inbox (id, created_at, corr_key, event_type, payload, processed_at, try_cnt, last_error, event_name) FROM stdin;
501	2026-04-27 08:16:12.500153+00	start_48c30b0b-218e-45ae-bc97-0bfc19dee365	START_CREDIT_RATING	{}	2026-04-27 08:17:41.136593+00	3	java.lang.RuntimeException: Error wheneverEvent :[500] during [POST] to [http://process-service:9094/instance] [InstanceService#start(ProcessExecutionCommand)]: [{"timestamp":1777277861357,"status":500,"error":"Internal Server Error","message":"Error executing process instance: java.lang.Exception: java.lang.Exception: You (org.uengine.five.service.IAMCompanyRoleMapping@3ae74ab1) are not permitted to initiate this process. The initiator group is 'Group: IT부서'.","path":"/instance"}] | feign.FeignException$InternalServerError: [500] during [POST] to [http://process-service:9094/instance] [InstanceService#start(ProcessExecutionCommand)]: [{"timestamp":1777277861357,"status":500,"error":"Internal Server Error","message":"Error executing process instance: java.lang.Exception: java.lang.Exception: You (org.uengine.five.service.IAMCompanyRoleMapping@3ae74ab1) are not permitted to initiate this process. The initiator group is 'Group: IT부서'.","path":"/instance"}]	\N
551	2026-04-27 08:19:21.79112+00	start_b4a03037-e5d6-4f9b-bc41-83a6c09f6430	START_CREDIT_RATING	{}	2026-04-27 08:19:22.013511+00	1	\N	\N
601	2026-04-27 08:43:37.038287+00	start_7c3d055f-0562-4fde-8a98-6531f9a44220	START_CREDIT_RATING	{}	2026-04-27 08:43:38.010668+00	1	\N	\N
651	2026-04-28 06:20:18.747072+00	start_487f335b-53ff-4dd9-81b1-b03c4aeece43	START_CREDIT_RATING	{}	2026-04-28 06:20:19.336704+00	1	\N	\N
701	2026-04-28 07:42:40.504691+00	start_1a6d921e-b7d5-4878-aa24-de5866665f10	START_CREDIT_RATING	{}	2026-04-28 07:42:42.903982+00	3	java.lang.RuntimeException: Error wheneverEvent :[500] during [POST] to [http://process-service:9094/instance] [InstanceService#start(ProcessExecutionCommand)]: [{"timestamp":1777362162959,"status":500,"error":"Internal Server Error","message":"Error executing process instance: java.lang.Exception: java.lang.Exception: You (org.uengine.five.service.IAMCompanyRoleMapping@7627014a) are not permitted to initiate this process. The initiator group is 'Who has the scope 'manager''.","path":"/instance"}] | feign.FeignException$InternalServerError: [500] during [POST] to [http://process-service:9094/instance] [InstanceService#start(ProcessExecutionCommand)]: [{"timestamp":1777362162959,"status":500,"error":"Internal Server Error","message":"Error executing process instance: java.lang.Exception: java.lang.Exception: You (org.uengine.five.service.IAMCompanyRoleMapping@7627014a) are not permitted to initiate this process. The initiator group is 'Who has the scope 'manager''.","path":"/instance"}]	\N
751	2026-04-28 07:43:49.150097+00	start_23699bc9-17bc-44ab-a3e9-0c1a1e04d9c6	START_CREDIT_RATING	{}	2026-04-28 07:43:49.890274+00	1	\N	\N
752	2026-05-21 01:36:21.603752+00	start_5e668af5-83fb-cd6d-c6b3-01c4c28594bc	START_CREDIT_RATING	{"applicationId":"verify-20260521-001","자산":5173,"신용도":400}	2026-05-21 01:36:22.00023+00	1	\N	\N
753	2026-05-21 01:51:26.329247+00	start_8a685f7e-0fb8-428b-a41d-71298fa240ee	START_CREDIT_RATING	{"applicationId":"verify-001","자산":5173,"신용도":400}	2026-05-21 01:51:26.999995+00	1	\N	\N
754	2026-05-21 01:52:20.021358+00	start_d63a62bd-d095-4a0f-b3c8-b16ec963a267	START_CREDIT_RATING	{"applicationId":"verify-001","자산":5173,"신용도":400}	2026-05-21 01:52:20.999032+00	1	\N	\N
755	2026-05-21 01:53:45.632289+00	start_dceb3134-aa11-49b3-ae1f-a509c8809246	START_CREDIT_RATING	{"applicationId":"verify-001","자산":5173,"신용도":400}	2026-05-21 01:53:45.999067+00	1	\N	\N
756	2026-05-21 01:55:37.687655+00	start_01f967cf-e26a-4d30-9181-5a9de6b86b6c	START_CREDIT_RATING	{"applicationId":"verify-001","자산":5173,"신용도":400}	2026-05-21 01:55:37.997766+00	1	\N	\N
\.


--
-- Data for Name: bpm_event_mapping; Type: TABLE DATA; Schema: public; Owner: uengine
--

COPY public.bpm_event_mapping (event_type, correlation_key, definition_id, is_start_event, tracing_tag) FROM stdin;
TroubleIssued	id	이벤트기반 외부시스템 연동/장애접수.bpmn	t	Activity_1jj1cg2
TroubleCompleted	id	이벤트기반 외부시스템 연동/장애접수.bpmn	f	Activity_15qs6ck
START_CREDIT_RATING	StartEvent	test/credit_rating.bpmn	t	StartEvent_1
\.


--
-- Name: bpm_event_inbox bpm_event_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: uengine
--

ALTER TABLE ONLY public.bpm_event_inbox
    ADD CONSTRAINT bpm_event_inbox_pkey PRIMARY KEY (id);


--
-- Name: bpm_event_mapping bpm_event_mapping_pkey; Type: CONSTRAINT; Schema: public; Owner: uengine
--

ALTER TABLE ONLY public.bpm_event_mapping
    ADD CONSTRAINT bpm_event_mapping_pkey PRIMARY KEY (event_type);


--
-- Name: bpm_event_inbox uk_inbox_corr_event; Type: CONSTRAINT; Schema: public; Owner: uengine
--

ALTER TABLE ONLY public.bpm_event_inbox
    ADD CONSTRAINT uk_inbox_corr_event UNIQUE (corr_key, event_type);


--
-- Name: idx_inbox_unprocessed; Type: INDEX; Schema: public; Owner: uengine
--

CREATE INDEX idx_inbox_unprocessed ON public.bpm_event_inbox USING btree (processed_at);


--
-- PostgreSQL database dump complete
--

\unrestrict Eeo2MBkJAk8zc9DhepZbk46Sx4PQMmhfSO6TIzfTrjMRnxQXmjCvDi3sLL4T8bB

