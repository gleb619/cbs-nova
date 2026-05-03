# API Contract

← [Back to TDD](../tdd.md)

## 10. API Contract

### 10.1 Execute Single Event (Unchanged)

```
POST /api/events/execute
{ "code", "action", "eventNumber"?, "eventParameters" }
```

### 10.2 Trigger Mass Operation Manually

```
POST /api/mass-operations/trigger
Content-Type: application/json
Authorization: Bearer {token}

{
  "code": "INTEREST_CHARGE",
  "context": {
    "date": "2025-06-01"
  }
}
```

Response:

```json
{
  "executionId": 5001,
  "code": "INTEREST_CHARGE",
  "category": "CREDITS",
  "status": "RUNNING",
  "totalItems": 84320,
  "startedAt": "2025-06-01T01:00:00Z"
}
```

### 10.3 Mass Operation Status

```
GET /api/mass-operations/{executionId}
```

```json
{
  "executionId": 5001,
  "code": "INTEREST_CHARGE",
  "status": "DONE_WITH_FAILURES",
  "totalItems": 84320,
  "processedCount": 84273,
  "failedCount": 47,
  "startedAt": "2025-06-01T01:00:00Z",
  "completedAt": "2025-06-01T03:14:22Z"
}
```

### 10.4 Mass Operation Items

```
GET /api/mass-operations/{executionId}/items?status=FAILED&page=0&size=50
```

```json
{
  "items": [
    {
      "itemId": 91234,
      "itemKey": "AGR-00198",
      "status": "FAILED",
      "errorMessage": "CreditBorrowerAccountTransaction: account frozen",
      "startedAt": "2025-06-01T01:04:11Z",
      "workflowEventNumber": 10042
    }
  ],
  "totalFailed": 47,
  "page": 0
}
```

### 10.5 Retry Failed Item

```
POST /api/mass-operations/{executionId}/items/{itemId}/retry
Authorization: Bearer {token}
```

```json
{
  "newItemId": 91890,
  "itemKey": "AGR-00198",
  "status": "RUNNING",
  "retryOf": 91234
}
```

---

### Response Shapes

### 9.2 Success Response

```json
{
  "eventNumber": 10042,
  "eventCode": "LOAN_DISBURSEMENT",
  "dslVersion": "1.5.0-a3f91bc",
  "action": "APPROVE",
  "status": "DONE",
  "workflow": {
    "workflowCode":  "LOAN_CONTRACT",
    "previousState": "ENTERED",
    "currentState":  "ACTIVE"
  },
  "display": {
    "Customer ID": "C-001",
    "Loan ID":     "L-9981",
    "Amount":      "500000",
    "Account":     "KZ123456789"
  },
  "results": [
    { "transaction": "KYC_CHECK",                "status": "EXECUTED" },
    { "transaction": "DEBIT_FUNDING_ACCOUNT",    "status": "EXECUTED" },
    { "transaction": "CREDIT_BORROWER_ACCOUNT",  "status": "EXECUTED" },
    { "transaction": "POST_DISBURSEMENT_ENTRY",  "status": "EXECUTED" }
  ]
}
```

### 9.3 Fault Response

```json
{
  "eventNumber": 10043,
  "action": "APPROVE",
  "status": "FAULTED",
  "workflow": {
    "previousState": "ENTERED",
    "currentState":  "FAULTED",
    "faultMessage":  "CreditBorrowerAccountTransaction: account frozen"
  },
  "compensated": ["DEBIT_FUNDING_ACCOUNT"]
}
```

### 9.4 Validation Errors

```json
{ "error": "INVALID_TRANSITION",  "currentState": "ACTIVE",   "action": "SUBMIT" }
{ "error": "MISSING_PARAMETERS",  "missing": ["loanId"] }
{ "error": "CONTEXT_FAULT",       "message": "FIND_CUSTOMER_CODE_BY_ID: not found" }
```
