# Privacy Threat Modeling: LINDDUN Framework

A LINDDUN framework systematizes the identification and mitigation of privacy vulnerabilities in software architectures, ensuring alignment with Privacy by Design principles required by GDPR and eIDAS 2.0.

## 1. Linkability (Trackability / Linkability)

**Concept:** The ability of an attacker (or legitimate entity) to correlate two or more interactions, transactions, or credential presentations as belonging to the same data subject, enabling the construction of behavioral profiles without knowledge of the real identity.

**Example in the EUDIW Ecosystem:**

- The wallet holder presents their Personal Identification Data (PID) in mDoc format to different Relying Parties (RPs).
- If the credential uses a conventional static Issuer signature (e.g., ECDSA), verifiers can collude and correlate the hash of that signature, tracking the user's activity across multiple services.

**Technical Mitigation:**

- Implementation of Zero-Knowledge Proofs (ZKPs)
- BBS+ signatures for proof randomization
- Dynamic generation of peer Decentralized Identifiers (Peer DIDs)
- Unlinkability at the transport layer via OpenID4VP

## 2. Identifiability

**Concept:** The ability to deduce the real identity of an individual from a set of attributes processed under a pseudonymous regime, using metadata correlation or external databases.

**Example in the EUDIW Ecosystem:**

- A user relies on selective disclosure (SD-JWT) to prove they are over 18 and their postal code to access a regional service.
- Although the name is not revealed, correlating postal code, access timestamp, and IP address can allow the Relying Party to reidentify the holder.

**Technical Mitigation:**

- Strict data minimization (least privilege)
- Attribute obfuscation (e.g., k-anonymity in Revocation Lists)
- Routing through anonymization networks
- Suppression of correlatable network identifiers

## 3. Non-Repudiation

**Concept:** While in classic security non-repudiation is a fundamental requirement (assurance of authorship), in the privacy domain it becomes a threat when it eliminates the user's plausible deniability for actions that do not require a legal binding.

**Example in the EUDIW Ecosystem:**

- An electronic voting system or health service access that requires the wallet to sign each HTTP request with a Qualified Electronic Signature (QES) based on a nominative certificate.
- This creates an irrefutable cryptographic trail of the user's presence and intent in a context where anonymity should be preserved.

**Technical Mitigation:**

- Restrict QES use strictly to acts with contractual legal value
- For common interactions, adopt ephemeral keys (e.g., DPoP with on-the-fly generated keys)
- Ring signatures
- Mathematical attribute verification protocols that do not attest sender identity

## 4. Detectability

**Concept:** The ability to infer the existence of data, the presence of a user, or the occurrence of a transaction, even when the communication channel is cryptographically protected and the content is obfuscated.

**Example in the EUDIW Ecosystem:**

- During trust establishment (Device Engagement) and credential presentation via BLE (Bluetooth Low Energy) or Wi-Fi Aware, a passive attacker monitors the radio frequency spectrum.
- Even without decrypting the payload (protected by mTLS/JWE), the attacker detects traffic pattern, packet size, and frequency, deducing that an identity transaction is occurring at a specific location.

**Technical Mitigation:**

- Implementation of cryptographic padding (adding random bytes to standardize JSON payload sizes)
- Injection of dummy traffic
- Obfuscation of metadata at the transport layer

## 5. Disclosure of Information

**Concept:** Unauthorized extraction, interception, or exposure of sensitive attributes, failing to preserve the confidentiality of the data subject (the threat most parallel to its counterpart in the STRIDE model).

**Example in the EUDIW Ecosystem:**

- The Wallet Application (WDA) stores the SD-JWT structure in plaintext or uses a weak cipher in the local SQLite database.
- A malicious application hosted on the same smartphone exploits a privilege escalation vulnerability to extract identity material (PID).

**Technical Mitigation:**

- Cryptographic custody in secure hardware (Trusted Execution Environment - TEE / StrongBox)
- AEAD cipher (AES-256-GCM) for data at rest
- Immediate sanitization of transient memory (zero-fill) after use of cryptographic material

## 6. Content Unawareness

**Concept:** The information asymmetry in which the data subject does not know the extent, purpose, or recipients of the information being transacted, compromising the legal validity of their consent.

**Example in the EUDIW Ecosystem:**

- The Relying Party sends a complex presentation request using Digital Credentials Query Language (DCQL).
- The wallet user interface (UI) fails to translate this technical query into natural language, causing the user to consent to sharing their full medical history when the service required only a vaccination credential.

**Technical Mitigation:**

- Explicit and granular consent interfaces (Just-in-Time Consent) compliant with WCAG 2.1
- Rigorous semantic mapping of claims to natural language
- Implementation of strictly user-centered (user-driven) flows

## 7. Policy and Consent Noncompliance

**Concept:** Processing, retention, or sharing of data by the Verifier (or Issuer) in violation of legislative directives (e.g., GDPR purpose limitation) or in noncompliance with terms agreed at the time of credential presentation.

**Example in the EUDIW Ecosystem:**

- The user presents their mobile driving license (mDL) to rent a vehicle.
- The Verifier collects the credential to prove license validity (Purpose A), but later retains the document permanently and shares biographical data with third-party insurers without the holder's prior consent (Purpose B).

**Technical Mitigation:**

- Decentralized trust governance
- Trust List-based audits
- Incorporation of Sticky Policies (where usage rules travel with the credential)
- Technological limitation of excess storage on the verifier side

## Architecture Note

When crossing this LINDDUN matrix with the STRIDE matrix, the system achieves exhaustive coverage: STRIDE ensures the infrastructure resists attack vectors, while LINDDUN ensures the architecture itself does not become a surveillance or exposure tool for the holder.
