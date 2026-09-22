# Security policy

## Reporting vulnerabilities

Please do not open a public issue for a vulnerability. After the repository is created on GitHub,
use **Security → Advisories → Report a vulnerability** for private disclosure. If private
vulnerability reporting is not enabled yet, contact the repository owners through the private
contact configured in the repository settings before publishing technical details.

## Scope

This project is primarily a runtime and UI framework. Security issues may include:

- unsafe task execution
- thread lifecycle leaks
- unbounded resource growth
- unsafe config parsing
- insecure default behavior

## Response

Reports will be acknowledged within 7 calendar days when a reachable private channel is provided.
Fixes will be prioritized according to severity and the correctness impact on users. A coordinated
disclosure date will be agreed with the reporter when the issue requires a public advisory.
