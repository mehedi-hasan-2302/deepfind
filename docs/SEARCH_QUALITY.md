# Search Quality Evaluation

DeepFind keeps a deterministic local evaluation corpus in `SearchQualityEvaluationTests`. It is intentionally small enough to understand when a ranking change fails and broad enough to cover the common searches named in the master build instructions.

## Corpus

| File | Representative indexed text | Purpose |
| --- | --- | --- |
| `legal/contract.pdf` | customer refund policy | content-only refund result |
| `finance/receipt.txt` | vendor invoice record | content-tier invoice result |
| `cloud/cloud-notes.md` | AWS cancellation procedure | multi-term content result |
| `career/resume.docx` | salary expectation | filename and content behavior |
| `meetings/meeting-notes.txt` | contract renewal and cloud budget | realistic distractor |
| `finance/invoice-2026.pdf` | quarterly billing statement | filename-prefix tier |
| `finance/customer-invoice-notes.txt` | billing follow-up | filename-token tier |
| `archive/invoice/client-notes.txt` | archived correspondence | path tier |

The test indexes controlled text directly through the same Lucene document path used after extraction. Parser correctness remains covered separately by real TXT, PDF, and DOCX integration tests.

## Expected common queries

| Query | Expected first result | Explanation |
| --- | --- | --- |
| `refund` | `contract.pdf` | document content |
| `AWS cancellation` | `cloud-notes.md` | both required content terms |
| `salary expectation` | `resume.docx` | both required content terms |
| `invoice` | `invoice-2026.pdf` | filename prefix outranks weaker tiers |
| `resume` | `resume.docx` | filename prefix |

The `invoice` evaluation also requires this relative order: filename prefix → filename token → folder path → document content. Phrase, filter, fuzzy, highlighting, and pagination behavior have dedicated regression suites.

## Change policy

Do not adjust boosts because a score looks large or small in isolation. Add a representative corpus case and a user-facing expected order first, reproduce the failure, then make the narrowest ranking change that fixes it without breaking existing expectations. Raw Lucene scores are implementation details and are not an interface contract.

STEP 21 established the baseline without changing production weights: all expected searches and ranking tiers already passed. Future corpus growth should be driven by observed false positives, false negatives, or unintuitive ordering.
