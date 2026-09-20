# Current limitations

The first beta is still being prepared. These are the limits to expect when trying the current local application.

## Sources and questions

Uploads accept Markdown and UTF-8 text, up to 1 MiB per file by default. PDF, OCR and media uploads are outside the initial scope. Source search matches titles and filenames.

Answers contain passages copied from visible documents. A passage can be quoted correctly and still omit an exception, miss part of a question or contain a mistake in the source itself. The selector can also refuse a question that the documents could answer.

The latest [context and omissions evaluation](../../reviews/2026-09-13-contexto-y-omisiones.md) found improvements alongside regressions. Hybrid retrieval, grouped context and omission checks therefore remain disabled in the default configuration. The [evaluation guide](../../demo/evaluation/README.md) explains how candidates are compared.

Atlas entries and relations are curated separately from document retrieval. Promoting an entry to canon does not change the sources used for questions.

## Accounts and deployment

Owners invite members by email and share the application URL themselves. The application does not send invitation notices. Membership activates when the recipient logs in with the matching verified email.

The supplied Compose stack uses localhost addresses, Keycloak development mode and Mailpit for verification messages. A hosted beta needs HTTPS, production identity and email settings, private service networks, and limits on query and storage use. See the [threat model](../security/THREAT_MODEL.md) for the security details.

Local inference depends on available hardware. Development has used 16 GB RAM and a 6 GB NVIDIA GPU; model loading and the first question can take noticeably longer than later requests. Source-job history also grows without pagination.

## What remains to be checked

The [review index](../../reviews/README.md) records which checks ran on each revision. Automated tests use model doubles; live evaluation measures model behavior, and recovery drills check backups.

The [user session](USER_VALIDATION.md) is prepared but has not yet been run with an external group. The next product question is whether a Game Master and two players can find and verify campaign information without help.
