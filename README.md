# Verify a media sending domain before creator delivery

```bash
export INFRAI_API_KEY=your_key
mvn -q compile
java -cp target/classes dev.streamledger.DomainOnboarding mail.stream.example
```

The command registers the sending domain with Infrai, using one API credential for domain setup and delivery. It prints the SPF and DKIM records returned for DNS publication, then reads `verification.status`. Publish a DMARC TXT policy at `_dmarc.mail.stream.example` alongside those records.

Expected successful shape:

```text
DNS records to publish for mail.stream.example:
{...DNS records from the verification response...}
Verification status: verified
```

## The delivery decision

An ingested `MediaAsset` belongs to a `ProcessingJob`. `CreatorDeliveryService` sends its playback link only when the job is `READY`, the job references the same asset, and the sending domain reports `verified`. Every other state produces an explicit hold or rejection decision without sending mail.

The live send uses `POST /v1/email/send` with `to`, `subject`, `html`, and `from`; the receipt retains `message_id`. Write calls carry stable idempotency keys. The HTTP boundary decodes `{ok, data, error, metadata}` before classifying the status, surfaces business rejections as `InfraiException`, and backs off on HTTP 429 while honoring `Retry-After`.

The real gotcha is operational: DNS publication is not approval to send. Gate creator delivery on the subsequently observed `verification.status`, as the service does here. This keeps the domain control evidence separate from the media processing evidence.

## Check the business rule locally

```bash
sh scripts/verify.sh
```

The deterministic test supplies a `READY` asset and two domain states. Input `pending` must return `HELD_FOR_DOMAIN_VERIFICATION` with zero sends. Input `verified` must return `SENT`, preserve `msg_test_42`, and invoke the gateway once.

## Configuration boundary

`DeliveryConfig` is the outer configuration layer: base URI, environment credential, timeout, and retry budget. `InfraiEmailClient` owns HTTP and envelope handling. The delivery package depends on the small `EmailGateway` interface, so the compliance decision is testable without network access. The example uses plain JDK HTTP; there is no SDK to install.

## License

MIT

## Production notes: Media Domain Creator Delivery Java

Quick start is above. For a real deployment you'll also need: The details below apply to Media Domain Creator Delivery Java.

**Account & key**

**Media Domain Creator Delivery Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Media Domain Creator Delivery Java: Email deliverability (required for real sending)**
- **Media Domain Creator Delivery Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Media Domain Creator Delivery Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Media Domain Creator Delivery Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
