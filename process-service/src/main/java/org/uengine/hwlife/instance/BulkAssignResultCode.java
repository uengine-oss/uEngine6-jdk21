package org.uengine.hwlife.instance;

final class BulkAssignResultCode {

  static final String INVALID_REQUEST = "LBM070001";
  static final String EMPTY_WORK_LIST = "LBM070002";
  static final String MISSING_ACTOR = "LBM070003";
  static final String MISSING_HANDLER = "LBM070004";
  static final String HANDLER_NOT_FOUND = "LBM070005";
  static final String MISSING_TASK_ID = "LBM070006";
  static final String DUPLICATE_TASK = "LBM070007";
  static final String INVALID_TASK_ID = "LBM070008";
  static final String WORKITEM_NOT_FOUND = "LBM070009";
  static final String INSTANCE_MISMATCH = "LBM070010";
  static final String WORKITEM_NOT_NEW = "LBM070011";
  static final String ALREADY_ASSIGNED = "LBM070012";
  static final String NOT_BULK_ASSIGNABLE = "LBM070013";
  static final String CLAIM_REJECTED = "LBM070019";
  static final String ASSIGNMENT_FAILED = "LBM070020";

  private BulkAssignResultCode() {
  }
}
