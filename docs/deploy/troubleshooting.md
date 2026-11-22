# Troubleshooting

| Symptom                                  | Check                                                                                   |
| ---------------------------------------- | --------------------------------------------------------------------------------------- |
| `/extension/v1/health/ready` returns 503 | The service can't reach PostgreSQL. Check `docker compose logs tandem-postgres`.        |
| The app shows planning sync waiting      | The service is unreachable or `/extension/v1/` isn't routed. Budget sync is unaffected. |
| Requests return 503 `actual_unavailable` | `ACTUAL_SERVER_URL` is wrong or Actual is down.                                         |
| Requests return 403 `budget_forbidden`   | The signed-in Actual account can't open that budget.                                    |
