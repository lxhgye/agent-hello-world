package cn.cgn.chat.service.gAIAgentic;

import java.util.regex.Pattern;

public class PersonalAssistantConfig {
    static final int MAXIMUM_AGENT_INVOCATIONS = 10;
    static final int MAXIMUM_DEVICE_TOOL_CALLING_ROUND_TRIPS = 20;
    static final String REQUEST_KEY = "request";
    static final String RESEARCH_RESULT_KEY = "researchResult";
    static final String DEVICE_OPERATION_RESULT_KEY = "deviceOperationResult";
    static final String GENERAL_ANSWER_KEY = "generalAnswer";
    static final String REVIEW_RESULT_KEY = "reviewResult";
    static final int MAXIMUM_LOG_TEXT_LENGTH = 500;
    static final int MAXIMUM_FRONTEND_TOOL_DETAIL_LENGTH = 220;
    static final long PLAN_STREAM_TIMEOUT_SECONDS = 120L;
    static final String PLAN_STREAM_PREFIX = "\u0000P";
    static final String ANSWER_START_PREFIX = "\u0000S";
    static final String ANSWER_STREAM_PREFIX = "\u0000A";
    static final String TOOL_STATUS_START_PREFIX = "\u0000W";
    static final String TOOL_STATUS_END_PREFIX = "\u0000E";
    static final String AGENT_STAGE_PREFIX = "\u0000G";
    static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\]\\)>\\\"']+");
    static final Pattern QUERY_ARGUMENT_PATTERN= Pattern.compile(
            "\\\"(?:query|q|arg0)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
            Pattern.CASE_INSENSITIVE);
    static final Pattern SENSITIVE_ARGUMENT_PATTERN=Pattern.compile(
            "(\\\"(?:content|text|contents|data)\\\"\\s*:\\s*\\\")(.*?)(\\\")(?=\\s*[,}])",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public PersonalAssistantConfig() {
    }
}