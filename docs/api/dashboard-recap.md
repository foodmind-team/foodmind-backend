# Dashboard and weekly recap API examples

The server owns every metric formula. Dashboard rows expose the metric code,
period, value, unit/currency, samples, denominator, and `empty` state so Web
and Android render identical values without recomputation.

## Dashboard

```http
GET /api/v1/dashboard?from=2026-07-27&to=2026-08-03&groupBy=WEEK&timeZone=Asia/Singapore
Authorization: Bearer <access-token>
```

```json
{
  "from": "2026-07-26T16:00:00Z",
  "to": "2026-08-02T16:00:00Z",
  "groupBy": "WEEK",
  "timeZone": "Asia/Singapore",
  "empty": false,
  "metrics": [
    {
      "code": "SPENDING_TOTAL",
      "label": "Spending total",
      "period": "2026-07-27",
      "value": 12.50,
      "unit": "MONEY",
      "currency": "SGD",
      "samples": 2,
      "denominator": 2,
      "empty": false,
      "dimension": "SGD",
      "dimensionLabel": "SGD"
    },
    {
      "code": "ACCEPTANCE_RATE",
      "label": "Recommendation acceptance rate",
      "period": "2026-07-27",
      "value": null,
      "unit": "RATE",
      "currency": null,
      "samples": 0,
      "denominator": 0,
      "empty": true,
      "dimension": null,
      "dimensionLabel": null
    }
  ],
  "spendingTotals": []
}
```

`spendingTotals` is a convenience subset of `metrics`; there is one row for
each original currency and no server-side conversion or cross-currency total.

## Weekly recap

```http
GET /api/v1/weekly-recaps/2026-07-27
Authorization: Bearer <access-token>
```

`weekStart` must be a Monday in the authenticated user's profile timezone. An
empty week returns `200` with `empty: true`, not made-up zeros for undefined
rates. The recap reads the dedicated weekly projection; it is not a client-side
recalculation of live dashboard interactions.
