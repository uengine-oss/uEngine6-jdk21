package org.uengine.hwlife.instance;

final class BulkAssignResultCode {

  static final String INVALID_REQUEST = "INVALID_REQUEST";
  static final String EMPTY_WORK_LIST = "EMPTY_WORK_LIST";
  static final String MISSING_ACTOR = "MISSING_ACTOR";
  static final String MISSING_HANDLER = "MISSING_HANDLER";
  static final String HANDLER_NOT_FOUND = "HANDLER_NOT_FOUND";
  static final String MISSING_TASK_ID = "MISSING_TASK_ID";
  static final String DUPLICATE_TASK = "DUPLICATE_TASK";
  static final String INVALID_TASK_ID = "INVALID_TASK_ID";
  static final String WORKITEM_NOT_FOUND = "WORKITEM_NOT_FOUND";
  static final String INSTANCE_MISMATCH = "INSTANCE_MISMATCH";
  static final String WORKITEM_NOT_NEW = "WORKITEM_NOT_NEW";
  static final String ALREADY_ASSIGNED = "ALREADY_ASSIGNED";
  static final String NOT_BULK_ASSIGNABLE = "NOT_BULK_ASSIGNABLE";
  static final String CLAIM_REJECTED = "CLAIM_REJECTED";
  static final String ASSIGNMENT_FAILED = "ASSIGNMENT_FAILED";

  private BulkAssignResultCode() {
  }
}
