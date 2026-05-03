# Handmade Pages — Settings & Complex Dictionaries

← [Back to TDD](../tdd.md)

## 11. Handmade Pages — Settings & Complex Dictionaries

### 11.1 Settings Page

Route: `/admin/system/settings`

Not a generic datatable. A grouped settings page where each key renders an appropriate input based on the JSON value
structure.

```
┌─────────────────────────────────────────────────────┐
│  System Settings                                    │
│  ─────────────────────────────────────────────────  │
│  ▼ Loan Configuration                               │
│    max_loan_amount         [__500000__]  number     │
│    default_interest_rate   [__12.5___]  number      │
│    auto_approve_threshold  [  ON  ]     boolean     │
│                                                     │
│  ▼ Notifications                                    │
│    sms_enabled             [  ON  ]     boolean     │
│    notification_template   [{...}  ✏️]  json        │
│                                                     │
│  ▼ System                                           │
│    maintenance_mode        [ OFF  ]     boolean     │
│    feature_flags           [{...}  ✏️]  json        │
└─────────────────────────────────────────────────────┘
```

Value type detection: if JSON value is `boolean` → toggle, `number` → numeric input, `string` → text input,
object/array → JSON editor. Groups defined by a `group` field on the setting record (or a dot-prefix convention:
`loan.max_amount`).

### 11.2 Complex Dictionaries

Dictionaries that are flat use the generated generic CRUD (fields from the JSONB structure rendered as `FieldJson` or
broken out if schema is known).

Dictionaries that are nested, versioned, or hierarchical get handmade pages registered via `customRoute`. Example: a
product dictionary with versions would have its own `/admin/dictionaries/loan-products` page with a version timeline
component.
